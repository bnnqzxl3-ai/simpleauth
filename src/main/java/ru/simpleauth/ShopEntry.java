package ru.simpleauth;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.util.Locale;

/**
 * Одна позиция каталога /cursedshop. Не просто Item — потому что
 * зачарованные книги все имеют один и тот же Item (ENCHANTED_BOOK), а
 * различаются только данными на стеке (какое зачарование записано). Так
 * что каталог хранит готовые ItemStack-шаблоны, а не голые Item.
 */
public class ShopEntry {

    public final ItemStack template;
    public final String enPath;

    public ShopEntry(ItemStack template, String enPath) {
        this.template = template;
        this.enPath = enPath;
    }

    /** Обычный предмет — берёт русское название из словаря, если есть, и ставит его отображаемым. */
    public static ShopEntry of(Item item) {
        String path = Registries.ITEM.getId(item).getPath();
        ItemStack stack = new ItemStack(item);
        String ru = ItemNames.of(path);
        if (ru != null) {
            stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
                    net.minecraft.text.Text.literal(ru)
                            .styled(s -> s.withItalic(false).withColor(net.minecraft.util.Formatting.WHITE)));
        }
        return new ShopEntry(stack, path);
    }

    /** Ищет по русскому/английскому названию либо по английскому id — без учёта регистра. */
    public boolean matches(String query) {
        String q = query.toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty()) return true;
        if (enPath.contains(q)) return true;
        String display = template.getName().getString().toLowerCase(Locale.ROOT);
        return display.contains(q);
    }
}
