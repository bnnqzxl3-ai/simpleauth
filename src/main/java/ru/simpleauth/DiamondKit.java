package ru.simpleauth;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potions;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Второй тихий кит — алмазный набор по конкретному списку, отдельно от
 * основного (незеритового) AdminKit. Ничего не пишет в лог и не рассылает
 * операторам, как и первый кит.
 *
 * Состав:
 *  - кирка (алмаз): Эффективность 5, Прочность 3, Починка
 *  - броня (алмаз, все 4 части): Защита 4, Прочность 3, Починка
 *  - меч (алмаз): Острота 5, Разящий клинок 3, Добыча 3, Прочность 3, Починка
 *  - 2× тотем бессмертия
 *  - 5× зелье огнестойкости (продолжительное, 8 минут — это ровно
 *    длительность обычного "долгого" зелья в ваниле)
 */
public class DiamondKit {

    private DiamondKit() {
    }

    private static void ench(MinecraftServer server, ItemStack stack,
                              RegistryKey<Enchantment> key, int level) {
        try {
            Registry<Enchantment> registry = server.getRegistryManager().get(RegistryKeys.ENCHANTMENT);
            registry.getEntry(key).ifPresent(entry -> stack.addEnchantment(entry, level));
        } catch (Exception ignored) {
            // молча: незачарованный предмет лучше, чем сломанная команда
        }
    }

    public static List<ItemStack> build(MinecraftServer server) {
        List<ItemStack> items = new ArrayList<>();

        // --- кирка ---
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        ench(server, pickaxe, Enchantments.EFFICIENCY, 5);
        ench(server, pickaxe, Enchantments.UNBREAKING, 3);
        ench(server, pickaxe, Enchantments.MENDING, 1);
        items.add(pickaxe);

        // --- броня ---
        ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET);
        ench(server, helmet, Enchantments.PROTECTION, 4);
        ench(server, helmet, Enchantments.UNBREAKING, 3);
        ench(server, helmet, Enchantments.MENDING, 1);
        items.add(helmet);

        ItemStack chest = new ItemStack(Items.DIAMOND_CHESTPLATE);
        ench(server, chest, Enchantments.PROTECTION, 4);
        ench(server, chest, Enchantments.UNBREAKING, 3);
        ench(server, chest, Enchantments.MENDING, 1);
        items.add(chest);

        ItemStack legs = new ItemStack(Items.DIAMOND_LEGGINGS);
        ench(server, legs, Enchantments.PROTECTION, 4);
        ench(server, legs, Enchantments.UNBREAKING, 3);
        ench(server, legs, Enchantments.MENDING, 1);
        items.add(legs);

        ItemStack boots = new ItemStack(Items.DIAMOND_BOOTS);
        ench(server, boots, Enchantments.PROTECTION, 4);
        ench(server, boots, Enchantments.UNBREAKING, 3);
        ench(server, boots, Enchantments.MENDING, 1);
        items.add(boots);

        // --- меч ---
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        ench(server, sword, Enchantments.SHARPNESS, 5);
        ench(server, sword, Enchantments.SMITE, 3);
        ench(server, sword, Enchantments.LOOTING, 3);
        ench(server, sword, Enchantments.UNBREAKING, 3);
        ench(server, sword, Enchantments.MENDING, 1);
        items.add(sword);

        // --- тотемы ---
        items.add(new ItemStack(Items.TOTEM_OF_UNDYING, 2));

        // --- зелья огнестойкости, 8 минут (долгая версия) ---
        ItemStack fireRes = new ItemStack(Items.POTION, 5);
        fireRes.set(DataComponentTypes.POTION_CONTENTS,
                new PotionContentsComponent(Potions.LONG_FIRE_RESISTANCE));
        items.add(fireRes);

        return items;
    }

    /** Кладёт всё в инвентарь, лишнее роняет под ноги. */
    public static int give(MinecraftServer server, ServerPlayerEntity player) {
        int count = 0;
        for (ItemStack stack : build(server)) {
            if (!player.getInventory().insertStack(stack)) {
                player.dropItem(stack, false);
            }
            count++;
        }
        return count;
    }
}
