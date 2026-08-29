package ru.simpleauth;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AuthCommands {

    /** Команды, доступные до входа. Всё остальное блокируется миксином. */
    public static final Set<String> ALLOWED_BEFORE_LOGIN =
            Set.of("register", "reg", "login", "l");

    public static boolean isAllowedBeforeLogin(String rawCommand) {
        String cmd = rawCommand.trim();
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        int space = cmd.indexOf(' ');
        if (space > 0) cmd = cmd.substring(0, space);
        return ALLOWED_BEFORE_LOGIN.contains(cmd.toLowerCase(Locale.ROOT));
    }

    /** Запрещена ли команда конфигом. */
    public static boolean isBlocked(AuthManager manager, String rawCommand) {
        if (manager == null) return false;
        List<String> blocked = manager.config().blockedCommands;
        if (blocked == null || blocked.isEmpty()) return false;
        String cmd = rawCommand.trim();
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        int space = cmd.indexOf(' ');
        if (space > 0) cmd = cmd.substring(0, space);
        for (String entry : blocked) {
            if (entry != null && entry.equalsIgnoreCase(cmd)) return true;
        }
        return false;
    }

    public static void register(AuthManager manager) {
        registerPrefix(manager);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            // /register <пароль> <пароль>
            var registerNode = dispatcher.register(CommandManager.literal("register")
                    .then(CommandManager.argument("password", StringArgumentType.word())
                            .then(CommandManager.argument("confirm", StringArgumentType.word())
                                    .executes(ctx -> {
                                        ServerPlayerEntity player = ctx.getSource().getPlayer();
                                        if (player == null) return 0;
                                        manager.tryRegister(player,
                                                StringArgumentType.getString(ctx, "password"),
                                                StringArgumentType.getString(ctx, "confirm"));
                                        return 1;
                                    })))
                    .executes(ctx -> usage(ctx, "/register <пароль> <пароль>")));
            dispatcher.register(CommandManager.literal("reg").redirect(registerNode));

            // /login <пароль>
            var loginNode = dispatcher.register(CommandManager.literal("login")
                    .then(CommandManager.argument("password", StringArgumentType.word())
                            .executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayer();
                                if (player == null) return 0;
                                manager.tryLogin(player, StringArgumentType.getString(ctx, "password"));
                                return 1;
                            }))
                    .executes(ctx -> usage(ctx, "/login <пароль>")));
            dispatcher.register(CommandManager.literal("l").redirect(loginNode));

            // /changepassword <старый> <новый>
            dispatcher.register(CommandManager.literal("changepassword")
                    .then(CommandManager.argument("old", StringArgumentType.word())
                            .then(CommandManager.argument("new", StringArgumentType.word())
                                    .executes(ctx -> {
                                        ServerPlayerEntity player = ctx.getSource().getPlayer();
                                        if (player == null) return 0;
                                        manager.tryChangePassword(player,
                                                StringArgumentType.getString(ctx, "old"),
                                                StringArgumentType.getString(ctx, "new"));
                                        return 1;
                                    })))
                    .executes(ctx -> usage(ctx, "/changepassword <старый> <новый>")));

            // /simpleauth ... (админ, уровень оператора 3)
            dispatcher.register(CommandManager.literal("simpleauth")
                    .requires(source -> source.hasPermissionLevel(3))
                    .then(CommandManager.literal("unregister")
                            .then(CommandManager.argument("name", StringArgumentType.word())
                                    .executes(ctx -> {
                                        String name = StringArgumentType.getString(ctx, "name");
                                        boolean ok = manager.database().unregister(name);
                                        String msg = ok
                                                ? String.format(manager.config().messages.playerUnregistered, name)
                                                : String.format(manager.config().messages.playerNotFound, name);
                                        ctx.getSource().sendFeedback(
                                                () -> Text.literal(msg).formatted(ok ? Formatting.GREEN : Formatting.RED),
                                                true);
                                        return ok ? 1 : 0;
                                    })))
                    .then(CommandManager.literal("reload")
                            .executes(ctx -> {
                                manager.setConfig(Config.load());
                                manager.database().load();
                                ctx.getSource().sendFeedback(
                                        () -> Text.literal(manager.config().messages.configReloaded)
                                                .formatted(Formatting.GREEN),
                                        true);
                                return 1;
                            }))
                    .then(CommandManager.literal("setspawn")
                            .executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayer();
                                if (player == null) return 0;
                                Config.SpawnPoint spawn = manager.config().authSpawn;
                                spawn.enabled = true;
                                spawn.world = player.getWorld().getRegistryKey().getValue().toString();
                                spawn.x = player.getX();
                                spawn.y = player.getY();
                                spawn.z = player.getZ();
                                spawn.yaw = player.getYaw();
                                spawn.pitch = player.getPitch();
                                manager.config().save();
                                ctx.getSource().sendFeedback(
                                        () -> Text.literal(manager.config().messages.authSpawnSet)
                                                .formatted(Formatting.GREEN), true);
                                return 1;
                            }))
                    .then(CommandManager.literal("removespawn")
                            .executes(ctx -> {
                                manager.config().authSpawn.enabled = false;
                                manager.config().save();
                                ctx.getSource().sendFeedback(
                                        () -> Text.literal(manager.config().messages.authSpawnRemoved)
                                                .formatted(Formatting.YELLOW), true);
                                return 1;
                            }))
                    .then(CommandManager.literal("fix")
                            .then(CommandManager.argument("name", StringArgumentType.word())
                                    .executes(ctx -> {
                                        String name = StringArgumentType.getString(ctx, "name");
                                        ServerPlayerEntity target = ctx.getSource().getServer()
                                                .getPlayerManager().getPlayer(name);
                                        if (target == null) {
                                            ctx.getSource().sendFeedback(
                                                    () -> Text.literal(String.format(
                                                            manager.config().messages.playerNotFound, name))
                                                            .formatted(Formatting.RED), false);
                                            return 0;
                                        }
                                        manager.resetToGameMode(target, true);
                                        ctx.getSource().sendFeedback(
                                                () -> Text.literal(String.format(
                                                        manager.config().messages.playerFixed, name))
                                                        .formatted(Formatting.GREEN), true);
                                        return 1;
                                    })))
                    .then(CommandManager.literal("show")
                            .executes(ctx -> {
                                Config.Show show = manager.config().shows != null
                                        && !manager.config().shows.isEmpty()
                                        ? manager.config().shows.get(0)
                                        : new Config.Show();
                                int count = manager.shows().start(ctx.getSource().getServer(), show);
                                ctx.getSource().sendFeedback(
                                        () -> Text.literal(String.format(
                                                manager.config().messages.showStarted, count))
                                                .formatted(Formatting.LIGHT_PURPLE), true);
                                return count;
                            }))
                    .then(CommandManager.literal("info")
                            .executes(ctx -> {
                                int size = manager.database().size();
                                ctx.getSource().sendFeedback(
                                        () -> Text.literal("SimpleAuth: аккаунтов в базе — " + size)
                                                .formatted(Formatting.AQUA),
                                        false);
                                return 1;
                            })));
        });
    }

    private static void registerPrefix(AuthManager manager) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("prefix")
                        .requires(source -> {
                            try {
                                return manager.prefixes().isOwner(source.getPlayer());
                            } catch (Exception e) {
                                return false;
                            }
                        })
                        .then(CommandManager.literal("color")
                                .then(CommandManager.argument("color", StringArgumentType.word())
                                        .executes(ctx -> {
                                            ServerPlayerEntity player = ctx.getSource().getPlayer();
                                            if (player == null) return 0;
                                            String name = player.getGameProfile().getName();
                                            String color = StringArgumentType.getString(ctx, "color");
                                            boolean ok = manager.prefixes().setColor(
                                                    ctx.getSource().getServer(), name, color);
                                            if (!ok) {
                                                feedback(ctx, manager.config().messages.prefixBadColor, Formatting.RED);
                                                return 0;
                                            }
                                            feedback(ctx, String.format(manager.config().messages.prefixSet,
                                                    name, color), Formatting.GREEN);
                                            return 1;
                                        })))
                        .then(CommandManager.literal("off")
                                .executes(ctx -> {
                                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                                    if (player == null) return 0;
                                    String name = player.getGameProfile().getName();
                                    manager.prefixes().clear(ctx.getSource().getServer(), name);
                                    feedback(ctx, String.format(manager.config().messages.prefixCleared, name),
                                            Formatting.YELLOW);
                                    return 1;
                                }))
                        .then(CommandManager.literal("give")
                                .then(CommandManager.argument("name", StringArgumentType.word())
                                        .then(CommandManager.argument("symbol", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    String name = StringArgumentType.getString(ctx, "name");
                                                    String symbol = clean(StringArgumentType.getString(ctx, "symbol"));
                                                    manager.prefixes().set(ctx.getSource().getServer(), name, symbol);
                                                    feedback(ctx, String.format(manager.config().messages.prefixSet,
                                                            name, symbol), Formatting.GREEN);
                                                    return 1;
                                                }))))
                        .then(CommandManager.argument("symbol", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                                    if (player == null) return 0;
                                    String name = player.getGameProfile().getName();
                                    String symbol = clean(StringArgumentType.getString(ctx, "symbol"));
                                    manager.prefixes().set(ctx.getSource().getServer(), name, symbol);
                                    feedback(ctx, String.format(manager.config().messages.prefixSet, name, symbol),
                                            Formatting.GREEN);
                                    return 1;
                                }))
                        .executes(ctx -> {
                            feedback(ctx, manager.config().messages.prefixUsage, Formatting.YELLOW);
                            return 0;
                        })));
    }

    /**
     * Убирает невидимые модификаторы вроде U+FE0E — из-за них майнкрафт
     * рисует квадратик вместо символа.
     */
    private static String clean(String input) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\uFE0E' || c == '\uFE0F' || c == '\u200B' || c == '\u200D') continue;
            out.append(c);
        }
        return out.toString().trim();
    }

    private static void feedback(CommandContext<ServerCommandSource> ctx, String message, Formatting color) {
        ctx.getSource().sendFeedback(() -> Text.literal(message).formatted(color), false);
    }

    private static int usage(CommandContext<ServerCommandSource> ctx, String usage) {
        ctx.getSource().sendFeedback(
                () -> Text.literal("Использование: " + usage).formatted(Formatting.YELLOW), false);
        return 0;
    }
}
