package ru.simpleauth;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.entity.EntityType;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityAnimationS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySetHeadYawS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * NPC-компаньон: тело живёт на пакетах (обычный игрок для клиента), голова —
 * в {@link AiBrain}. Ответы модели уходят только в чат и никогда не попадают
 * в диспетчер команд.
 */
public class AiCompanion {

    private final SessionManager manager;
    private final AiBrain brain;

    private FakePlayer npc;
    private ServerWorld world;
    private UUID followTarget;

    private final Set<UUID> shownTo = new HashSet<>();
    private final Map<UUID, Long> lastAsk = new HashMap<>();

    private float yaw;
    private float pitch;
    private int idleTicks;

    public AiCompanion(SessionManager manager) {
        this.manager = manager;
        this.brain = new AiBrain(manager);
    }

    public AiBrain brain() {
        return brain;
    }

    public boolean isSpawned() {
        return npc != null;
    }

    public String name() {
        String configured = manager.config().aiName;
        return configured == null || configured.isBlank() ? "Claude" : configured.trim();
    }

    private GameProfile profile() {
        ServerSettings cfg = manager.config();
        String name = name();
        UUID id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        GameProfile profile = new GameProfile(id, name);
        if (cfg.aiSkinValue != null && !cfg.aiSkinValue.isBlank()) {
            String signature = cfg.aiSkinSignature == null || cfg.aiSkinSignature.isBlank()
                    ? null : cfg.aiSkinSignature;
            profile.getProperties().put("textures",
                    new Property("textures", cfg.aiSkinValue, signature));
        }
        return profile;
    }

    public void spawn(ServerWorld target, double x, double y, double z, float facing) {
        MinecraftServer server = target.getServer();
        despawn(server);

        this.world = target;
        this.npc = FakePlayer.get(target, profile());
        this.yaw = facing;
        this.pitch = 0.0F;
        npc.refreshPositionAndAngles(x, y, z, facing, 0.0F);
        npc.setHeadYaw(facing);
        shownTo.clear();

        ServerSettings cfg = manager.config();
        cfg.aiSpawned = true;
        cfg.aiSpawnWorld = target.getRegistryKey().getValue().toString();
        cfg.aiSpawnX = x;
        cfg.aiSpawnY = y;
        cfg.aiSpawnZ = z;
        cfg.aiSpawnYaw = facing;
        cfg.save();
    }

    public void despawn(MinecraftServer server) {
        if (npc != null && server != null) {
            broadcast(server, new EntitiesDestroyS2CPacket(npc.getId()));
            broadcast(server, new PlayerRemoveS2CPacket(List.of(npc.getUuid())));
        }
        npc = null;
        world = null;
        followTarget = null;
        shownTo.clear();

        ServerSettings cfg = manager.config();
        if (cfg.aiSpawned) {
            cfg.aiSpawned = false;
            cfg.save();
        }
    }

    /** Восстановить NPC после рестарта сервера, если он был заспавнен. */
    public void restore(MinecraftServer server) {
        ServerSettings cfg = manager.config();
        if (!cfg.aiEnabled || !cfg.aiSpawned || npc != null) return;
        ServerWorld target = SessionManager.worldByName(server, cfg.aiSpawnWorld);
        if (target == null) return;
        spawn(target, cfg.aiSpawnX, cfg.aiSpawnY, cfg.aiSpawnZ, cfg.aiSpawnYaw);
    }

    public void follow(ServerPlayerEntity player) {
        followTarget = player == null ? null : player.getUuid();
    }

    public void stay() {
        followTarget = null;
    }

    public void teleportTo(ServerPlayerEntity player) {
        if (npc == null) return;
        Vec3d dir = player.getRotationVec(1.0F);
        spawn(player.getServerWorld(),
                player.getX() + dir.x * 2.0,
                player.getY(),
                player.getZ() + dir.z * 2.0,
                player.getYaw() + 180.0F);
    }

    public void tick(MinecraftServer server) {
        if (npc == null || world == null) return;

        List<ServerPlayerEntity> online = server.getPlayerManager().getPlayerList();
        Set<UUID> present = new HashSet<>();
        for (ServerPlayerEntity player : online) {
            present.add(player.getUuid());
            if (shownTo.add(player.getUuid())) showTo(player);
        }
        shownTo.retainAll(present);

        ServerPlayerEntity target = nearest(online);
        boolean moved = false;

        if (target != null) {
            ServerSettings cfg = manager.config();
            if (followTarget != null && target.getUuid().equals(followTarget)) {
                double dx = target.getX() - npc.getX();
                double dz = target.getZ() - npc.getZ();
                double flat = Math.sqrt(dx * dx + dz * dz);
                if (flat > cfg.aiFollowDistance) {
                    double step = Math.min(cfg.aiFollowSpeed, flat - cfg.aiFollowDistance);
                    double ny = MathHelper.lerp(0.25D, npc.getY(), target.getY());
                    npc.refreshPositionAndAngles(
                            npc.getX() + dx / flat * step, ny, npc.getZ() + dz / flat * step,
                            yaw, pitch);
                    moved = true;
                }
            }
            lookAt(target);
        }

        if (moved || ++idleTicks >= 20) {
            idleTicks = 0;
            npc.setHeadYaw(yaw);
            broadcast(server, new EntityPositionS2CPacket(npc));
            broadcast(server, new EntitySetHeadYawS2CPacket(npc, angleByte(yaw)));
        }
    }

