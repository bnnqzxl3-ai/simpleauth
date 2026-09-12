package ru.simpleauth;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Выдача списка материалов, упакованного в шалкеры.
 * Список берётся из конфига, при первом запуске заполняется значениями ниже.
 */
public class MaterialKit {

    private MaterialKit() {
    }

    private static final int SLOTS_PER_BOX = 27;
    private static final int MAX_BOXES = 27;

    /** Материалы японского дома. Правится в конфиге, поле materials. */
    public static Map<String, Integer> defaults() {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("dirt", 2289);
        m.put("spruce_slab", 2259);
        m.put("spruce_log", 2221);
        m.put("grass_block", 1800);
        m.put("oak_leaves", 1600);
        m.put("podzol", 1551);
        m.put("oak_trapdoor", 1497);
        m.put("dark_oak_slab", 854);
        m.put("spruce_stairs", 810);
        m.put("orange_tulip", 665);
        m.put("brown_mushroom_block", 664);
        m.put("white_concrete", 643);
        m.put("oak_log", 479);
        m.put("dark_oak_stairs", 409);
        m.put("bone_block", 361);
        m.put("oak_planks", 354);
        m.put("bookshelf", 316);
        m.put("sandstone", 274);
        m.put("smooth_sandstone", 272);
        m.put("white_concrete_powder", 270);
        m.put("smooth_quartz", 257);
        m.put("gray_terracotta", 249);
        m.put("stone", 225);
        m.put("crafting_table", 210);
        m.put("dark_oak_fence_gate", 205);
        m.put("anvil", 203);
        m.put("gray_concrete", 188);
        m.put("birch_leaves", 184);
        m.put("dark_oak_fence", 178);
        m.put("spruce_fence", 168);
        m.put("cobblestone", 162);
        m.put("stone_bricks", 161);
        m.put("gray_concrete_powder", 160);
        m.put("andesite", 133);
        m.put("spruce_fence_gate", 130);
        m.put("green_concrete_powder", 129);
        m.put("cauldron", 118);
        m.put("oak_slab", 112);
        m.put("oak_stairs", 108);
        m.put("glowstone", 95);
        m.put("stone_button", 94);
        m.put("tall_grass", 93);
        m.put("jungle_slab", 91);
        m.put("string", 86);
        m.put("granite", 85);
        m.put("dark_oak_planks", 85);
        m.put("coarse_dirt", 78);
        m.put("white_terracotta", 77);
        m.put("water_bucket", 63);
        m.put("iron_bars", 59);
        m.put("green_shulker_box", 56);
        m.put("chiseled_quartz_block", 48);
        m.put("cactus", 45);
        m.put("flower_pot", 44);
        m.put("spruce_planks", 42);
        m.put("birch_slab", 34);
        m.put("green_glazed_terracotta", 34);
        m.put("sand", 34);
        m.put("terracotta", 28);
        m.put("green_carpet", 25);
        m.put("stone_brick_stairs", 25);
        m.put("brown_stained_glass_pane", 24);
        m.put("fern", 23);
        m.put("polished_andesite", 23);
        m.put("sea_lantern", 22);
        m.put("stone_brick_slab", 21);
        m.put("smooth_stone", 20);
        m.put("bedrock", 18);
        m.put("daylight_detector", 17);
        m.put("blue_orchid", 17);
        m.put("sunflower", 16);
        m.put("jungle_leaves", 13);
        m.put("birch_fence", 12);
        m.put("dark_oak_wood", 12);
        m.put("spruce_wood", 12);
        m.put("green_wool", 11);
        m.put("stone_pressure_plate", 11);
        m.put("birch_door", 10);
        m.put("light_gray_wool", 10);
        m.put("jungle_stairs", 10);
        m.put("peony", 8);
        m.put("cobblestone_slab", 7);
        m.put("lily_pad", 6);
        m.put("allium", 5);
        m.put("hopper", 4);
        m.put("brewing_stand", 4);
        m.put("chest", 4);
        m.put("white_wool", 3);
        m.put("potato", 3);
        m.put("red_wool", 3);
        m.put("lime_shulker_box", 3);
        m.put("carrot", 3);
        m.put("birch_fence_gate", 2);
        m.put("iron_trapdoor", 2);
        m.put("tripwire_hook", 2);
        m.put("wet_sponge", 2);
        m.put("pink_glazed_terracotta", 2);
        m.put("light_gray_terracotta", 2);
        m.put("ender_chest", 2);
        m.put("structure_block", 1);
        m.put("light_blue_stained_glass_pane", 1);
        m.put("infested_cracked_stone_bricks", 1);
        m.put("dead_bush", 1);
        m.put("dandelion", 1);
        m.put("pink_concrete", 1);
        m.put("lever", 1);
        m.put("blue_stained_glass", 1);
        return m;
    }

    private static Item item(String id) {
        try {
            Identifier identifier = Identifier.tryParse(id.contains(":") ? id : "minecraft:" + id);
            if (identifier == null) return null;
            Item found = Registries.ITEM.get(identifier);
            return found == Items.AIR ? null : found;
        } catch (Exception e) {
            return null;
        }
    }

    /** Шалкеры нельзя класть внутрь шалкера — такие выдаём отдельно. */
    private static boolean isShulker(Item item) {
        try {
            return Registries.ITEM.getId(item).getPath().endsWith("shulker_box");
        } catch (Exception e) {
            return false;
        }
    }

    public static int give(AuthManager manager, ServerPlayerEntity player, int multiplier) {
        Map<String, Integer> materials = manager.config().materials;
        if (materials == null || materials.isEmpty()) return 0;

        List<ItemStack> loose = new ArrayList<>();
        List<ItemStack> packable = new ArrayList<>();
        List<String> unknown = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : materials.entrySet()) {
            Item item = item(entry.getKey());
            if (item == null) {
                unknown.add(entry.getKey());
                continue;
            }
            int needed = Math.max(0, entry.getValue()) * Math.max(1, multiplier);
            int max = new ItemStack(item).getMaxCount();
            boolean shulker = isShulker(item);

            while (needed > 0) {
                int size = Math.min(max, needed);
                ItemStack stack = new ItemStack(item, size);
                if (shulker) {
                    loose.add(stack);
                } else {
                    packable.add(stack);
                }
                needed -= size;
            }
        }

        // раскладываем по шалкерам
        int boxes = 0;
        for (int i = 0; i < packable.size() && boxes < MAX_BOXES; i += SLOTS_PER_BOX) {
            List<ItemStack> part = new ArrayList<>(
                    packable.subList(i, Math.min(i + SLOTS_PER_BOX, packable.size())));
            ItemStack box = new ItemStack(Items.SHULKER_BOX);
            box.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(part));
            boxes++;
            box.set(DataComponentTypes.CUSTOM_NAME,
                    Text.literal("Материалы " + boxes).formatted(Formatting.AQUA)
                            .styled(s -> s.withItalic(false)));
            handOver(player, box);
        }

        for (ItemStack stack : loose) {
            handOver(player, stack);
        }

        if (!unknown.isEmpty()) {
            player.sendMessage(Text.literal("Не найдены: " + String.join(", ", unknown))
                    .formatted(Formatting.DARK_GRAY), false);
        }
        return boxes;
    }

    private static void handOver(ServerPlayerEntity player, ItemStack stack) {
        if (!player.getInventory().insertStack(stack)) {
            player.dropItem(stack, false);
        }
    }
}
