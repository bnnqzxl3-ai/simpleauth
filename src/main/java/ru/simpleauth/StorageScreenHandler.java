package ru.simpleauth;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;

/**
 * Личное хранилище владельца (/ecc) — обычный сундук на 54 слота (6 рядов),
 * ведёт себя как настоящий сундук: предметы можно свободно перекладывать,
 * ничего не заблокировано. Содержимое хранится отдельно от любого игрового
 * сундука в мире и переживает рестарт сервера — см. OwnerStorage.
 */
public class StorageScreenHandler extends GenericContainerScreenHandler {

    private static final int ROWS = 6;

    public StorageScreenHandler(int syncId, PlayerInventory playerInventory) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, OwnerStorage.get(), ROWS);
    }
}
