package ru.simpleauth;

import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Косметика на частицах: крылья, нимб, аура, спираль, след.
 * Всё рисуется сервером, игрокам ничего ставить не надо.
 */
public class CosmeticsManager {

    private final AuthManager manager;
    private int tick = 0;

    public CosmeticsManager(AuthManager manager) {
        this.manager = manager;
    }

    /** Доступные виды косметики. */
    public static final String[] TYPES = {
            "wings", "halo", "aura", "helix", "trail",
            "orbit", "tornado", "cube", "rings", "sphere",
            "pulse", "rain", "fountain", "infinity", "star", "crown",
            "galaxy", "dome", "lightning", "flower", "comet", "clock", "dna", "vortex"
    };

    /** Готовые сочетания: название -> вид и частицы. */
    private static final Map<String, String[]> COMBOS = new LinkedHashMap<>();

    static {
        COMBOS.put("angel", new String[]{"halo", "endrod"});
        COMBOS.put("demon", new String[]{"wings", "soul"});
        COMBOS.put("king", new String[]{"crown", "totem"});
        COMBOS.put("storm", new String[]{"lightning", "spark"});
        COMBOS.put("void", new String[]{"vortex", "portal"});
        COMBOS.put("galaxy", new String[]{"galaxy", "enchant"});
        COMBOS.put("inferno", new String[]{"tornado", "flame"});
        COMBOS.put("frost", new String[]{"dome", "glow"});
        COMBOS.put("love", new String[]{"flower", "heart"});
        COMBOS.put("ghost", new String[]{"sphere", "smoke"});
        COMBOS.put("nature", new String[]{"dna", "happy"});
        COMBOS.put("comet", new String[]{"comet", "firework"});
        COMBOS.put("clockwork", new String[]{"clock", "crit"});
        COMBOS.put("dragon", new String[]{"rings", "dragon"});
    }

    public static boolean isCombo(String value) {
        return value != null && COMBOS.containsKey(value.toLowerCase(Locale.ROOT));
    }

    public static String[] combo(String value) {
        return COMBOS.get(value.toLowerCase(Locale.ROOT));
    }

    public static String comboList() {
        return String.join(", ", COMBOS.keySet());
    }

    private static final Map<String, ParticleEffect> PARTICLES = new LinkedHashMap<>();

    static {
        PARTICLES.put("endrod", ParticleTypes.END_ROD);
        PARTICLES.put("flame", ParticleTypes.FLAME);
        PARTICLES.put("soul", ParticleTypes.SOUL_FIRE_FLAME);
        PARTICLES.put("enchant", ParticleTypes.ENCHANT);
        PARTICLES.put("heart", ParticleTypes.HEART);
        PARTICLES.put("happy", ParticleTypes.HAPPY_VILLAGER);
        PARTICLES.put("crit", ParticleTypes.CRIT);
        PARTICLES.put("cloud", ParticleTypes.CLOUD);
        PARTICLES.put("dragon", ParticleTypes.DRAGON_BREATH);
        PARTICLES.put("totem", ParticleTypes.TOTEM_OF_UNDYING);
        PARTICLES.put("spark", ParticleTypes.ELECTRIC_SPARK);
        PARTICLES.put("glow", ParticleTypes.GLOW);
        PARTICLES.put("portal", ParticleTypes.PORTAL);
        PARTICLES.put("note", ParticleTypes.NOTE);
        PARTICLES.put("smoke", ParticleTypes.SMOKE);
        PARTICLES.put("firework", ParticleTypes.FIREWORK);
    }

    public static boolean isType(String value) {
        if (value == null) return false;
        for (String type : TYPES) {
            if (type.equalsIgnoreCase(value)) return true;
        }
        return false;
    }

    public static boolean isParticle(String value) {
        return value != null && PARTICLES.containsKey(value.toLowerCase(Locale.ROOT));
    }

    public static String typeList() {
        return String.join(", ", TYPES);
    }

    public static String particleList() {
        return String.join(", ", PARTICLES.keySet());
    }

    private static ParticleEffect particle(String name) {
        ParticleEffect effect = name == null ? null : PARTICLES.get(name.toLowerCase(Locale.ROOT));
        return effect != null ? effect : ParticleTypes.END_ROD;
    }

    private Config config() {
        return manager.config();
    }

