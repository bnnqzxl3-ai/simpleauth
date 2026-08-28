package ru.simpleauth;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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

    public static void register(AuthManager manager) {
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

    private static int usage(CommandContext<ServerCommandSource> ctx, String usage) {
        ctx.getSource().sendFeedback(
                () -> Text.literal("Использование: " + usage).formatted(Formatting.YELLOW), false);
        return 0;
    }
}
