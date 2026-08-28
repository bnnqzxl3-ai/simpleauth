package ru.simpleauth;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AuthManager {

    private final PlayerDatabase database;
    private Config config;

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final Set<UUID> authenticated = ConcurrentHashMap.newKeySet();

    public AuthManager(PlayerDatabase database, Config config) {
        this.database = database;
        this.config = config;
    }

    private static class Pending {
        double x, y, z;
        float yaw, pitch;
        long joinedAt;
        long lastReminder;
        int attempts;
        boolean wasInvulnerable;
    }

    public PlayerDatabase database() {
        return database;
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

    // ------------------------------------------------------------------ join

    public void onJoin(ServerPlayerEntity player) {
        Pending p = new Pending();
        p.x = player.getX();
        p.y = player.getY();
        p.z = player.getZ();
        p.yaw = player.getYaw();
        p.pitch = player.getPitch();
        p.joinedAt = System.currentTimeMillis();
        p.lastReminder = 0L;
        p.attempts = 0;
        p.wasInvulnerable = player.isInvulnerable();
        pending.put(player.getUuid(), p);
        authenticated.remove(player.getUuid());

        if (config.invulnerableUntilLogin) {
            player.setInvulnerable(true);
        }
        if (config.blindnessUntilLogin) {
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.BLINDNESS, 999999, 0, false, false, false));
        }

        String name = player.getGameProfile().getName();
        if (database.isRegistered(name)) {
            send(player, config.messages.needLogin, Formatting.YELLOW);
        } else {
            send(player, config.messages.needRegister, Formatting.YELLOW);
        }
    }

    public void onDisconnect(ServerPlayerEntity player) {
        pending.remove(player.getUuid());
        authenticated.remove(player.getUuid());
    }

    // ---------------------------------------------------------------- unlock

    private void unlock(ServerPlayerEntity player) {
        Pending p = pending.remove(player.getUuid());
        authenticated.add(player.getUuid());
        if (config.invulnerableUntilLogin) {
            player.setInvulnerable(p != null && p.wasInvulnerable);
        }
        if (config.blindnessUntilLogin) {
            player.removeStatusEffect(StatusEffects.BLINDNESS);
        }
    }

    // -------------------------------------------------------------- commands

    public void tryRegister(ServerPlayerEntity player, String password, String confirm) {
        String name = player.getGameProfile().getName();
        if (isAuthenticated(player)) {
            send(player, config.messages.alreadyLoggedIn, Formatting.GRAY);
            return;
        }
        if (database.isRegistered(name)) {
            send(player, config.messages.alreadyRegistered, Formatting.RED);
            return;
        }
        if (!password.equals(confirm)) {
            send(player, config.messages.passwordsDoNotMatch, Formatting.RED);
            return;
        }
        if (password.length() < config.minPasswordLength) {
            send(player, String.format(config.messages.passwordTooShort, config.minPasswordLength), Formatting.RED);
            return;
        }
        if (password.length() > config.maxPasswordLength) {
            send(player, String.format(config.messages.passwordTooLong, config.maxPasswordLength), Formatting.RED);
            return;
        }
        database.register(name, password);
        unlock(player);
        send(player, config.messages.registered, Formatting.GREEN);
        SimpleAuth.LOGGER.info("[SimpleAuth] Новый аккаунт: {}", name);
    }

    public void tryLogin(ServerPlayerEntity player, String password) {
        String name = player.getGameProfile().getName();
        if (isAuthenticated(player)) {
            send(player, config.messages.alreadyLoggedIn, Formatting.GRAY);
            return;
        }
        if (!database.isRegistered(name)) {
            send(player, config.messages.notRegistered, Formatting.RED);
            return;
        }
        if (database.check(name, password)) {
            database.touchLogin(name);
            unlock(player);
            send(player, config.messages.loggedIn, Formatting.GREEN);
            return;
        }

        Pending p = pending.get(player.getUuid());
        if (p != null) {
            p.attempts++;
            if (config.maxLoginAttempts > 0 && p.attempts >= config.maxLoginAttempts) {
                player.networkHandler.disconnect(Text.literal(config.messages.kickTooManyAttempts));
                return;
            }
        }
        send(player, config.messages.wrongPassword, Formatting.RED);
    }

    public void tryChangePassword(ServerPlayerEntity player, String oldPassword, String newPassword) {
        String name = player.getGameProfile().getName();
        if (!isAuthenticated(player)) {
            send(player, config.messages.mustLoginFirst, Formatting.RED);
            return;
        }
        if (!database.check(name, oldPassword)) {
            send(player, config.messages.wrongPassword, Formatting.RED);
            return;
        }
        if (newPassword.length() < config.minPasswordLength) {
            send(player, String.format(config.messages.passwordTooShort, config.minPasswordLength), Formatting.RED);
            return;
        }
        if (newPassword.length() > config.maxPasswordLength) {
            send(player, String.format(config.messages.passwordTooLong, config.maxPasswordLength), Formatting.RED);
            return;
        }
        database.changePassword(name, newPassword);
        send(player, config.messages.passwordChanged, Formatting.GREEN);
    }

    // ------------------------------------------------------------------ tick

    public void tick(MinecraftServer server) {
        if (pending.isEmpty()) return;
        long now = System.currentTimeMillis();

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            Pending p = pending.get(player.getUuid());
            if (p == null) continue;

            // держим игрока на месте
            if (player.getX() != p.x || player.getY() != p.y || player.getZ() != p.z) {
                player.networkHandler.requestTeleport(p.x, p.y, p.z, p.yaw, p.pitch);
            }
            player.setVelocity(Vec3d.ZERO);
            player.fallDistance = 0.0F;

            // напоминание
            long reminderMs = Math.max(1, config.reminderIntervalSeconds) * 1000L;
            if (now - p.lastReminder >= reminderMs) {
                p.lastReminder = now;
                String name = player.getGameProfile().getName();
                String msg = database.isRegistered(name)
                        ? config.messages.needLogin
                        : config.messages.needRegister;
                send(player, msg, Formatting.YELLOW);
            }

            // таймаут
            if (config.loginTimeoutSeconds > 0
                    && now - p.joinedAt > config.loginTimeoutSeconds * 1000L) {
                player.networkHandler.disconnect(Text.literal(config.messages.kickTimeout));
            }
        }
    }

    // ----------------------------------------------------------------- utils

    public static void send(ServerPlayerEntity player, String message, Formatting color) {
        player.sendMessage(Text.literal("[Auth] " + message).formatted(color), false);
    }

    public void notifyMustLogin(ServerPlayerEntity player) {
        send(player, config.messages.mustLoginFirst, Formatting.RED);
    }
}
