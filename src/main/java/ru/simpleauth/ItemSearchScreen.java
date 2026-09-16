package ru.simpleauth;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Поиск предмета для /cursedshop через текстовое поле наковальни — тот же
 * трюк, что и в ItemSearchScreen для треков. Фильтрует по подстроке в
 * идентификаторе предмета (например "diamond_sword"), без учёта регистра.
 */
public class ItemSearchScreen extends AnvilScreenHandler {

    private final List<Item> allItems;
    private final int qtyIndex;
    private String currentQuery = "";
    private final int resultSlot;

    public ItemSearchScreen(int syncId, PlayerInventory playerInventory,
                             List<Item> allItems, int qtyIndex) {
        super(syncId, playerInventory, ScreenHandlerContext.EMPTY);
        this.allItems = allItems;
        this.qtyIndex = qtyIndex;
        this.resultSlot = getResultSlotIndex();
        getSlot(0).setStack(new ItemStack(Items.PAPER));
    }

    @Override
    public boolean setNewItemName(String newItemName) {
        this.currentQuery = newItemName == null ? "" : newItemName;
        return super.setNewItemName(newItemName);
    }

    @Override
    protected boolean canTakeOutput(PlayerEntity player, boolean present) {
        return true;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (slotIndex == resultSlot && player instanceof ServerPlayerEntity serverPlayer) {
            String query = currentQuery.trim().toLowerCase(Locale.ROOT);
            List<Item> matches = new ArrayList<>();
            for (Item item : allItems) {
                String id = Registries.ITEM.getId(item).getPath();
                if (query.isEmpty() || id.contains(query)) {
                    matches.add(item);
                }
            }

            serverPlayer.closeHandledScreen();

            if (matches.isEmpty()) {
                serverPlayer.sendMessage(
                        Text.literal("Ничего не найдено по запросу: " + currentQuery)
                                .formatted(Formatting.RED),
                        false);
                return;
            }

            serverPlayer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                    (syncId2, inv2, p2) -> new ShopScreenHandler(syncId2, inv2, matches, 0, qtyIndex, null),
                    Text.literal("Найдено: " + matches.size())));
        }
        // остальные клики блокируем — это поле поиска, не настоящая наковальня
    }
}
