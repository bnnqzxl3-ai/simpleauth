package ru.simpleauth;

import net.minecraft.server.MinecraftServer;

import java.util.List;

/**
 * Автоматически переключает музыку в зоне на следующий трек по списку через
 * заданный интервал минут. Использует тот же ZoneMusic.switchTrack(...), что
 * и ручное меню, — переключение всегда мгновенное для всех, кто сейчас
 * слушает музыку в зоне, а не только со следующим циклом трека.
 *
 * Порядок циклический: дошли до конца списка — начинают сначала. Текущий
 * индекс сохраняется в конфиге, чтобы не сбрасывался при рестарте сервера.
 */
public class AutoTrackSwitcher {

    private final AuthManager manager;
    private int tickCounter = 0;

    public AutoTrackSwitcher(AuthManager manager) {
        this.manager = manager;
    }

    private Config config() {
        return manager.config();
    }

    public void tick(MinecraftServer server) {
        Config cfg = config();
        if (!cfg.autoTrackSwitchEnabled) return;

        List<Config.TrackDef> tracks = cfg.tracks;
        if (tracks == null || tracks.isEmpty()) return;

        tickCounter++;
        int interval = Math.max(200, cfg.autoTrackSwitchIntervalMinutes * 60 * 20);
        if (tickCounter < interval) return;
        tickCounter = 0;

        int nextIndex = (cfg.autoTrackSwitchIndex + 1) % tracks.size();
        cfg.autoTrackSwitchIndex = nextIndex;

        Config.TrackDef next = tracks.get(nextIndex);
        manager.zoneMusic().switchTrack(server, next);
        manager.actionLogger().log("AUTO_TRACK_SWITCH -> " + next.name);
    }
}
