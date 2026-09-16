package ru.simpleauth;

import net.minecraft.entity.ItemEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Тихая периодическая очистка предметов, лежащих на земле, по всем мирам
 * сервера. Ничего не пишет в лог сервера/консоль — только отправляет личное
 * сообщение владельцу (config.owner), если он сейчас онлайн. Если владельца
 * нет на сервере в момент очистки, сообщение просто не отправляется (не
 * копится и не шлётся задним числом).
 */
public class ItemCleaner {

    private final AuthManager manager;
    private int tickCounter = 0;

    public ItemCleaner(AuthManager manager) {
        this.manager = manager;
    }

    private Config config() {
        return manager.config();
    }

    public void tick(MinecraftServer server) {
        Config cfg = config();
        if (!cfg.itemCleanupEnabled) return;

        tickCounter++;
        int intervalTicks = Math.max(20, cfg.itemCleanupIntervalSeconds * 20);
        if (tickCounter < intervalTicks) return;
        tickCounter = 0;

        int removed = 0;
        for (ServerWorld world : server.getWorlds()) {
            for (ItemEntity item : world.getEntitiesByType(
                    net.minecraft.entity.EntityType.ITEM, e -> true)) {
                item.discard();
                removed++;
            }
        }

        notifyOwner(server, removed);
    }

    /** Личное сообщение владельцу — никаких записей в консоль/лог. */
    private void notifyOwner(MinecraftServer server, int removed) {
        String ownerName = config().owner;
        if (ownerName == null || ownerName.isBlank()) return;

        for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
            if (ownerName.equalsIgnoreCase(online.getGameProfile().getName())) {
                online.sendMessage(
                        Text.literal("Очистка предметов прошла — удалено: ")
                                .formatted(Formatting.GRAY)
                                .append(Text.literal(String.valueOf(removed))
                                        .formatted(Formatting.YELLOW)),
                        false);
                break;
            }
        }
    }
}
