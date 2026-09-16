package ru.simpleauth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Инструменты NPC. Каждый безопасен сам по себе: лимиты зашиты в код и в
 * конфиг, поэтому никакая фраза в чате не заставит выдать лишнего или
 * что-то сломать. Всё выполняется в серверном потоке.
 */
public class AiTools {

    private final SessionManager manager;
    private final AiCompanion companion;
    private final AiNotes notes;

    private int givenToday;
    private long givenDay;

    public AiTools(SessionManager manager, AiCompanion companion, AiNotes notes) {
        this.manager = manager;
        this.companion = companion;
        this.notes = notes;
    }

    // --- описания для модели -------------------------------------------------

    public JsonArray definitions() {
        JsonArray tools = new JsonArray();

        tools.add(tool("look_around",
                "Осмотреться: своя позиция, время суток, погода, кто из игроков рядом "
                + "и сколько у них здоровья, сколько вокруг враждебных мобов.",
                schema()));

        JsonObject say = schema();
        property(say, "text", "string", "Что сказать вслух в чат сервера.");
        required(say, "text");
        tools.add(tool("say", "Сказать фразу в чат. Это твой единственный способ говорить.", say));

        JsonObject follow = schema();
        property(follow, "player", "string", "Ник игрока.");
        required(follow, "player");
        tools.add(tool("follow", "Пойти за игроком и держаться рядом с ним.", follow));

        tools.add(tool("stay", "Остановиться и стоять на месте.", schema()));

        JsonObject give = schema();
        property(give, "player", "string", "Ник игрока, которому отдать.");
        property(give, "item", "string", "Идентификатор предмета, например torch или bread.");
        property(give, "count", "integer", "Сколько штук.");
        required(give, "player", "item", "count");
        tools.add(tool("give",
                "Отдать игроку предметы из разрешённого списка. Список и лимиты фиксированы, "
                + "запросить больше нельзя. Доступно сейчас: " + whitelistHint(),
                give));

        JsonObject defend = schema();
        property(defend, "player", "string", "Ник игрока, которого защищаем.");
        required(defend, "player");
        tools.add(tool("defend",
                "Атаковать враждебных мобов рядом с игроком. Работает только по враждебным мобам.",
                defend));

        JsonObject remember = schema();
        property(remember, "player", "string", "Про кого запоминаем.");
        property(remember, "note", "string", "Короткий факт: что человек любит, что обещал, над чем работает.");
        required(remember, "player", "note");
        tools.add(tool("remember",
                "Запомнить факт о человеке надолго — переживает перезапуск сервера.", remember));

        JsonObject recall = schema();
        property(recall, "player", "string", "Про кого вспоминаем.");
        required(recall, "player");
        tools.add(tool("recall", "Вспомнить, что ты знаешь о человеке.", recall));

        JsonObject goal = schema();
        property(goal, "goal", "string", "Чем ты сейчас занят. Пустая строка — дел нет.");
        required(goal, "goal");
        tools.add(tool("set_goal",
                "Записать своё текущее дело. Ты увидишь его на следующем самостоятельном ходу, "
                + "так что длинную задачу можно вести по шагам.", goal));

        return tools;
    }

    private static JsonObject tool(String name, String description, JsonObject schema) {
        JsonObject tool = new JsonObject();
        tool.addProperty("name", name);
        tool.addProperty("description", description);
        tool.add("input_schema", schema);
        return tool;
    }

