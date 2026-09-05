package ru.simpleauth;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AuthManager {

    private final PlayerDatabase database;
    private Config config;

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final Set<UUID> authenticated = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Verification> verifying = new ConcurrentHashMap<>();
    private float shimmerPhase = 0.0F;
    private final PrefixManager prefixes = new PrefixManager(this);
    private final ShowManager shows = new ShowManager(this);
    private final CosmeticsManager cosmetics = new CosmeticsManager(this);
    private final HatManager hats = new HatManager(this);
    private final RegionCleaner cleaner = new RegionCleaner();
    private volatile boolean physicsDisabled = false;
    private final DeathShow deathShow = new DeathShow(this);
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    /** Сессия по адресу: пока не истекла, пароль не спрашиваем. */
    private static class Session {
        String ipHash;
        long expiresAt;
    }

    /** Слепок, с которым сверяем игрока после входа. */
    private static class Verification {
        long startedAt;
        boolean hasData;
        String gameMode;
        float health;
        int food;
        int xpLevel;
        int items;
        String invHash;
    }

    public AuthManager(PlayerDatabase database, Config config) {
        this.database = database;
        this.config = config;
    }

    private static class Pending {
        // куда вернуть после входа
        String returnWorld;
        double returnX, returnY, returnZ;
        float returnYaw, returnPitch;
        // где держим до входа
        double lockX, lockY, lockZ;
        float lockYaw, lockPitch;
        String lockWorld;

        boolean needsTeleport;
        long joinedAt;
        long lastReminder;
        long lastActionBar;
        int attempts;
        boolean wasRegistered;
    }

    public PlayerDatabase database() {
        return database;
    }

    public PrefixManager prefixes() {
        return prefixes;
    }

    public ShowManager shows() {
        return shows;
    }

    public CosmeticsManager cosmetics() {
        return cosmetics;
    }

    public HatManager hats() {
        return hats;
    }

    public RegionCleaner cleaner() {
        return cleaner;
    }

    public DeathShow deathShow() {
        return deathShow;
    }

    /** Режим без пересчёта соседей: нужен на время вставки схемы. */
    public boolean isPhysicsDisabled() {
        return physicsDisabled;
    }

    public void setPhysicsDisabled(boolean value) {
        this.physicsDisabled = value;
    }

    public Config config() {
        return config;
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    public boolean isAuthenticated(UUID uuid) {
        return authenticated.contains(uuid);
    }

    public boolean isAuthenticated(ServerPlayerEntity player) {
        return player != null && authenticated.contains(player.getUuid());
    }

    public boolean isVerifying(ServerPlayerEntity player) {
        return player != null && verifying.containsKey(player.getUuid());
    }

    // ------------------------------------------------------------------ join

    public void onJoin(ServerPlayerEntity player) {
        String name = player.getGameProfile().getName();
        boolean registered = database.isRegistered(name);

        Pending p = new Pending();
        p.joinedAt = System.currentTimeMillis();
        p.wasRegistered = registered;

        String currentWorld = player.getWorld().getRegistryKey().getValue().toString();

        // Точка возврата. Если сервер упал прямо во время авторизации, игрок сейчас
        // стоит на точке авторизации — тогда берём координаты, сохранённые в базе.
        PlayerDatabase.Account account = registered ? database.get(name) : null;
        boolean atAuthSpawn = config.authSpawn.enabled && isNear(player, config.authSpawn);

        if (atAuthSpawn && account != null && account.lastX != null) {
            p.returnWorld = account.lastWorld != null ? account.lastWorld : currentWorld;
            p.returnX = account.lastX;
            p.returnY = account.lastY;
            p.returnZ = account.lastZ;
            p.returnYaw = account.lastYaw != null ? account.lastYaw : player.getYaw();
            p.returnPitch = account.lastPitch != null ? account.lastPitch : player.getPitch();
        } else {
            p.returnWorld = currentWorld;
            p.returnX = player.getX();
            p.returnY = player.getY();
            p.returnZ = player.getZ();
            p.returnYaw = player.getYaw();
            p.returnPitch = player.getPitch();
            if (registered) {
                database.saveReturnPosition(name, p.returnWorld,
                        p.returnX, p.returnY, p.returnZ, p.returnYaw, p.returnPitch);
            }
        }

        // где держим игрока до входа
        if (config.authSpawn.enabled) {
            p.lockWorld = config.authSpawn.world;
            p.lockX = config.authSpawn.x;
            p.lockY = config.authSpawn.y;
            p.lockZ = config.authSpawn.z;
            p.lockYaw = config.authSpawn.yaw;
            p.lockPitch = config.authSpawn.pitch;
            p.needsTeleport = true;
        } else {
            p.lockWorld = currentWorld;
            p.lockX = p.returnX;
            p.lockY = p.returnY;
            p.lockZ = p.returnZ;
            p.lockYaw = p.returnYaw;
            p.lockPitch = p.returnPitch;
            p.needsTeleport = false;
        }

        pending.put(player.getUuid(), p);
        authenticated.remove(player.getUuid());

        if (config.blindnessUntilLogin) {
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.BLINDNESS, 999999, 0, false, false, false));
        }

        // Игрока держат на месте, поэтому он может висеть в воздухе.
        // Без права полёта сервер выкинет его с «Flying is not allowed».
        player.setNoGravity(true);
        player.getAbilities().allowFlying = true;
        player.getAbilities().flying = true;
        player.sendAbilitiesUpdate();

        if (config.showTitles) {
            showTitle(player,
                    Text.literal(config.messages.titleServerName)
                            .formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD),
                    Text.literal(registered ? config.messages.subtitleLogin : config.messages.subtitleRegister)
                            .formatted(Formatting.WHITE),
                    10, 80, 20);
        }

        if (registered && tryResumeSession(player)) {
            return;
        }

        send(player, registered ? config.messages.needLogin : config.messages.needRegister, Formatting.YELLOW);
    }

    /** Пытается пустить без пароля, если сессия с этого же адреса ещё жива. */
    private boolean tryResumeSession(ServerPlayerEntity player) {
        if (config.sessionMinutes <= 0) return false;
        String name = player.getGameProfile().getName();
        Session session = sessions.get(name.toLowerCase(Locale.ROOT));
        if (session == null) return false;
        if (System.currentTimeMillis() > session.expiresAt) {
            sessions.remove(name.toLowerCase(Locale.ROOT));
            return false;
        }
        String currentHash = ipHash(rawIp(player));
        if (currentHash == null || !currentHash.equals(session.ipHash)) return false;

        database.touchLogin(name);
        unlock(player);
        send(player, config.messages.sessionResumed, Formatting.GREEN);
        return true;
    }

    public void onDisconnect(ServerPlayerEntity player) {
        boolean wasPending = pending.remove(player.getUuid()) != null;
        boolean wasLoggedIn = authenticated.remove(player.getUuid());
        verifying.remove(player.getUuid());
        shows.onDisconnect(player);

        // слепок состояния — с ним сверимся при следующем входе
        if (wasLoggedIn) {
            try {
                String name = player.getGameProfile().getName();
                database.saveSnapshot(name,
                        player.interactionManager.getGameMode().name(),
                        player.getHealth(),
                        player.getHungerManager().getFoodLevel(),
                        player.experienceLevel,
                        filledSlots(player),
                        inventoryHash(player));
            } catch (Exception e) {
                SimpleAuth.LOGGER.warn("[SimpleAuth] Не удалось снять слепок при выходе", e);
            }
        }
        // Если игрок вышел, не успев войти, состояние авторизации нельзя дать
        // записать в его файл — иначе при следующем заходе он останется
        // неуязвимым и с полётом.
        if (wasPending) {
            try {
                resetToGameMode(player, false);
            } catch (Exception e) {
                SimpleAuth.LOGGER.warn("[SimpleAuth] Не удалось сбросить состояние при выходе", e);
            }
        }
    }

    // ---------------------------------------------------------------- unlock

    private void unlock(ServerPlayerEntity player) {
        Pending p = pending.remove(player.getUuid());
        authenticated.add(player.getUuid());

        if (config.blindnessUntilLogin) {
            player.removeStatusEffect(StatusEffects.BLINDNESS);
        }

        resetToGameMode(player, true);
        if (player.getServer() != null) {
            prefixes.apply(player.getServer(), player.getGameProfile().getName());
        }

        // вернуть на место выхода
        if (p != null && config.returnToLastPosition && config.authSpawn.enabled) {
            ServerWorld world = worldByName(player.getServer(), p.returnWorld);
            if (world != null) {
                player.teleport(world, p.returnX, p.returnY, p.returnZ, p.returnYaw, p.returnPitch);
                send(player, config.messages.returnedToPosition, Formatting.GRAY);
            }
        }

        if (config.playSounds) {
            sound(player, SoundEvents.ENTITY_PLAYER_LEVELUP, 1.2F);
        }
        player.sendMessage(Text.empty(), true);

        if (config.verifyAfterLogin) {
            startVerification(player);
        } else if (config.showTitles) {
            showTitle(player,
                    Text.literal(config.messages.titleWelcome)
                            .formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD),
                    Text.literal(String.format(config.messages.subtitleWelcome,
                            player.getGameProfile().getName())).formatted(Formatting.WHITE),
                    10, 60, 20);
        }
    }

    // -------------------------------------------------------------- commands

    public boolean tryRegister(ServerPlayerEntity player, String password, String confirm) {
        String name = player.getGameProfile().getName();
        if (isAuthenticated(player)) {
            send(player, config.messages.alreadyLoggedIn, Formatting.GRAY);
            return false;
        }
        if (database.isRegistered(name)) {
            send(player, config.messages.alreadyRegistered, Formatting.RED);
            return false;
        }
        if (!password.equals(confirm)) {
            send(player, config.messages.passwordsDoNotMatch, Formatting.RED);
            return false;
        }
        if (password.length() < config.minPasswordLength) {
            send(player, String.format(config.messages.passwordTooShort, config.minPasswordLength), Formatting.RED);
            return false;
        }
        if (password.length() > config.maxPasswordLength) {
            send(player, String.format(config.messages.passwordTooLong, config.maxPasswordLength), Formatting.RED);
            return false;
        }

        Pending p = pending.get(player.getUuid());
        database.register(name, password);
        rememberSession(player, name);
        database.addKnownIp(name, ipHash(rawIp(player)));
        if (p != null) {
            database.saveReturnPosition(name, p.returnWorld,
                    p.returnX, p.returnY, p.returnZ, p.returnYaw, p.returnPitch);
        }
        unlock(player);
        send(player, config.messages.registered, Formatting.GREEN);
        SimpleAuth.LOGGER.info("[SimpleAuth] Новый аккаунт: {}", name);

        if (config.broadcastNewPlayer && player.getServer() != null) {
            player.getServer().getPlayerManager().broadcast(
                    Text.literal(String.format(config.messages.broadcastNewPlayerMsg, name))
                            .formatted(Formatting.LIGHT_PURPLE), false);
        }
        return true;
    }

    public boolean tryLogin(ServerPlayerEntity player, String password) {
        String name = player.getGameProfile().getName();
        if (isAuthenticated(player)) {
            send(player, config.messages.alreadyLoggedIn, Formatting.GRAY);
            return false;
        }
        if (!database.isRegistered(name)) {
            send(player, config.messages.notRegistered, Formatting.RED);
            return false;
        }
        if (database.check(name, password)) {
            database.touchLogin(name);
            rememberSession(player, name);
            checkAddress(player, name);
            unlock(player);
            send(player, config.messages.loggedIn, Formatting.GREEN);
            return true;
        }

        if (config.playSounds) {
            sound(player, SoundEvents.ENTITY_VILLAGER_NO, 1.0F);
        }

        Pending p = pending.get(player.getUuid());
        if (p != null) {
            p.attempts++;
            if (config.maxLoginAttempts > 0 && p.attempts >= config.maxLoginAttempts) {
                player.networkHandler.disconnect(Text.literal(config.messages.kickTooManyAttempts));
                return false;
            }
        }
        send(player, config.messages.wrongPassword, Formatting.RED);
        return false;
    }

    public boolean tryChangePassword(ServerPlayerEntity player, String oldPassword, String newPassword) {
        String name = player.getGameProfile().getName();
        if (!isAuthenticated(player)) {
            send(player, config.messages.mustLoginFirst, Formatting.RED);
            return false;
        }
        if (!database.check(name, oldPassword)) {
            send(player, config.messages.wrongPassword, Formatting.RED);
            return false;
        }
        if (newPassword.length() < config.minPasswordLength) {
            send(player, String.format(config.messages.passwordTooShort, config.minPasswordLength), Formatting.RED);
            return false;
        }
        if (newPassword.length() > config.maxPasswordLength) {
            send(player, String.format(config.messages.passwordTooLong, config.maxPasswordLength), Formatting.RED);
            return false;
        }
        database.changePassword(name, newPassword);
        send(player, config.messages.passwordChanged, Formatting.GREEN);
        return true;
    }

    // ------------------------------------------------------------------ tick

    public void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        shimmerPhase += Math.max(0.001F, config.shimmerSpeed);
        if (shimmerPhase > 1.0F) shimmerPhase -= 1.0F;
        tickVerification(server, now);
        shows.tick(server, now);
        cosmetics.tick(server);
        cleaner.tick(server);
        deathShow.tick(server);
        if (pending.isEmpty()) return;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            Pending p = pending.get(player.getUuid());
            if (p == null) continue;

            // телепорт на точку авторизации (один раз, на первом тике)
            if (p.needsTeleport) {
                p.needsTeleport = false;
                ServerWorld world = worldByName(server, p.lockWorld);
                if (world != null) {
                    player.teleport(world, p.lockX, p.lockY, p.lockZ, p.lockYaw, p.lockPitch);
                }
            }

            // держим на месте: возвращаем, только если реально отошёл,
            // иначе постоянные телепорты выглядят для анти-читов как рывки
            double dx = player.getX() - p.lockX;
            double dy = player.getY() - p.lockY;
            double dz = player.getZ() - p.lockZ;
            if (dx * dx + dy * dy + dz * dz > 0.05D) {
                player.networkHandler.requestTeleport(p.lockX, p.lockY, p.lockZ, p.lockYaw, p.lockPitch);
            }
            player.setVelocity(Vec3d.ZERO);
            player.fallDistance = 0.0F;

            // напоминание в чат
            long reminderMs = Math.max(1, config.reminderIntervalSeconds) * 1000L;
            if (now - p.lastReminder >= reminderMs) {
                p.lastReminder = now;
                send(player, p.wasRegistered ? config.messages.needLogin : config.messages.needRegister,
                        Formatting.YELLOW);
            }

            // обратный отсчёт над хотбаром
            if (config.showActionBarTimer && config.loginTimeoutSeconds > 0
                    && now - p.lastActionBar >= 100L) {
                p.lastActionBar = now;
                long total = config.loginTimeoutSeconds;
                long left = total - (now - p.joinedAt) / 1000L;
                if (left >= 0) {
                    int width = 20;
                    int filled = (int) Math.round(width * (double) left / (double) total);
                    StringBuilder bar = new StringBuilder();
                    for (int i = 0; i < width; i++) {
                        bar.append(i < filled ? '|' : '.');
                    }
                    String line = bar + "  " + String.format(config.messages.actionBarTimer, left);
                    if (config.actionBarShimmer) {
                        player.sendMessage(shimmer(line, shimmerPhase), true);
                    } else {
                        Formatting color = left <= 10 ? Formatting.RED
                                : (left <= 25 ? Formatting.GOLD : Formatting.GREEN);
                        player.sendMessage(Text.literal(line).formatted(color), true);
                    }
                }
            }

            // таймаут
            if (config.loginTimeoutSeconds > 0
                    && now - p.joinedAt > config.loginTimeoutSeconds * 1000L) {
                player.networkHandler.disconnect(Text.literal(config.messages.kickTimeout));
            }
        }
    }

    // ----------------------------------------------------------------- utils

    /**
     * Приводит неуязвимость, полёт и гравитацию к тому, что положено текущему
     * режиму игры. Специально не читает прежнее состояние игрока: оно могло
     * остаться испорченным после обрыва на этапе авторизации.
     */
    public void resetToGameMode(ServerPlayerEntity player, boolean sendUpdate) {
        GameMode mode = player.interactionManager.getGameMode();
        boolean creative = mode == GameMode.CREATIVE;
        boolean spectator = mode == GameMode.SPECTATOR;

        player.setNoGravity(false);
        player.setInvulnerable(creative || spectator);

        player.getAbilities().invulnerable = creative || spectator;
        player.getAbilities().allowFlying = creative || spectator;
        player.getAbilities().flying = spectator;
        player.getAbilities().creativeMode = creative;
        // Отдельный сохраняемый флаг: без него игрок в выживании ведёт себя
        // как в приключении — блоки не ломаются.
        player.getAbilities().allowModifyWorld = creative || mode == GameMode.SURVIVAL;

        if (sendUpdate) {
            player.sendAbilitiesUpdate();
        }
    }

    // ------------------------------------------------------ проверка входа

    private void startVerification(ServerPlayerEntity player) {
        PlayerDatabase.Account account = database.get(player.getGameProfile().getName());
        Verification v = new Verification();
        v.startedAt = System.currentTimeMillis();
        v.hasData = account != null && account.snapGameMode != null;
        if (v.hasData) {
            v.gameMode = account.snapGameMode;
            v.health = account.snapHealth != null ? account.snapHealth : player.getHealth();
            v.food = account.snapFood != null ? account.snapFood : 20;
            v.xpLevel = account.snapXpLevel != null ? account.snapXpLevel : 0;
            v.items = account.snapItems != null ? account.snapItems : 0;
            v.invHash = account.snapInvHash;
        }
        verifying.put(player.getUuid(), v);

        if (config.showTitles) {
            showTitle(player,
                    Text.literal(config.messages.titleVerify)
                            .formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD),
                    Text.literal(config.messages.subtitleVerify).formatted(Formatting.GRAY),
                    5, Math.max(1, config.verifySeconds) * 20, 5);
        }
    }

    private void tickVerification(MinecraftServer server, long now) {
        if (verifying.isEmpty()) return;
        long duration = Math.max(1, config.verifySeconds) * 1000L;

        Iterator<Map.Entry<UUID, Verification>> it = verifying.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Verification> entry = it.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            if (now - entry.getValue().startedAt < duration) continue;
            it.remove();
            try {
                finishVerification(player, entry.getValue());
            } catch (Exception e) {
                SimpleAuth.LOGGER.warn("[SimpleAuth] Ошибка проверки после входа", e);
            }
        }
    }

    private void finishVerification(ServerPlayerEntity player, Verification v) {
        String name = player.getGameProfile().getName();
        List<String> issues = new ArrayList<>();

        // флаги состояния всегда приводим к режиму игры
        boolean creative = player.interactionManager.getGameMode() == GameMode.CREATIVE;
        boolean spectator = player.interactionManager.getGameMode() == GameMode.SPECTATOR;
        boolean flagsWrong = player.getAbilities().allowFlying != (creative || spectator)
                || player.isInvulnerable() != (creative || spectator)
                || player.hasNoGravity();
        if (flagsWrong) {
            resetToGameMode(player, true);
            issues.add(config.messages.verifyAbilitiesFixed);
        }

        if (v.hasData) {
            String mode = player.interactionManager.getGameMode().name();
            if (!mode.equals(v.gameMode)) {
                issues.add(String.format(config.messages.verifyGameMode, v.gameMode, mode));
            }
            if (Math.abs(player.getHealth() - v.health) > 0.51F) {
                issues.add(String.format(config.messages.verifyHealth, v.health, player.getHealth()));
            }
            int food = player.getHungerManager().getFoodLevel();
            if (food != v.food) {
                issues.add(String.format(config.messages.verifyFood, v.food, food));
            }
            if (player.experienceLevel != v.xpLevel) {
                issues.add(String.format(config.messages.verifyXp, v.xpLevel, player.experienceLevel));
            }
            int slots = filledSlots(player);
            if (slots != v.items) {
                issues.add(String.format(config.messages.verifyItems, v.items, slots));
            } else if (v.invHash != null && !v.invHash.equals(inventoryHash(player))) {
                issues.add(config.messages.verifyInventory);
            }
        }

        if (!v.hasData) {
            send(player, config.messages.verifyNoData, Formatting.GRAY);
        } else if (issues.isEmpty()) {
            send(player, config.messages.verifyOk, Formatting.GREEN);
        } else {
            send(player, config.messages.verifyHeader, Formatting.GOLD);
            for (String issue : issues) {
                player.sendMessage(Text.literal("  • " + issue).formatted(Formatting.YELLOW), false);
            }
            if (config.notifyOpsOnMismatch && player.getServer() != null) {
                Text notice = Text.literal(String.format(
                        config.messages.verifyOpNotice, name, issues.size())).formatted(Formatting.GOLD);
                for (ServerPlayerEntity op : player.getServer().getPlayerManager().getPlayerList()) {
                    if (op != player && op.hasPermissionLevel(2)) {
                        op.sendMessage(notice, false);
                    }
                }
                SimpleAuth.LOGGER.info("[SimpleAuth] Расхождения у {}: {}", name, issues);
            }
        }

        if (config.showTitles) {
            boolean clean = issues.isEmpty();
            showTitle(player,
                    Text.literal(clean ? config.messages.titleVerifyOk : config.messages.titleVerifyDiff)
                            .formatted(clean ? Formatting.GREEN : Formatting.GOLD, Formatting.BOLD),
                    Text.literal(clean
                            ? String.format(config.messages.subtitleVerifyOk, name)
                            : config.messages.subtitleVerifyDiff).formatted(Formatting.WHITE),
                    5, 50, 15);
        }
        if (config.playSounds) {
            sound(player, SoundEvents.BLOCK_NOTE_BLOCK_PLING, issues.isEmpty() ? 1.8F : 0.7F);
        }
    }

    private static int filledSlots(ServerPlayerEntity player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (!player.getInventory().getStack(i).isEmpty()) count++;
        }
        return count;
    }

    private static String inventoryHash(ServerPlayerEntity player) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            sb.append(i).append(':')
                    .append(Registries.ITEM.getId(stack.getItem())).append('x')
                    .append(stack.getCount()).append(';');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        } catch (Exception e) {
            return sb.length() + "";
        }
    }

    private static boolean isNear(ServerPlayerEntity player, Config.SpawnPoint spawn) {
        double dx = player.getX() - spawn.x;
        double dy = player.getY() - spawn.y;
        double dz = player.getZ() - spawn.z;
        return dx * dx + dy * dy + dz * dz < 9.0;
    }

    public static ServerWorld worldByName(MinecraftServer server, String id) {
        if (server == null || id == null) return null;
        try {
            Identifier identifier = Identifier.tryParse(id);
            if (identifier == null) return null;
            RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, identifier);
            return server.getWorld(key);
        } catch (Exception e) {
            SimpleAuth.LOGGER.warn("[SimpleAuth] Неизвестный мир: {}", id);
            return null;
        }
    }

    public void showTitle(ServerPlayerEntity player, Text title, Text subtitle,
                          int fadeIn, int stay, int fadeOut) {
        player.networkHandler.sendPacket(new TitleFadeS2CPacket(fadeIn, stay, fadeOut));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
        player.networkHandler.sendPacket(new TitleS2CPacket(title));
    }

    private void sound(ServerPlayerEntity player, SoundEvent event, float pitch) {
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                event, SoundCategory.MASTER, 0.7F, pitch);
    }

    private void sound(ServerPlayerEntity player, RegistryEntry<SoundEvent> event, float pitch) {
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                event, SoundCategory.MASTER, 0.7F, pitch);
    }

    // -------------------------------------------------- сессии и адреса

    private void rememberSession(ServerPlayerEntity player, String name) {
        if (config.sessionMinutes <= 0) return;
        String hash = ipHash(rawIp(player));
        if (hash == null) return;
        Session session = new Session();
        session.ipHash = hash;
        session.expiresAt = System.currentTimeMillis() + config.sessionMinutes * 60_000L;
        sessions.put(name.toLowerCase(Locale.ROOT), session);
    }

    /** Сверяет адрес со списком известных и при необходимости оповещает владельца. */
    private void checkAddress(ServerPlayerEntity player, String name) {
        if (!config.newIpAlerts) return;
        boolean isOwner = config.owner != null && config.owner.equalsIgnoreCase(name);
        if (!isOwner && !config.newIpAlertsForAll) {
            // всё равно запоминаем адрес, просто молча
            database.addKnownIp(name, ipHash(rawIp(player)));
            return;
        }

        String raw = rawIp(player);
        String hash = ipHash(raw);
        if (hash == null) return;

        boolean firstEver = database.hasNoKnownIps(name);
        boolean isNew = database.addKnownIp(name, hash);
        if (!isNew || firstEver) return;

        String masked = maskIp(raw);
        SimpleAuth.LOGGER.warn("[SimpleAuth] Вход {} с нового адреса {}", name, masked);

        Text alert = Text.literal(String.format(config.messages.newIpAlert, name, masked))
                .formatted(Formatting.GOLD, Formatting.BOLD);

        ServerPlayerEntity owner = null;
        if (player.getServer() != null && config.owner != null) {
            for (ServerPlayerEntity online : player.getServer().getPlayerManager().getPlayerList()) {
                if (config.owner.equalsIgnoreCase(online.getGameProfile().getName())) {
                    owner = online;
                    break;
                }
            }
        }
        if (owner != null) {
            owner.sendMessage(alert, false);
        }
        if (isOwner) {
            player.sendMessage(Text.literal(String.format(config.messages.newIpAlertSelf, masked))
                    .formatted(Formatting.YELLOW), false);
        }
    }

    public void resetKnownIps(String name) {
        database.clearKnownIps(name);
        sessions.remove(name.toLowerCase(Locale.ROOT));
    }

    private static String rawIp(ServerPlayerEntity player) {
        try {
            String ip = player.getIp();
            if (ip == null) return null;
            ip = ip.trim();
            if (ip.startsWith("/")) ip = ip.substring(1);
            // порт отрезаем только у IPv4, у IPv6 двоеточий много
            int colon = ip.indexOf(':');
            if (colon > 0 && ip.indexOf(':', colon + 1) < 0) {
                ip = ip.substring(0, colon);
            }
            return ip;
        } catch (Exception e) {
            return null;
        }
    }

    /** В файл пишем только хеш, сам адрес нигде не сохраняется. */
    private static String ipHash(String ip) {
        if (ip == null || ip.isEmpty()) return null;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(("simpleauth:" + ip).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String maskIp(String ip) {
        if (ip == null || ip.isEmpty()) return "неизвестно";
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + "." + parts[2] + ".***";
        }
        return ip.length() > 8 ? ip.substring(0, 8) + "..." : ip;
    }

    // ------------------------------------------------------------ перелив

    /** Раскрашивает строку бегущим радужным градиентом. */
    public static Text shimmer(String text, float phase) {
        return shimmer(text, phase, 0.62F, 0.035F);
    }

    public static Text shimmer(String text, float phase, float saturation, float spread) {
        MutableText out = Text.empty();
        for (int i = 0; i < text.length(); i++) {
            float hue = (phase + i * spread) % 1.0F;
            if (hue < 0.0F) hue += 1.0F;
            out.append(Text.literal(String.valueOf(text.charAt(i)))
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(hsvToRgb(hue, saturation, 1.0F)))));
        }
        return out;
    }

    private static int hsvToRgb(float h, float s, float v) {
        int sector = (int) (h * 6.0F) % 6;
        float f = h * 6.0F - (int) (h * 6.0F);
        float p = v * (1.0F - s);
        float q = v * (1.0F - f * s);
        float t = v * (1.0F - (1.0F - f) * s);

        float r = v;
        float g = t;
        float b = p;
        if (sector == 1) {
            r = q; g = v; b = p;
        } else if (sector == 2) {
            r = p; g = v; b = t;
        } else if (sector == 3) {
            r = p; g = q; b = v;
        } else if (sector == 4) {
            r = t; g = p; b = v;
        } else if (sector == 5) {
            r = v; g = p; b = q;
        }
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    public static void send(ServerPlayerEntity player, String message, Formatting color) {
        player.sendMessage(Text.literal("[Auth] " + message).formatted(color), false);
    }

    public void notifyMustLogin(ServerPlayerEntity player) {
        send(player, config.messages.mustLoginFirst, Formatting.RED);
    }
}
