package ru.simpleauth;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolItem;
import net.minecraft.item.TridentItem;

import java.util.function.Predicate;

/**
 * Категории каталога /cursedshop. Порядок важен — предмет попадает в
 * первую категорию, под которую подходит (проверяются по порядку сверху
 * вниз). БЛОКИ и РАЗНОЕ — catch-all в самом конце, иначе бы забрали себе
 * всё подряд раньше более узких категорий.
 *
 * Категория КНИГИ обрабатывается особо — помимо самих книг из этого
 * фильтра, в неё дополнительно подмешиваются все зачарованные книги на
 * все уровни (см. EnchantedBooksCatalog), это нельзя сделать через
 * простой Predicate<Item>, поскольку зачарованные книги — это один и тот
 * же Item с разными данными на стеке.
 */
public enum ShopCategory {

    BOOKS("📚 Книги и зачарования", Items.ENCHANTED_BOOK, item ->
            item == Items.BOOK || item == Items.WRITABLE_BOOK || item == Items.WRITTEN_BOOK
                    || item == Items.KNOWLEDGE_BOOK),

    POTIONS("🧪 Зелья", Items.POTION, item ->
            item == Items.POTION || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION
                    || item == Items.EXPERIENCE_BOTTLE),

    FOOD("🍖 Еда", Items.COOKED_BEEF, item ->
            new ItemStack(item).getComponents().contains(DataComponentTypes.FOOD)),

    ARMOR("🛡 Броня", Items.DIAMOND_CHESTPLATE, item -> item instanceof ArmorItem),

    WEAPONS_TOOLS("⚔ Оружие и инструменты", Items.DIAMOND_SWORD, item ->
            item instanceof ToolItem || item instanceof SwordItem || item instanceof RangedWeaponItem
                    || item instanceof TridentItem || item == Items.SHIELD || item == Items.FISHING_ROD
                    || item == Items.SHEARS || item == Items.FLINT_AND_STEEL),

    SPAWN_EGGS("🥚 Яйца призыва", Items.PIG_SPAWN_EGG, item -> item instanceof SpawnEggItem),

    BLOCKS("🧱 Блоки", Items.BRICKS, item -> item instanceof BlockItem),

    MISC("📦 Разное", Items.CHEST, item -> true); // catch-all, обязательно последним

    public final String label;
    public final Item icon;
    public final Predicate<Item> matcher;

    ShopCategory(String label, Item icon, Predicate<Item> matcher) {
        this.label = label;
        this.icon = icon;
        this.matcher = matcher;
    }

    /** Первая подходящая категория для предмета, по порядку объявления. */
    public static ShopCategory of(Item item) {
        for (ShopCategory category : values()) {
            if (category.matcher.test(item)) return category;
        }
        return MISC;
    }
}
