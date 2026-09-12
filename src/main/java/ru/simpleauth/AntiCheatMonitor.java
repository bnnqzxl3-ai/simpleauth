package ru.simpleauth;

import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Лёгкие эвристики против читов: слишком быстрое перемещение и подозрительный
 * "полёт" вне креатива/элитры/левитации. Это НЕ полноценный античит — простые
 * пороговые проверки, которые могут ошибаться на нестандартных ситуациях
 * (лёд, потоки воды, рывки от взрывов, лаги сети). Поэтому мод НИКОГО не
 * наказывает и не кикает сам — только присылает владельцу пометку
 * "подозрение" в чат, решение остаётся за человеком.
 */
public class AntiCheatMonitor {

    private final AuthManager manager;
    private final ActionLogger log;

    private static class State {
        double lastX, lastY, lastZ;
        boolean initialized = false;
        int speedTicks = 0;
        int airTicks = 0;
        long lastWarnMillis = 0;
    }

    private final Map<UUID, State> states = new HashMap<>();

    public AntiCheatMonitor(AuthManager manager, ActionLogger log) {
        this.manager = manager;
        this.log = log;
    }

    private Config config() {
        return manager.config();
    }

    public void tick(MinecraftServer server) {
        Config cfg = config();
        if (!cfg.anticheatEnabled) return;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            checkPlayer(player, cfg);
        }
    }

    private void checkPlayer(ServerPlayerEntity player, Config cfg) {
        State st = states.computeIfAbsent(player.getUuid(), k -> new State());

        double x = player.getX(), y = player.getY(), z = player.getZ();
        if (!st.initialized) {
            st.lastX = x; st.lastY = y; st.lastZ = z;
            st.initialized = true;
            return;
        }

        double dx = x - st.lastX, dz = z - st.lastZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        st.lastX = x; st.lastY = y; st.lastZ = z;

        // --- проверка скорости ---
        boolean exempt = player.isCreative() || player.isSpectator()
                || player.getVehicle() != null
                || player.hasStatusEffect(StatusEffects.SPEED)
                || player.isFallFlying();

        if (!exempt && horizontal > cfg.anticheatSpeedThreshold) {
            st.speedTicks++;
            if (st.speedTicks >= cfg.anticheatSpeedTicks) {
                warn(player, String.format(
                        "подозрение на speed-хак (%.2f блоков/тик)", horizontal));
                st.speedTicks = 0;
            }
        } else {
            st.speedTicks = Math.max(0, st.speedTicks - 1);
        }

        // --- проверка полёта ---
        boolean flightExempt = player.isCreative() || player.isSpectator()
                || player.isFallFlying()
                || player.hasStatusEffect(StatusEffects.LEVITATION)
                || player.hasStatusEffect(StatusEffects.SLOW_FALLING)
                || player.getVehicle() != null
                || player.isTouchingWater() || player.isInLava()
                || player.getAbilities().flying;

        if (!flightExempt && !player.isOnGround()) {
            st.airTicks++;
            if (st.airTicks >= cfg.anticheatFlightTicks) {
                warn(player, String.format(
                        "подозрение на полёт (в воздухе %d тиков без опоры)", st.airTicks));
                st.airTicks = 0;
            }
        } else {
            st.airTicks = 0;
        }
    }

    private void warn(ServerPlayerEntity suspect, String reason) {
        Config cfg = config();
        State st = states.get(suspect.getUuid());
        long now = System.currentTimeMillis();
        if (st != null && now - st.lastWarnMillis < cfg.anticheatCooldownSeconds * 1000L) {
            return; // не спамим по одному игроку слишком часто
        }
        if (st != null) st.lastWarnMillis = now;

        String name = suspect.getGameProfile().getName();
        log.log("ANTICHEAT " + name + ": " + reason);

        String ownerName = cfg.owner;
        if (ownerName == null || ownerName.isBlank()) return;
        if (ownerName.equalsIgnoreCase(name)) return; // не пишем владельцу о нём самом

        for (ServerPlayerEntity online : suspect.getServer().getPlayerManager().getPlayerList()) {
            if (ownerName.equalsIgnoreCase(online.getGameProfile().getName())) {
                online.sendMessage(
                        Text.literal("[Античит] ").formatted(Formatting.RED)
                                .append(Text.literal(name).formatted(Formatting.YELLOW))
                                .append(Text.literal(" — " + reason).formatted(Formatting.GRAY)),
                        false);
                break;
            }
        }
    }
}
