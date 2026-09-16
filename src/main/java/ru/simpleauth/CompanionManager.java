package ru.simpleauth;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ИИ-компаньон — ручной волк, обвязанный разговором с настоящей языковой
 * моделью (Anthropic API). Ходить и защищать умеет "бесплатно" — это
 * готовое ванильное поведение приручённого волка.
 *
 * Что делает его действительно думающим, а не просто болтающим:
 *  - к каждому запросу подмешивается реальная обстановка (CompanionContext):
 *    время, погода, здоровье, кто рядом, где вы;
 *  - он может САМ действовать — вставляет в ответ теги вроде [СИДЕТЬ]
 *    или [АТАКОВАТЬ], которые тут выполняются по-настоящему;
 *  - он сам решает, что запомнить надолго, через [ЗАПОМНИТЬ: ...]
 *    (CompanionMemory, переживает рестарт сервера).
 * Все служебные теги вырезаются из реплики до показа в чате.
 */
public class CompanionManager {

    private static final Pattern REMEMBER = Pattern.compile("\\[ЗАПОМНИТЬ:([^\\]]*)\\]");
    private static final Pattern ACTION = Pattern.compile("\\[(СИДЕТЬ|ЗА МНОЙ|АТАКОВАТЬ)\\]");

    private final SessionManager manager;
    private final Map<UUID, List<CompanionAI.Turn>> history = new ConcurrentHashMap<>();
    private final CompanionMemory memory = new CompanionMemory();

    public CompanionManager(SessionManager manager) {
        this.manager = manager;
    }

    public CompanionMemory memory() {
        return memory;
    }

    /**
     * Проверяет, обращено ли сообщение из чата к компаньону по имени.
     * Понимает "Али, привет", "али: привет", "Али привет" — регистр не важен.
     * Возвращает сам текст без имени, либо null если обращения нет.
     *
     * Имя должно стоять в начале сообщения: иначе любое упоминание клички
     * посреди разговора с другими игроками перехватывалось бы ботом.
     */
    public String extractAddressed(String raw) {
        ServerSettings cfg = manager.config();
        if (!cfg.companionEnabled) return null;
        String name = cfg.companionName;
        if (name == null || name.isBlank() || raw == null) return null;

        String trimmed = raw.trim();
        if (trimmed.length() < name.length()) return null;
        if (!trimmed.substring(0, name.length()).equalsIgnoreCase(name)) return null;

        String rest = trimmed.substring(name.length());
        // после имени должен идти разделитель, иначе "Алина" сработает как "Али"
        if (!rest.isEmpty() && Character.isLetterOrDigit(rest.charAt(0))) return null;

        rest = rest.replaceFirst("^[\\s,:!\\-—]+", "").trim();
        return rest.isEmpty() ? null : rest;
    }

    /** Находит уже заспавненного компаньона по UUID из конфига, если он ещё существует в мире. */
    public WolfEntity find(MinecraftServer server) {
        ServerSettings cfg = manager.config();
        if (cfg.companionUuid == null || cfg.companionUuid.isBlank()) return null;
        UUID uuid;
        try {
            uuid = UUID.fromString(cfg.companionUuid);
        } catch (IllegalArgumentException e) {
            return null;
        }
        for (ServerWorld world : server.getWorlds()) {
            if (world.getEntity(uuid) instanceof WolfEntity wolf) {
                return wolf;
            }
        }
        return null;
    }

    /** Спавнит нового компаньона рядом с игроком. */
    public WolfEntity spawn(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        WolfEntity wolf = EntityType.WOLF.create(world);
        if (wolf == null) return null;

        BlockPos pos = player.getBlockPos();
        wolf.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                player.getYaw(), 0);
        wolf.setOwner(player);
        wolf.setTamed(true, true);
        wolf.setSitting(false);
        wolf.setPersistent();
        wolf.setCustomName(Text.literal(manager.config().companionName).formatted(Formatting.LIGHT_PURPLE));
        wolf.setCustomNameVisible(true);
        wolf.setHealth(wolf.getMaxHealth());

        world.spawnEntity(wolf);

