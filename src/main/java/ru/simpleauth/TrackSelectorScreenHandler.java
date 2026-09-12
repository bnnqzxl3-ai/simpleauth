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
 * Меню выбора трека и громкости для музыкальной зоны — большой сундук на 54
 * слота (6 рядов). Верхние ряды — треки из конфига, последний ряд (слоты
 * 44-53) — громкость от 10% до 100% с шагом 10.
 *
 * Блеск (тот самый переливающийся зачарованный отблеск) висит на предмете,
 * который активен ПРЯМО СЕЙЧАС - текущий трек и текущая громкость - так
 * блеск не просто "для красоты", а сразу показывает, что выбрано. Остальные
 * пункты подписаны золотым текстом, без блеска - чтобы меню не рябило.
 *
 * Любой клик по значимым слотам перехватывается: предметы забрать нельзя,
 * это кнопки.
 */
public class TrackSelectorScreenHandler extends GenericContainerScreenHandler {

    private static final int ROWS = 6;
    private static final int SIZE = ROWS * 9;
    private static final int VOLUME_START_SLOT = 44; // последний слот 5-го ряда + весь 6-й ряд
    private static final int[] VOLUME_PERCENTS = {10, 20, 30, 40, 50, 60, 70, 80, 90, 100};

    private final AuthManager manager;
    private final List<Config.TrackDef> tracks;

    public TrackSelectorScreenHandler(int syncId, PlayerInventory playerInventory,
                                      AuthManager manager, List<Config.TrackDef> tracks) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory,
                buildInventory(manager, tracks), ROWS);
        this.manager = manager;
        this.tracks = tracks;
    }

    private static SimpleInventory buildInventory(AuthManager manager, List<Config.TrackDef> tracks) {
        SimpleInventory inv = new SimpleInventory(SIZE);
        Config cfg = manager.config();

        int trackLimit = Math.min(tracks.size(), VOLUME_START_SLOT);
        for (int i = 0; i < trackLimit; i++) {
            Config.TrackDef track = tracks.get(i);
            boolean current = track.soundId != null && track.soundId.equals(cfg.zoneMusicSound);

            ItemStack stack = new ItemStack(Items.MUSIC_DISC_13);
            Text name = current
                    ? Text.literal("▶ " + track.name).formatted(Formatting.GREEN, Formatting.BOLD)
                    : Text.literal(track.name).formatted(Formatting.GOLD);
            stack.set(DataComponentTypes.CUSTOM_NAME, name.copy().styled(s -> s.withItalic(false)));
            if (current) {
                stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
            }
            inv.setStack(i, stack);
        }

        int currentPercent = Math.round(cfg.zoneMusicVolume * 100.0F / 10.0F) * 10;
        for (int i = 0; i < VOLUME_PERCENTS.length; i++) {
            int percent = VOLUME_PERCENTS[i];
            boolean current = percent == currentPercent;

            ItemStack stack = new ItemStack(Items.NOTE_BLOCK);
            Text name = current
                    ? Text.literal("▶ Громкость: " + percent + "%").formatted(Formatting.GREEN, Formatting.BOLD)
                    : Text.literal("Громкость: " + percent + "%").formatted(Formatting.AQUA);
            stack.set(DataComponentTypes.CUSTOM_NAME, name.copy().styled(s -> s.withItalic(false)));
            if (current) {
                stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
            }
            inv.setStack(VOLUME_START_SLOT + i, stack);
        }

        return inv;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;

        // клик по треку
        if (slotIndex >= 0 && slotIndex < tracks.size() && slotIndex < VOLUME_START_SLOT) {
            Config.TrackDef track = tracks.get(slotIndex);
            manager.zoneMusic().switchTrack(serverPlayer.getServer(), track);
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

        // клик по громкости
        int volumeIndex = slotIndex - VOLUME_START_SLOT;
        if (volumeIndex >= 0 && volumeIndex < VOLUME_PERCENTS.length) {
            int percent = VOLUME_PERCENTS[volumeIndex];
            Config cfg = manager.config();
            cfg.zoneMusicVolume = percent / 100.0F;
            cfg.save();
            manager.actionLogger().log("VOLUME_SET " + serverPlayer.getGameProfile().getName()
                    + " -> " + percent + "%");

            serverPlayer.sendMessage(
                    Text.literal("Громкость музыки в зоне: ")
                            .formatted(Formatting.GREEN)
                            .append(Text.literal(percent + "%").formatted(Formatting.AQUA)),
                    false);
            serverPlayer.closeHandledScreen();
        }
        // остальные клики (пустые слоты) игнорируем молча
    }
}
