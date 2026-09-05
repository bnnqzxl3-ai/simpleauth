package ru.simpleauth;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Шоу по расписанию: титул, вспышка эффектов, игрок замирает на несколько секунд,
 * после чего состояние возвращается к исходному.
 */
public class ShowManager {

    private final AuthManager manager;
    private final Map<UUID, Active> active = new ConcurrentHashMap<>();
    private final Map<String, String> fired = new ConcurrentHashMap<>();
    private long lastCheck = 0L;

    public ShowManager(AuthManager manager) {
        this.manager = manager;
    }

    private static class Active {
        Config.Show show;
        int tick;
        int totalTicks;
        double x, y, z;
        float yaw, pitch;
        float health;
        int food;
    }

    private Config config() {
        return manager.config();
    }

    public boolean isActive(ServerPlayerEntity player) {
        return player != null && active.containsKey(player.getUuid());
    }

    public void onDisconnect(ServerPlayerEntity player) {
        active.remove(player.getUuid());
    }

    // --------------------------------------------------------- расписание

    public void tick(MinecraftServer server, long now) {
        runActive(server);

        if (now - lastCheck < 1000L) return;
        lastCheck = now;
        if (config().shows == null || config().shows.isEmpty()) return;

        ZoneId zone;
        try {
            zone = ZoneId.of(config().timeZone);
        } catch (Exception e) {
            zone = ZoneId.systemDefault();
        }
        ZonedDateTime zoned = ZonedDateTime.now(zone);
        LocalTime current = zoned.toLocalTime().withSecond(0).withNano(0);
        String today = zoned.toLocalDate().toString();

        for (Config.Show show : config().shows) {
            if (show == null || !show.enabled || show.time == null) continue;
            LocalTime target;
            try {
                target = LocalTime.parse(show.time.trim());
            } catch (Exception e) {
                continue;
            }
            if (!current.equals(target)) continue;

            String key = show.time + "|" + show.title;
            if (today.equals(fired.put(key, today))) continue;

            start(server, show);
        }

        if (fired.size() > 32) {
            fired.entrySet().removeIf(e -> !today.equals(e.getValue()));
        }
    }

