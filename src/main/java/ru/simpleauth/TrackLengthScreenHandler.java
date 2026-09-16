package ru.simpleauth;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Изменение длины (в секундах) цикла текущего трека через текстовое поле
 * наковальни — тот же трюк, что и в TrackSearchScreenHandler, только вместо
 * поиска здесь парсится целое число секунд.
 *
 * Правит длину именно у трека, который сейчас проигрывается в зоне (ищет по
 * soundId в списке cfg.tracks) и одновременно cfg.zoneMusicLengthSeconds,
 * который использует ZoneMusic для зацикливания.
 */
public class TrackLengthScreenHandler extends AnvilScreenHandler {

    private final AuthManager manager;
    private String currentText = "";
    private final int resultSlot;

    public TrackLengthScreenHandler(int syncId, PlayerInventory playerInventory, AuthManager manager) {
        super(syncId, playerInventory, ScreenHandlerContext.EMPTY);
        this.manager = manager;
        this.resultSlot = getResultSlotIndex();
        getSlot(0).setStack(new ItemStack(Items.PAPER));
    }

    @Override
    public boolean setNewItemName(String newItemName) {
        this.currentText = newItemName == null ? "" : newItemName;
        return super.setNewItemName(newItemName);
    }

    @Override
    protected boolean canTakeOutput(PlayerEntity player, boolean present) {
        return true;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (slotIndex == resultSlot && player instanceof ServerPlayerEntity serverPlayer) {
            serverPlayer.closeHandledScreen();

            int seconds;
            try {
                seconds = Integer.parseInt(currentText.trim());
            } catch (NumberFormatException e) {
                serverPlayer.sendMessage(
                        Text.literal("Нужно целое число секунд, а не \"" + currentText + "\"")
                                .formatted(Formatting.RED),
                        false);
                return;
            }
            if (seconds < 5) {
                serverPlayer.sendMessage(
                        Text.literal("Слишком коротко, минимум 5 секунд.").formatted(Formatting.RED),
                        false);
                return;
            }

            Config cfg = manager.config();
            cfg.zoneMusicLengthSeconds = seconds;
            for (Config.TrackDef track : cfg.tracks) {
                if (track.soundId != null && track.soundId.equals(cfg.zoneMusicSound)) {
                    track.lengthSeconds = seconds;
                    break;
                }
            }
            cfg.save();
            manager.actionLogger().log("TRACK_LENGTH_SET " + serverPlayer.getGameProfile().getName()
                    + " -> " + seconds + "s");

            serverPlayer.sendMessage(
                    Text.literal("Длина трека установлена: ")
                            .formatted(Formatting.GREEN)
                            .append(Text.literal(seconds + " сек").formatted(Formatting.AQUA)),
                    false);
        }
    }
}
