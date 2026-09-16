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
 * ИИ-компаньон — общий для всего сервера ручной волк, с которым может
 * поговорить и которому может отдать простую команду любой игрок (не
 * только владелец). Ходить и защищать умеет "бесплатно" — готовое
 * ванильное поведение приручённого волка плюс собственное доследование за
 * ближайшим игроком (см. tickFollow), раз он теперь общий, а не личный.
 *
 * Разговор — настоящий вызов Anthropic API, асинхронно. К каждому запросу
 * подмешивается обстановка (CompanionContext) и долговременная память
 * (CompanionMemory, переживает рестарт). И вопрос игрока, и ответ
 * компаньона видны в общем чате — это открытый разговор, не приватный.
 *
 * Администрирование (заспавнить/переименовать/стереть память) остаётся
 * только у владельца — иначе кто угодно мог бы стереть ему память или
 * переименовать. Сам разговор и простые команды открыты всем.
 */
public class CompanionManager {

    private static final Pattern REMEMBER = Pattern.compile("\\[ЗАПОМНИТЬ:([^\\]]*)\\]");
    private static final Pattern ACTION = Pattern.compile("\\[(СИДЕТЬ|ЗА МНОЙ|АТАКОВАТЬ)\\]");

    /** Минимальный перерыв между обращениями от одного игрока — защита от спама по API. */
    private static final long COOLDOWN_MILLIS = 8000;

    private final SessionManager manager;
    private final List<CompanionAI.Turn> history = new ArrayList<>();
    private final CompanionMemory memory = new CompanionMemory();
    private final Map<UUID, Long> lastAsk = new ConcurrentHashMap<>();
    private long lastFollowTick = 0;

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

    /** Спавнит нового компаньона рядом с игроком (только владелец, см. CommandRegistry). */
    public WolfEntity spawn(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server != null) {
            WolfEntity existing = find(server);
            if (existing != null && existing.isAlive()) {
                // уже есть живой компаньон — просто подводим его к игроку,
                // а не плодим второго с тем же именем
                BlockPos pos = player.getBlockPos();
                existing.teleport((ServerWorld) player.getWorld(),
                        pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                        java.util.Set.of(), player.getYaw(), 0);
                return existing;
            }
        }

        ServerWorld world = player.getServerWorld();
        WolfEntity wolf = EntityType.WOLF.create(world);
        if (wolf == null) return null;

        BlockPos pos = player.getBlockPos();
        wolf.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                player.getYaw(), 0);
        // тамим на того, кто заспавнил — это оставляет боевые бонусы
        // приручённого волка (защита владельца), а ходить он будет за
        // ближайшим игроком вообще, не только за ним (см. tickFollow)
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
        history.clear();
        return wolf;
    }

    /**
     * Периодически подводит компаньона к ближайшему онлайн-игроку — он
     * теперь общий, а не личный, поэтому доследование не завязано на
     * ванильного "владельца". Вызывается из SessionManager.tick().
     */
    public void tickFollow(MinecraftServer server) {
        long now = System.currentTimeMillis();
        if (now - lastFollowTick < 1000) return;
        lastFollowTick = now;

        WolfEntity wolf = find(server);
        if (wolf == null) return;
        if (wolf.isSitting()) return;

        ServerPlayerEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.getServerWorld() != wolf.getWorld()) continue;
            double dist = wolf.squaredDistanceTo(p);
            if (dist < best) {
                best = dist;
                nearest = p;
            }
        }
        if (nearest == null) return;

        if (best > 8 * 8 && best < 64 * 64) {
            wolf.getNavigation().startMovingTo(nearest.getX(), nearest.getY(), nearest.getZ(), 1.0);
        }
    }

    /**
     * Отправляет сообщение компаньону и асинхронно доставляет ответ. И
     * реплика игрока, и ответ компаньона видны в общем чате всем —
     * разговор открытый, не приватный.
     *
     * Возвращает false, если сработал перерыв между обращениями (защита
     * от спама по API) — в этом случае обращение просто тихо
     * игнорируется, чат не засоряется отказами.
     */
    public boolean say(ServerPlayerEntity player, WolfEntity companion, String message) {
        long now = System.currentTimeMillis();
        Long last = lastAsk.get(player.getUuid());
        if (last != null && now - last < COOLDOWN_MILLIS) {
            return false;
        }
        lastAsk.put(player.getUuid(), now);

        // переключаемся на того, кто позвал, сразу — не ждём периодическую
        // проверку ближайшего игрока (tickFollow раз в секунду)
        if (!companion.isSitting()) {
            companion.getNavigation().startMovingTo(
                    player.getX(), player.getY(), player.getZ(), 1.0);
        }

        ServerSettings cfg = manager.config();
        MinecraftServer server = player.getServer();
        if (server == null) return true;

        String situation = CompanionContext.build(player, companion);
        String prompt = buildPrompt(situation, player, message);

        CompanionAI.ask(cfg, history, prompt)
                .thenAccept(raw -> server.execute(() -> {
                    String reply = handleMemory(raw);
                    reply = handleActions(reply, player, companion);
                    reply = reply.replaceAll("[ \\t]{2,}", " ").trim();

                    history.add(new CompanionAI.Turn("user", prompt));
                    history.add(new CompanionAI.Turn("assistant", reply));
                    trimHistory(history, cfg.companionHistoryTurns);

                    if (reply.isEmpty()) return;
                    Text out = Text.literal(cfg.companionName + ": ")
                            .formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD)
                            .append(Text.literal(reply).formatted(Formatting.WHITE));
                    server.getPlayerManager().broadcast(out, false);
                }))
                .exceptionally(ex -> {
                    server.execute(() -> player.sendMessage(
                            Text.literal(cfg.companionName + " не смог ответить: ")
                                    .formatted(Formatting.RED)
                                    .append(Text.literal(rootMessage(ex)).formatted(Formatting.GRAY)),
                            false));
                    return null;
                });
        return true;
    }

    /** Склеивает память, обстановку и то, кто именно обращается, в один запрос. */
    private String buildPrompt(String situation, ServerPlayerEntity asker, String message) {
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
        sb.append("К тебе обращается игрок ")
                .append(asker.getGameProfile().getName())
                .append(" (ты дружишь со всеми на сервере, не только с ним): ")
                .append(message);
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
                case "ЗА МНОЙ" -> {
                    companion.setSitting(false);
                    companion.getNavigation().startMovingTo(
                            player.getX(), player.getY(), player.getZ(), 1.0);
                }
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

    /** Натравливает компаньона на ближайшего враждебного моба рядом с игроком, который его позвал. */
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
