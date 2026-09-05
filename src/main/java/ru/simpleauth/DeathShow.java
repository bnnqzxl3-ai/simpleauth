package ru.simpleauth;

import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Вознесение при смерти от руки другого игрока: белая спираль вокруг тела,
 * подъём камеры вверх и нарастающий звон. Респавн остаётся обычным — игрок
 * сам нажимает кнопку и появляется на своей точке.
 */
public class DeathShow {

    private final AuthManager manager;
    private final Map<UUID, Rise> rising = new ConcurrentHashMap<>();

    public DeathShow(AuthManager manager) {
        this.manager = manager;
    }

    private static class Rise {
        ServerWorld world;
        double x, y, z;
        float yaw, pitch;
        int tick;
        int total;
        double height;
    }

    private Config config() {
        return manager.config();
    }

    /** Вызывается при смерти игрока. killer может быть null. */
    public void onDeath(ServerPlayerEntity victim, ServerPlayerEntity killer) {
        if (!config().deathShowEnabled) return;
        if (config().deathShowOnlyPvp && killer == null) return;
        // на массовой рубке десяток одновременных показов не нужен
        if (rising.size() >= Math.max(1, config().deathShowMaxAtOnce)) return;

        Rise rise = new Rise();
        rise.world = victim.getServerWorld();
        rise.x = victim.getX();
        rise.y = victim.getY();
        rise.z = victim.getZ();
        rise.yaw = victim.getYaw();
        rise.pitch = victim.getPitch();
        rise.tick = 0;
        rise.total = Math.max(20, config().deathShowSeconds * 20);
        rise.height = Math.max(1.0, config().deathShowHeight);
        rising.put(victim.getUuid(), rise);

        // первый удар: глухой раскат и вспышка на месте гибели
        sound(rise.world, rise.x, rise.y, rise.z, SoundEvents.ITEM_TRIDENT_THUNDER, 0.7F, 1.6F);
        sound(rise.world, rise.x, rise.y, rise.z, SoundEvents.BLOCK_BEACON_ACTIVATE, 0.8F, 1.5F);
        rise.world.spawnParticles(ParticleTypes.FLASH,
                rise.x, rise.y + 1.0, rise.z, 1, 0.0, 0.0, 0.0, 0.0);
        rise.world.spawnParticles(ParticleTypes.END_ROD,
                rise.x, rise.y + 1.0, rise.z, 25, 0.35, 0.5, 0.35, 0.12);

        if (killer != null) {
            sound(killer.getServerWorld(), killer.getX(), killer.getY(), killer.getZ(),
                    SoundEvents.ITEM_TOTEM_USE, 0.5F, 1.8F);
        }
    }

    public void tick(MinecraftServer server) {
        if (rising.isEmpty()) return;

        for (Map.Entry<UUID, Rise> entry : rising.entrySet()) {
            Rise rise = entry.getValue();
            rise.tick++;
            double progress = Math.min(1.0, rise.tick / (double) rise.total);

            // плавное замедление к верхней точке
            double eased = 1.0 - Math.pow(1.0 - progress, 2.0);
            double lift = eased * rise.height;

            // рисуем через тик и только если рядом есть кому смотреть
            if (rise.tick % 2 == 0 && hasViewers(rise)) {
                drawSpiral(rise, lift, progress);
            }

            // камеру погибшего тянем вверх, пока он на экране смерти
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player != null && !player.isAlive()) {
                try {
                    player.networkHandler.requestTeleport(
                            rise.x, rise.y + lift, rise.z, rise.yaw, rise.pitch);
                } catch (Exception ignored) {
                }
            }

            // звон, поднимающийся по тону
            if (rise.tick % 8 == 0) {
                float pitch = 0.8F + (float) progress * 1.2F;
                sound(rise.world, rise.x, rise.y + lift, rise.z,
                        SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.55F, pitch);
            }

            if (rise.tick >= rise.total) {
                finish(rise);
                rising.remove(entry.getKey());
            }
        }
    }

    /** Есть ли поблизости игроки: если нет, частицы никому не нужны. */
    private boolean hasViewers(Rise rise) {
        for (ServerPlayerEntity viewer : rise.world.getPlayers()) {
            if (viewer.squaredDistanceTo(rise.x, rise.y, rise.z) <= 2304.0) {
                return true;
            }
        }
        return false;
    }

    private void drawSpiral(Rise rise, double lift, double progress) {
        // две ленты, закручивающиеся вокруг поднимающейся точки
        for (int strand = 0; strand < 2; strand++) {
            double angle = rise.tick * 0.45 + strand * Math.PI;
            double radius = 0.85 * (1.0 - progress * 0.55);
            rise.world.spawnParticles(ParticleTypes.END_ROD,
                    rise.x + Math.cos(angle) * radius,
                    rise.y + lift + 0.6,
                    rise.z + Math.sin(angle) * radius,
                    1, 0.0, 0.0, 0.0, 0.0);
        }

        // редкие искры, тянущиеся снизу вверх следом
        if (rise.tick % 6 == 0) {
            rise.world.spawnParticles(ParticleTypes.FIREWORK,
                    rise.x, rise.y + lift * 0.6, rise.z, 2, 0.25, 0.35, 0.25, 0.02);
        }

        // след на земле: кольцо на месте гибели, гаснущее к концу
        if (rise.tick % 10 == 0 && progress < 0.7) {
            for (int i = 0; i < 4; i++) {
                double angle = (i / 4.0) * Math.PI * 2.0 + rise.tick * 0.1;
                double radius = 0.6 + progress * 1.4;
                rise.world.spawnParticles(ParticleTypes.CLOUD,
                        rise.x + Math.cos(angle) * radius,
                        rise.y + 0.1,
                        rise.z + Math.sin(angle) * radius,
                        1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    private void finish(Rise rise) {
        double top = rise.y + rise.height;
        rise.world.spawnParticles(ParticleTypes.END_ROD,
                rise.x, top + 0.6, rise.z, 30, 0.4, 0.4, 0.4, 0.25);
        rise.world.spawnParticles(ParticleTypes.FLASH,
                rise.x, top + 0.6, rise.z, 1, 0.0, 0.0, 0.0, 0.0);
        sound(rise.world, rise.x, top, rise.z, SoundEvents.ENTITY_PLAYER_LEVELUP, 0.7F, 1.7F);
        sound(rise.world, rise.x, top, rise.z, SoundEvents.BLOCK_BEACON_DEACTIVATE, 0.5F, 1.9F);
    }

    // две перегрузки: имена звуков в маппингах бывают и тем, и другим типом
    private void sound(ServerWorld world, double x, double y, double z,
                       SoundEvent event, float volume, float pitch) {
        world.playSound(null, x, y, z, event, SoundCategory.PLAYERS, volume, pitch);
    }

    private void sound(ServerWorld world, double x, double y, double z,
                       RegistryEntry<SoundEvent> event, float volume, float pitch) {
        world.playSound(null, x, y, z, event, SoundCategory.PLAYERS, volume, pitch);
    }
}