    // -------------------------------------------------------------- данные

    public Config.Cosmetic get(String playerName) {
        if (config().cosmetics == null) return null;
        return config().cosmetics.get(playerName.toLowerCase(Locale.ROOT));
    }

    public void set(String playerName, String type, String particleName) {
        Config.Cosmetic cosmetic = get(playerName);
        if (cosmetic == null) {
            cosmetic = new Config.Cosmetic();
            cosmetic.name = playerName;
        }
        if (type != null) cosmetic.type = type.toLowerCase(Locale.ROOT);
        if (particleName != null) cosmetic.particle = particleName.toLowerCase(Locale.ROOT);
        config().cosmetics.put(playerName.toLowerCase(Locale.ROOT), cosmetic);
        config().save();
    }

    public void clear(String playerName) {
        if (config().cosmetics == null) return;
        config().cosmetics.remove(playerName.toLowerCase(Locale.ROOT));
        config().save();
    }

    // ------------------------------------------------------------ отрисовка

    public void tick(MinecraftServer server) {
        if (config().cosmetics == null || config().cosmetics.isEmpty()) return;
        tick++;
        if (tick % 2 != 0) return;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!manager.isAuthenticated(player)) continue;
            if (player.isSpectator()) continue;

            Config.Cosmetic cosmetic = get(player.getGameProfile().getName());
            if (cosmetic == null || cosmetic.type == null) continue;

