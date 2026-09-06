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
 * Косметика на частицах. Всё рисуется сервером, игрокам ничего ставить не надо.
 *
 * Про нагрузку: каждая частица — отдельный пакет всем, кто рядом. Поэтому
 * рисуем не каждый тик, держим общий лимит частиц на тик и в каждой фигуре
 * обходимся минимумом точек, растягивая её появление во времени.
 */
public class CosmeticsManager {

    private final AuthManager manager;
    private int tick = 0;
    private int budget = 0;
    /** Кому сейчас рисуем — чтобы не слать частицы ему самому. */
    private ServerPlayerEntity current = null;
    private boolean currentShowSelf = false;

    public CosmeticsManager(AuthManager manager) {
        this.manager = manager;
    }

    public static final String[] TYPES = {
            // вокруг игрока
            "wings", "halo", "aura", "helix", "orbit", "rings", "sphere", "dome",
            // движение и следы
            "trail", "comet", "footsteps", "swarm",
            // столбы и вспышки
            "tornado", "vortex", "pulse", "lightning", "pillar", "fountain",
            // фигуры
            "cube", "infinity", "star", "crown", "galaxy", "flower", "clock", "dna",
            // на теле
            "cape", "horns", "ears", "heartbeat", "runes", "bubbles", "rain",
            // живые: частицы летят сами, а не висят точками
            "burst", "shockwave", "snow", "flies", "wisp", "spin", "geyser", "sparkle"
    };

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

    /** Готовые сочетания: название -> вид и частицы. */
    private static final Map<String, String[]> COMBOS = new LinkedHashMap<>();

    static {
        COMBOS.put("angel", new String[]{"halo", "endrod"});
        COMBOS.put("demon", new String[]{"horns", "flame"});
        COMBOS.put("king", new String[]{"crown", "totem"});
        COMBOS.put("knight", new String[]{"cape", "crit"});
        COMBOS.put("cat", new String[]{"ears", "heart"});
        COMBOS.put("storm", new String[]{"lightning", "spark"});
        COMBOS.put("void", new String[]{"vortex", "portal"});
        COMBOS.put("galaxy", new String[]{"galaxy", "enchant"});
        COMBOS.put("inferno", new String[]{"tornado", "flame"});
        COMBOS.put("frost", new String[]{"dome", "glow"});
        COMBOS.put("love", new String[]{"heartbeat", "heart"});
        COMBOS.put("ghost", new String[]{"sphere", "smoke"});
        COMBOS.put("nature", new String[]{"dna", "happy"});
        COMBOS.put("bees", new String[]{"swarm", "happy"});
        COMBOS.put("aqua", new String[]{"bubbles", "glow"});
        COMBOS.put("beacon", new String[]{"pillar", "endrod"});
        COMBOS.put("comet", new String[]{"comet", "firework"});
        COMBOS.put("dragon", new String[]{"rings", "dragon"});
        COMBOS.put("wizard", new String[]{"runes", "enchant"});
        COMBOS.put("disco", new String[]{"orbit", "note"});
        COMBOS.put("winter", new String[]{"snow", "glow"});
        COMBOS.put("swamp", new String[]{"flies", "happy"});
        COMBOS.put("titan", new String[]{"shockwave", "crit"});
        COMBOS.put("volcano", new String[]{"geyser", "flame"});
        COMBOS.put("spirit", new String[]{"wisp", "soul"});
        COMBOS.put("party", new String[]{"burst", "firework"});
    }

    // ------------------------------------------------------------ справочники

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

    public static boolean isCombo(String value) {
        return value != null && COMBOS.containsKey(value.toLowerCase(Locale.ROOT));
    }

    public static String[] combo(String value) {
        return COMBOS.get(value.toLowerCase(Locale.ROOT));
    }

    public static String typeList() {
        return String.join(", ", TYPES);
    }

    public static String particleList() {
        return String.join(", ", PARTICLES.keySet());
    }

