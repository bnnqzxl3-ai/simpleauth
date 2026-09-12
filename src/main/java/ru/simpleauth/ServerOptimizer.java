package ru.simpleauth;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Лёгкая оптимизация сервера, не требующая сторонних модов:
 *  - лимит враждебных мобов в одном чанке (гасит лаг от моб-ферм);
 *  - склейка одинаковых предметов на земле в более крупные стопки
 *    (меньше item-энтитей одновременно тикает);
 *  - периодический отчёт о TPS владельцу в чат.
 *
 * Намеренно НЕ трогает выгрузку чанков — этим уже занимается ванильный
 * чанк-менеджер по view-distance, и вмешательство мода туда скорее
 * навредит (риск сломать тикающие зоны/сущности), чем поможет.
 */
public class ServerOptimizer {

    private final AuthManager manager;

    private int mobCapTicks = 0;
    private int itemMergeTicks = 0;
    private int lagReportTicks = 0;

    // скользящее окно длительности тиков для оценки TPS
    private long lastTickNanos = 0L;
    private final List<Long> tickDurations = new ArrayList<>();
    private static final int WINDOW = 100;

    public ServerOptimizer(AuthManager manager) {
        this.manager = manager;
    }

    private Config config() {
        return manager.config();
    }

    public void tick(MinecraftServer server) {
        trackTickDuration();

        Config cfg = config();

        if (cfg.mobCapEnabled) {
            mobCapTicks++;
            int interval = Math.max(20, cfg.mobCapCheckIntervalSeconds * 20);
            if (mobCapTicks >= interval) {
                mobCapTicks = 0;
                capMobs(server, cfg.mobCapPerChunk);
            }
        }

        if (cfg.itemMergeEnabled) {
            itemMergeTicks++;
            int interval = Math.max(20, cfg.itemMergeIntervalSeconds * 20);
            if (itemMergeTicks >= interval) {
                itemMergeTicks = 0;
                mergeItems(server);
            }
        }

        if (cfg.lagReportEnabled) {
            lagReportTicks++;
            int interval = Math.max(200, cfg.lagReportIntervalMinutes * 60 * 20);
            if (lagReportTicks >= interval) {
                lagReportTicks = 0;
                reportLag(server);
            }
        }
    }

    private void trackTickDuration() {
        long now = System.nanoTime();
        if (lastTickNanos != 0L) {
            tickDurations.add(now - lastTickNanos);
            while (tickDurations.size() > WINDOW) {
                tickDurations.remove(0);
            }
        }
        lastTickNanos = now;
    }

    /** Удаляет лишних враждебных мобов сверх лимита в переполненных чанках. */
    private void capMobs(MinecraftServer server, int capPerChunk) {
        for (ServerWorld world : server.getWorlds()) {
            Map<Long, List<HostileEntity>> byChunk = new HashMap<>();

            // огромный бокс вокруг спавна фактически = "весь загруженный мир"
            Box worldBox = new Box(
                    -30000000, world.getBottomY(), -30000000,
                    30000000, world.getTopY(), 30000000);

            for (HostileEntity mob : world.getEntitiesByClass(
                    HostileEntity.class, worldBox, e -> e.isAlive())) {
                if (mob.hasCustomName()) continue; // именных не трогаем
                long key = ChunkPos.toLong(mob.getChunkPos());
                byChunk.computeIfAbsent(key, k -> new ArrayList<>()).add(mob);
            }

            for (List<HostileEntity> mobs : byChunk.values()) {
                if (mobs.size() <= capPerChunk) continue;
                int excess = mobs.size() - capPerChunk;
                for (int i = 0; i < excess; i++) {
                    mobs.get(i).discard();
                }
            }
        }
    }

    /** Склеивает предметы одного типа в пределах чанка в более крупные стопки. */
    private void mergeItems(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            Map<String, List<ItemEntity>> groups = new HashMap<>();

            for (ItemEntity item : world.getEntitiesByType(
                    net.minecraft.entity.EntityType.ITEM, e -> e.isAlive())) {
                ItemStack stack = item.getStack();
                if (stack.isEmpty()) continue;
                long chunkKey = ChunkPos.toLong(new ChunkPos(item.getBlockPos()));
                String key = chunkKey + ":" + stack.getItem().toString();
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
            }

            for (List<ItemEntity> group : groups.values()) {
                if (group.size() < 2) continue;
                mergeGroup(group);
            }
        }
    }

    private void mergeGroup(List<ItemEntity> group) {
        int maxStack = group.get(0).getStack().getMaxCount();
        int total = 0;
        for (ItemEntity e : group) total += e.getStack().getCount();

        int stacksNeeded = (int) Math.ceil(total / (double) maxStack);
        if (stacksNeeded >= group.size()) return; // уже некуда склеивать

        int remaining = total;
        for (int i = 0; i < group.size(); i++) {
            ItemEntity entity = group.get(i);
            if (i < stacksNeeded) {
                int give = Math.min(maxStack, remaining);
                ItemStack stack = entity.getStack().copy();
                stack.setCount(give);
                entity.setStack(stack);
                remaining -= give;
            } else {
                entity.discard();
            }
        }
    }

    /** Личный отчёт владельцу о примерном TPS — не пишется в консоль/лог. */
    private void reportLag(MinecraftServer server) {
        if (tickDurations.isEmpty()) return;

        long sum = 0L;
        for (long d : tickDurations) sum += d;
        double avgMs = (sum / (double) tickDurations.size()) / 1_000_000.0;
        double tps = Math.min(20.0, 1000.0 / Math.max(avgMs, 1.0));

        String ownerName = manager.config().owner;
        if (ownerName == null || ownerName.isBlank()) return;

        for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
            if (ownerName.equalsIgnoreCase(online.getGameProfile().getName())) {
                online.sendMessage(
                        Text.literal(String.format(
                                        "TPS ~%.1f, тик ~%.1fms", tps, avgMs))
                                .formatted(tps >= 18 ? Formatting.GREEN
                                        : tps >= 15 ? Formatting.YELLOW : Formatting.RED),
                        false);
                break;
            }
        }
    }
}