            try {
                draw(player, cosmetic);
            } catch (Exception e) {
                SimpleAuth.LOGGER.warn("[SimpleAuth] Ошибка отрисовки косметики", e);
            }
        }
    }

    private void draw(ServerPlayerEntity player, Config.Cosmetic cosmetic) {
        ServerWorld world = player.getServerWorld();
        ParticleEffect effect = particle(cosmetic.particle);
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

        switch (cosmetic.type) {
            case "wings" -> drawWings(world, player, effect, x, y, z);
            case "halo" -> drawHalo(world, effect, x, y, z);
            case "aura" -> drawAura(world, effect, x, y, z);
            case "helix" -> drawHelix(world, effect, x, y, z);
            case "trail" -> drawTrail(world, player, effect, x, y, z);
            case "orbit" -> drawOrbit(world, effect, x, y, z);
            case "tornado" -> drawTornado(world, effect, x, y, z);
            case "cube" -> drawCube(world, effect, x, y, z);
            case "rings" -> drawRings(world, effect, x, y, z);
            case "sphere" -> drawSphere(world, effect, x, y, z);
            case "pulse" -> drawPulse(world, effect, x, y, z);
            case "rain" -> drawRain(world, effect, x, y, z);
            case "fountain" -> drawFountain(world, effect, x, y, z);
            case "infinity" -> drawInfinity(world, effect, x, y, z);
            case "star" -> drawStar(world, effect, x, y, z);
            case "crown" -> drawCrown(world, effect, x, y, z);
            case "galaxy" -> drawGalaxy(world, effect, x, y, z);
            case "dome" -> drawDome(world, effect, x, y, z);
            case "lightning" -> drawLightning(world, effect, x, y, z);
            case "flower" -> drawFlower(world, effect, x, y, z);
            case "comet" -> drawComet(world, player, effect, x, y, z);
            case "clock" -> drawClock(world, effect, x, y, z);
            case "dna" -> drawDna(world, effect, x, y, z);
            case "vortex" -> drawVortex(world, effect, x, y, z);
            default -> {
            }
        }
    }

    private void drawWings(ServerWorld world, ServerPlayerEntity player, ParticleEffect effect,
                           double x, double y, double z) {
        float yaw = (float) Math.toRadians(player.getYaw());
        // вектор "вперёд" и "вправо" относительно взгляда
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double rightX = Math.cos(yaw);
        double rightZ = Math.sin(yaw);

        // взмах: крылья то шире, то уже
        double flap = 0.75 + 0.25 * Math.sin(tick * 0.18);

        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 9; i++) {
                double t = i / 8.0;
                double spread = (0.18 + t * 0.85) * flap;
                double height = 1.05 + t * 1.0 - t * t * 0.55;
                double px = x + rightX * spread * side - forwardX * 0.28;
                double pz = z + rightZ * spread * side - forwardZ * 0.28;
                world.spawnParticles(effect, px, y + height, pz, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    private void drawHalo(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        double radius = 0.36;
        for (int i = 0; i < 8; i++) {
            double angle = (i / 8.0) * Math.PI * 2.0 + tick * 0.08;
            world.spawnParticles(effect,
                    x + Math.cos(angle) * radius,
                    y + 2.15,
                    z + Math.sin(angle) * radius,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private void drawAura(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        double radius = 0.95;
        for (int i = 0; i < 6; i++) {
            double angle = (i / 6.0) * Math.PI * 2.0 + tick * 0.12;
            world.spawnParticles(effect,
                    x + Math.cos(angle) * radius,
                    y + 0.1,
                    z + Math.sin(angle) * radius,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private void drawHelix(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        for (int i = 0; i < 2; i++) {
            double angle = tick * 0.22 + i * Math.PI;
            double height = ((tick * 0.04) % 2.2);
            world.spawnParticles(effect,
                    x + Math.cos(angle) * 0.55,
                    y + height,
                    z + Math.sin(angle) * 0.55,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** Три точки на разных орбитах, как электроны вокруг ядра. */
    private void drawOrbit(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        for (int i = 0; i < 3; i++) {
            double angle = tick * (0.10 + i * 0.035) + i * 2.09;
            double tilt = Math.sin(tick * 0.05 + i * 1.7) * 0.75;
            world.spawnParticles(effect,
                    x + Math.cos(angle) * 1.15,
                    y + 1.0 + tilt,
                    z + Math.sin(angle) * 1.15,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** Расширяющийся кверху вихрь. */
    private void drawTornado(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        for (int i = 0; i < 6; i++) {
            double height = (i / 6.0) * 2.4;
            double radius = 0.15 + height * 0.45;
            double angle = tick * 0.35 + height * 3.2;
            world.spawnParticles(effect,
                    x + Math.cos(angle) * radius,
                    y + height,
                    z + Math.sin(angle) * radius,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** Каркас куба, медленно вращающийся вокруг игрока. */
    private void drawCube(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        double size = 0.85;
        double angle = tick * 0.045;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        // по одной точке на каждом ребре, бегущей туда-сюда
        double t = (Math.sin(tick * 0.09) + 1.0) / 2.0;

        for (int corner = 0; corner < 4; corner++) {
            double cx = (corner == 0 || corner == 3) ? -size : size;
            double cz = (corner < 2) ? -size : size;
            double rx = cx * cos - cz * sin;
            double rz = cx * sin + cz * cos;
            // вертикальные рёбра
            world.spawnParticles(effect, x + rx, y + 0.1 + t * 1.9, z + rz, 1, 0.0, 0.0, 0.0, 0.0);
            // верх и низ
            world.spawnParticles(effect, x + rx, y + 0.1, z + rz, 1, 0.0, 0.0, 0.0, 0.0);
            world.spawnParticles(effect, x + rx, y + 2.0, z + rz, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** Три кольца на разной высоте, вращаются в разные стороны. */
    private void drawRings(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        double[] heights = {0.25, 1.05, 1.85};
        double[] radii = {0.75, 1.0, 0.6};
        for (int r = 0; r < heights.length; r++) {
            double direction = (r % 2 == 0) ? 1.0 : -1.0;
            for (int i = 0; i < 5; i++) {
                double angle = (i / 5.0) * Math.PI * 2.0 + tick * 0.11 * direction;
                world.spawnParticles(effect,
                        x + Math.cos(angle) * radii[r],
                        y + heights[r],
                        z + Math.sin(angle) * radii[r],
                        1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    /** Точки, равномерно рассыпанные по сфере. */
    private void drawSphere(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        double golden = Math.PI * (3.0 - Math.sqrt(5.0));
        int total = 40;
        int offset = (tick * 3) % total;
        for (int k = 0; k < 6; k++) {
            int i = (offset + k * 7) % total;
            double yy = 1.0 - (i / (double) (total - 1)) * 2.0;
            double radius = Math.sqrt(Math.max(0.0, 1.0 - yy * yy)) * 1.1;
            double angle = golden * i + tick * 0.04;
            world.spawnParticles(effect,
                    x + Math.cos(angle) * radius,
                    y + 1.0 + yy * 1.1,
                    z + Math.sin(angle) * radius,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** Волна, разбегающаяся кольцом от ног. */
    private void drawPulse(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        double phase = (tick % 40) / 40.0;
        double radius = phase * 2.6;
        double height = phase * 0.5;
        int points = 6 + (int) (phase * 10);
        for (int i = 0; i < points; i++) {
            double angle = (i / (double) points) * Math.PI * 2.0;
            world.spawnParticles(effect,
                    x + Math.cos(angle) * radius,
                    y + 0.1 + height,
                    z + Math.sin(angle) * radius,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** Дождик, сыплющийся сверху. */
    private void drawRain(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        for (int i = 0; i < 3; i++) {
            double angle = Math.random() * Math.PI * 2.0;
            double radius = Math.random() * 1.1;
            world.spawnParticles(effect,
                    x + Math.cos(angle) * radius,
                    y + 2.6,
                    z + Math.sin(angle) * radius,
                    1, 0.0, -0.12, 0.0, 0.06);
        }
    }

    /** Фонтан из-под ног. */
    private void drawFountain(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        world.spawnParticles(effect, x, y + 0.15, z, 0, 0.0, 0.55, 0.0, 0.55);
        for (int i = 0; i < 2; i++) {
            double angle = tick * 0.4 + i * Math.PI;
            world.spawnParticles(effect,
                    x + Math.cos(angle) * 0.2,
                    y + 0.15,
                    z + Math.sin(angle) * 0.2,
                    0, Math.cos(angle) * 0.12, 0.4, Math.sin(angle) * 0.12, 0.35);
        }
    }

    /** Восьмёрка вокруг игрока. */
    private void drawInfinity(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        for (int i = 0; i < 4; i++) {
            double t = tick * 0.09 + i * 0.55;
            double denominator = 1.0 + Math.sin(t) * Math.sin(t);
            double px = (1.4 * Math.cos(t)) / denominator;
            double pz = (1.4 * Math.sin(t) * Math.cos(t)) / denominator;
            world.spawnParticles(effect, x + px, y + 1.1, z + pz, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** Пятиконечная звезда над головой. */
    private void drawStar(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        double spin = tick * 0.05;
        for (int i = 0; i < 10; i++) {
            double angle = (i / 10.0) * Math.PI * 2.0 + spin;
            double radius = (i % 2 == 0) ? 0.75 : 0.32;
            world.spawnParticles(effect,
                    x + Math.cos(angle) * radius,
                    y + 2.35,
                    z + Math.sin(angle) * radius,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** Корона с зубцами над головой. */
    private void drawCrown(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        double spin = tick * 0.06;
        for (int i = 0; i < 8; i++) {
            double angle = (i / 8.0) * Math.PI * 2.0 + spin;
            double spike = (i % 2 == 0) ? 0.28 : 0.0;
            world.spawnParticles(effect,
                    x + Math.cos(angle) * 0.42,
                    y + 2.1 + spike,
                    z + Math.sin(angle) * 0.42,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** Спиральная галактика с двумя рукавами. */
    private void drawGalaxy(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        for (int arm = 0; arm < 2; arm++) {
            for (int i = 0; i < 5; i++) {
                double t = i / 5.0;
                double radius = 0.25 + t * 1.6;
                double angle = t * 3.4 + arm * Math.PI + tick * 0.06;
                world.spawnParticles(effect,
                        x + Math.cos(angle) * radius,
                        y + 1.15 + Math.sin(t * 3.0) * 0.12,
                        z + Math.sin(angle) * radius,
                        1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    /** Купол-щит вокруг игрока. */
    private void drawDome(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        for (int i = 0; i < 7; i++) {
            double lift = ((tick * 0.03) + i / 7.0) % 1.0;
            double theta = lift * (Math.PI / 2.0);
            double radius = Math.cos(theta) * 1.35;
            double height = Math.sin(theta) * 1.7;
            double angle = tick * 0.14 + i * 0.9;
            world.spawnParticles(effect,
                    x + Math.cos(angle) * radius,
                    y + 0.15 + height,
                    z + Math.sin(angle) * radius,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** Ломаная молния, бьющая сверху вниз. */
    private void drawLightning(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        if (tick % 6 != 0) return;
        double baseAngle = Math.random() * Math.PI * 2.0;
        double offsetX = Math.cos(baseAngle) * 0.8;
        double offsetZ = Math.sin(baseAngle) * 0.8;
        double driftX = 0.0;
        double driftZ = 0.0;
        for (int i = 0; i < 9; i++) {
            double height = 2.6 - i * 0.3;
            driftX += (Math.random() - 0.5) * 0.28;
            driftZ += (Math.random() - 0.5) * 0.28;
            world.spawnParticles(effect,
                    x + offsetX + driftX,
                    y + height,
                    z + offsetZ + driftZ,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** Цветок: лепестки по кривой розы. */
    private void drawFlower(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        for (int i = 0; i < 8; i++) {
            double angle = (i / 8.0) * Math.PI * 2.0 + tick * 0.045;
            double radius = Math.abs(Math.cos(3.0 * angle)) * 1.3 + 0.15;
            world.spawnParticles(effect,
                    x + Math.cos(angle) * radius,
                    y + 0.25,
                    z + Math.sin(angle) * radius,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** Хвост кометы, тянется за спиной по направлению движения. */
    private void drawComet(ServerWorld world, ServerPlayerEntity player, ParticleEffect effect,
                           double x, double y, double z) {
        Vec3d velocity = player.getVelocity();
        double length = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        double dirX;
        double dirZ;
        if (length > 0.02) {
            dirX = -velocity.x / length;
            dirZ = -velocity.z / length;
        } else {
            float yaw = (float) Math.toRadians(player.getYaw());
            dirX = Math.sin(yaw);
            dirZ = -Math.cos(yaw);
        }
        for (int i = 0; i < 6; i++) {
            double distance = 0.3 + i * 0.32;
            double wobble = Math.sin(tick * 0.3 + i * 0.7) * 0.16;
            world.spawnParticles(effect,
                    x + dirX * distance + dirZ * wobble,
                    y + 1.1 + Math.sin(tick * 0.2 + i * 0.5) * 0.1,
                    z + dirZ * distance - dirX * wobble,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** Часы: две стрелки над головой, крутятся с разной скоростью. */
    private void drawClock(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        double[] speeds = {0.22, 0.03};
        double[] lengths = {0.85, 0.55};
        for (int hand = 0; hand < 2; hand++) {
            double angle = tick * speeds[hand];
            for (int i = 1; i <= 4; i++) {
                double radius = (i / 4.0) * lengths[hand];
                world.spawnParticles(effect,
                        x + Math.cos(angle) * radius,
                        y + 2.3,
                        z + Math.sin(angle) * radius,
                        1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    /** Двойная спираль с перекладинами. */
    private void drawDna(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        for (int i = 0; i < 5; i++) {
            double height = ((tick * 0.035) + i / 5.0) % 1.0;
            double angle = height * Math.PI * 4.0 + tick * 0.05;
            double px = Math.cos(angle) * 0.55;
            double pz = Math.sin(angle) * 0.55;
            double py = y + 0.1 + height * 2.2;
            world.spawnParticles(effect, x + px, py, z + pz, 1, 0.0, 0.0, 0.0, 0.0);
            world.spawnParticles(effect, x - px, py, z - pz, 1, 0.0, 0.0, 0.0, 0.0);
            // перекладина между нитями
            if (i % 2 == 0) {
                world.spawnParticles(effect, x + px * 0.35, py, z + pz * 0.35, 1, 0.0, 0.0, 0.0, 0.0);
                world.spawnParticles(effect, x - px * 0.35, py, z - pz * 0.35, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    /** Воронка: спираль, затягивающаяся внутрь. */
    private void drawVortex(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        for (int i = 0; i < 7; i++) {
            double phase = ((tick * 0.04) + i / 7.0) % 1.0;
            double radius = 2.1 * (1.0 - phase);
            double angle = phase * 9.0 + tick * 0.12;
            world.spawnParticles(effect,
                    x + Math.cos(angle) * radius,
                    y + 0.1 + phase * 0.9,
                    z + Math.sin(angle) * radius,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private void drawTrail(ServerWorld world, ServerPlayerEntity player, ParticleEffect effect,
                          double x, double y, double z) {
        // след только когда игрок реально движется
        double speed = player.getVelocity().horizontalLengthSquared();
        if (speed < 0.002) return;
        world.spawnParticles(effect, x, y + 0.1, z, 2, 0.15, 0.05, 0.15, 0.01);
    }
}
