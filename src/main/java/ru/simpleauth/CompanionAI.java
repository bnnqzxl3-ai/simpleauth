package ru.simpleauth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Обёртка над Anthropic Messages API (https://api.anthropic.com/v1/messages).
 * Запрос идёт асинхронно (HttpClient.sendAsync), поэтому не блокирует
 * основной поток сервера — результат возвращается через CompletableFuture
 * и должен доставляться игроку только через server.execute(...), чтобы не
 * трогать сущности/игроков из чужого потока.
 */
public class CompanionAI {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private CompanionAI() {
    }

    /** Одна реплика диалога — для истории в запросе. */
    public record Turn(String role, String text) {
    }

    /**
     * Отправляет запрос и возвращает текст ответа. Ошибки (сеть, ключ,
     * лимиты) заворачиваются в понятное сообщение через exceptionally на
     * стороне вызывающего кода — сюда прилетает либо готовый текст, либо
     * исключение.
     */
    public static CompletableFuture<String> ask(ServerSettings cfg, List<Turn> history, String userMessage) {
        if (cfg.companionApiKey == null || cfg.companionApiKey.isBlank()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("API-ключ не задан (companionApiKey в config.json)"));
        }

        JsonObject body = new JsonObject();
        body.addProperty("model", cfg.companionModel);
        body.addProperty("max_tokens", cfg.companionMaxTokens);
        body.addProperty("system", cfg.companionSystemPrompt.replace("%name%", cfg.companionName));

        JsonArray messages = new JsonArray();
        for (Turn turn : history) {
            JsonObject msg = new JsonObject();
            msg.addProperty("role", turn.role());
            msg.addProperty("content", turn.text());
            messages.add(msg);
        }
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userMessage);
        messages.add(userMsg);
        body.add("messages", messages);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.anthropic.com/v1/messages"))
                .timeout(Duration.ofSeconds(30))
                .header("content-type", "application/json")
                .header("x-api-key", cfg.companionApiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(CompanionAI::extractText);
    }

    private static String extractText(HttpResponse<String> response) {
        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();

        if (response.statusCode() != 200) {
            String message = root.has("error") && root.getAsJsonObject("error").has("message")
                    ? root.getAsJsonObject("error").get("message").getAsString()
                    : "HTTP " + response.statusCode();
            throw new RuntimeException("Anthropic API: " + message);
        }

        JsonArray content = root.getAsJsonArray("content");
        StringBuilder sb = new StringBuilder();
        for (JsonElement el : content) {
            JsonObject block = el.getAsJsonObject();
            if (block.has("type") && "text".equals(block.get("type").getAsString()) && block.has("text")) {
                sb.append(block.get("text").getAsString());
            }
        }
        return sb.toString().trim();
    }
}
