package ru.simpleauth;

import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Проигрывает кастомную музыку игрокам, находящимся внутри заданной зоны.
 *
 * Звук берётся по имени из ресурспака (например simpleauth:hardbass), который
 * игроки получают при заходе на сервер (server resource pack). Сам мод только
 * триггерит проигрывание — аудиофайл должен лежать у клиента в паке.
 *
 * Логика: раз в несколько тиков проверяем всех игроков. Кто вошёл в зону —
 * запускаем трек; кто в зоне давно — перезапускаем по истечении длины трека
 * (зацикливание, так как playSound одноразовый); кто вышел — глушим звук.
 */
public class ZoneMusic {

    private final AuthManager manager;

    /** Для каждого игрока в зоне — тик, когда трек был (пере)запущен. */
    private final Map<UUID, Long> playing = new HashMap<>();

    private long tickCounter = 0;

    public ZoneMusic(AuthManager manager) {
        this.manager = manager;
    }

    private Config config() {
        return manager.config();
    }

    public void tick(MinecraftServer server) {
        Config cfg = config();
        if (!cfg.zoneMusicEnabled) {
            if (!playing.isEmpty()) {
                stopAll(server);
            }
            return;
        }

        tickCounter++;
        // проверяем не каждый тик — раз в 10 (2 раза в секунду) достаточно
        if (tickCounter % 10 != 0) return;

        int minX = Math.min(cfg.zoneMusicX1, cfg.zoneMusicX2);
        int maxX = Math.max(cfg.zoneMusicX1, cfg.zoneMusicX2);
        int minY = Math.min(cfg.zoneMusicY1, cfg.zoneMusicY2);
        int maxY = Math.max(cfg.zoneMusicY1, cfg.zoneMusicY2);
        int minZ = Math.min(cfg.zoneMusicZ1, cfg.zoneMusicZ2);
        int maxZ = Math.max(cfg.zoneMusicZ1, cfg.zoneMusicZ2);

        long loopTicks = Math.max(20L, cfg.zoneMusicLengthSeconds * 20L);
        Identifier id = Identifier.tryParse(cfg.zoneMusicSound);
        if (id == null) return;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            boolean inside =
                    player.getX() >= minX && player.getX() <= maxX + 1 &&
                    player.getY() >= minY && player.getY() <= maxY + 1 &&
                    player.getZ() >= minZ && player.getZ() <= maxZ + 1;

            UUID uuid = player.getUuid();
            Long since = playing.get(uuid);

            if (inside) {
                boolean needStart = (since == null)
                        || (tickCounter - since >= loopTicks);
                if (needStart) {
                    play(player, id, cfg.zoneMusicVolume);
                    playing.put(uuid, tickCounter);
                }
            } else if (since != null) {
                stop(player, id);
                playing.remove(uuid);
            }
        }
    }

    private void play(ServerPlayerEntity player, Identifier id, float volume) {
        // SoundEvent по кастомному имени из ресурспака.
        SoundEvent event = SoundEvent.of(id);
        player.playSoundToPlayer(event, SoundCategory.RECORDS, volume, 1.0F);
    }

    private void stop(ServerPlayerEntity player, Identifier id) {
        // Останавливаем именно наш трек в категории RECORDS.
        player.networkHandler.sendPacket(
                new StopSoundS2CPacket(id, SoundCategory.RECORDS));
    }

    private void stopAll(MinecraftServer server) {
        Identifier id = Identifier.tryParse(config().zoneMusicSound);
        for (UUID uuid : playing.keySet()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null && id != null) {
                stop(player, id);
            }
        }
        playing.clear();
    }

    /** Игрок вышел из игры — забываем его состояние. */
    public void onDisconnect(UUID uuid) {
        playing.remove(uuid);
    }
}
