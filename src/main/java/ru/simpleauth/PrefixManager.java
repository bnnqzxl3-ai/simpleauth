package ru.simpleauth;

import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Locale;
import java.util.Map;

/**
 * Префиксы игроков. Под капотом — обычные команды скорборда, поэтому символ
 * виден сразу в трёх местах: в чате, в списке игроков и над головой.
 */
public class PrefixManager {

    private final AuthManager manager;

    public PrefixManager(AuthManager manager) {
        this.manager = manager;
    }

    private Config config() {
        return manager.config();
    }

    /** Имя команды скорборда для конкретного игрока. */
    private static String teamName(String player) {
        String slug = player.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
        if (slug.length() > 12) slug = slug.substring(0, 12);
        return "sa_" + slug;
    }

    /** Только владелец сервера может менять префиксы. Консоль тоже может. */
    public boolean isOwner(ServerPlayerEntity player) {
        if (player == null) return true;
        String owner = config().owner;
        return owner != null && owner.equalsIgnoreCase(player.getGameProfile().getName());
    }

    // ------------------------------------------------------------ изменение

    public void set(MinecraftServer server, String playerName, String symbol) {
        Config.PrefixEntry entry = config().prefixes.get(playerName.toLowerCase(Locale.ROOT));
        if (entry == null) {
            entry = new Config.PrefixEntry();
            entry.name = playerName;
        }
        entry.symbol = symbol;
        config().prefixes.put(playerName.toLowerCase(Locale.ROOT), entry);
        config().save();
        apply(server, playerName);
    }

    public boolean setColor(MinecraftServer server, String playerName, String color) {
        if (Formatting.byName(color) == null) return false;
        Config.PrefixEntry entry = config().prefixes.get(playerName.toLowerCase(Locale.ROOT));
        if (entry == null) {
            entry = new Config.PrefixEntry();
            entry.name = playerName;
        }
        entry.color = color.toUpperCase(Locale.ROOT);
        config().prefixes.put(playerName.toLowerCase(Locale.ROOT), entry);
        config().save();
        apply(server, playerName);
        return true;
    }

    public void clear(MinecraftServer server, String playerName) {
        config().prefixes.remove(playerName.toLowerCase(Locale.ROOT));
        config().save();
        // Команду не удаляем — просто убираем префикс, так надёжнее.
        Team team = server.getScoreboard().getTeam(teamName(playerName));
        if (team != null) {
            team.setPrefix(Text.empty());
        }
    }

    // ------------------------------------------------------------ применение

    /** Применяет сохранённый префикс к игроку. Вызывается и при заходе. */
    public void apply(MinecraftServer server, String playerName) {
        Config.PrefixEntry entry = config().prefixes.get(playerName.toLowerCase(Locale.ROOT));
        if (entry == null || entry.symbol == null || entry.symbol.isEmpty()) return;

        try {
            Scoreboard scoreboard = server.getScoreboard();
            String name = teamName(playerName);
            Team team = scoreboard.getTeam(name);
            if (team == null) {
                team = scoreboard.addTeam(name);
            }

            Formatting color = Formatting.byName(entry.color);
            Text prefix = color != null
                    ? Text.literal(entry.symbol + " ").formatted(color)
                    : Text.literal(entry.symbol + " ");
            team.setPrefix(prefix);

            scoreboard.addScoreHolderToTeam(playerName, team);
        } catch (Exception e) {
            SimpleAuth.LOGGER.warn("[SimpleAuth] Не удалось применить префикс для {}", playerName, e);
        }
    }

    /** Применяет префиксы всем, кто есть в конфиге. */
    public void applyAll(MinecraftServer server) {
        if (config().prefixes == null) return;
        for (Map.Entry<String, Config.PrefixEntry> e : config().prefixes.entrySet()) {
            String name = e.getValue().name != null ? e.getValue().name : e.getKey();
            apply(server, name);
        }
    }

    public Config.PrefixEntry get(String playerName) {
        return config().prefixes.get(playerName.toLowerCase(Locale.ROOT));
    }
}
