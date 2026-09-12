package ru.simpleauth;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
 * Поиск трека прямо в игре через текстовое поле наковальни (переименование
 * предмета используется как строка ввода — стандартный ванильный трюк).
 * Наковальня открывается без привязки к реальному блоку в мире
 * (ScreenHandlerContext.EMPTY), опыт и предмет не расходуются — обычное
 * поведение полностью переопределено под поиск.
 *
 * По клику на результат переименования (правый слот) берём введённый текст,
 * фильтруем список треков по подстроке в названии (без учёта регистра) и
 * открываем обычное меню выбора уже только с подходящими треками.
 */
public class TrackSearchScreenHandler extends AnvilScreenHandler {

    private final AuthManager manager;
    private final List<Config.TrackDef> allTracks;
    private String currentQuery = "";
    private final int resultSlot;

    public TrackSearchScreenHandler(int syncId, PlayerInventory playerInventory,
                                    AuthManager manager, List<Config.TrackDef> allTracks) {
        super(syncId, playerInventory, ScreenHandlerContext.EMPTY);
        this.manager = manager;
        this.allTracks = allTracks;
        this.resultSlot = getResultSlotIndex();
        // предмет-заглушка, чтобы поле переименования стало активным
        getSlot(0).setStack(new ItemStack(Items.PAPER));
    }

    @Override
    public boolean setNewItemName(String newItemName) {
        this.currentQuery = newItemName == null ? "" : newItemName;
        return super.setNewItemName(newItemName);
    }

    @Override
    protected boolean canTakeOutput(PlayerEntity player, boolean present) {
        return true; // поиск бесплатный, без уровня опыта
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (slotIndex == resultSlot && player instanceof ServerPlayerEntity serverPlayer) {
            String query = currentQuery.trim().toLowerCase(Locale.ROOT);
            List<Config.TrackDef> matches = new ArrayList<>();
            for (Config.TrackDef track : allTracks) {
                if (track.name != null
                        && (query.isEmpty() || track.name.toLowerCase(Locale.ROOT).contains(query))) {
                    matches.add(track);
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
                    (syncId2, inv2, p2) -> new TrackSelectorScreenHandler(syncId2, inv2, manager, matches),
                    Text.literal("Найдено: " + matches.size())));
            return;
        }
        // все остальные клики (перемещение бумаги, инвентарь игрока) блокируем —
        // это поле поиска, а не настоящая наковальня
    }
}