    private static JsonObject schema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", new JsonObject());
        return schema;
    }

    private static void property(JsonObject schema, String name, String type, String description) {
        JsonObject field = new JsonObject();
        field.addProperty("type", type);
        field.addProperty("description", description);
        schema.getAsJsonObject("properties").add(name, field);
    }

    private static void required(JsonObject schema, String... names) {
        JsonArray list = new JsonArray();
        for (String name : names) list.add(name);
        schema.add("required", list);
    }

    private String whitelistHint() {
        Map<String, Integer> allowed = manager.config().aiGiveWhitelist;
        if (allowed == null || allowed.isEmpty()) return "ничего";
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, Integer> entry : allowed.entrySet()) {
            if (text.length() > 0) text.append(", ");
            text.append(entry.getKey()).append(" до ").append(entry.getValue());
        }
        return text.toString();
    }

    // --- выполнение ----------------------------------------------------------

    /** Вызывается в серверном потоке. Возвращает текст результата для модели. */
    public String run(MinecraftServer server, String name, JsonObject input) {
        try {
            return switch (name) {
                case "look_around" -> lookAround(server);
                case "say" -> say(server, string(input, "text"));
                case "follow" -> follow(server, string(input, "player"));
                case "stay" -> stay();
                case "give" -> give(server, string(input, "player"),
                        string(input, "item"), integer(input, "count"));
                case "defend" -> defend(server, string(input, "player"));
                case "remember" -> remember(string(input, "player"), string(input, "note"));
                case "recall" -> recall(string(input, "player"));
                case "set_goal" -> setGoal(string(input, "goal"));
                default -> "Такого инструмента нет.";
            };
        } catch (Exception e) {
            return "Не вышло: " + e;
        }
    }

    private static String string(JsonObject input, String key) {
        JsonElement value = input == null ? null : input.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString().trim();
    }

    private static int integer(JsonObject input, String key) {
        JsonElement value = input == null ? null : input.get(key);
        try {
            return value == null || value.isJsonNull() ? 0 : value.getAsInt();
        } catch (Exception e) {
            return 0;
        }
    }

    private String lookAround(MinecraftServer server) {
        ServerWorld world = companion.currentWorld();
        if (world == null || !companion.isSpawned()) return "Ты сейчас не в мире.";

        StringBuilder text = new StringBuilder();
        BlockPos pos = BlockPos.ofFloored(companion.x(), companion.y(), companion.z());
        long time = world.getTimeOfDay() % 24000L;
        boolean night = time > 12500L && time < 23000L;

        text.append("Ты стоишь в ").append(world.getRegistryKey().getValue())
                .append(" на ").append(pos.getX()).append(" / ").append(pos.getY())
                .append(" / ").append(pos.getZ()).append(".\n");
        text.append("Сейчас ").append(night ? "ночь" : "день")
                .append(world.isThundering() ? ", гроза" : world.isRaining() ? ", дождь" : ", ясно")
                .append(". Освещение на твоём месте: ").append(world.getLightLevel(pos)).append(".\n");

        List<String> people = new ArrayList<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.getServerWorld() != world) continue;
            double distance = Math.sqrt(player.squaredDistanceTo(
                    companion.x(), companion.y(), companion.z()));
            if (distance > manager.config().aiHearRadius * 2) continue;
            people.add(String.format(Locale.ROOT, "%s в %.0f блоках, здоровье %.0f из 20",
                    player.getGameProfile().getName(), distance, player.getHealth()));
        }
        text.append(people.isEmpty() ? "Рядом никого нет.\n"
                : "Рядом: " + String.join("; ", people) + ".\n");

        double radius = manager.config().aiHearRadius;
        Box box = new Box(companion.x() - radius, companion.y() - radius, companion.z() - radius,
                companion.x() + radius, companion.y() + radius, companion.z() + radius);
        int mobs = world.getEntitiesByClass(HostileEntity.class, box, e -> e.isAlive()).size();
        text.append("Враждебных мобов поблизости: ").append(mobs).append(".");

        String goal = companion.goal();
        if (!goal.isEmpty()) text.append("\nТвоё текущее дело: ").append(goal).append(".");
        return text.toString();
    }

    private String say(MinecraftServer server, String text) {
        if (text.isEmpty()) return "Пустую фразу не говорю.";
        companion.say(server, text);
        return "Сказано.";
    }

    private String follow(MinecraftServer server, String name) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(name);
        if (player == null) return "Игрока " + name + " нет в сети.";
        companion.follow(player);
        return "Идёшь за " + player.getGameProfile().getName() + ".";
    }

    private String stay() {
        companion.stay();
        return "Стоишь на месте.";
    }

    private String give(MinecraftServer server, String name, String itemId, int count) {
        ServerSettings cfg = manager.config();
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(name);
        if (player == null) return "Игрока " + name + " нет в сети.";
        if (count <= 0) return "Количество должно быть больше нуля.";

        Map<String, Integer> allowed = cfg.aiGiveWhitelist;
        String key = itemId.contains(":") ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
        key = key.toLowerCase(Locale.ROOT);
        Integer perCall = allowed == null ? null : allowed.get(key);
        if (perCall == null) {
            return "Предмет " + itemId + " выдавать нельзя. Можно только: " + whitelistHint() + ".";
        }
        if (count > perCall) count = perCall;

        long today = System.currentTimeMillis() / 86_400_000L;
        if (today != givenDay) {
            givenDay = today;
            givenToday = 0;
        }
        int left = Math.max(0, cfg.aiGiveDailyLimit - givenToday);
        if (left <= 0) return "На сегодня лимит выдачи исчерпан.";
        if (count > left) count = left;

        Identifier identifier = Identifier.tryParse("minecraft:" + key);
        Item item = identifier == null ? null : Registries.ITEM.get(identifier);
        if (item == null || item == Items.AIR) return "Не знаю такой предмет: " + itemId + ".";

        int handed = 0;
        int maxStack = new ItemStack(item).getMaxCount();
        while (handed < count) {
            int size = Math.min(maxStack, count - handed);
            ItemStack stack = new ItemStack(item, size);
            if (!player.getInventory().insertStack(stack)) player.dropItem(stack, false);
            handed += size;
        }
        givenToday += handed;
        return "Отдано " + handed + " " + key + " игроку " + player.getGameProfile().getName()
                + ". Осталось на сегодня: " + Math.max(0, cfg.aiGiveDailyLimit - givenToday) + ".";
    }

    private String defend(MinecraftServer server, String name) {
        ServerSettings cfg = manager.config();
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(name);
        if (player == null) return "Игрока " + name + " нет в сети.";
        if (!companion.isSpawned()) return "Ты сейчас не в мире.";

        ServerWorld world = player.getServerWorld();
        double radius = cfg.aiDefendRadius;
        Box box = new Box(player.getX() - radius, player.getY() - radius, player.getZ() - radius,
                player.getX() + radius, player.getY() + radius, player.getZ() + radius);

        int hit = 0;
        for (HostileEntity mob : world.getEntitiesByClass(HostileEntity.class, box, e -> e.isAlive())) {
            if (hit >= cfg.aiDefendMaxTargets) break;
            mob.damage(world.getDamageSources().playerAttack(companion.entity()), cfg.aiDefendDamage);
            hit++;
        }
        companion.swing(server);
        return hit == 0 ? "Враждебных мобов рядом с " + name + " нет."
                : "Ударил " + hit + " мобов рядом с " + name + ".";
    }

    private String remember(String player, String note) {
        if (player.isEmpty() || note.isEmpty()) return "Нечего запоминать.";
        notes.add(player, note);
        return "Запомнил про " + player + ".";
    }

    private String recall(String player) {
        List<String> known = notes.get(player);
        if (known.isEmpty()) return "Про " + player + " ты пока ничего не знаешь.";
        return "Что ты знаешь про " + player + ": " + String.join("; ", known) + ".";
    }

    private String setGoal(String goal) {
        companion.setGoal(goal);
        return goal.isEmpty() ? "Дел больше нет." : "Твоё дело теперь: " + goal + ".";
    }
}
