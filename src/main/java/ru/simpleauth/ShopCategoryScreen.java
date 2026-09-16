package ru.simpleauth;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Первый экран /cursedshop — выбор категории. Клик по категории открывает
 * ShopScreenHandler, уже отфильтрованный под неё.
 */
public class ShopCategoryScreen extends GenericContainerScreenHandler {

    private static final int ROWS = 3;
    private static final int SIZE = ROWS * 9;

    private static Map<ShopCategory, List<Item>> cachedByCategory;

    public ShopCategoryScreen(int syncId, PlayerInventory playerInventory) {
        super(ScreenHandlerType.GENERIC_9X3, syncId, playerInventory, buildInventory(), ROWS);
    }

    /** Раскладывает все предметы игры по категориям один раз, дальше только кэш. */
    public static Map<ShopCategory, List<Item>> byCategory() {
        if (cachedByCategory == null) {
            Map<ShopCategory, List<Item>> map = new EnumMap<>(ShopCategory.class);
            for (ShopCategory category : ShopCategory.values()) {
                map.put(category, new ArrayList<>());
            }
            for (Item item : ShopScreenHandler.allItems()) {
                map.get(ShopCategory.of(item)).add(item);
            }
            cachedByCategory = map;
        }
        return cachedByCategory;
    }

    private static SimpleInventory buildInventory() {
        SimpleInventory inv = new SimpleInventory(SIZE);
        Map<ShopCategory, List<Item>> grouped = byCategory();
        ShopCategory[] categories = ShopCategory.values();
        for (int i = 0; i < categories.length && i < SIZE; i++) {
            ShopCategory category = categories[i];
            int count = grouped.get(category).size();
            ItemStack stack = new ItemStack(category.icon);
            stack.set(DataComponentTypes.CUSTOM_NAME,
                    Text.literal(category.label).formatted(Formatting.GOLD, Formatting.BOLD)
                            .styled(s -> s.withItalic(false)));
            stack.set(DataComponentTypes.LORE,
                    new net.minecraft.component.type.LoreComponent(List.of(
                            Text.literal(count + " предметов").formatted(Formatting.GRAY)
                                    .styled(s -> s.withItalic(false)))));
            inv.setStack(i, stack);
        }
        return inv;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
        if (slotIndex < 0 || slotIndex >= ShopCategory.values().length) return;

        ShopCategory category = ShopCategory.values()[slotIndex];
        List<Item> items = byCategory().get(category);

        serverPlayer.closeHandledScreen();
        serverPlayer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId2, inv2, p2) -> new ShopScreenHandler(syncId2, inv2, items, 0, 0, category),
                Text.literal(category.label)));
    }
}
