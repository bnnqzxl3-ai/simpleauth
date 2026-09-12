package ru.simpleauth;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Меню выбора трека для музыкальной зоны — открывается как обычный сундук на
 * 9 слотов. Каждый слот — предмет-ярлык одного трека из конфига; клик по
 * слоту переключает музыку в зоне на этот трек и закрывает меню. Предметы
 * взять/переместить нельзя — любой клик перехватывается.
 */
public class TrackSelectorScreenHandler extends GenericContainerScreenHandler {

    private final AuthManager manager;
    private final List<Config.TrackDef> tracks;

    public TrackSelectorScreenHandler(int syncId, PlayerInventory playerInventory,
                                      AuthManager manager, List<Config.TrackDef> tracks) {
        super(ScreenHandlerType.GENERIC_9X1, syncId, playerInventory,
                buildInventory(tracks), 1);
        this.manager = manager;
        this.tracks = tracks;
    }

    private static SimpleInventory buildInventory(List<Config.TrackDef> tracks) {
        SimpleInventory inv = new SimpleInventory(9);
        for (int i = 0; i < tracks.size() && i < 9; i++) {
            Config.TrackDef track = tracks.get(i);
            ItemStack stack = new ItemStack(Items.MUSIC_DISC_13);
            stack.set(DataComponentTypes.CUSTOM_NAME,
                    Text.literal(track.name).formatted(Formatting.AQUA)
                            .styled(s -> s.withItalic(false)));
            inv.setStack(i, stack);
        }
        return inv;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        // перехватываем любой клик по слотам с треками — предметы не двигаются
        if (slotIndex >= 0 && slotIndex < tracks.size()
                && player instanceof ServerPlayerEntity serverPlayer) {
            Config.TrackDef track = tracks.get(slotIndex);
            Config cfg = manager.config();
            cfg.zoneMusicSound = track.soundId;
            cfg.zoneMusicLengthSeconds = Math.max(20, track.lengthSeconds);
            cfg.save();
            manager.actionLogger().log("TRACK_SWITCH " + serverPlayer.getGameProfile().getName()
                    + " -> " + track.name);

            serverPlayer.sendMessage(
                    Text.literal("Музыка в зоне переключена на: ")
                            .formatted(Formatting.GREEN)
                            .append(Text.literal(track.name).formatted(Formatting.AQUA)),
                    false);
            serverPlayer.closeHandledScreen();
            return;
        }
        // клики по слотам инвентаря игрока (если такие есть в раскладке) игнорируем молча
    }
}
