package ru.simpleauth;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * Выдача снаряжения владельцу. Ничего не пишет в лог и не рассылает
 * операторам: команда регистрируется без вызова sendFeedback с broadcast.
 */
public class AdminKit {

    private AdminKit() {
    }

    /** Накладывает чары. Незнакомое зачарование просто пропускаем. */
    private static void ench(MinecraftServer server, ItemStack stack,
                             RegistryKey<Enchantment> key, int level) {
        try {
            Registry<Enchantment> registry = server.getRegistryManager().get(RegistryKeys.ENCHANTMENT);
            registry.getEntry(key).ifPresent(entry -> stack.addEnchantment(entry, level));
        } catch (Exception ignored) {
            // молча: незачарованный предмет лучше, чем сломанная команда
        }
    }

    private static ItemStack named(ItemStack stack, String name, Formatting color) {
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal(name).formatted(color).styled(s -> s.withItalic(false)));
        return stack;
    }

    public static List<ItemStack> build(MinecraftServer server) {
        List<ItemStack> items = new ArrayList<>();

        // --- броня ---
        ItemStack helmet = new ItemStack(Items.NETHERITE_HELMET);
        ench(server, helmet, Enchantments.PROTECTION, 5);
        ench(server, helmet, Enchantments.UNBREAKING, 3);
        ench(server, helmet, Enchantments.MENDING, 1);
        ench(server, helmet, Enchantments.THORNS, 3);
        ench(server, helmet, Enchantments.RESPIRATION, 3);
        ench(server, helmet, Enchantments.AQUA_AFFINITY, 1);
        items.add(helmet);

        ItemStack chest = new ItemStack(Items.NETHERITE_CHESTPLATE);
        ench(server, chest, Enchantments.PROTECTION, 5);
        ench(server, chest, Enchantments.UNBREAKING, 3);
        ench(server, chest, Enchantments.MENDING, 1);
        ench(server, chest, Enchantments.THORNS, 3);
        items.add(chest);

        ItemStack legs = new ItemStack(Items.NETHERITE_LEGGINGS);
        ench(server, legs, Enchantments.PROTECTION, 5);
        ench(server, legs, Enchantments.UNBREAKING, 3);
        ench(server, legs, Enchantments.MENDING, 1);
        ench(server, legs, Enchantments.THORNS, 3);
        ench(server, legs, Enchantments.SWIFT_SNEAK, 3);
        items.add(legs);

        ItemStack boots = new ItemStack(Items.NETHERITE_BOOTS);
        ench(server, boots, Enchantments.PROTECTION, 5);
        ench(server, boots, Enchantments.UNBREAKING, 3);
        ench(server, boots, Enchantments.MENDING, 1);
        ench(server, boots, Enchantments.THORNS, 3);
        ench(server, boots, Enchantments.FEATHER_FALLING, 4);
        ench(server, boots, Enchantments.DEPTH_STRIDER, 3);
        ench(server, boots, Enchantments.SOUL_SPEED, 3);
        items.add(boots);

        // --- элитры ---
        ItemStack elytra = new ItemStack(Items.ELYTRA);
        ench(server, elytra, Enchantments.UNBREAKING, 3);
        ench(server, elytra, Enchantments.MENDING, 1);
        ench(server, elytra, Enchantments.PROTECTION, 5);
        items.add(elytra);

        // --- меч ---
        ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
        ench(server, sword, Enchantments.SHARPNESS, 5);
        ench(server, sword, Enchantments.LOOTING, 3);
        ench(server, sword, Enchantments.FIRE_ASPECT, 2);
        ench(server, sword, Enchantments.SWEEPING_EDGE, 3);
        ench(server, sword, Enchantments.KNOCKBACK, 2);
        ench(server, sword, Enchantments.UNBREAKING, 3);
        ench(server, sword, Enchantments.MENDING, 1);
        items.add(sword);

        // --- инструменты ---
        ItemStack pickaxe = new ItemStack(Items.NETHERITE_PICKAXE);
        ench(server, pickaxe, Enchantments.EFFICIENCY, 5);
        ench(server, pickaxe, Enchantments.FORTUNE, 3);
        ench(server, pickaxe, Enchantments.UNBREAKING, 3);
        ench(server, pickaxe, Enchantments.MENDING, 1);
        items.add(pickaxe);

        ItemStack axe = new ItemStack(Items.NETHERITE_AXE);
        ench(server, axe, Enchantments.EFFICIENCY, 5);
        ench(server, axe, Enchantments.FORTUNE, 3);
        ench(server, axe, Enchantments.SHARPNESS, 5);
        ench(server, axe, Enchantments.UNBREAKING, 3);
        ench(server, axe, Enchantments.MENDING, 1);
        items.add(axe);

        ItemStack shovel = new ItemStack(Items.NETHERITE_SHOVEL);
        ench(server, shovel, Enchantments.EFFICIENCY, 5);
        ench(server, shovel, Enchantments.FORTUNE, 3);
        ench(server, shovel, Enchantments.UNBREAKING, 3);
        ench(server, shovel, Enchantments.MENDING, 1);
        items.add(shovel);

        ItemStack hoe = new ItemStack(Items.NETHERITE_HOE);
        ench(server, hoe, Enchantments.EFFICIENCY, 5);
        ench(server, hoe, Enchantments.FORTUNE, 3);
        ench(server, hoe, Enchantments.UNBREAKING, 3);
        ench(server, hoe, Enchantments.MENDING, 1);
        items.add(hoe);

        // --- алмазный меч с запредельными уровнями ---
        ItemStack blade = new ItemStack(Items.DIAMOND_SWORD);
        ench(server, blade, Enchantments.SHARPNESS, 30);
        ench(server, blade, Enchantments.LOOTING, 1000);
        ench(server, blade, Enchantments.SWEEPING_EDGE, 1000);
        ench(server, blade, Enchantments.UNBREAKING, 3);
        ench(server, blade, Enchantments.MENDING, 1);
        items.add(named(blade, "Резящий клинок", Formatting.LIGHT_PURPLE));

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
