package ru.simpleauth;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.IronGolemEntity;
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
 * ИИ-компаньон — общий для всего сервера железный голем, с которым может
 * поговорить и которому может отдать простую команду любой игрок (не
 * только владелец). Специально НЕ приручается ванильным способом
 * (setOwner/setTamed) — у голема, в отличие от волка, вообще нет такого
 * API, а значит нет и встроенного "иди к хозяину", которое раньше
 * конкурировало с нашим собственным доследованием и дёргало его туда-сюда.
 * Теперь ходьба и цель атаки — целиком наш код (tickFollow, attackNearest).
 *
 * Голем помечен как "созданный игроком" (setPlayerCreated) — стандартный
 * ванильный флаг, из-за которого он никогда не нападает на игроков сам по
 * себе, только на то, что мы явно укажем через setTarget.
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
    private static final Pattern ACTION = Pattern.compile("\\[(СИДЕТЬ|ЗА МНОЙ|АТАКОВАТЬ|ТЕЛЕПОРТ)\\]");

    /** Минимальный перерыв между обращениями от одного игрока — защита от спама по API. */
    private static final long COOLDOWN_MILLIS = 8000;

    private final SessionManager manager;
    private final List<CompanionAI.Turn> history = new ArrayList<>();
    private final CompanionMemory memory = new CompanionMemory();
    private final Map<UUID, Long> lastAsk = new ConcurrentHashMap<>();
    private long lastFollowTick = 0;

    /**
     * У голема нет ванильного "сидеть", как у прирученного волка — это
     * свой флаг: пока true, tickFollow его не трогает, он просто стоит.
     * Не переживает рестарт (и не нужно — при рестарте разумно снова
     * начать следовать).
     */
    private volatile boolean staying = false;

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
    public IronGolemEntity find(MinecraftServer server) {
        ServerSettings cfg = manager.config();
        if (cfg.companionUuid == null || cfg.companionUuid.isBlank()) return null;
        UUID uuid;
        try {
            uuid = UUID.fromString(cfg.companionUuid);
        } catch (IllegalArgumentException e) {
            return null;
        }
        for (ServerWorld world : server.getWorlds()) {
            if (world.getEntity(uuid) instanceof IronGolemEntity golem) {
                return golem;
            }
        }
        return null;
    }

    /** Спавнит нового компаньона рядом с игроком (только владелец, см. CommandRegistry). */
    public IronGolemEntity spawn(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server != null) {
            IronGolemEntity existing = find(server);
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
        IronGolemEntity golem = EntityType.IRON_GOLEM.create(world);
        if (golem == null) return null;

        BlockPos pos = player.getBlockPos();
        golem.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                player.getYaw(), 0);
        // "создан игроком" — стандартный ванильный флаг: голем никогда не
        // нападёт на игроков сам по себе, только на то, что укажем сами
        golem.setPlayerCreated(true);
        golem.setPersistent();
        golem.setCustomName(Text.literal(manager.config().companionName).formatted(Formatting.LIGHT_PURPLE));
        golem.setCustomNameVisible(true);
        golem.setHealth(golem.getMaxHealth());

        world.spawnEntity(golem);

        ServerSettings cfg = manager.config();
        cfg.companionUuid = golem.getUuid().toString();
        cfg.save();
        history.clear();
        staying = false;
        return golem;
    }

    /**
     * Периодически подводит компаньона к ближайшему онлайн-игроку. Раньше
     * это конкурировало с ванильным "иди к хозяину" у волка — у голема
     * такого встроенного поведения нет вообще, конфликтовать не с чем.
     */
    public void tickFollow(MinecraftServer server) {
        long now = System.currentTimeMillis();
        if (now - lastFollowTick < 1000) return;
        lastFollowTick = now;

        IronGolemEntity golem = find(server);
        if (golem == null) return;
        if (staying) return;

        ServerPlayerEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.getServerWorld() != golem.getWorld()) continue;
            double dist = golem.squaredDistanceTo(p);
            if (dist < best) {
                best = dist;
                nearest = p;
            }
        }
        if (nearest == null) return;

        if (best > 8 * 8 && best < 64 * 64) {
            golem.getNavigation().startMovingTo(nearest.getX(), nearest.getY(), nearest.getZ(), 1.0);
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
    public boolean say(ServerPlayerEntity player, IronGolemEntity companion, String message) {
        long now = System.currentTimeMillis();
        Long last = lastAsk.get(player.getUuid());
        if (last != null && now - last < COOLDOWN_MILLIS) {
            return false;
        }
        lastAsk.put(player.getUuid(), now);

        // переключаемся на того, кто позвал, сразу — не ждём периодическую
        // проверку ближайшего игрока (tickFollow раз в секунду)
        if (!staying) {
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
    private String handleActions(String raw, ServerPlayerEntity player, IronGolemEntity companion) {
        Matcher matcher = ACTION.matcher(raw);
        StringBuilder cleaned = new StringBuilder();
        while (matcher.find()) {
            switch (matcher.group(1)) {
                case "СИДЕТЬ" -> staying = true;
                case "ЗА МНОЙ" -> {
                    staying = false;
                    companion.getNavigation().startMovingTo(
                            player.getX(), player.getY(), player.getZ(), 1.0);
                }
                case "АТАКОВАТЬ" -> {
                    staying = false;
                    attackNearest(player, companion);
                }
                case "ТЕЛЕПОРТ" -> {
                    staying = false;
                    companion.teleport((net.minecraft.server.world.ServerWorld) player.getWorld(),
                            player.getX(), player.getY(), player.getZ(),
                            java.util.Set.of(), player.getYaw(), 0);
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
    private void attackNearest(ServerPlayerEntity player, IronGolemEntity companion) {
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
