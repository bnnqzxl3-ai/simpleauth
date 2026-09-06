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
 * Вознесение при смерти от руки другого игрока.
 *
 * Эффект собран из трёх фаз, разворачивающихся по ходу подъёма:
 *   1) взрыв на месте гибели — вспышка, разлетающееся кольцо души и раскат;
 *   2) сам подъём — двойная светящаяся спираль, вихрь портала внутри неё и
 *      столб света, который тянется от земли к поднимающейся точке;
 *   3) кульминация наверху — вспышка, всплеск end_rod и звон тотема.
 *
 * Эффект отвязан от игрока: он крутится вокруг любой точки мира. Если точка
 * принадлежит погибшему игроку (не тестовому NPC), его камера дополнительно
 * тянется вверх, пока он на экране смерти.
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
        // если true — вверх тянется и камера этого игрока (UUID = ключ карты)
        boolean pullCamera;
    }

    private Config config() {
        return manager.config();
    }

    /** Вызывается при смерти игрока. killer может быть null. */
    public void onDeath(ServerPlayerEntity victim, ServerPlayerEntity killer) {
        if (!config().deathShowEnabled) return;
        if (config().deathShowOnlyPvp && killer == null) return;
        if (rising.size() >= Math.max(1, config().deathShowMaxAtOnce)) return;

        start(victim.getUuid(), victim.getServerWorld(),
                victim.getX(), victim.getY(), victim.getZ(),
                victim.getYaw(), victim.getPitch(), true);

        if (killer != null) {
            sound(killer.getServerWorld(), killer.getX(), killer.getY(), killer.getZ(),
                    SoundEvents.ITEM_TOTEM_USE, 0.5F, 1.8F);
        }
    }

    /**
     * Запуск эффекта в произвольной точке. Используется как смертью игрока, так
     * и тестовой командой на NPC. id — ключ показа (UUID сущности), pullCamera —
     * тянуть ли вверх камеру одноимённого игрока.
     */
    public void start(UUID id, ServerWorld world,
                      double x, double y, double z,
                      float yaw, float pitch, boolean pullCamera) {
        if (rising.size() >= Math.max(1, config().deathShowMaxAtOnce)) return;

        Rise rise = new Rise();
        rise.world = world;
        rise.x = x;
        rise.y = y;
        rise.z = z;
        rise.yaw = yaw;
        rise.pitch = pitch;
        rise.tick = 0;
        rise.total = Math.max(20, config().deathShowSeconds * 20);
        rise.height = Math.max(1.0, config().deathShowHeight);
        rise.pullCamera = pullCamera;
        rising.put(id, rise);

        // фаза 1: удар на месте гибели
        sound(world, x, y, z, SoundEvents.ITEM_TRIDENT_THUNDER, 0.8F, 1.5F);
        sound(world, x, y, z, SoundEvents.BLOCK_BEACON_ACTIVATE, 0.9F, 1.4F);
        sound(world, x, y, z, SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.7F, 0.8F);

        world.spawnParticles(ParticleTypes.FLASH, x, y + 1.0, z, 2, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticles(ParticleTypes.EXPLOSION, x, y + 1.0, z, 1, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticles(ParticleTypes.END_ROD, x, y + 1.0, z, 40, 0.4, 0.6, 0.4, 0.18);

        // разлетающееся по земле кольцо души
        int ringPoints = 24;
        for (int i = 0; i < ringPoints; i++) {
            double a = (i / (double) ringPoints) * Math.PI * 2.0;
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    x + Math.cos(a) * 0.4, y + 0.2, z + Math.sin(a) * 0.4,
                    0, Math.cos(a) * 0.35, 0.05, Math.sin(a) * 0.35, 0.6);
        }
    }

    public void tick(MinecraftServer server) {
        if (rising.isEmpty()) return;

        for (Map.Entry<UUID, Rise> entry : rising.entrySet()) {
            Rise rise = entry.getValue();
            rise.tick++;
            double progress = Math.min(1.0, rise.tick / (double) rise.total);

            // плавное замедление к верхней точке (ease-out cubic)
            double eased = 1.0 - Math.pow(1.0 - progress, 3.0);
            double lift = eased * rise.height;

            if (hasViewers(rise)) {
                drawColumn(rise, lift, progress);
                drawSpiral(rise, lift, progress);
                drawVortex(rise, lift, progress);
            }

            // камеру погибшего тянем вверх, пока он на экране смерти
            if (rise.pullCamera) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
                if (player != null && !player.isAlive()) {
                    try {
                        player.networkHandler.requestTeleport(
                                rise.x, rise.y + lift, rise.z, rise.yaw, rise.pitch);
                    } catch (Exception ignored) {
                    }
                }
            }

            // звон, поднимающийся по тону
            if (rise.tick % 6 == 0) {
                float p = 0.7F + (float) progress * 1.4F;
                sound(rise.world, rise.x, rise.y + lift, rise.z,
                        SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.6F, p);
            }
            if (rise.tick % 14 == 0) {
                sound(rise.world, rise.x, rise.y + lift, rise.z,
                        SoundEvents.BLOCK_CONDUIT_AMBIENT_SHORT, 0.5F, 1.2F);
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

    /** Столб света от земли до поднимающейся точки — задаёт вертикальную ось. */
    private void drawColumn(Rise rise, double lift, double progress) {
        int segments = 4 + (int) (lift * 1.5);
        for (int i = 0; i < segments; i++) {
            double t = i / (double) Math.max(1, segments);
            double h = t * (lift + 0.5);
            double jitter = 0.05 + 0.03 * Math.sin(rise.tick * 0.4 + i);
            rise.world.spawnParticles(ParticleTypes.END_ROD,
                    rise.x, rise.y + h, rise.z,
                    1, jitter, 0.02, jitter, 0.0);
        }
    }

    /** Две светящиеся ленты, туго закрученные вокруг оси подъёма. */
    private void drawSpiral(Rise rise, double lift, double progress) {
        for (int strand = 0; strand < 2; strand++) {
            double angle = rise.tick * 0.6 + strand * Math.PI;
            double radius = 1.1 * (1.0 - progress * 0.6);
            double head = rise.y + lift + 0.6;

            rise.world.spawnParticles(ParticleTypes.END_ROD,
                    rise.x + Math.cos(angle) * radius, head,
                    rise.z + Math.sin(angle) * radius, 1, 0.0, 0.0, 0.0, 0.0);

            // след ленты, отстающий чуть ниже головы спирали
            double trailAngle = angle - 0.6;
            rise.world.spawnParticles(ParticleTypes.WAX_ON,
                    rise.x + Math.cos(trailAngle) * radius, head - 0.4,
                    rise.z + Math.sin(trailAngle) * radius, 1, 0.0, 0.0, 0.0, 0.0);
        }

        // редкие искры, тянущиеся снизу вверх следом
        if (rise.tick % 5 == 0) {
            rise.world.spawnParticles(ParticleTypes.FIREWORK,
                    rise.x, rise.y + lift * 0.6, rise.z, 3, 0.25, 0.4, 0.25, 0.03);
        }
    }

    /** Затягивающийся внутрь вихрь портала у головы спирали. */
    private void drawVortex(Rise rise, double lift, double progress) {
        if (rise.tick % 2 != 0) return;
        double head = rise.y + lift + 0.6;
        for (int i = 0; i < 3; i++) {
            double angle = rise.tick * -0.5 + i * (Math.PI * 2.0 / 3.0);
            double radius = 0.9 * (1.0 - progress * 0.4);
            double vx = -Math.cos(angle) * 0.15;
            double vz = -Math.sin(angle) * 0.15;
            rise.world.spawnParticles(ParticleTypes.PORTAL,
                    rise.x + Math.cos(angle) * radius, head,
                    rise.z + Math.sin(angle) * radius,
                    0, vx, 0.1, vz, 1.0);
        }
    }

    /** Кульминация наверху. */
    private void finish(Rise rise) {
        double top = rise.y + rise.height;
        rise.world.spawnParticles(ParticleTypes.FLASH, rise.x, top + 0.6, rise.z, 2, 0.0, 0.0, 0.0, 0.0);
        rise.world.spawnParticles(ParticleTypes.END_ROD, rise.x, top + 0.6, rise.z, 60, 0.5, 0.5, 0.5, 0.3);
        rise.world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING,
                rise.x, top + 0.6, rise.z, 40, 0.5, 0.5, 0.5, 0.35);

        sound(rise.world, rise.x, top, rise.z, SoundEvents.ENTITY_PLAYER_LEVELUP, 0.8F, 1.6F);
        sound(rise.world, rise.x, top, rise.z, SoundEvents.ITEM_TOTEM_USE, 0.6F, 1.4F);
        sound(rise.world, rise.x, top, rise.z, SoundEvents.BLOCK_BEACON_DEACTIVATE, 0.5F, 1.8F);
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