    public static String comboList() {
        return String.join(", ", COMBOS.keySet());
    }

    /** Всё разом: наборы, виды и частицы. Отсюда берутся подсказки по TAB. */
    public static java.util.List<String> allNames() {
        java.util.List<String> names = new java.util.ArrayList<>(COMBOS.keySet());
        java.util.Collections.addAll(names, TYPES);
        names.addAll(PARTICLES.keySet());
        names.add("off");
        names.add("list");
        names.add("self");
        return names;
    }

    /** Единый список для /cos list: всё, что можно ввести одной командой. */
    public static java.util.List<String> fullList() {
        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add("Пиши /cos <название> — вид, частицы и наборы понимаются одинаково.");
        lines.add("Наборы (сразу вид + частицы): " + comboList());
        lines.add("Виды: " + typeList());
        lines.add("Частицы: " + particleList());
        lines.add("Снять: /cos off | Показать себе: /cos self");
        return lines;
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

        int interval = Math.max(1, config().cosmeticsIntervalTicks);
        if (tick % interval != 0) return;

        // общий лимит на всех: даже при полном сервере нагрузка не взлетит
        budget = Math.max(10, config().cosmeticsMaxParticlesPerTick);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (budget <= 0) break;
            if (!manager.isAuthenticated(player)) continue;
            if (player.isSpectator() || player.isInvisible()) continue;

            Config.Cosmetic cosmetic = get(player.getGameProfile().getName());
            if (cosmetic == null || cosmetic.type == null) continue;

            current = player;
            currentShowSelf = cosmetic.showSelf;
            try {
                draw(player, cosmetic);
            } catch (Exception e) {
                SimpleAuth.LOGGER.warn("[SimpleAuth] Ошибка отрисовки косметики", e);
            } finally {
                current = null;
            }
        }
    }

    /** Переключает, видит ли игрок собственную косметику. */
    public boolean toggleShowSelf(String playerName) {
        Config.Cosmetic cosmetic = get(playerName);
        if (cosmetic == null) return false;
        cosmetic.showSelf = !cosmetic.showSelf;
        config().save();
        return cosmetic.showSelf;
    }

    /** Одна частица с учётом общего лимита. */
    private void spawn(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        emit(world, effect, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
    }

    /** Частица с заданной скоростью — для фонтана, снега и прочего живого. */
    private void spawnMoving(ServerWorld world, ParticleEffect effect, double x, double y, double z,
                             double dx, double dy, double dz, double speed) {
        emit(world, effect, x, y, z, 0, dx, dy, dz, speed);
    }

    /**
     * Отправка частицы. Если владелец не хочет видеть свою косметику, шлём её
     * всем вокруг поимённо, минуя его самого: от первого лица частицы иначе
     * лезут прямо в камеру, а какой ракурс у игрока — сервер не знает.
     */
    private void emit(ServerWorld world, ParticleEffect effect, double x, double y, double z,
                      int count, double dx, double dy, double dz, double speed) {
        if (budget <= 0) return;
        budget--;

        if (currentShowSelf || current == null) {
            world.spawnParticles(effect, x, y, z, count, dx, dy, dz, speed);
            return;
        }

        for (ServerPlayerEntity viewer : world.getPlayers()) {
            if (viewer == current) continue;
            if (viewer.squaredDistanceTo(x, y, z) > 1024.0) continue;
            world.spawnParticles(viewer, effect, false, x, y, z, count, dx, dy, dz, speed);
        }
    }

    private void draw(ServerPlayerEntity player, Config.Cosmetic cosmetic) {
        ServerWorld world = player.getServerWorld();
        ParticleEffect effect = particle(cosmetic.particle);
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

        switch (cosmetic.type) {
            case "wings" -> wings(world, player, effect, x, y, z);
            case "halo" -> halo(world, effect, x, y, z);
            case "aura" -> aura(world, effect, x, y, z);
            case "helix" -> helix(world, effect, x, y, z);
            case "orbit" -> orbit(world, effect, x, y, z);
            case "rings" -> rings(world, effect, x, y, z);
            case "sphere" -> sphere(world, effect, x, y, z);
            case "dome" -> dome(world, effect, x, y, z);
            case "trail" -> trail(world, player, effect, x, y, z);
            case "comet" -> comet(world, player, effect, x, y, z);
            case "footsteps" -> footsteps(world, player, effect, x, y, z);
            case "swarm" -> swarm(world, effect, x, y, z);
            case "tornado" -> tornado(world, effect, x, y, z);
            case "vortex" -> vortex(world, effect, x, y, z);
            case "pulse" -> pulse(world, effect, x, y, z);
            case "lightning" -> lightning(world, effect, x, y, z);
            case "pillar" -> pillar(world, effect, x, y, z);
            case "fountain" -> fountain(world, effect, x, y, z);
            case "cube" -> cube(world, effect, x, y, z);
            case "infinity" -> infinity(world, effect, x, y, z);
            case "star" -> star(world, effect, x, y, z);
            case "crown" -> crown(world, effect, x, y, z);
            case "galaxy" -> galaxy(world, effect, x, y, z);
            case "flower" -> flower(world, effect, x, y, z);
            case "clock" -> clock(world, effect, x, y, z);
            case "dna" -> dna(world, effect, x, y, z);
            case "cape" -> cape(world, player, effect, x, y, z);
            case "horns" -> horns(world, player, effect, x, y, z);
            case "ears" -> ears(world, player, effect, x, y, z);
            case "heartbeat" -> heartbeat(world, effect, x, y, z);
            case "runes" -> runes(world, effect, x, y, z);
            case "bubbles" -> bubbles(world, effect, x, y, z);
            case "rain" -> rain(world, effect, x, y, z);
            case "burst" -> burst(world, effect, x, y, z);
            case "shockwave" -> shockwave(world, effect, x, y, z);
            case "snow" -> snow(world, effect, x, y, z);
            case "flies" -> flies(world, effect, x, y, z);
            case "wisp" -> wisp(world, effect, x, y, z);
            case "spin" -> spin(world, effect, x, y, z);
            case "geyser" -> geyser(world, effect, x, y, z);
            case "sparkle" -> sparkle(world, effect, x, y, z);
            default -> {
            }
        }
    }

    // --------------------------------------------------------- вокруг игрока

    private void wings(ServerWorld world, ServerPlayerEntity player, ParticleEffect effect,
                       double x, double y, double z) {
        float yaw = (float) Math.toRadians(player.getYaw());
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double rightX = Math.cos(yaw);
        double rightZ = Math.sin(yaw);
        double flap = 0.75 + 0.25 * Math.sin(tick * 0.22);

        // пять точек на крыло вместо девяти: контур читается так же
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 5; i++) {
                double t = i / 4.0;
                double spread = (0.2 + t * 0.85) * flap;
                double height = 1.05 + t * 1.0 - t * t * 0.55;
                spawn(world, effect,
                        x + rightX * spread * side - forwardX * 0.28,
                        y + height,
                        z + rightZ * spread * side - forwardZ * 0.28);
            }
        }
    }

    private void halo(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        for (int i = 0; i < 4; i++) {
            double angle = (i / 4.0) * Math.PI * 2.0 + tick * 0.12;
            spawn(world, effect, x + Math.cos(angle) * 0.36, y + 2.15, z + Math.sin(angle) * 0.36);
        }
    }

    private void aura(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        for (int i = 0; i < 4; i++) {
            double angle = (i / 4.0) * Math.PI * 2.0 + tick * 0.16;
            spawn(world, effect, x + Math.cos(angle) * 0.95, y + 0.1, z + Math.sin(angle) * 0.95);
        }
    }

    private void helix(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        for (int i = 0; i < 2; i++) {
            double angle = tick * 0.3 + i * Math.PI;
            double height = ((tick * 0.06) % 2.2);
            spawn(world, effect, x + Math.cos(angle) * 0.55, y + height, z + Math.sin(angle) * 0.55);
        }
    }

    private void orbit(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        for (int i = 0; i < 3; i++) {
            double angle = tick * (0.14 + i * 0.05) + i * 2.09;
            double tilt = Math.sin(tick * 0.07 + i * 1.7) * 0.75;
            spawn(world, effect, x + Math.cos(angle) * 1.15, y + 1.0 + tilt, z + Math.sin(angle) * 1.15);
        }
    }

    private void rings(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        double[] heights = {0.25, 1.05, 1.85};
        double[] radii = {0.75, 1.0, 0.6};
        for (int r = 0; r < heights.length; r++) {
            double direction = (r % 2 == 0) ? 1.0 : -1.0;
            // по две точки на кольцо, но они бегут — глаз достраивает круг
            for (int i = 0; i < 2; i++) {
                double angle = (i / 2.0) * Math.PI * 2.0 + tick * 0.16 * direction;
                spawn(world, effect,
                        x + Math.cos(angle) * radii[r], y + heights[r], z + Math.sin(angle) * radii[r]);
            }
        }
    }

    private void sphere(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        double golden = Math.PI * (3.0 - Math.sqrt(5.0));
        int total = 40;
        int offset = (tick * 5) % total;
        for (int k = 0; k < 4; k++) {
            int i = (offset + k * 11) % total;
            double yy = 1.0 - (i / (double) (total - 1)) * 2.0;
            double radius = Math.sqrt(Math.max(0.0, 1.0 - yy * yy)) * 1.1;
            double angle = golden * i + tick * 0.05;
            spawn(world, effect, x + Math.cos(angle) * radius, y + 1.0 + yy * 1.1, z + Math.sin(angle) * radius);
        }
    }

    private void dome(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        for (int i = 0; i < 5; i++) {
            double lift = ((tick * 0.04) + i / 5.0) % 1.0;
            double theta = lift * (Math.PI / 2.0);
            double angle = tick * 0.18 + i * 0.9;
            spawn(world, effect,
                    x + Math.cos(angle) * Math.cos(theta) * 1.35,
                    y + 0.15 + Math.sin(theta) * 1.7,
                    z + Math.sin(angle) * Math.cos(theta) * 1.35);
        }
    }

    // ------------------------------------------------------ движение и следы

    private void trail(ServerWorld world, ServerPlayerEntity player, ParticleEffect effect,
                       double x, double y, double z) {
        if (player.getVelocity().horizontalLengthSquared() < 0.002) return;
        spawn(world, effect, x, y + 0.1, z);
        spawn(world, effect, x + (Math.random() - 0.5) * 0.3, y + 0.15, z + (Math.random() - 0.5) * 0.3);
    }

    private void comet(ServerWorld world, ServerPlayerEntity player, ParticleEffect effect,
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
        for (int i = 0; i < 4; i++) {
            double distance = 0.35 + i * 0.42;
            double wobble = Math.sin(tick * 0.35 + i * 0.7) * 0.16;
            spawn(world, effect,
                    x + dirX * distance + dirZ * wobble,
                    y + 1.1 + Math.sin(tick * 0.25 + i * 0.5) * 0.1,
                    z + dirZ * distance - dirX * wobble);
        }
    }

    /** Отпечатки под ногами: по одному на шаг, слева и справа поочерёдно. */
    private void footsteps(ServerWorld world, ServerPlayerEntity player, ParticleEffect effect,
                           double x, double y, double z) {
        if (player.getVelocity().horizontalLengthSquared() < 0.004) return;
        if (tick % 4 != 0) return;
        float yaw = (float) Math.toRadians(player.getYaw());
        double side = ((tick / 4) % 2 == 0) ? 0.22 : -0.22;
        spawn(world, effect, x + Math.cos(yaw) * side, y + 0.05, z + Math.sin(yaw) * side);
    }

    /** Рой: точки хаотично снуют вокруг, как пчёлы. */
    private void swarm(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        for (int i = 0; i < 3; i++) {
            double a = tick * (0.19 + i * 0.11) + i * 2.2;
            double b = tick * (0.13 + i * 0.07);
            spawn(world, effect,
                    x + Math.cos(a) * (0.7 + Math.sin(b) * 0.4),
                    y + 1.0 + Math.sin(a * 1.7) * 0.7,
                    z + Math.sin(a) * (0.7 + Math.cos(b) * 0.4));
        }
    }

    // ----------------------------------------------------- столбы и вспышки

    private void tornado(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        for (int i = 0; i < 4; i++) {
            double height = ((tick * 0.05) + i / 4.0) % 1.0 * 2.4;
            double radius = 0.15 + height * 0.45;
            double angle = tick * 0.4 + height * 3.2;
            spawn(world, effect, x + Math.cos(angle) * radius, y + height, z + Math.sin(angle) * radius);
        }
    }

    private void vortex(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        for (int i = 0; i < 4; i++) {
            double phase = ((tick * 0.05) + i / 4.0) % 1.0;
            double radius = 2.1 * (1.0 - phase);
            double angle = phase * 9.0 + tick * 0.15;
            spawn(world, effect, x + Math.cos(angle) * radius, y + 0.1 + phase * 0.9, z + Math.sin(angle) * radius);
        }
    }

    private void pulse(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        double phase = (tick % 30) / 30.0;
        double radius = phase * 2.6;
        for (int i = 0; i < 5; i++) {
            double angle = (i / 5.0) * Math.PI * 2.0;
            spawn(world, effect, x + Math.cos(angle) * radius, y + 0.1 + phase * 0.5, z + Math.sin(angle) * radius);
        }
    }

    private void lightning(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        if (tick % 8 != 0) return;
        double baseAngle = Math.random() * Math.PI * 2.0;
        double offsetX = Math.cos(baseAngle) * 0.8;
        double offsetZ = Math.sin(baseAngle) * 0.8;
        double driftX = 0.0;
        double driftZ = 0.0;
        for (int i = 0; i < 6; i++) {
            driftX += (Math.random() - 0.5) * 0.3;
            driftZ += (Math.random() - 0.5) * 0.3;
            spawn(world, effect, x + offsetX + driftX, y + 2.6 - i * 0.45, z + offsetZ + driftZ);
        }
    }

    /** Столб света прямо вверх, как у маяка. */
    private void pillar(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        for (int i = 0; i < 3; i++) {
            double height = ((tick * 0.08) + i / 3.0) % 1.0;
            double wobble = Math.sin(height * 6.0 + tick * 0.1) * 0.08;
            spawn(world, effect, x + wobble, y + 0.2 + height * 4.5, z + wobble);
        }
    }

    private void fountain(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        spawnMoving(world, effect, x, y + 0.15, z, 0.0, 0.55, 0.0, 0.5);
        double angle = tick * 0.5;
        spawnMoving(world, effect, x + Math.cos(angle) * 0.2, y + 0.15, z + Math.sin(angle) * 0.2,
                Math.cos(angle) * 0.12, 0.4, Math.sin(angle) * 0.12, 0.35);
    }

    // ------------------------------------------------------------- фигуры

    private void cube(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        double size = 0.85;
        double angle = tick * 0.06;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double t = (Math.sin(tick * 0.12) + 1.0) / 2.0;

        for (int corner = 0; corner < 4; corner++) {
            double cx = (corner == 0 || corner == 3) ? -size : size;
            double cz = (corner < 2) ? -size : size;
            double rx = cx * cos - cz * sin;
            double rz = cx * sin + cz * cos;
            spawn(world, effect, x + rx, y + 0.1 + t * 1.9, z + rz);
        }
    }

    private void infinity(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        for (int i = 0; i < 3; i++) {
            double t = tick * 0.12 + i * 0.7;
            double denominator = 1.0 + Math.sin(t) * Math.sin(t);
            spawn(world, effect,
                    x + (1.4 * Math.cos(t)) / denominator,
                    y + 1.1,
                    z + (1.4 * Math.sin(t) * Math.cos(t)) / denominator);
        }
    }

    private void star(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        double spin = tick * 0.07;
        // рисуем половину лучей за раз, чередуя — форма всё равно читается
        int offset = (tick % 2 == 0) ? 0 : 1;
        for (int i = offset; i < 10; i += 2) {
            double angle = (i / 10.0) * Math.PI * 2.0 + spin;
            double radius = (i % 2 == 0) ? 0.75 : 0.32;
            spawn(world, effect, x + Math.cos(angle) * radius, y + 2.35, z + Math.sin(angle) * radius);
        }
    }

    private void crown(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        double spin = tick * 0.08;
        for (int i = 0; i < 4; i++) {
            double angle = (i / 4.0) * Math.PI * 2.0 + spin;
            double spike = (i % 2 == 0) ? 0.28 : 0.0;
            spawn(world, effect, x + Math.cos(angle) * 0.42, y + 2.1 + spike, z + Math.sin(angle) * 0.42);
        }
    }

    private void galaxy(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        for (int arm = 0; arm < 2; arm++) {
            for (int i = 0; i < 3; i++) {
                double t = ((tick * 0.02) + i / 3.0) % 1.0;
                double radius = 0.25 + t * 1.6;
                double angle = t * 3.4 + arm * Math.PI + tick * 0.08;
                spawn(world, effect,
                        x + Math.cos(angle) * radius,
                        y + 1.15 + Math.sin(t * 3.0) * 0.12,
                        z + Math.sin(angle) * radius);
            }
        }
    }

    private void flower(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        for (int i = 0; i < 5; i++) {
            double angle = (i / 5.0) * Math.PI * 2.0 + tick * 0.06;
            double radius = Math.abs(Math.cos(3.0 * angle)) * 1.3 + 0.15;
            spawn(world, effect, x + Math.cos(angle) * radius, y + 0.25, z + Math.sin(angle) * radius);
        }
    }

    private void clock(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        double[] speeds = {0.28, 0.04};
        double[] lengths = {0.85, 0.55};
        for (int hand = 0; hand < 2; hand++) {
            double angle = tick * speeds[hand];
            for (int i = 1; i <= 2; i++) {
                double radius = (i / 2.0) * lengths[hand];
                spawn(world, effect, x + Math.cos(angle) * radius, y + 2.3, z + Math.sin(angle) * radius);
            }
        }
    }

    private void dna(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        for (int i = 0; i < 3; i++) {
            double height = ((tick * 0.045) + i / 3.0) % 1.0;
            double angle = height * Math.PI * 4.0 + tick * 0.06;
            double px = Math.cos(angle) * 0.55;
            double pz = Math.sin(angle) * 0.55;
            double py = y + 0.1 + height * 2.2;
            spawn(world, effect, x + px, py, z + pz);
            spawn(world, effect, x - px, py, z - pz);
        }
    }

    // -------------------------------------------------------------- на теле

    /** Плащ за спиной: полотно, которое колышется. */
    private void cape(ServerWorld world, ServerPlayerEntity player, ParticleEffect effect,
                      double x, double y, double z) {
        float yaw = (float) Math.toRadians(player.getYaw());
        double backX = Math.sin(yaw);
        double backZ = -Math.cos(yaw);
        double rightX = Math.cos(yaw);
        double rightZ = Math.sin(yaw);

        for (int row = 0; row < 3; row++) {
            double height = 1.55 - row * 0.45;
            double sway = Math.sin(tick * 0.18 - row * 0.6) * 0.14 + row * 0.06;
            for (int col = -1; col <= 1; col += 2) {
                double offset = col * (0.28 + row * 0.05);
                spawn(world, effect,
                        x + backX * (0.22 + sway) + rightX * offset,
                        y + height,
                        z + backZ * (0.22 + sway) + rightZ * offset);
            }
        }
    }

    /** Рога: две дуги, растущие с головы. */
    private void horns(ServerWorld world, ServerPlayerEntity player, ParticleEffect effect,
                       double x, double y, double z) {
        float yaw = (float) Math.toRadians(player.getYaw());
        double rightX = Math.cos(yaw);
        double rightZ = Math.sin(yaw);
        double backX = Math.sin(yaw);
        double backZ = -Math.cos(yaw);

        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 3; i++) {
                double t = i / 2.0;
                double spread = (0.16 + t * 0.20) * side;
                spawn(world, effect,
                        x + rightX * spread + backX * t * 0.18,
                        y + 1.85 + t * 0.42,
                        z + rightZ * spread + backZ * t * 0.18);
            }
        }
    }

    /** Кошачьи ушки: два треугольника на макушке. */
    private void ears(ServerWorld world, ServerPlayerEntity player, ParticleEffect effect,
                      double x, double y, double z) {
        float yaw = (float) Math.toRadians(player.getYaw());
        double rightX = Math.cos(yaw);
        double rightZ = Math.sin(yaw);
        double twitch = Math.sin(tick * 0.12) * 0.04;

        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 3; i++) {
                double t = i / 2.0;
                double spread = (0.24 - t * 0.09) * side;
                spawn(world, effect,
                        x + rightX * spread,
                        y + 1.9 + t * 0.3 + twitch,
                        z + rightZ * spread);
            }
        }
    }

    /** Сердцебиение: двойной толчок кольцом на уровне груди. */
    private void heartbeat(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        int phase = tick % 30;
        double scale;
        if (phase < 4) {
            scale = 0.35 + phase * 0.10;
        } else if (phase < 8) {
            scale = 0.75 - (phase - 4) * 0.08;
        } else if (phase < 12) {
            scale = 0.43 + (phase - 8) * 0.13;
        } else if (phase < 16) {
            scale = 0.95 - (phase - 12) * 0.15;
        } else {
            return;
        }
        for (int i = 0; i < 4; i++) {
            double angle = (i / 4.0) * Math.PI * 2.0 + tick * 0.05;
            spawn(world, effect, x + Math.cos(angle) * scale, y + 1.25, z + Math.sin(angle) * scale);
        }
    }

    /** Руны: четыре знака, медленно плавающие вокруг головы. */
    private void runes(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        for (int i = 0; i < 4; i++) {
            double angle = (i / 4.0) * Math.PI * 2.0 + tick * 0.035;
            double bob = Math.sin(tick * 0.09 + i * 1.6) * 0.14;
            spawn(world, effect, x + Math.cos(angle) * 0.72, y + 1.75 + bob, z + Math.sin(angle) * 0.72);
        }
    }

    /** Пузырьки, медленно всплывающие вдоль тела. */
    private void bubbles(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        for (int i = 0; i < 2; i++) {
            double height = ((tick * 0.035) + i * 0.5) % 1.0;
            double angle = i * 3.1 + height * 2.0;
            double radius = 0.35 + Math.sin(height * 3.0) * 0.15;
            spawn(world, effect, x + Math.cos(angle) * radius, y + height * 2.1, z + Math.sin(angle) * radius);
        }
    }

    /** Раз в пару секунд — короткий разлёт во все стороны. */
    private void burst(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        if (tick % 40 != 0) return;
        for (int i = 0; i < 6; i++) {
            double angle = (i / 6.0) * Math.PI * 2.0;
            spawnMoving(world, effect, x, y + 1.0, z,
                    Math.cos(angle), 0.25, Math.sin(angle), 0.45);
        }
    }

    /** Плоская волна по земле, разлетается наружу и гаснет. */
    private void shockwave(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        int phase = tick % 24;
        if (phase > 8) return;
        double radius = 0.3 + phase * 0.32;
        for (int i = 0; i < 5; i++) {
            double angle = (i / 5.0) * Math.PI * 2.0 + phase * 0.2;
            spawnMoving(world, effect,
                    x + Math.cos(angle) * radius, y + 0.08, z + Math.sin(angle) * radius,
                    Math.cos(angle), 0.02, Math.sin(angle), 0.12);
        }
    }

    /** Снегопад: медленное падение с боковым сносом. */
    private void snow(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        for (int i = 0; i < 2; i++) {
            double angle = Math.random() * Math.PI * 2.0;
            double radius = Math.random() * 1.4;
            spawnMoving(world, effect,
                    x + Math.cos(angle) * radius, y + 2.4, z + Math.sin(angle) * radius,
                    Math.cos(tick * 0.05) * 0.3, -0.05, Math.sin(tick * 0.05) * 0.3, 0.02);
        }
    }

    /** Мошкара: дёрганые точки со случайным разлётом. */
    private void flies(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        for (int i = 0; i < 2; i++) {
            double a = tick * (0.31 + i * 0.17) + i * 3.3;
            spawnMoving(world, effect,
                    x + Math.cos(a) * 0.6,
                    y + 1.1 + Math.sin(a * 2.3) * 0.5,
                    z + Math.sin(a * 1.4) * 0.6,
                    (Math.random() - 0.5), (Math.random() - 0.5), (Math.random() - 0.5), 0.06);
        }
    }

    /** Блуждающий огонёк: две точки плавно бродят вокруг. */
    private void wisp(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        for (int i = 0; i < 2; i++) {
            double a = tick * 0.055 + i * Math.PI;
            double b = tick * 0.031 + i * 1.9;
            spawn(world, effect,
                    x + Math.sin(a) * 1.1 + Math.cos(b) * 0.35,
                    y + 1.2 + Math.sin(b * 1.3) * 0.55,
                    z + Math.cos(a) * 1.1 + Math.sin(b) * 0.35);
        }
    }

    /** Быстрая тугая спираль у ног. */
    private void spin(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        for (int i = 0; i < 3; i++) {
            double angle = tick * 0.75 + i * 2.09;
            double radius = 0.3 + Math.sin(tick * 0.1) * 0.12;
            spawnMoving(world, effect,
                    x + Math.cos(angle) * radius, y + 0.08, z + Math.sin(angle) * radius,
                    -Math.sin(angle) * 0.5, 0.05, Math.cos(angle) * 0.5, 0.08);
        }
    }

    /** Гейзер: редкие мощные выбросы вверх. */
    private void geyser(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        if (tick % 20 > 4) return;
        spawnMoving(world, effect, x, y + 0.1, z, 0.0, 1.0, 0.0, 0.9);
        spawnMoving(world, effect, x + (Math.random() - 0.5) * 0.3, y + 0.1,
                z + (Math.random() - 0.5) * 0.3, 0.0, 0.8, 0.0, 0.7);
    }

    /** Искры, которые вспыхивают в случайных местах вплотную к телу. */
    private void sparkle(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        for (int i = 0; i < 2; i++) {
            spawn(world, effect,
                    x + (Math.random() - 0.5) * 0.9,
                    y + Math.random() * 2.0,
                    z + (Math.random() - 0.5) * 0.9);
        }
    }

    private void rain(ServerWorld world, ParticleEffect effect, double x, double y, double z) {
        for (int i = 0; i < 2; i++) {
            double angle = Math.random() * Math.PI * 2.0;
            double radius = Math.random() * 1.1;
            spawnMoving(world, effect,
                    x + Math.cos(angle) * radius, y + 2.6, z + Math.sin(angle) * radius,
                    0.0, -0.12, 0.0, 0.06);
        }
    }
}
