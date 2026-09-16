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
 *
 * Помимо статичного цвета, у префикса может быть переливающийся эффект
 * (см. PrefixEffects) — тогда цвет циклически меняется в реальном времени
 * через tickEffects(), вызываемый из общего тика SessionManager.
 */
public class TagManager {

    private final SessionManager manager;
    private int effectTickCounter = 0;
    private int effectPhase = 0;

    public TagManager(SessionManager manager) {
        this.manager = manager;
    }

    private ServerSettings config() {
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
        ServerSettings.PrefixEntry entry = config().prefixes.get(playerName.toLowerCase(Locale.ROOT));
        if (entry == null) {
            entry = new ServerSettings.PrefixEntry();
            entry.name = playerName;
        }
        entry.symbol = symbol;
        config().prefixes.put(playerName.toLowerCase(Locale.ROOT), entry);
        config().save();
        apply(server, playerName);
    }

    public boolean setColor(MinecraftServer server, String playerName, String color) {
        if (Formatting.byName(color) == null) return false;
        ServerSettings.PrefixEntry entry = config().prefixes.get(playerName.toLowerCase(Locale.ROOT));
        if (entry == null) {
            entry = new ServerSettings.PrefixEntry();
            entry.name = playerName;
        }
        entry.color = color.toUpperCase(Locale.ROOT);
        entry.effect = null; // статичный цвет отменяет переливание
        config().prefixes.put(playerName.toLowerCase(Locale.ROOT), entry);
        config().save();
        apply(server, playerName);
        return true;
    }

    /** Включает переливающийся эффект. Возвращает false, если такого эффекта нет или у игрока нет префикса. */
    public boolean setEffect(MinecraftServer server, String playerName, String effectId) {
        ServerSettings.PrefixEntry entry = config().prefixes.get(playerName.toLowerCase(Locale.ROOT));
        if (entry == null || entry.symbol == null || entry.symbol.isEmpty()) return false;
        if (PrefixEffects.find(effectId) == null) return false;

        entry.effect = effectId.toLowerCase(Locale.ROOT);
        config().prefixes.put(playerName.toLowerCase(Locale.ROOT), entry);
        config().save();
        return true;
    }

    /** Отключает переливание, возвращает префикс к статичному цвету. */
    public void clearEffect(MinecraftServer server, String playerName) {
        ServerSettings.PrefixEntry entry = config().prefixes.get(playerName.toLowerCase(Locale.ROOT));
        if (entry == null) return;
        entry.effect = null;
        config().save();
        apply(server, playerName);
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
        ServerSettings.PrefixEntry entry = config().prefixes.get(playerName.toLowerCase(Locale.ROOT));
        if (entry == null || entry.symbol == null || entry.symbol.isEmpty()) return;

        try {
            Scoreboard scoreboard = server.getScoreboard();
            String name = teamName(playerName);
            Team team = scoreboard.getTeam(name);
            if (team == null) {
                team = scoreboard.addTeam(name);
            }

            // если включён переливающийся эффект — цветом занимается
            // tickEffects(), тут ставим только сам символ
            Formatting color = entry.effect == null ? Formatting.byName(entry.color) : null;
            Text prefix = color != null
                    ? Text.literal(entry.symbol + " ").formatted(color)
                    : Text.literal(entry.symbol + " ");
            team.setPrefix(prefix);

            scoreboard.addScoreHolderToTeam(playerName, team);
        } catch (Exception e) {
            ServerCore.LOGGER.warn("[ServerCore] Не удалось применить префикс для {}", playerName, e);
        }
    }

    /** Применяет префиксы всем, кто есть в конфиге. */
    public void applyAll(MinecraftServer server) {
        if (config().prefixes == null) return;
        for (Map.Entry<String, ServerSettings.PrefixEntry> e : config().prefixes.entrySet()) {
            String name = e.getValue().name != null ? e.getValue().name : e.getKey();
            apply(server, name);
        }
    }

    /** Прокручивает цвет на шаг у всех, кто включил переливающийся эффект. Вызывается из общего тика. */
    public void tickEffects(MinecraftServer server) {
        if (config().prefixes == null || config().prefixes.isEmpty()) return;

        effectTickCounter++;
        int interval = Math.max(1, config().shimmerIntervalTicks);
        if (effectTickCounter < interval) return;
        effectTickCounter = 0;
        effectPhase++;

        for (Map.Entry<String, ServerSettings.PrefixEntry> e : config().prefixes.entrySet()) {
            ServerSettings.PrefixEntry entry = e.getValue();
            if (entry.effect == null || entry.symbol == null || entry.symbol.isEmpty()) continue;

            PrefixEffects.Effect effect = PrefixEffects.find(entry.effect);
            if (effect == null) continue;

            String playerName = entry.name != null ? entry.name : e.getKey();
            Formatting color = effect.cycle()[effectPhase % effect.cycle().length];
            try {
                Scoreboard scoreboard = server.getScoreboard();
                Team team = scoreboard.getTeam(teamName(playerName));
                if (team == null) continue; // ещё не применён обычный apply() — подождём следующего тика
                team.setPrefix(Text.literal(entry.symbol + " ").formatted(color));
            } catch (Exception ex) {
                // переливание не критично — молча пропускаем игрока в этом тике
            }
        }
    }

    public ServerSettings.PrefixEntry get(String playerName) {
        return config().prefixes.get(playerName.toLowerCase(Locale.ROOT));
    }
}
