package ru.simpleauth;

import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Проигрывает кастомную музыку игрокам, находящимся внутри заданной зоны.
 *
 * Звук берётся по имени из ресурспака, который игроки получают при заходе на
 * сервер (server resource pack). Сам мод только триггерит проигрывание —
 * аудиофайл должен лежать у клиента в паке.
 *
 * Логика простая, один таймер на всё: у каждого трека есть длина в секундах
 * (задаётся кнопкой "⏱ Длина трека" в меню). Когда она истекает — либо
 * перезапускается тот же трек (если автопереключение выключено), либо мод
 * сам переходит на следующий трек по списку (если включено). Раньше это были
 * два независимых таймера (длина трека отдельно, автопереключение по
 * минутам — отдельно), из-за чего переключение происходило не в то время,
 * которое реально задавалось для трека. Теперь источник времени один.
 *
 * Пока игрок в зоне, ему в action bar показывается название текущего трека
 * мерцающим жёлто-зелёным цветом (обновляется каждый тик показа, чтобы не
 * пропадало само по себе).
 *
 * Честное ограничение: у Minecraft нет способа изменить громкость уже
 * играющего звука без его перезапуска с начала. Смена громкости
 * (applyVolume) НЕ перезапускает текущий трек — новое значение подхватится
 * само на следующем перезапуске/переключении.
 */
public class AmbientAudio {

    private final SessionManager manager;

    /** Для каждого игрока в зоне — тик, когда он начал слышать текущий трек. */
    private final Map<UUID, Long> playing = new HashMap<>();

    private long tickCounter = 0;
    private long resumeAtTick = 0;

    /** Тик, когда начал играть ТЕКУЩИЙ трек — единственный источник времени для смены/перезапуска. */
    private long currentTrackStartTick = -1;

    // мерцание жёлтый/зелёный для action bar
    private static final Formatting[] SHIMMER = {
            Formatting.YELLOW, Formatting.YELLOW, Formatting.GREEN, Formatting.GREEN
    };

    public AmbientAudio(SessionManager manager) {
        this.manager = manager;
    }

    private ServerSettings config() {
        return manager.config();
    }

    public void tick(MinecraftServer server) {
        ServerSettings cfg = config();
        if (!cfg.zoneMusicEnabled) {
            if (!playing.isEmpty()) {
                stopAll(server);
            }
            currentTrackStartTick = -1;
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

        Identifier id = Identifier.tryParse(cfg.zoneMusicSound);
        if (id == null) return;

        if (currentTrackStartTick < 0) {
            currentTrackStartTick = tickCounter;
        }

        // Единственная проверка времени: истекла длина текущего трека?
        long loopTicks = Math.max(20L, cfg.zoneMusicLengthSeconds * 20L);
        boolean timeUp = (tickCounter - currentTrackStartTick) >= loopTicks
                && tickCounter >= resumeAtTick;

        if (timeUp) {
            Identifier oldId = id;
            if (cfg.autoTrackSwitchEnabled && cfg.tracks != null && !cfg.tracks.isEmpty()) {
                int nextIndex = (cfg.autoTrackSwitchIndex + 1) % cfg.tracks.size();
                cfg.autoTrackSwitchIndex = nextIndex;
                ServerSettings.TrackDef next = cfg.tracks.get(nextIndex);
                cfg.zoneMusicSound = next.soundId;
                cfg.zoneMusicLengthSeconds = Math.max(5, next.lengthSeconds);
                cfg.save();
                manager.actionLogger().log("AUTO_TRACK_SWITCH -> " + next.name);
                Identifier newId = Identifier.tryParse(cfg.zoneMusicSound);
                if (newId != null) id = newId;
            }
            // перезапускаем (тот же трек, либо только что выбранный следующий)
            // для всех, кто сейчас слушает музыку в зоне
            for (UUID uuid : new ArrayList<>(playing.keySet())) {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
                if (p != null) {
                    stop(p, oldId);
                    play(p, id, cfg.zoneMusicVolume);
                    playing.put(uuid, tickCounter);
                }
            }
            currentTrackStartTick = tickCounter;
            resumeAtTick = tickCounter + 4;
        }

        Text trackLabel = buildTrackLabel(cfg);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            boolean inside =
                    player.getX() >= minX && player.getX() <= maxX + 1 &&
                    player.getY() >= minY && player.getY() <= maxY + 1 &&
                    player.getZ() >= minZ && player.getZ() <= maxZ + 1;

            UUID uuid = player.getUuid();
            Long since = playing.get(uuid);

            if (inside) {
                // новый в зоне — стартуем сразу; тех, кто уже слушает, трогать
                // не надо (они уже обработаны выше, если время истекло)
                if (since == null && tickCounter >= resumeAtTick) {
                    play(player, id, cfg.zoneMusicVolume);
                    playing.put(uuid, tickCounter);
                }
                // держим название трека в action bar, пока игрок в зоне
                player.sendMessage(trackLabel, true);
            } else if (since != null) {
                stop(player, id);
                playing.remove(uuid);
            }
        }
    }

    /** Название текущего трека для action bar, с мерцающим жёлто-зелёным цветом. */
    private Text buildTrackLabel(ServerSettings cfg) {
        String name = cfg.zoneMusicSound;
        for (ServerSettings.TrackDef track : cfg.tracks) {
            if (track.soundId != null && track.soundId.equals(cfg.zoneMusicSound)) {
                name = track.name;
                break;
            }
        }
        Formatting color = SHIMMER[(int) (tickCounter / 5 % SHIMMER.length)];
        return Text.literal("♪ " + name).formatted(color, Formatting.BOLD);
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

    /**
     * Мгновенно переключает трек для ВСЕХ, кто сейчас слушает музыку в зоне.
     * Глушит старый трек, ждёт короткую паузу (чтобы не было щелчка от
     * наложения хвоста старого и начала нового), затем запускает новый —
     * это ближайшее безопасное приближение к "плавному" переключению без
     * артефактов перезапуска.
     */
    public void switchTrack(MinecraftServer server, ServerSettings.TrackDef track) {
        ServerSettings cfg = config();
        Identifier oldId = Identifier.tryParse(cfg.zoneMusicSound);

        if (oldId != null) {
            for (UUID uuid : playing.keySet()) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                if (player != null) {
                    stop(player, oldId);
                }
            }
        }
        playing.clear();

        cfg.zoneMusicSound = track.soundId;
        cfg.zoneMusicLengthSeconds = Math.max(5, track.lengthSeconds);
        cfg.save();

        currentTrackStartTick = tickCounter;
        // короткая пауза (4 тика ~ 0.2с) перед стартом нового трека — чтобы
        // не было щелчка от наложения хвоста старого и начала нового
        resumeAtTick = tickCounter + 4;
    }

    /**
     * Применяет новую громкость БЕЗ перезапуска уже играющего трека — по
     * прямой просьбе не дёргать музыку на 0:00 при каждом клике по кнопке
     * громкости. Значение сохраняется в конфиг и подхватится само на
     * следующем естественном перезапуске цикла (когда текущий трек доиграет
     * свою длину) или когда кто-то заново зайдёт в зону. То есть смена не
     * мгновенная — это осознанный компромежок в пользу отсутствия рестарта.
     */
    public void applyVolume(MinecraftServer server, float newVolume) {
        ServerSettings cfg = config();
        cfg.zoneMusicVolume = newVolume;
        cfg.save();
        // намеренно не трогаем playing/не вызываем stop+play — трек играет как играл
    }
}