    private ServerPlayerEntity nearest(List<ServerPlayerEntity> online) {
        ServerPlayerEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        double radius = manager.config().aiHearRadius;
        for (ServerPlayerEntity player : online) {
            if (player.getServerWorld() != world) continue;
            double distance = player.squaredDistanceTo(npc.getX(), npc.getY(), npc.getZ());
            if (distance < bestDistance && distance <= radius * radius) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    private void lookAt(ServerPlayerEntity target) {
        double dx = target.getX() - npc.getX();
        double dy = target.getEyeY() - npc.getEyeY();
        double dz = target.getZ() - npc.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        yaw = (float) (MathHelper.atan2(dz, dx) * 57.2957795D) - 90.0F;
        pitch = (float) (-(MathHelper.atan2(dy, flat) * 57.2957795D));
        npc.setYaw(yaw);
        npc.setPitch(pitch);
    }

    private void showTo(ServerPlayerEntity player) {
        player.networkHandler.sendPacket(new PlayerListS2CPacket(
                EnumSet.of(PlayerListS2CPacket.Action.ADD_PLAYER,
                        PlayerListS2CPacket.Action.UPDATE_LISTED),
                List.of(npc)));
        player.networkHandler.sendPacket(new EntitySpawnS2CPacket(
                npc.getId(), npc.getUuid(),
                npc.getX(), npc.getY(), npc.getZ(),
                npc.getPitch(), npc.getYaw(),
                EntityType.PLAYER, 0, Vec3d.ZERO, npc.getHeadYaw()));
        player.networkHandler.sendPacket(new EntitySetHeadYawS2CPacket(npc, angleByte(yaw)));
    }

    private void broadcast(MinecraftServer server, Packet<?> packet) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.networkHandler.sendPacket(packet);
        }
    }

    private static byte angleByte(float degrees) {
        return (byte) MathHelper.floor(degrees * 256.0F / 360.0F);
    }

    /** Игрок написал в чат — решаем, отвечать ли. */
    public void onChat(ServerPlayerEntity sender, String message) {
        ServerSettings cfg = manager.config();
        if (!cfg.aiEnabled || npc == null || message == null || message.isBlank()) return;
        if (!manager.isAuthenticated(sender)) return;

        boolean named = message.toLowerCase(Locale.ROOT).contains(name().toLowerCase(Locale.ROOT));
        if (cfg.aiOnlyWhenNamed && !named) return;
        if (!named) {
            if (sender.getServerWorld() != world) return;
            double distance = sender.squaredDistanceTo(npc.getX(), npc.getY(), npc.getZ());
            if (distance > cfg.aiHearRadius * cfg.aiHearRadius) return;
        }

        long now = System.currentTimeMillis();
        Long last = lastAsk.get(sender.getUuid());
        if (last != null && now - last < cfg.aiCooldownSeconds * 1000L) return;
        lastAsk.put(sender.getUuid(), now);

        MinecraftServer server = sender.getServer();
        brain.ask(sender.getUuid(), sender.getGameProfile().getName(), message,
                answer -> say(server, answer));
    }

    public void say(MinecraftServer server, String raw) {
        String text = sanitize(raw);
        if (text.isEmpty() || server == null) return;

        Text line = Text.literal("<").formatted(Formatting.DARK_GRAY)
                .append(Text.literal(name()).formatted(Formatting.AQUA))
                .append(Text.literal("> ").formatted(Formatting.DARK_GRAY))
                .append(Text.literal(text).formatted(Formatting.WHITE));
        server.getPlayerManager().broadcast(line, false);

        if (npc != null) broadcast(server, new EntityAnimationS2CPacket(npc, 0));
    }

    /**
     * Ведущий слэш убираем: реплика модели уходит в чат как текст, но пусть она
     * и выглядеть не может как команда.
     */
    private static String sanitize(String raw) {
        if (raw == null) return "";
        String text = raw.replace('§', ' ').replaceAll("\\s+", " ").trim();
        while (text.startsWith("/")) text = text.substring(1).trim();
        if (text.length() > 256) text = text.substring(0, 256).trim() + "...";
        return text;
    }
}
