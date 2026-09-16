package ru.simpleauth;

import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.collection.DefaultedList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Личное хранилище владельца (команда /ecc) — 54 слота, содержимое
 * переживает рестарт сервера. Сохраняется на диск автоматически при любом
 * изменении (markDirty) через стандартный ванильный Inventories
 * writeNbt/readNbt — тот же механизм, которым сохраняются обычные сундуки.
 */
public class OwnerStorage {

    private static final int SIZE = 54;
    private static SimpleInventory instance;
    private static RegistryWrapper.WrapperLookup registryLookup;

    private OwnerStorage() {
    }

    public static void init(MinecraftServer server) {
        registryLookup = server.getRegistryManager();
        if (instance == null) instance = new PersistentInventory(SIZE);
        load();
    }

    public static SimpleInventory get() {
        if (instance == null) instance = new PersistentInventory(SIZE);
        return instance;
    }

    private static Path file() {
        return ServerSettings.dir().resolve("owner_storage.dat");
    }

    private static void load() {
        Path path = file();
        if (!Files.exists(path) || registryLookup == null) return;
        try {
            NbtCompound root = NbtIo.readCompressed(path, NbtSizeTracker.ofUnlimitedBytes());
            DefaultedList<ItemStack> stacks = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);
            Inventories.readNbt(root, stacks, registryLookup);
            for (int i = 0; i < SIZE; i++) {
                instance.setStack(i, stacks.get(i));
            }
        } catch (IOException e) {
            ServerCore.LOGGER.warn("Не удалось загрузить owner_storage.dat: {}", e.getMessage());
        }
    }

    private static void save() {
        if (instance == null || registryLookup == null) return;
        try {
            DefaultedList<ItemStack> stacks = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);
            for (int i = 0; i < SIZE; i++) {
                stacks.set(i, instance.getStack(i));
            }
            NbtCompound root = new NbtCompound();
            Inventories.writeNbt(root, stacks, registryLookup);
            Files.createDirectories(ServerSettings.dir());
            NbtIo.writeCompressed(root, file());
        } catch (IOException e) {
            ServerCore.LOGGER.warn("Не удалось сохранить owner_storage.dat: {}", e.getMessage());
        }
    }

    /** SimpleInventory, который сам сохраняется на диск при любом изменении. */
    private static class PersistentInventory extends SimpleInventory {
        PersistentInventory(int size) {
            super(size);
        }

        @Override
        public void markDirty() {
            super.markDirty();
            OwnerStorage.save();
        }
    }
}
