package com.simpleauth;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class AuthCommands {

    private AuthCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        for (String alias : new String[]{"register", "reg"}) {
            dispatcher.register(CommandManager.literal(alias)
                    .then(CommandManager.argument("password", StringArgumentType.word())
                            .then(CommandManager.argument("confirm", StringArgumentType.word())
                                    .executes(AuthCommands::doRegister))));
        }

        for (String alias : new String[]{"login", "l"}) {
            dispatcher.register(CommandManager.literal(alias)
                    .then(CommandManager.argument("password", StringArgumentType.word())
                            .executes(AuthCommands::doLogin)));
        }

        dispatcher.register(CommandManager.literal("changepassword")
                .then(CommandManager.argument("old", StringArgumentType.word())
                        .then(CommandManager.argument("new", StringArgumentType.word())
                                .executes(AuthCommands::doChangePassword))));

        dispatcher.register(CommandManager.literal("authadmin")
                .requires(source -> source.hasPermissionLevel(3))
                .then(CommandManager.literal("unregister")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                                .executes(AuthCommands::doAdminUnregister)))
                .then(CommandManager.literal("setpassword")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                                .then(CommandManager.argument("password", StringArgumentType.word())
                                        .executes(AuthCommands::doAdminSetPassword)))));
    }

    private static int doRegister(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = playerOf(ctx);
        if (player == null) {
            return 0;
        }
        String name = player.getGameProfile().getName();
        if (SimpleAuth.ACCOUNTS.isRegistered(name)) {
            say(player, "Вы уже зарегистрированы. Используйте /login <пароль>", Formatting.RED);
            return 0;
        }
        String password = StringArgumentType.getString(ctx, "password");
        String confirm = StringArgumentType.getString(ctx, "confirm");
        if (!password.equals(confirm)) {
            say(player, "Пароли не совпадают", Formatting.RED);
            return 0;
        }
        if (password.length() < SimpleAuth.MIN_PASSWORD_LENGTH) {
            say(player, "Пароль должен быть не короче " + SimpleAuth.MIN_PASSWORD_LENGTH + " символов", Formatting.RED);
            return 0;
        }
        SimpleAuth.ACCOUNTS.register(name, password);
        SimpleAuth.unlock(player);
        say(player, "Аккаунт создан. Добро пожаловать!", Formatting.GREEN);
        SimpleAuth.LOGGER.info("Зарегистрирован игрок {}", name);
        return 1;
    }

    private static int doLogin(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = playerOf(ctx);
        if (player == null) {
            return 0;
        }
        String name = player.getGameProfile().getName();
        if (!SimpleAuth.ACCOUNTS.isRegistered(name)) {
            say(player, "Вы ещё не зарегистрированы. Используйте /register <пароль> <пароль>", Formatting.RED);
            return 0;
        }
        if (!SimpleAuth.isLocked(player)) {
            say(player, "Вы уже вошли", Formatting.YELLOW);
            return 0;
        }
        String password = StringArgumentType.getString(ctx, "password");
        if (!SimpleAuth.ACCOUNTS.checkPassword(name, password)) {
            if (!SimpleAuth.failedAttempt(player)) {
                say(player, "Неверный пароль", Formatting.RED);
            }
            return 0;
        }
        SimpleAuth.unlock(player);
        say(player, "Вход выполнен. Приятной игры!", Formatting.GREEN);
        return 1;
    }

    private static int doChangePassword(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = playerOf(ctx);
        if (player == null) {
            return 0;
        }
        if (SimpleAuth.isLocked(player)) {
            say(player, "Сначала войдите в аккаунт", Formatting.RED);
            return 0;
        }
        String name = player.getGameProfile().getName();
        String oldPassword = StringArgumentType.getString(ctx, "old");
        String newPassword = StringArgumentType.getString(ctx, "new");
        if (!SimpleAuth.ACCOUNTS.checkPassword(name, oldPassword)) {
            say(player, "Старый пароль неверный", Formatting.RED);
            return 0;
        }
        if (newPassword.length() < SimpleAuth.MIN_PASSWORD_LENGTH) {
            say(player, "Новый пароль слишком короткий", Formatting.RED);
            return 0;
        }
        SimpleAuth.ACCOUNTS.setPassword(name, newPassword);
        say(player, "Пароль изменён", Formatting.GREEN);
        return 1;
    }

    private static int doAdminUnregister(CommandContext<ServerCommandSource> ctx) {
        String target = StringArgumentType.getString(ctx, "player");
        boolean removed = SimpleAuth.ACCOUNTS.unregister(target);
        ctx.getSource().sendFeedback(() -> Text.literal(removed
                ? "Аккаунт " + target + " удалён"
                : "Аккаунт " + target + " не найден"), true);
        return removed ? 1 : 0;
    }

    private static int doAdminSetPassword(CommandContext<ServerCommandSource> ctx) {
        String target = StringArgumentType.getString(ctx, "player");
        String password = StringArgumentType.getString(ctx, "password");
        SimpleAuth.ACCOUNTS.setPassword(target, password);
        ctx.getSource().sendFeedback(() -> Text.literal("Пароль игрока " + target + " изменён"), true);
        return 1;
    }

    private static ServerPlayerEntity playerOf(CommandContext<ServerCommandSource> ctx) {
        if (ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
            return player;
        }
        ctx.getSource().sendFeedback(() -> Text.literal("Команда доступна только игрокам"), false);
        return null;
    }

    private static void say(ServerPlayerEntity player, String text, Formatting color) {
        player.sendMessage(Text.literal(text).formatted(color), false);
    }
}
