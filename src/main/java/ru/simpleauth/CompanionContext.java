package ru.simpleauth;

import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;

/**
 * Собирает обстановку вокруг игрока и компаньона в короткий текстовый
 * блок, который подмешивается к каждому запросу. Благодаря этому
 * компаньон отвечает не "в вакууме", а видит, что реально происходит:
 * ночь, дождь, здоровье, кто рядом, где вы находитесь.
 *
 * Специально держим это компактным — каждый лишний символ здесь уходит
 * в каждый запрос и увеличивает счёт по API.
 */
public class CompanionContext {

    private static final int SCAN_RADIUS = 16;

    private CompanionContext() {
    }

    public static String build(ServerPlayerEntity player, IronGolemEntity companion) {
        ServerWorld world = player.getServerWorld();
        StringBuilder sb = new StringBuilder();

        sb.append("Обстановка сейчас:\n");

        // где находимся
        String dimension = world.getRegistryKey().getValue().getPath();
        sb.append("- Мир: ").append(translateDimension(dimension)).append('\n');

        world.getBiome(player.getBlockPos()).getKey().ifPresent(key ->
                sb.append("- Биом: ").append(key.getValue().getPath()).append('\n'));

        sb.append("- Координаты игрока: ")
                .append(player.getBlockX()).append(' ')
                .append(player.getBlockY()).append(' ')
                .append(player.getBlockZ()).append('\n');

        // время и погода
        long timeOfDay = world.getTimeOfDay() % 24000L;
        sb.append("- Время: ").append(describeTime(timeOfDay)).append('\n');
        if (world.isThundering()) {
            sb.append("- Погода: гроза\n");
        } else if (world.isRaining()) {
            sb.append("- Погода: дождь\n");
        }

        // состояние игрока
        sb.append("- Здоровье игрока: ")
                .append(Math.round(player.getHealth())).append('/')
                .append(Math.round(player.getMaxHealth()));
        if (player.getHealth() <= player.getMaxHealth() * 0.3F) {
            sb.append(" (МАЛО, игрок в опасности)");
        }
        sb.append('\n');

        sb.append("- Сытость игрока: ")
                .append(player.getHungerManager().getFoodLevel()).append("/20\n");

        String heldItem = player.getMainHandStack().isEmpty()
                ? "ничего"
                : player.getMainHandStack().getName().getString();
        sb.append("- В руке у игрока: ").append(heldItem).append('\n');

        // состояние компаньона
        sb.append("- Твоё здоровье: ")
                .append(Math.round(companion.getHealth())).append('/')
                .append(Math.round(companion.getMaxHealth())).append('\n');

        double distance = Math.sqrt(companion.squaredDistanceTo(player));
        sb.append("- Расстояние до игрока: ").append(Math.round(distance)).append(" блоков\n");

        // кто рядом
        List<String> threats = nearbyHostiles(world, player);
        if (threats.isEmpty()) {
            sb.append("- Враждебных мобов рядом не видно\n");
        } else {
            sb.append("- Враждебные рядом: ").append(String.join(", ", threats)).append('\n');
        }

        return sb.toString();
    }

    private static List<String> nearbyHostiles(ServerWorld world, ServerPlayerEntity player) {
        Box box = player.getBoundingBox().expand(SCAN_RADIUS);
        List<String> names = new ArrayList<>();
        for (HostileEntity mob : world.getEntitiesByClass(HostileEntity.class, box, e -> e.isAlive())) {
            String name = mob.getType().getName().getString();
            if (!names.contains(name)) names.add(name);
            if (names.size() >= 5) break;
        }
        return names;
    }

    private static String describeTime(long timeOfDay) {
        if (timeOfDay < 6000) return "утро";
        if (timeOfDay < 12000) return "день";
        if (timeOfDay < 13000) return "закат";
        if (timeOfDay < 23000) return "ночь (опасно, мобы спавнятся)";
        return "рассвет";
    }

    private static String translateDimension(String dimension) {
        return switch (dimension) {
            case "overworld" -> "обычный мир";
            case "the_nether" -> "Нижний мир (Ад)";
            case "the_end" -> "Край";
            default -> dimension;
        };
    }
}
