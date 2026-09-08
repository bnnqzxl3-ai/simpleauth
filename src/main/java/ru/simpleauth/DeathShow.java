package ru.simpleauth;

import net.minecraft.entity.Entity;
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
 * Эффект собран из фаз, разворачивающихся по ходу подъёма:
 *   1) взрыв на месте гибели — вспышка, разлетающееся кольцо души и раскат;
 *   2) подъём — двойная светящаяся спираль, вихрь портала, столб света,
 *      расходящиеся кольца-ореолы, восходящие души и падающие звёзды;
 *   3) кульминация наверху — вспышка, всплеск end_rod и звон тотема.
 *
 * Эффект отвязан от игрока: крутится вокруг любой точки. К нему можно
 * привязать сущность (target) — она вращается и поднимается вместе с эффектом.
 * Если точка принадлежит погибшему игроку, его камера тоже тянется вверх.
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
        boolean pullCamera;
        // сущность, которую крутим и поднимаем вместе с эффектом (может быть null)
        Entity target;
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
                victim.getYaw(), victim.getPitch(), true, null);

        if (killer != null) {
            sound(killer.getServerWorld(), killer.getX(), killer.getY(), killer.getZ(),
                    SoundEvents.ITEM_TOTEM_USE, 0.5F, 1.8F);
        }
    }

    /** Совместимость: запуск без привязанной сущности. */
    public void start(UUID id, ServerWorld world,
                      double x, double y, double z,
                      float yaw, float pitch, boolean pullCamera) {
        start(id, world, x, y, z, yaw, pitch, pullCamera, null);
    }

    /**
     * Запуск эффекта в произвольной точке. target — сущность, которая будет
     * вращаться и подниматься вместе с эффектом (например тестовый манекен).
     */
    public void start(UUID id, ServerWorld world,
                      double x, double y, double z,
                      float yaw, float pitch, boolean pullCamera, Entity target) {
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
        rise.target = target;
        rising.put(id, rise);

        // фаза 1: удар на месте гибели
        sound(world, x, y, z, SoundEvents.ITEM_TRIDENT_THUNDER, 0.9F, 1.5F);
        sound(world, x, y, z, SoundEvents.BLOCK_BEACON_ACTIVATE, 0.9F, 1.4F);
        sound(world, x, y, z, SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.7F, 0.8F);
        sound(world, x, y, z, SoundEvents.ENTITY_ENDER_DRAGON_FLAP, 0.6F, 1.4F);

        world.spawnParticles(ParticleTypes.FLASH, x, y + 1.0, z, 2, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticles(ParticleTypes.EXPLOSION, x, y + 1.0, z, 1, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticles(ParticleTypes.END_ROD, x, y + 1.0, z, 50, 0.5, 0.7, 0.5, 0.2);
        world.spawnParticles(ParticleTypes.FIREWORK, x, y + 1.0, z, 30, 0.3, 0.4, 0.3, 0.25);

        // разлетающееся по земле кольцо души
        int ringPoints = 32;
        for (int i = 0; i < ringPoints; i++) {
            double a = (i / (double) ringPoints) * Math.PI * 2.0;
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    x + Math.cos(a) * 0.4, y + 0.2, z + Math.sin(a) * 0.4,
                    0, Math.cos(a) * 0.4, 0.05, Math.sin(a) * 0.4, 0.7);
        }
    }

    public void tick(MinecraftServer server) {
        if (rising.isEmpty()) return;

        for (Map.Entry<UUID, Rise> entry : rising.entrySet()) {
            Rise rise = entry.getValue();
            rise.tick++;
            double progress = Math.min(1.0, rise.tick / (double) rise.total);

            double eased = 1.0 - Math.pow(1.0 - progress, 3.0);
            double lift = eased * rise.height;

            if (hasViewers(rise)) {
                drawColumn(rise, lift, progress);
                drawSpiral(rise, lift, progress);
                drawVortex(rise, lift, progress);
                drawHalos(rise, lift, progress);
                drawRisingSouls(rise, lift, progress);
                drawFallingStars(rise, lift, progress);
                drawDragonHead(rise, lift, progress);
            }

            // крутим и поднимаем привязанную сущность (манекен)
            if (rise.target != null && rise.target.isAlive()) {
                double spin = rise.tick * 22.0; // градусов за тик — быстрое вращение
                float yaw = (float) (spin % 360.0);
                rise.target.refreshPositionAndAngles(
                        rise.x, rise.y + lift, rise.z, yaw, 0.0F);
                if (rise.target instanceof net.minecraft.entity.LivingEntity living) {
                    living.setHeadYaw(yaw);
                    living.setBodyYaw(yaw);
                }
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

            // звоны, поднимающиеся по тону
            if (rise.tick % 6 == 0) {
                float p = 0.7F + (float) progress * 1.4F;
                sound(rise.world, rise.x, rise.y + lift, rise.z,
                        SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.6F, p);
            }
            if (rise.tick % 14 == 0) {
                sound(rise.world, rise.x, rise.y + lift, rise.z,
                        SoundEvents.BLOCK_CONDUIT_AMBIENT_SHORT, 0.5F, 1.2F);
            }
            if (rise.tick % 20 == 0) {
                sound(rise.world, rise.x, rise.y + lift, rise.z,
                        SoundEvents.BLOCK_BELL_RESONATE, 0.4F, 1.6F);
            }

            if (rise.tick >= rise.total) {
                finish(rise);
                rising.remove(entry.getKey());
            }
        }
    }

    private boolean hasViewers(Rise rise) {
        for (ServerPlayerEntity viewer : rise.world.getPlayers()) {
            if (viewer.squaredDistanceTo(rise.x, rise.y, rise.z) <= 2304.0) {
                return true;
            }
        }
        return false;
    }

    /** Столб света от земли до поднимающейся точки. */
    private void drawColumn(Rise rise, double lift, double progress) {
        int segments = 4 + (int) (lift * 1.5);
        for (int i = 0; i < segments; i++) {
            double t = i / (double) Math.max(1, segments);
            double h = t * (lift + 0.5);
            double jitter = 0.05 + 0.03 * Math.sin(rise.tick * 0.4 + i);
            rise.world.spawnParticles(ParticleTypes.END_ROD,
                    rise.x, rise.y + h, rise.z, 1, jitter, 0.02, jitter, 0.0);
        }
    }

    /** Три светящиеся ленты, туго закрученные вокруг оси. */
    private void drawSpiral(Rise rise, double lift, double progress) {
        for (int strand = 0; strand < 3; strand++) {
            double angle = rise.tick * 0.6 + strand * (Math.PI * 2.0 / 3.0);
            double radius = 1.2 * (1.0 - progress * 0.6);
            double head = rise.y + lift + 0.6;

            rise.world.spawnParticles(ParticleTypes.END_ROD,
                    rise.x + Math.cos(angle) * radius, head,
                    rise.z + Math.sin(angle) * radius, 1, 0.0, 0.0, 0.0, 0.0);

            double trailAngle = angle - 0.6;
            rise.world.spawnParticles(ParticleTypes.WAX_ON,
                    rise.x + Math.cos(trailAngle) * radius, head - 0.4,
                    rise.z + Math.sin(trailAngle) * radius, 1, 0.0, 0.0, 0.0, 0.0);
        }

        if (rise.tick % 5 == 0) {
            rise.world.spawnParticles(ParticleTypes.FIREWORK,
                    rise.x, rise.y + lift * 0.6, rise.z, 3, 0.25, 0.4, 0.25, 0.03);
        }
    }

    /** Втягивающийся внутрь вихрь портала у головы спирали. */
    private void drawVortex(Rise rise, double lift, double progress) {
        if (rise.tick % 2 != 0) return;
        double head = rise.y + lift + 0.6;
        for (int i = 0; i < 4; i++) {
            double angle = rise.tick * -0.5 + i * (Math.PI / 2.0);
            double radius = 0.9 * (1.0 - progress * 0.4);
            double vx = -Math.cos(angle) * 0.15;
            double vz = -Math.sin(angle) * 0.15;
            rise.world.spawnParticles(ParticleTypes.PORTAL,
                    rise.x + Math.cos(angle) * radius, head,
                    rise.z + Math.sin(angle) * radius, 0, vx, 0.1, vz, 1.0);
        }
    }

    /** Пульсирующие кольца-ореолы, расходящиеся вокруг тела. */
    private void drawHalos(Rise rise, double lift, double progress) {
        if (rise.tick % 8 != 0) return;
        double haloY = rise.y + lift + 1.0;
        int points = 20;
        double radius = 0.5 + (rise.tick % 24) / 24.0 * 1.5;
        for (int i = 0; i < points; i++) {
            double a = (i / (double) points) * Math.PI * 2.0;
            rise.world.spawnParticles(ParticleTypes.GLOW,
                    rise.x + Math.cos(a) * radius, haloY,
                    rise.z + Math.sin(a) * radius, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** Души, поднимающиеся снизу к телу. */
    private void drawRisingSouls(Rise rise, double lift, double progress) {
        if (rise.tick % 3 != 0) return;
        for (int i = 0; i < 2; i++) {
            double a = Math.random() * Math.PI * 2.0;
            double r = 0.4 + Math.random() * 1.0;
            rise.world.spawnParticles(ParticleTypes.SOUL,
                    rise.x + Math.cos(a) * r, rise.y + 0.2,
                    rise.z + Math.sin(a) * r, 0, 0.0, 0.25, 0.0, 0.05);
        }
    }

    /** Искры-звёзды, срывающиеся с головы спирали и падающие вниз. */
    private void drawFallingStars(Rise rise, double lift, double progress) {
        if (rise.tick % 4 != 0) return;
        double head = rise.y + lift + 0.8;
        for (int i = 0; i < 2; i++) {
            double a = Math.random() * Math.PI * 2.0;
            double r = 0.6 + Math.random() * 0.4;
            rise.world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                    rise.x + Math.cos(a) * r, head,
                    rise.z + Math.sin(a) * r, 0,
                    Math.cos(a) * 0.1, -0.2, Math.sin(a) * 0.1, 0.15);
        }
    }

    /**
     * Силуэт головы дракона из частиц, парящий над телом и вращающийся вместе
     * с эффектом. Форма задана набором точек в локальных координатах
     * (z — вдоль морды вперёд, y — вверх, x — вбок), которые поворачиваются
     * вокруг вертикальной оси на текущий угол вращения.
     */
    private void drawDragonHead(Rise rise, double lift, double progress) {
        // голова появляется во второй половине подъёма и парит выше тела
        if (progress < 0.35) return;

        double headY = rise.y + lift + 2.4;
        double angle = Math.toRadians((rise.tick * 22.0) % 360.0);
        double cos = Math.cos(angle), sin = Math.sin(angle);
        double scale = 1.3;

        // Профиль морды в плоскости XZ=0 (x=0), пары точек отразим по X.
        // Контур: затылок → лоб → нос → верхняя губа → нижняя челюсть.
        double[][] profile = {
                {0.0, 0.55, -0.9},   // затылок верх
                {0.0, 0.62, -0.4},   // лоб
                {0.0, 0.58, 0.2},    // переносица
                {0.0, 0.48, 0.9},    // нос верх
                {0.0, 0.30, 1.25},   // кончик морды
                {0.0, 0.12, 0.95},   // верхняя губа
                {0.0, 0.05, 0.35},   // линия рта
                {0.0, -0.05, -0.3},  // горло
                {0.0, -0.18, 0.6},   // нижняя челюсть перед
                {0.0, -0.15, 1.0},   // подбородок
        };
        for (double[] p : profile) {
            emitLocal(rise, headY, cos, sin, scale, p[0], p[1], p[2],
                    ParticleTypes.DRAGON_BREATH);
        }

        // Боковые обводы морды (симметрично слева/справа) — придают объём.
        double[][] sides = {
                {0.28, 0.45, -0.6},
                {0.30, 0.35, 0.1},
                {0.26, 0.22, 0.7},
                {0.18, 0.12, 1.1},
                {0.30, 0.05, -0.1},
        };
        for (double[] p : sides) {
            emitLocal(rise, headY, cos, sin, scale, p[0], p[1], p[2],
                    ParticleTypes.SOUL_FIRE_FLAME);
            emitLocal(rise, headY, cos, sin, scale, -p[0], p[1], p[2],
                    ParticleTypes.SOUL_FIRE_FLAME);
        }

        // Рога, отведённые назад-вверх.
        double[][] horns = {
                {0.22, 0.70, -0.7},
                {0.30, 0.90, -1.0},
                {0.36, 1.10, -1.3},
        };
        for (double[] p : horns) {
            emitLocal(rise, headY, cos, sin, scale, p[0], p[1], p[2],
                    ParticleTypes.END_ROD);
            emitLocal(rise, headY, cos, sin, scale, -p[0], p[1], p[2],
                    ParticleTypes.END_ROD);
        }

        // Светящиеся глаза.
        emitLocal(rise, headY, cos, sin, scale, 0.22, 0.40, 0.35, ParticleTypes.FLAME);
        emitLocal(rise, headY, cos, sin, scale, -0.22, 0.40, 0.35, ParticleTypes.FLAME);

        // Дыхание из пасти — струя вперёд по направлению морды.
        if (rise.tick % 3 == 0) {
            double lx = 0.0, ly = 0.15, lz = 1.3 * scale;
            double wx = lx * cos - lz * sin;
            double wz = lx * sin + lz * cos;
            double vx = -sin * 0.35;
            double vz = cos * 0.35;
            rise.world.spawnParticles(ParticleTypes.DRAGON_BREATH,
                    rise.x + wx, headY + ly, rise.z + wz,
                    0, vx, 0.0, vz, 0.6);
            rise.world.spawnParticles(ParticleTypes.FLAME,
                    rise.x + wx, headY + ly, rise.z + wz,
                    0, vx, 0.02, vz, 0.4);
        }
    }

    /** Переводит локальную точку головы в мир (поворот вокруг Y) и рисует. */
    private void emitLocal(Rise rise, double baseY, double cos, double sin,
                           double scale, double lx, double ly, double lz,
                           net.minecraft.particle.ParticleEffect particle) {
        double sx = lx * scale, sy = ly * scale, sz = lz * scale;
        double wx = sx * cos - sz * sin;
        double wz = sx * sin + sz * cos;
        rise.world.spawnParticles(particle,
                rise.x + wx, baseY + sy, rise.z + wz, 1, 0.0, 0.0, 0.0, 0.0);
    }

    /** Кульминация наверху. */
    private void finish(Rise rise) {
        double top = rise.y + rise.height;
        rise.world.spawnParticles(ParticleTypes.FLASH, rise.x, top + 0.6, rise.z, 3, 0.0, 0.0, 0.0, 0.0);
        rise.world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, rise.x, top + 0.6, rise.z, 1, 0.0, 0.0, 0.0, 0.0);
        rise.world.spawnParticles(ParticleTypes.END_ROD, rise.x, top + 0.6, rise.z, 80, 0.6, 0.6, 0.6, 0.35);
        rise.world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, rise.x, top + 0.6, rise.z, 60, 0.6, 0.6, 0.6, 0.4);
        rise.world.spawnParticles(ParticleTypes.FIREWORK, rise.x, top + 0.6, rise.z, 40, 0.5, 0.5, 0.5, 0.3);

        sound(rise.world, rise.x, top, rise.z, SoundEvents.ENTITY_PLAYER_LEVELUP, 0.9F, 1.6F);
        sound(rise.world, rise.x, top, rise.z, SoundEvents.ITEM_TOTEM_USE, 0.7F, 1.4F);
        sound(rise.world, rise.x, top, rise.z, SoundEvents.BLOCK_BEACON_DEACTIVATE, 0.5F, 1.8F);
        sound(rise.world, rise.x, top, rise.z, SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, 0.7F, 1.2F);
    }

    private void sound(ServerWorld world, double x, double y, double z,
                       SoundEvent event, float volume, float pitch) {
        world.playSound(null, x, y, z, event, SoundCategory.PLAYERS, volume, pitch);
    }

    private void sound(ServerWorld world, double x, double y, double z,
                       RegistryEntry<SoundEvent> event, float volume, float pitch) {
        world.playSound(null, x, y, z, event, SoundCategory.PLAYERS, volume, pitch);
    }
}
