package ru.simpleauth;

import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Готовые переливающиеся эффекты для /prefix effect. Каждый — просто
 * список цветов, между которыми префикс циклически переключается (тот же
 * приём, что и в DisplayEffectTask для ника владельца, только теперь
 * доступно несколько наборов и любому, у кого есть префикс).
 */
public class PrefixEffects {

    public record Effect(String id, String label, Formatting[] cycle) {
    }

    public static final List<Effect> ALL = List.of(
            new Effect("purple", "Фиолетовый перелив", new Formatting[]{
                    Formatting.DARK_PURPLE, Formatting.LIGHT_PURPLE,
                    Formatting.DARK_PURPLE, Formatting.LIGHT_PURPLE, Formatting.WHITE}),
            new Effect("gold", "Золотой перелив", new Formatting[]{
                    Formatting.GOLD, Formatting.YELLOW,
                    Formatting.GOLD, Formatting.YELLOW, Formatting.WHITE}),
            new Effect("rainbow", "Радуга", new Formatting[]{
                    Formatting.RED, Formatting.GOLD, Formatting.YELLOW, Formatting.GREEN,
                    Formatting.AQUA, Formatting.BLUE, Formatting.LIGHT_PURPLE}),
            new Effect("fire", "Огонь", new Formatting[]{
                    Formatting.DARK_RED, Formatting.RED, Formatting.GOLD, Formatting.YELLOW}),
            new Effect("ice", "Лёд", new Formatting[]{
                    Formatting.AQUA, Formatting.BLUE, Formatting.WHITE, Formatting.DARK_AQUA}),
            new Effect("toxic", "Токсичный", new Formatting[]{
                    Formatting.DARK_GREEN, Formatting.GREEN, Formatting.DARK_GREEN}),
            new Effect("blood", "Кровавый", new Formatting[]{
                    Formatting.DARK_RED, Formatting.RED, Formatting.BLACK, Formatting.RED})
    );

    private PrefixEffects() {
    }

    public static Effect find(String id) {
        if (id == null) return null;
        for (Effect e : ALL) {
            if (e.id().equalsIgnoreCase(id)) return e;
        }
        return null;
    }
}