        ServerSettings cfg = manager.config();
        cfg.companionUuid = wolf.getUuid().toString();
        cfg.save();
        history.remove(wolf.getUuid());
        return wolf;
    }

    /**
     * Отправляет сообщение компаньону и асинхронно доставляет ответ в чат.
     * Ответ приходит из чужого потока, поэтому всё, что трогает игрока и
     * сущности, выполняется через server.execute(...).
     */
    public void say(ServerPlayerEntity player, WolfEntity companion, String message) {
        ServerSettings cfg = manager.config();
        MinecraftServer server = player.getServer();
        if (server == null) return;

        String name = cfg.companionName;
        player.sendMessage(
                Text.literal(name + " ").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD)
                        .append(Text.literal("думает...").formatted(Formatting.GRAY, Formatting.ITALIC)),
                false);

        List<CompanionAI.Turn> turns = history.computeIfAbsent(companion.getUuid(), k -> new ArrayList<>());

        // обстановка и память собираются СЕЙЧАС, в главном потоке — трогать
        // мир из потока HTTP-ответа нельзя
        String situation = CompanionContext.build(player, companion);
        String prompt = buildPrompt(situation, message);

        CompanionAI.ask(cfg, turns, prompt)
                .thenAccept(raw -> server.execute(() -> {
                    String reply = handleMemory(raw);
                    reply = handleActions(reply, player, companion);
                    reply = reply.trim();

                    turns.add(new CompanionAI.Turn("user", message));
                    turns.add(new CompanionAI.Turn("assistant", reply));
                    trimHistory(turns, cfg.companionHistoryTurns);

                    if (reply.isEmpty()) return;
                    player.sendMessage(
                            Text.literal(name + ": ").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD)
                                    .append(Text.literal(reply).formatted(Formatting.WHITE)),
                            false);
                }))
                .exceptionally(ex -> {
                    server.execute(() -> player.sendMessage(
                            Text.literal(name + " не смог ответить: ")
                                    .formatted(Formatting.RED)
                                    .append(Text.literal(rootMessage(ex)).formatted(Formatting.GRAY)),
                            false));
                    return null;
                });
    }

    /** Склеивает память, обстановку и саму реплику игрока в один запрос. */
    private String buildPrompt(String situation, String message) {
        StringBuilder sb = new StringBuilder();

        List<String> facts = memory.all();
        if (!facts.isEmpty()) {
            sb.append("Что ты помнишь с прошлых разговоров:\n");
            for (String fact : facts) {
                sb.append("- ").append(fact).append('\n');
            }
            sb.append('\n');
        }

        sb.append(situation).append('\n');
        sb.append("Игрок говорит: ").append(message);
        return sb.toString();
    }

    /** Вырезает из ответа теги [ЗАПОМНИТЬ: ...] и складывает их в долговременную память. */
    private String handleMemory(String raw) {
        Matcher matcher = REMEMBER.matcher(raw);
        StringBuilder cleaned = new StringBuilder();
        while (matcher.find()) {
            memory.add(matcher.group(1));
            matcher.appendReplacement(cleaned, "");
        }
        matcher.appendTail(cleaned);
        return cleaned.toString();
    }

    /** Вырезает теги действий и реально их выполняет. */
    private String handleActions(String raw, ServerPlayerEntity player, WolfEntity companion) {
        Matcher matcher = ACTION.matcher(raw);
        StringBuilder cleaned = new StringBuilder();
        while (matcher.find()) {
            switch (matcher.group(1)) {
                case "СИДЕТЬ" -> companion.setSitting(true);
                case "ЗА МНОЙ" -> companion.setSitting(false);
                case "АТАКОВАТЬ" -> {
                    companion.setSitting(false);
                    attackNearest(player, companion);
                }
                default -> {
                }
            }
            matcher.appendReplacement(cleaned, "");
        }
        matcher.appendTail(cleaned);
        return cleaned.toString();
    }

    /** Натравливает компаньона на ближайшего враждебного моба рядом с игроком. */
    private void attackNearest(ServerPlayerEntity player, WolfEntity companion) {
        ServerWorld world = player.getServerWorld();
        Box box = player.getBoundingBox().expand(16);
        HostileEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (HostileEntity mob : world.getEntitiesByClass(HostileEntity.class, box, e -> e.isAlive())) {
            double dist = mob.squaredDistanceTo(player);
            if (dist < best) {
                best = dist;
                nearest = mob;
            }
        }
        if (nearest != null) {
            companion.setTarget(nearest);
        }
    }

    private static void trimHistory(List<CompanionAI.Turn> turns, int keepTurns) {
        int maxEntries = Math.max(2, keepTurns * 2);
        while (turns.size() > maxEntries) {
            turns.remove(0);
        }
    }

    private static String rootMessage(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) cause = cause.getCause();
        String msg = cause.getMessage();
        return msg != null ? msg : cause.getClass().getSimpleName();
    }
}
