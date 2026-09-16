package ru.simpleauth;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Обращения к Claude API. Запрос уходит в отдельный поток, ответ возвращается
 * колбэком уже в серверном потоке — сеть никогда не блокирует тик.
 */
public class AiBrain {

    private static final String ENDPOINT = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";
    private static final Gson GSON = new Gson();

    private record Turn(String role, String text) {
    }

    private final SessionManager manager;
    private final Map<UUID, Deque<Turn>> memory = new ConcurrentHashMap<>();
    private final AtomicInteger inFlight = new AtomicInteger();
    private boolean keyWarned;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ExecutorService pool = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "servercore-ai");
        thread.setDaemon(true);
        return thread;
    });

    public AiBrain(SessionManager manager) {
        this.manager = manager;
    }

    public void forget(UUID player) {
        memory.remove(player);
    }

    public void forgetAll() {
        memory.clear();
    }

    /** Ключ берём из переменной окружения, а если её нет — из конфига. */
    private String apiKey() {
        ServerSettings cfg = manager.config();
        if (cfg.aiApiKeyEnv != null && !cfg.aiApiKeyEnv.isBlank()) {
            String value = System.getenv(cfg.aiApiKeyEnv);
            if (value != null && !value.isBlank()) return value.trim();
        }
        if (cfg.aiApiKey != null && !cfg.aiApiKey.isBlank()) return cfg.aiApiKey.trim();
        return null;
    }

    public boolean hasKey() {
        return apiKey() != null;
    }

    public boolean busy() {
        return inFlight.get() > 0;
    }

    /**
     * Спросить модель от имени игрока. reply вызывается в серверном потоке,
     * поэтому внутри него можно трогать мир и игроков.
     */
    public void ask(UUID speaker, String speakerName, String message, Consumer<String> reply) {
        String key = apiKey();
        if (key == null) {
            if (!keyWarned) {
                keyWarned = true;
                ServerCore.LOGGER.warn("[ServerCore] AI: ключ не найден — задай переменную {} "
                        + "или aiApiKey в конфиге, иначе NPC будет молчать.", manager.config().aiApiKeyEnv);
            }
            return;
        }
        // Очередь не копим: лучше пропустить реплику, чем платить за десять сразу.
        if (inFlight.get() >= 3) return;

        ServerSettings cfg = manager.config();
        Deque<Turn> history = memory.computeIfAbsent(speaker, id -> new ArrayDeque<>());
        String question = speakerName + ": " + message;
        JsonObject body = buildBody(cfg, history, question);

        inFlight.incrementAndGet();
        pool.submit(() -> {
            String answer = null;
            try {
                answer = send(key, body);
            } catch (Exception e) {
                ServerCore.LOGGER.warn("[ServerCore] AI: запрос не удался: {}", e.toString());
            } finally {
                inFlight.decrementAndGet();
            }
            if (answer == null || answer.isBlank()) return;

            synchronized (history) {
                history.addLast(new Turn("user", question));
                history.addLast(new Turn("assistant", answer));
                int limit = Math.max(2, cfg.aiMemoryMessages);
                while (history.size() > limit) history.removeFirst();
            }

            String result = answer;
            manager.schedule(1, server -> reply.accept(result));
        });
    }

    private JsonObject buildBody(ServerSettings cfg, Deque<Turn> history, String question) {
        JsonObject body = new JsonObject();
        body.addProperty("model", cfg.aiModel == null || cfg.aiModel.isBlank()
                ? "claude-opus-5" : cfg.aiModel.trim());
        body.addProperty("max_tokens", Math.max(32, cfg.aiMaxTokens));
        body.addProperty("system", cfg.aiSystemPrompt);

        if (cfg.aiEffort != null && !cfg.aiEffort.isBlank()) {
            JsonObject output = new JsonObject();
            output.addProperty("effort", cfg.aiEffort.trim());
            body.add("output_config", output);
        }

        JsonArray messages = new JsonArray();
        synchronized (history) {
            for (Turn turn : history) messages.add(message(turn.role(), turn.text()));
        }
        messages.add(message("user", question));
        body.add("messages", messages);
        return body;
    }

    private static JsonObject message(String role, String text) {
        JsonObject entry = new JsonObject();
        entry.addProperty("role", role);
        entry.addProperty("content", text);
        return entry;
    }

    private String send(String key, JsonObject body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT))
                .timeout(Duration.ofSeconds(60))
                .header("content-type", "application/json")
                .header("anthropic-version", API_VERSION)
                .header("x-api-key", key)
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = http.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            ServerCore.LOGGER.warn("[ServerCore] AI: HTTP {} — {}",
                    response.statusCode(), shorten(response.body()));
            return null;
        }

        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonElement stop = root.get("stop_reason");
        if (stop != null && !stop.isJsonNull() && "refusal".equals(stop.getAsString())) {
            return null;
        }

        JsonElement content = root.get("content");
        if (content == null || !content.isJsonArray()) return null;

        StringBuilder text = new StringBuilder();
        for (JsonElement element : content.getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            JsonObject block = element.getAsJsonObject();
            JsonElement type = block.get("type");
            if (type != null && "text".equals(type.getAsString()) && block.has("text")) {
                text.append(block.get("text").getAsString());
            }
        }
        return text.toString().trim();
    }

    private static String shorten(String body) {
        if (body == null) return "";
        String flat = body.replaceAll("\\s+", " ").trim();
        return flat.length() > 300 ? flat.substring(0, 300) + "..." : flat;
    }
}