    /** Запускает шоу вручную. */
    public int start(MinecraftServer server, Config.Show show) {
        List<ServerPlayerEntity> targets = new ArrayList<>();
        boolean everyone = show.target == null || show.target.isEmpty() || "*".equals(show.target);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!manager.isAuthenticated(player)) continue;
            if (everyone || show.target.equalsIgnoreCase(player.getGameProfile().getName())) {
                targets.add(player);
            }
        }

        for (ServerPlayerEntity player : targets) {
            begin(player, show);
        }
        if (!targets.isEmpty()) {
            SimpleAuth.LOGGER.info("[SimpleAuth] Шоу «{}» для {} игроков", show.title, targets.size());
        }
        return targets.size();
    }

    // ------------------------------------------------------------- запуск

    private void begin(ServerPlayerEntity player, Config.Show show) {
        Active a = new Active();
        a.show = show;
        a.tick = 0;
        a.totalTicks = Math.max(20, show.durationSeconds * 20);
        a.x = player.getX();
        a.y = player.getY();
        a.z = player.getZ();
        a.yaw = player.getYaw();
        a.pitch = player.getPitch();
        a.health = player.getHealth();
        a.food = player.getHungerManager().getFoodLevel();
        active.put(player.getUuid(), a);

        if (show.freeze) {
            // Slowness с большим уровнем не даёт идти, jump boost с уровнем 128 —
            // прыгать. Частицы у эффектов выключены, чтобы не портить картинку.
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.SLOWNESS, a.totalTicks + 10, 200, false, false, false));
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.JUMP_BOOST, a.totalTicks + 10, 128, false, false, false));
        }

        manager.showTitle(player,
                Text.literal(show.title).formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD),
                Text.literal(show.subtitle == null ? "" : show.subtitle).formatted(Formatting.WHITE),
                5, a.totalTicks, 15);

        sound(player, SoundEvents.ITEM_TOTEM_USE, 1.0F, 1.0F);
        sound(player, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6F, 1.4F);

        ServerWorld world = player.getServerWorld();
        world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING,
                player.getX(), player.getY() + 1.0, player.getZ(), 120, 0.5, 0.8, 0.5, 0.35);
        world.spawnParticles(ParticleTypes.FLASH,
                player.getX(), player.getY() + 1.2, player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
    }

    // --------------------------------------------------------- само шоу

    private void runActive(MinecraftServer server) {
        if (active.isEmpty()) return;

        for (Map.Entry<UUID, Active> entry : active.entrySet()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            Active a = entry.getValue();
            if (player == null) {
                active.remove(entry.getKey());
                continue;
            }

            a.tick++;

            if (a.show.freeze) {
                double dx = player.getX() - a.x;
                double dy = player.getY() - a.y;
                double dz = player.getZ() - a.z;
                if (dx * dx + dy * dy + dz * dz > 0.05D) {
                    player.networkHandler.requestTeleport(a.x, a.y, a.z, a.yaw, a.pitch);
                }
                player.setVelocity(Vec3d.ZERO);
                player.fallDistance = 0.0F;
            }

            drawEffects(player, a);

            if (a.tick >= a.totalTicks) {
                finish(player, a);
                active.remove(entry.getKey());
            }
        }
    }

    private void drawEffects(ServerPlayerEntity player, Active a) {
        ServerWorld world = player.getServerWorld();
        double progress = (double) a.tick / (double) a.totalTicks;

        // растущее кольцо вокруг игрока
        if (a.tick % 2 == 0) {
            double radius = 0.6 + progress * 2.2;
            double height = 0.2 + progress * 1.6;
            for (int i = 0; i < 10; i++) {
                double angle = (i / 10.0) * Math.PI * 2.0 + a.tick * 0.16;
                world.spawnParticles(ParticleTypes.END_ROD,
                        a.x + Math.cos(angle) * radius,
                        a.y + height,
                        a.z + Math.sin(angle) * radius,
                        1, 0.0, 0.0, 0.0, 0.0);
            }
        }

        // столб пламени душ снизу вверх
        if (a.tick % 4 == 0) {
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    a.x, a.y + progress * 2.4, a.z, 6, 0.25, 0.1, 0.25, 0.02);
        }

        // такты со звуком и всплеском
        if (a.tick % 20 == 0) {
            sound(player, SoundEvents.BLOCK_BEACON_ACTIVATE, 0.5F, 1.2F + a.tick / 100.0F);
            world.spawnParticles(ParticleTypes.FIREWORK,
                    a.x, a.y + 1.0, a.z, 25, 0.6, 0.6, 0.6, 0.12);
        }
    }

    private void finish(ServerPlayerEntity player, Active a) {
        if (a.show.freeze) {
            player.removeStatusEffect(StatusEffects.SLOWNESS);
            player.removeStatusEffect(StatusEffects.JUMP_BOOST);
        }

        // возвращаем всё как было
        player.setHealth(a.health);
        player.getHungerManager().setFoodLevel(a.food);
        player.setFireTicks(0);
        player.fallDistance = 0.0F;
        player.setVelocity(Vec3d.ZERO);

        ServerWorld world = player.getServerWorld();
        world.spawnParticles(ParticleTypes.FIREWORK,
                a.x, a.y + 1.0, a.z, 60, 0.8, 0.8, 0.8, 0.25);
        sound(player, SoundEvents.ENTITY_PLAYER_LEVELUP, 0.8F, 1.4F);

        AuthManager.send(player, config().messages.showDone, Formatting.LIGHT_PURPLE);
    }

    // ------------------------------------------------------------- утилиты

    private void sound(ServerPlayerEntity player, SoundEvent event, float volume, float pitch) {
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                event, SoundCategory.MASTER, volume, pitch);
    }

    private void sound(ServerPlayerEntity player, RegistryEntry<SoundEvent> event, float volume, float pitch) {
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                event, SoundCategory.MASTER, volume, pitch);
    }
}
