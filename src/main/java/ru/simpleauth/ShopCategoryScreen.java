package ru.simpleauth;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Первый экран /cursedshop — выбор категории, крупный (6 рядов) с
 * декоративной рамкой из стекла по краям. Клик по категории открывает
 * ShopScreenHandler, уже отфильтрованный под неё.
 *
 * Категории раскладываются в верхнем ряду по центру — по одной на слот, с
 * количеством товаров под названием.
 */
public class ShopCategoryScreen extends GenericContainerScreenHandler {

    private static final int ROWS = 6;
    private static final int SIZE = ROWS * 9;
    // верхний ряд, начиная со 2-го слота — центрируем 8 категорий из 9 возможных
    private static final int FIRST_CATEGORY_SLOT = 10;

    private static Map<ShopCategory, List<ShopEntry>> cachedByCategory;

    public ShopCategoryScreen(int syncId, PlayerInventory playerInventory, MinecraftServer server) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, buildInventory(server), ROWS);
    }

    /** Раскладывает все предметы игры по категориям один раз (включая все зачарованные книги), дальше кэш. */
    public static Map<ShopCategory, List<ShopEntry>> byCategory(MinecraftServer server) {
        if (cachedByCategory == null) {
            Map<ShopCategory, List<ShopEntry>> map = new EnumMap<>(ShopCategory.class);
            for (ShopCategory category : ShopCategory.values()) {
                map.put(category, new ArrayList<>());
            }
            for (Item item : ShopScreenHandler.allItems()) {
                map.get(ShopCategory.of(item)).add(ShopEntry.of(item));
            }
            // зачарованные книги на все уровни — отдельно, их не найти простым перебором Item
            map.get(ShopCategory.BOOKS).addAll(EnchantedBooksCatalog.build(server));
            cachedByCategory = map;
        }
        return cachedByCategory;
    }

    private static SimpleInventory buildInventory(MinecraftServer server) {
        SimpleInventory inv = new SimpleInventory(SIZE);

        // декоративная рамка — светло-серое стекло по краям, чтобы не было
        // ощущения "пустого сундука"
        ItemStack border = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        border.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal(" ").styled(s -> s.withItalic(false)));
        for (int i = 0; i < SIZE; i++) {
            int row = i / 9;
            int col = i % 9;
            if (row == 0 || row == ROWS - 1 || col == 0 || col == 8) {
                inv.setStack(i, border);
            }
        }

        Map<ShopCategory, List<ShopEntry>> grouped = byCategory(server);
        ShopCategory[] categories = ShopCategory.values();
        int slot = FIRST_CATEGORY_SLOT;
        for (ShopCategory category : categories) {
            // пропускаем рамку, если упёрлись в правый край
            if (slot % 9 == 8 || slot % 9 == 0) slot++;
            if (slot >= SIZE - 9) break; // не залезаем на нижнюю рамку

            int count = grouped.get(category).size();
            ItemStack stack = new ItemStack(category.icon);
            stack.set(DataComponentTypes.CUSTOM_NAME,
                    Text.literal(category.label).formatted(Formatting.GOLD, Formatting.BOLD)
                            .styled(s -> s.withItalic(false)));
            stack.set(DataComponentTypes.LORE,
                    new LoreComponent(List.of(
                            Text.literal(count + " товаров").formatted(Formatting.GRAY)
                                    .styled(s -> s.withItalic(false)),
                            Text.literal("Нажми, чтобы открыть").formatted(Formatting.DARK_GRAY)
                                    .styled(s -> s.withItalic(false)))));
            inv.setStack(slot, stack);
            slot += 2; // разрежаем по горизонтали, чтобы не слипались визуально
        }

        return inv;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
        MinecraftServer server = serverPlayer.getServer();
        if (server == null) return;

        Map<ShopCategory, List<ShopEntry>> grouped = byCategory(server);
        ShopCategory[] categories = ShopCategory.values();
        int slot = FIRST_CATEGORY_SLOT;
        for (ShopCategory category : categories) {
            if (slot % 9 == 8 || slot % 9 == 0) slot++;
            if (slot >= SIZE - 9) break;
            if (slot == slotIndex) {
                List<ShopEntry> entries = grouped.get(category);
                serverPlayer.closeHandledScreen();
                serverPlayer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                        (syncId2, inv2, p2) -> new ShopScreenHandler(syncId2, inv2, entries, 0, 0, category),
                        Text.literal(category.label)));
                return;
            }
            slot += 2;
        }
        // клик по рамке или пустому месту — игнор
    }
}
