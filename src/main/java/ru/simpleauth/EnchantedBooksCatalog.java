package ru.simpleauth;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * Генерирует по одной зачарованной книге на каждый уровень каждого
 * зачарования в игре — все зачарования нужны на уровнях от 1 до
 * максимального, а не только максимальный, раз попросили "все книги с
 * чарами". Использует STORED_ENCHANTMENTS (не ENCHANTMENTS — это разные
 * компоненты, книги хранят зачарование отдельно от того, что реально
 * работает на надетом предмете).
 */
public class EnchantedBooksCatalog {

    private EnchantedBooksCatalog() {
    }

    private static final String[] ROMAN = {"I", "II", "III", "IV", "V"};

    public static List<ShopEntry> build(MinecraftServer server) {
        List<ShopEntry> list = new ArrayList<>();
        Registry<Enchantment> registry = server.getRegistryManager().get(RegistryKeys.ENCHANTMENT);

        for (RegistryEntry.Reference<Enchantment> entry : registry.streamEntries().toList()) {
            String path = entry.registryKey().getValue().getPath();
            String ru = EnchantmentNames.RU.getOrDefault(path, path);
            int maxLevel = entry.value().getMaxLevel();

            for (int level = 1; level <= maxLevel; level++) {
                ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);

                ItemEnchantmentsComponent.Builder builder =
                        new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
                builder.add(entry, level);
                stack.set(DataComponentTypes.STORED_ENCHANTMENTS, builder.build());

                String levelLabel = maxLevel > 1 && level <= ROMAN.length ? " " + ROMAN[level - 1] : "";
                stack.set(DataComponentTypes.CUSTOM_NAME,
                        Text.literal(ru + levelLabel).formatted(Formatting.AQUA)
                                .styled(s -> s.withItalic(false)));

                list.add(new ShopEntry(stack, path + "_" + level));
            }
        }
        return list;
    }
}
