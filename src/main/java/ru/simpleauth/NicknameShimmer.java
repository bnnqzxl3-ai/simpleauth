package ru.simpleauth;

import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Formatting;

import java.util.Locale;

/**
 * Живое золотое "переливание" ника одного игрока — работает через команду
 * скорборда (как и обычные префиксы в PrefixManager), поэтому цвет меняется
 * сразу в трёх местах: над головой, в tab-списке и в чате.
 *
 * Честное ограничение: ванильная команда скорборда красит именем одним из
 * 16 стандартных цветов чата, произвольный плавный RGB-градиент недоступен
 * без риска сломать проверку подписанных сообщений в чате. Поэтому эффект —
 * частая смена между двумя золотыми оттенками (GOLD/YELLOW) с редкой
 * вспышкой WHITE, что на экране выглядит как мерцание/блеск.
 *
 * В чате конкретное сообщение красится тем цветом, что был активен в момент
 * отправки, — то есть каждое новое сообщение может "поймать" другую фазу
 * мерцания, а над головой и в tab-листе цвет меняется в реальном времени.
 */
public class NicknameShimmer {

    private final AuthManager manager;
    private int tickCounter = 0;
    private int phase = 0;

    // золото -> жёлтый -> золото -> жёлтый -> белая вспышка -> повтор
    private static final Formatting[] CYCLE = {
            Formatting.GOLD, Formatting.YELLOW,
            Formatting.GOLD, Formatting.YELLOW,
            Formatting.WHITE
    };

    public NicknameShimmer(AuthManager manager) {
        this.manager = manager;
    }

    private Config config() {
        return manager.config();
    }

    private static String teamName(String player) {
        String slug = player.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
        if (slug.length() > 12) slug = slug.substring(0, 12);
        return "sa_shim_" + slug;
    }

    public void tick(MinecraftServer server) {
        Config cfg = config();
        if (!cfg.shimmerNickEnabled) return;
        String playerName = cfg.shimmerNickPlayer;
        if (playerName == null || playerName.isBlank()) return;

        tickCounter++;
        int interval = Math.max(1, cfg.shimmerIntervalTicks);
        if (tickCounter < interval) return;
        tickCounter = 0;

        phase = (phase + 1) % CYCLE.length;
        apply(server, playerName, CYCLE[phase]);
    }

    private void apply(MinecraftServer server, String playerName, Formatting color) {
        try {
            Scoreboard scoreboard = server.getScoreboard();
            String name = teamName(playerName);
            Team team = scoreboard.getTeam(name);
            if (team == null) {
                team = scoreboard.addTeam(name);
                scoreboard.addScoreHolderToTeam(playerName, team);
            }
            team.setColor(color);
        } catch (Exception e) {
            // мерцание не критично для работы сервера — молча пропускаем тик
        }
    }
}
