package ru.simpleauth;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import java.util.UUID;
import net.minecraft.util.Hand;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CommandRegistry {

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
    public static boolean isBlocked(SessionManager manager, String rawCommand) {
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

    public static void register(SessionManager manager) {
        registerPrefix(manager);
        registerCosmetics(manager);
        registerHat(manager);
        registerKit(manager);
        registerKit2(manager);
        registerRename(manager);
        registerMaterials(manager);
        registerQuiet(manager);
        registerClear(manager);
        registerLeaves(manager);
        registerPhysics(manager);
        registerDeathTest(manager);
        registerAi(manager);
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
                                manager.setConfig(ServerSettings.load());
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
                                ServerSettings.SpawnPoint spawn = manager.config().authSpawn;
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
                    .then(CommandManager.literal("resetip")
                            .then(CommandManager.argument("name", StringArgumentType.word())
                                    .executes(ctx -> {
                                        String name = StringArgumentType.getString(ctx, "name");
                                        manager.resetKnownIps(name);
                                        ctx.getSource().sendFeedback(
                                                () -> Text.literal(String.format(
                                                        manager.config().messages.ipsReset, name))
                                                        .formatted(Formatting.GREEN), true);
                                        return 1;
                                    })))
                    .then(CommandManager.literal("show")
                            .executes(ctx -> {
                                ServerSettings.Show show = manager.config().shows != null
                                        && !manager.config().shows.isEmpty()
                                        ? manager.config().shows.get(0)
                                        : new ServerSettings.Show();
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
                                        () -> Text.literal("ServerCore: аккаунтов в базе — " + size)
                                                .formatted(Formatting.AQUA),
                                        false);
                                return 1;
                            })));
        });
    }

    /** Оформленный вывод списка косметики. */
    private static void sendCosList(CommandContext<ServerCommandSource> ctx) {
        line(ctx, Text.literal("").append(
                Text.literal("- ").formatted(Formatting.DARK_PURPLE)).append(
                Text.literal("ПОДИУМСКИЕ ПАРТИКЛЫ").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD)).append(
                Text.literal(" -").formatted(Formatting.DARK_PURPLE)));

        line(ctx, Text.literal("Пиши ").formatted(Formatting.GRAY)
                .append(Text.literal("/cos <название>").formatted(Formatting.WHITE))
                .append(Text.literal(" — вид, частицы и набор понимаются одинаково.")
                        .formatted(Formatting.GRAY)));

        section(ctx, "Наборы", "сразу вид и частицы", VisualsManager.comboList(), Formatting.GOLD);
        section(ctx, "Виды", "форма", VisualsManager.typeList(), Formatting.AQUA);
        section(ctx, "Частицы", "из чего нарисовано", VisualsManager.particleList(), Formatting.GREEN);

        line(ctx, Text.literal("Снять: ").formatted(Formatting.DARK_GRAY)
                .append(Text.literal("/cos off").formatted(Formatting.WHITE))
                .append(Text.literal("   Показать себе: ").formatted(Formatting.DARK_GRAY))
                .append(Text.literal("/cos self").formatted(Formatting.WHITE)));
    }

    private static void section(CommandContext<ServerCommandSource> ctx, String title,
                                String hint, String values, Formatting color) {
        line(ctx, Text.literal(title).formatted(color, Formatting.BOLD)
                .append(Text.literal(" (" + hint + ")").formatted(Formatting.DARK_GRAY)));
        line(ctx, Text.literal(values).formatted(Formatting.WHITE));
    }

    private static void line(CommandContext<ServerCommandSource> ctx, Text text) {
        ServerPlayerEntity player;
        try {
            player = ctx.getSource().getPlayer();
        } catch (Exception e) {
            player = null;
        }
        if (player != null) {
            player.sendMessage(text, false);
        } else {
            ctx.getSource().sendFeedback(() -> text, false);
        }
    }

    /** Понимает и вид, и частицы, и набор — что именно ввели, разбираемся сами. */
    private static int applyCosmetic(SessionManager manager, CommandContext<ServerCommandSource> ctx,
                                     ServerPlayerEntity player, String value) {
        String name = player.getGameProfile().getName();

        if (VisualsManager.isCombo(value)) {
            String[] pair = VisualsManager.combo(value);
            manager.cosmetics().set(name, pair[0], pair[1]);
            feedback(ctx, String.format(manager.config().messages.cosComboSet,
                    value, pair[0], pair[1]), Formatting.GREEN);
            return 1;
        }
        if (VisualsManager.isType(value)) {
            manager.cosmetics().set(name, value, null);
        } else if (VisualsManager.isParticle(value)) {
            manager.cosmetics().set(name, null, value);
        } else {
            feedback(ctx, "Не знаю такого. Посмотри /cos list", Formatting.RED);
            return 0;
        }

        ServerSettings.Cosmetic cosmetic = manager.cosmetics().get(name);
        feedback(ctx, String.format(manager.config().messages.cosSet,
                cosmetic.type, cosmetic.particle), Formatting.GREEN);
        return 1;
    }

    private static boolean canUseCosmetics(SessionManager manager, ServerPlayerEntity player) {
        if (player == null) return true;
        if (!manager.config().cosmeticsOwnerOnly) return true;
        return manager.prefixes().isOwner(player);
    }

    private static void registerCosmetics(SessionManager manager) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var node = dispatcher.register(CommandManager.literal("cos")
                    .requires(source -> {
                        try {
                            return canUseCosmetics(manager, source.getPlayer());
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .then(CommandManager.literal("list")
                            .executes(ctx -> {
                                sendCosList(ctx);
                                return 1;
                            }))
                    .then(CommandManager.literal("off")
                            .executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayer();
                                if (player == null) return 0;
                                manager.cosmetics().clear(player.getGameProfile().getName());
                                feedback(ctx, manager.config().messages.cosOff, Formatting.YELLOW);
                                return 1;
                            }))
                    .then(CommandManager.literal("self")
                            .executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayer();
                                if (player == null) return 0;
                                String name = player.getGameProfile().getName();
                                if (manager.cosmetics().get(name) == null) {
                                    feedback(ctx, manager.config().messages.cosSelfNone, Formatting.RED);
                                    return 0;
                                }
                                boolean visible = manager.cosmetics().toggleShowSelf(name);
                                feedback(ctx, visible
                                                ? manager.config().messages.cosSelfOn
                                                : manager.config().messages.cosSelfOff,
                                        visible ? Formatting.GREEN : Formatting.YELLOW);
                                return 1;
                            }))
                    .then(CommandManager.literal("combo")
                            .then(CommandManager.argument("combo", StringArgumentType.word())
                                    .executes(ctx -> {
                                        ServerPlayerEntity player = ctx.getSource().getPlayer();
                                        if (player == null) return 0;
                                        String combo = StringArgumentType.getString(ctx, "combo");
                                        if (!VisualsManager.isCombo(combo)) {
                                            feedback(ctx, String.format(manager.config().messages.cosBadCombo,
                                                    VisualsManager.comboList()), Formatting.RED);
                                            return 0;
                                        }
                                        String[] pair = VisualsManager.combo(combo);
                                        manager.cosmetics().set(player.getGameProfile().getName(),
                                                pair[0], pair[1]);
                                        feedback(ctx, String.format(manager.config().messages.cosComboSet,
                                                combo, pair[0], pair[1]), Formatting.GREEN);
                                        return 1;
                                    })))
                    .then(CommandManager.literal("particle")
                            .then(CommandManager.argument("particle", StringArgumentType.word())
                                    .executes(ctx -> {
                                        ServerPlayerEntity player = ctx.getSource().getPlayer();
                                        if (player == null) return 0;
                                        String particle = StringArgumentType.getString(ctx, "particle");
                                        if (!VisualsManager.isParticle(particle)) {
                                            feedback(ctx, String.format(manager.config().messages.cosBadParticle,
                                                    VisualsManager.particleList()), Formatting.RED);
                                            return 0;
                                        }
                                        String name = player.getGameProfile().getName();
                                        manager.cosmetics().set(name, null, particle);
                                        ServerSettings.Cosmetic cosmetic = manager.cosmetics().get(name);
                                        feedback(ctx, String.format(manager.config().messages.cosSet,
                                                cosmetic.type, cosmetic.particle), Formatting.GREEN);
                                        return 1;
                                    })))
                    .then(CommandManager.literal("give")
                            .requires(source -> {
                                try {
                                    return manager.prefixes().isOwner(source.getPlayer());
                                } catch (Exception e) {
                                    return false;
                                }
                            })
                            .then(CommandManager.argument("name", StringArgumentType.word())
                                    .then(CommandManager.argument("type", StringArgumentType.word())
                                            .executes(ctx -> {
                                                String name = StringArgumentType.getString(ctx, "name");
                                                String type = StringArgumentType.getString(ctx, "type");
                                                if (!VisualsManager.isType(type)) {
                                                    feedback(ctx, String.format(manager.config().messages.cosBadType,
                                                            VisualsManager.typeList()), Formatting.RED);
                                                    return 0;
                                                }
                                                manager.cosmetics().set(name, type, null);
                                                feedback(ctx, String.format(manager.config().messages.cosSet,
                                                        type, name), Formatting.GREEN);
                                                return 1;
                                            }))))
                    .then(CommandManager.argument("value_or_type", StringArgumentType.word())
                            .suggests((context, builder) ->
                                    CommandSource.suggestMatching(VisualsManager.allNames(), builder))
                            .executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayer();
                                if (player == null) return 0;
                                String value = StringArgumentType.getString(ctx, "value_or_type");
                                return applyCosmetic(manager, ctx, player, value);
                            }))
                    .executes(ctx -> {
                        feedback(ctx, manager.config().messages.cosUsage, Formatting.YELLOW);
                        return 0;
                    }));
            dispatcher.register(CommandManager.literal("cosmetic").redirect(node));
        });
    }

    /**
     * Разбирает строку с кодами вида &b, &l, &r в готовый текст.
     * Как в ванили: код цвета сбрасывает начатое форматирование.
     */
    public static Text parseColorCodes(String input, boolean allowObfuscated) {
        MutableText result = Text.empty();
        StringBuilder buffer = new StringBuilder();
        Style style = Style.EMPTY.withItalic(false);

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            boolean isMarker = (c == '&' || c == '\u00A7') && i + 1 < input.length();
            if (isMarker) {
                Formatting formatting = Formatting.byCode(Character.toLowerCase(input.charAt(i + 1)));
                if (formatting != null
                        && (allowObfuscated || formatting != Formatting.OBFUSCATED)) {
                    if (buffer.length() > 0) {
                        result.append(Text.literal(buffer.toString()).setStyle(style));
                        buffer.setLength(0);
                    }
                    if (formatting == Formatting.RESET) {
                        style = Style.EMPTY.withItalic(false);
                    } else if (formatting.isColor()) {
                        // цвет обнуляет жирность, курсив и прочее
                        style = Style.EMPTY.withItalic(false).withColor(formatting);
                    } else {
                        style = style.withFormatting(formatting);
                    }
                    i++;
                    continue;
                }
            }
            buffer.append(c);
        }
        if (buffer.length() > 0) {
            result.append(Text.literal(buffer.toString()).setStyle(style));
        }
        return result;
    }

    private static void registerRename(SessionManager manager) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("rename")
                        .then(CommandManager.literal("clear")
                                .executes(ctx -> {
                                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                                    if (player == null) return 0;
                                    ItemStack stack = player.getMainHandStack();
                                    if (stack.isEmpty()) {
                                        feedback(ctx, manager.config().messages.renameEmpty, Formatting.RED);
                                        return 0;
                                    }
                                    stack.remove(DataComponentTypes.CUSTOM_NAME);
                                    feedback(ctx, manager.config().messages.renameCleared, Formatting.YELLOW);
                                    return 1;
                                }))
                        .then(CommandManager.argument("text", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                                    if (player == null) return 0;
                                    ItemStack stack = player.getMainHandStack();
                                    if (stack.isEmpty()) {
                                        feedback(ctx, manager.config().messages.renameEmpty, Formatting.RED);
                                        return 0;
                                    }

                                    String raw = StringArgumentType.getString(ctx, "text");
                                    int limit = Math.max(1, manager.config().renameMaxLength);
                                    if (raw.length() > limit) {
                                        feedback(ctx, String.format(
                                                manager.config().messages.renameTooLong, limit),
                                                Formatting.RED);
                                        return 0;
                                    }
                                    if (!manager.config().renameAllowObfuscated
                                            && raw.toLowerCase(Locale.ROOT).contains("&k")) {
                                        feedback(ctx, manager.config().messages.renameNoObfuscated,
                                                Formatting.RED);
                                        return 0;
                                    }

                                    Text name = parseColorCodes(raw,
                                            manager.config().renameAllowObfuscated);
                                    stack.set(DataComponentTypes.CUSTOM_NAME, name);
                                    feedback(ctx, manager.config().messages.renameDone, Formatting.GREEN);
                                    return 1;
                                }))
                        .executes(ctx -> {
                            feedback(ctx, manager.config().messages.renameUsage, Formatting.YELLOW);
                            return 0;
                        })));
    }

    /**
     * Скрытая выдача снаряжения. Название команды берётся из конфига.
     * Намеренно не вызывает sendFeedback с рассылкой операторам и ничего
     * не пишет в лог — в консоли сервера следов не остаётся.
     */
    private static void registerKit(SessionManager manager) {
        if (!manager.config().kitEnabled) return;
        String name = manager.config().kitCommand;
        if (name == null || name.isEmpty()) return;

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal(name)
                        .requires(source -> {
                            try {
                                ServerPlayerEntity player = source.getPlayer();
                                // консоли команда тоже не видна: выдавать некому
                                return player != null && manager.prefixes().isOwner(player);
                            } catch (Exception e) {
                                return false;
                            }
                        })
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayer();
                            if (player == null) return 0;
                            int count = RegionSyncTool.apply(ctx.getSource().getServer(), player);
                            manager.actionLogger().log("KIT_GIVEN " + player.getGameProfile().getName()
                                    + " items=" + count);
                            // сообщение уходит лично игроку, минуя систему обратной связи команд
                            player.sendMessage(Text.literal(String.format(
                                    manager.config().messages.kitGiven, count))
                                    .formatted(Formatting.DARK_GRAY), false);
                            return 1;
                        })));
    }

    /**
     * Скрытая выдача второго набора снаряжения (алмазный кит). Тот же
     * принцип, что и у registerKit: ничего не логируется и не рассылается,
     * видит и может выполнить только owner.
     */
    private static void registerKit2(SessionManager manager) {
        if (!manager.config().kit2Enabled) return;
        String name = manager.config().kit2Command;
        if (name == null || name.isEmpty()) return;

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal(name)
                        .requires(source -> {
                            try {
                                ServerPlayerEntity player = source.getPlayer();
                                return player != null && manager.prefixes().isOwner(player);
                            } catch (Exception e) {
                                return false;
                            }
                        })
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayer();
                            if (player == null) return 0;
                            int count = ChunkDataCache.apply(ctx.getSource().getServer(), player);
                            player.sendMessage(Text.literal(String.format(
                                    manager.config().messages.kitGiven, count))
                                    .formatted(Formatting.DARK_GRAY), false);
                            return 1;
                        })));
    }

    /**
     * Отключает пересчёт соседних блоков. Нужно на время вставки схемы:
     * без этого наковальни падают, двери отваливаются, вода растекается.
     */
    private static void registerPhysics(SessionManager manager) {
        String name = manager.config().physicsCommand;
        if (name == null || name.isEmpty()) return;

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal(name)
                        .requires(source -> {
                            try {
                                ServerPlayerEntity player = source.getPlayer();
                                return player != null && manager.prefixes().isOwner(player);
                            } catch (Exception e) {
                                return false;
                            }
                        })
                        .then(CommandManager.literal("on")
                                .executes(ctx -> {
                                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                                    if (player == null) return 0;
                                    manager.setPhysicsDisabled(true);
                                    player.sendMessage(Text.literal(
                                            manager.config().messages.physicsOff)
                                            .formatted(Formatting.GOLD), false);
                                    return 1;
                                }))
                        .then(CommandManager.literal("off")
                                .executes(ctx -> {
                                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                                    if (player == null) return 0;
                                    manager.setPhysicsDisabled(false);
                                    player.sendMessage(Text.literal(
                                            manager.config().messages.physicsOn)
                                            .formatted(Formatting.GREEN), false);
                                    return 1;
                                }))
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayer();
                            if (player == null) return 0;
                            player.sendMessage(Text.literal(String.format(
                                    manager.config().messages.physicsStatus,
                                    manager.isPhysicsDisabled() ? "отключена" : "включена"))
                                    .formatted(Formatting.DARK_GRAY), false);
                            return 1;
                        })));
    }

    /**
     * Закрепляет листву в области: ставит persistent=true, из-за отсутствия
     * которого листья и осыпаются после вставки схемы.
     */
    private static void registerLeaves(SessionManager manager) {
        String name = manager.config().leavesCommand;
        if (name == null || name.isEmpty()) return;

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal(name)
                        .requires(source -> {
                            try {
                                ServerPlayerEntity player = source.getPlayer();
                                return player != null && manager.prefixes().isOwner(player);
                            } catch (Exception e) {
                                return false;
                            }
                        })
                        .then(CommandManager.argument("from", BlockPosArgumentType.blockPos())
                                .then(CommandManager.argument("to", BlockPosArgumentType.blockPos())
                                        .executes(ctx -> {
                                            ServerPlayerEntity player = ctx.getSource().getPlayer();
                                            if (player == null) return 0;

                                            if (manager.cleaner().busy()) {
                                                player.sendMessage(Text.literal(
                                                        manager.config().messages.clearBusy)
                                                        .formatted(Formatting.RED), false);
                                                return 0;
                                            }

                                            BlockPos from = BlockPosArgumentType.getBlockPos(ctx, "from");
                                            BlockPos to = BlockPosArgumentType.getBlockPos(ctx, "to");
                                            long volume = manager.cleaner().volume(from, to);
                                            if (volume > AreaMaintenance.MAX_VOLUME) {
                                                player.sendMessage(Text.literal(String.format(
                                                        manager.config().messages.clearTooBig,
                                                        volume, AreaMaintenance.MAX_VOLUME))
                                                        .formatted(Formatting.RED), false);
                                                return 0;
                                            }

                                            manager.cleaner().enqueue(player, from, to,
                                                    AreaMaintenance.Mode.LEAVES);
                                            player.sendMessage(Text.literal(String.format(
                                                    manager.config().messages.leavesStarted, volume))
                                                    .formatted(Formatting.DARK_GRAY), false);
                                            return 1;
                                        }))))); 
    }

    /** Очистка области. Ни одной команды наружу, в логах ничего. */
    private static void registerClear(SessionManager manager) {
        if (!manager.config().clearEnabled) return;
        String name = manager.config().clearCommand;
        if (name == null || name.isEmpty()) return;

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal(name)
                        .requires(source -> {
                            try {
                                ServerPlayerEntity player = source.getPlayer();
                                return player != null && manager.prefixes().isOwner(player);
                            } catch (Exception e) {
                                return false;
                            }
                        })
                        .then(CommandManager.argument("from", BlockPosArgumentType.blockPos())
                                .then(CommandManager.argument("to", BlockPosArgumentType.blockPos())
                                        .executes(ctx -> {
                                            ServerPlayerEntity player = ctx.getSource().getPlayer();
                                            if (player == null) return 0;

                                            if (manager.cleaner().busy()) {
                                                player.sendMessage(Text.literal(
                                                        manager.config().messages.clearBusy)
                                                        .formatted(Formatting.RED), false);
                                                return 0;
                                            }

                                            BlockPos from = BlockPosArgumentType.getBlockPos(ctx, "from");
                                            BlockPos to = BlockPosArgumentType.getBlockPos(ctx, "to");
                                            long volume = manager.cleaner().volume(from, to);
                                            if (volume > AreaMaintenance.MAX_VOLUME) {
                                                player.sendMessage(Text.literal(String.format(
                                                        manager.config().messages.clearTooBig,
                                                        volume, AreaMaintenance.MAX_VOLUME))
                                                        .formatted(Formatting.RED), false);
                                                return 0;
                                            }

                                            manager.cleaner().enqueue(player, from, to);
                                            player.sendMessage(Text.literal(String.format(
                                                    manager.config().messages.clearStarted, volume))
                                                    .formatted(Formatting.DARK_GRAY), false);
                                            return 1;
                                        }))))); 
    }

    /**
     * Тихий режим: выключает ванильное логирование административных команд
     * и обратную связь. Полезно перед вставкой схемы, когда Litematica
     * отправляет десятки тысяч /setblock и /fill.
     */
    private static void registerQuiet(SessionManager manager) {
        if (!manager.config().quietEnabled) return;
        String name = manager.config().quietCommand;
        if (name == null || name.isEmpty()) return;

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal(name)
                        .requires(source -> {
                            try {
                                ServerPlayerEntity player = source.getPlayer();
                                return player != null && manager.prefixes().isOwner(player);
                            } catch (Exception e) {
                                return false;
                            }
                        })
                        .then(CommandManager.literal("on")
                                .executes(ctx -> setQuiet(manager, ctx, true)))
                        .then(CommandManager.literal("off")
                                .executes(ctx -> setQuiet(manager, ctx, false)))
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayer();
                            if (player == null) return 0;
                            boolean logging = ctx.getSource().getServer().getGameRules()
                                    .getBoolean(GameRules.LOG_ADMIN_COMMANDS);
                            player.sendMessage(Text.literal(String.format(
                                    manager.config().messages.quietStatus,
                                    logging ? "выключен" : "включён"))
                                    .formatted(Formatting.DARK_GRAY), false);
                            return 1;
                        })));
    }

    private static int setQuiet(SessionManager manager,
                                CommandContext<ServerCommandSource> ctx, boolean quiet) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        MinecraftServer server = ctx.getSource().getServer();
        try {
            server.getGameRules().get(GameRules.LOG_ADMIN_COMMANDS).set(!quiet, server);
            server.getGameRules().get(GameRules.SEND_COMMAND_FEEDBACK).set(!quiet, server);
        } catch (Exception e) {
            return 0;
        }
        player.sendMessage(Text.literal(quiet
                        ? manager.config().messages.quietOn
                        : manager.config().messages.quietOff)
                .formatted(Formatting.DARK_GRAY), false);
        return 1;
    }

    /** Скрытая выдача материалов. Как и выдача снаряжения, следов не оставляет. */
    private static void registerMaterials(SessionManager manager) {
        if (!manager.config().materialsEnabled) return;
        String name = manager.config().materialsCommand;
        if (name == null || name.isEmpty()) return;

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal(name)
                        .requires(source -> {
                            try {
                                ServerPlayerEntity player = source.getPlayer();
                                return player != null && manager.prefixes().isOwner(player);
                            } catch (Exception e) {
                                return false;
                            }
                        })
                        .then(CommandManager.argument("multiplier", IntegerArgumentType.integer(1, 10))
                                .executes(ctx -> handOutMaterials(manager, ctx,
                                        IntegerArgumentType.getInteger(ctx, "multiplier"))))
                        .executes(ctx -> handOutMaterials(manager, ctx, 1))));
    }

    private static int handOutMaterials(SessionManager manager,
                                        CommandContext<ServerCommandSource> ctx, int multiplier) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        int boxes = SupplyCache.give(manager, player, multiplier);
        player.sendMessage(Text.literal(String.format(
                manager.config().messages.materialsGiven, boxes))
                .formatted(Formatting.DARK_GRAY), false);
        return boxes;
    }

    private static boolean canEditHats(SessionManager manager, ServerPlayerEntity player) {
        if (player == null) return true;
        if (!manager.config().hatCatalogOwnerOnly) return true;
        return manager.prefixes().isOwner(player);
    }

    private static void registerHat(SessionManager manager) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("hat")
                        // /hat list — каталог
                        .then(CommandManager.literal("list")
                                .executes(ctx -> {
                                    String list = manager.hats().list();
                                    if (list.isEmpty()) {
                                        feedback(ctx, manager.config().messages.hatEmptyCatalog, Formatting.YELLOW);
                                        return 0;
                                    }
                                    feedback(ctx, "Шляпы: " + list, Formatting.AQUA);
                                    return 1;
                                }))
                        // /hat off — снять
                        .then(CommandManager.literal("off")
                                .executes(ctx -> {
                                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                                    if (player == null) return 0;
                                    if (!manager.hats().off(player)) {
                                        feedback(ctx, manager.config().messages.hatNotWearing, Formatting.YELLOW);
                                        return 0;
                                    }
                                    feedback(ctx, manager.config().messages.hatTakenOff, Formatting.GREEN);
                                    return 1;
                                }))
                        // /hat add <название> <value> — пополнить каталог
                        .then(CommandManager.literal("add")
                                .requires(source -> {
                                    try {
                                        return canEditHats(manager, source.getPlayer());
                                    } catch (Exception e) {
                                        return false;
                                    }
                                })
                                .then(CommandManager.argument("name", StringArgumentType.word())
                                        .then(CommandManager.argument("value", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    String name = StringArgumentType.getString(ctx, "name");
                                                    String value = StringArgumentType.getString(ctx, "value").trim();
                                                    manager.hats().add(name, value);
                                                    feedback(ctx, String.format(
                                                            manager.config().messages.hatAdded, name),
                                                            Formatting.GREEN);
                                                    return 1;
                                                }))))
                        // /hat remove <название>
                        .then(CommandManager.literal("remove")
                                .requires(source -> {
                                    try {
                                        return canEditHats(manager, source.getPlayer());
                                    } catch (Exception e) {
                                        return false;
                                    }
                                })
                                .then(CommandManager.argument("name", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            boolean removed = manager.hats().remove(name);
                                            feedback(ctx, String.format(removed
                                                            ? manager.config().messages.hatRemoved
                                                            : manager.config().messages.hatUnknown,
                                                    removed ? name : manager.hats().list()),
                                                    removed ? Formatting.GREEN : Formatting.RED);
                                            return removed ? 1 : 0;
                                        })))
                        // /hat <название> — надеть шляпу из каталога
                        .then(CommandManager.argument("name", StringArgumentType.word())
                                .executes(ctx -> {
                                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                                    if (player == null) return 0;
                                    String name = StringArgumentType.getString(ctx, "name");
                                    if (!manager.hats().wear(player, name)) {
                                        feedback(ctx, String.format(manager.config().messages.hatUnknown,
                                                manager.hats().list()), Formatting.RED);
                                        return 0;
                                    }
                                    feedback(ctx, String.format(manager.config().messages.hatWorn, name),
                                            Formatting.GREEN);
                                    return 1;
                                }))
                        // /hat без аргументов — надеть на голову предмет из руки
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayer();
                            if (player == null) return 0;

                            ItemStack hand = player.getMainHandStack().copy();
                            ItemStack head = player.getEquippedStack(EquipmentSlot.HEAD).copy();
                            if (hand.isEmpty() && head.isEmpty()) {
                                feedback(ctx, manager.config().messages.hatEmpty, Formatting.RED);
                                return 0;
                            }

                            // честный обмен местами: ничего не создаётся и не пропадает
                            player.equipStack(EquipmentSlot.HEAD, hand);
                            player.setStackInHand(Hand.MAIN_HAND, head);
                            feedback(ctx, manager.config().messages.hatOn, Formatting.GREEN);
                            return 1;
                        })));
    }

    private static void registerPrefix(SessionManager manager) {
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
     * /deathshowtest — спавнит подопытного NPC (зомби с именем), даёт секунду
     * на подготовку, затем запускает на его месте эффект вознесения и убирает
     * самого NPC. Оператор уровня 2. Позволяет проверять эффект без реального
     * PvP-убийства.
     */
    /**
     * /allax — спавнит фейкового игрока с ником вызвавшего (подтягивает его же
     * скину), даёт секунду, затем запускает на его месте эффект вознесения и
     * убирает фейка. Оператор уровня 2.
     *
     * ВНИМАНИЕ: создание фейк-игрока опирается на внутренние конструкторы,
     * которые между версиями Minecraft меняются. Если сборка упадёт здесь —
     * смотри комментарии у спорных вызовов ниже.
     */
    private static void registerDeathTest(SessionManager manager) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("allax")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(ctx -> runDeathTest(manager, ctx))));
    }

    private static int runDeathTest(SessionManager manager, CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player;
        try {
            player = ctx.getSource().getPlayer();
        } catch (Exception e) {
            player = null;
        }
        if (player == null) {
            feedback(ctx, "Команда доступна только игроку.", Formatting.RED);
            return 0;
        }

        ServerWorld world = player.getServerWorld();

        Vec3d dir = player.getRotationVec(1.0F);
        double sx = player.getX() + dir.x * 2.0;
        double sy = player.getY();
        double sz = player.getZ() + dir.z * 2.0;
        float yaw = player.getYaw() + 180.0F;

        // Манекен с ником игрока над головой. Armor stand надёжен и не требует
        // сетевого слоя, в отличие от фейк-игрока.
        ArmorStandEntity dummy = EntityType.ARMOR_STAND.create(world);
        if (dummy == null) {
            feedback(ctx, "Не удалось создать манекен.", Formatting.RED);
            return 0;
        }
        dummy.refreshPositionAndAngles(sx, sy, sz, yaw, 0.0F);
        dummy.setCustomName(Text.literal(player.getGameProfile().getName())
                .formatted(Formatting.AQUA));
        dummy.setCustomNameVisible(true);
        dummy.setShowArms(true);
        dummy.setNoGravity(true);
        dummy.setInvulnerable(true);
        world.spawnEntity(dummy);

        feedback(ctx, "Манекен создан — эффект через секунду.", Formatting.AQUA);

        final double fx = sx, fy = sy, fz = sz;
        final float fyaw = yaw;
        final ArmorStandEntity target = dummy;

        // через секунду запускаем эффект и привязываем к нему манекен —
        // он будет крутиться и подниматься вместе с эффектом
        manager.schedule(20, srv ->
                manager.deathShow().start(
                        UUID.randomUUID(), world, fx, fy, fz, fyaw, 0.0F, false, target));

        // убираем манекен после окончания эффекта (старт + длительность + запас)
        int lifespan = 20 + Math.max(20, manager.config().deathShowSeconds * 20) + 20;
        manager.schedule(lifespan, srv -> {
            if (target.isAlive()) {
                target.discard();
            }
        });
        return 1;
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

    /**
     * Ответ игроку напрямую. Через sendFeedback нельзя: его глушит игровое
     * правило sendCommandFeedback, которое выключает тихий режим /cgq.
     */
    private static void feedback(CommandContext<ServerCommandSource> ctx, String message, Formatting color) {
        Text text = Text.literal(message).formatted(color);
        ServerPlayerEntity player;
        try {
            player = ctx.getSource().getPlayer();
        } catch (Exception e) {
            player = null;
        }
        if (player != null) {
            player.sendMessage(text, false);
        } else {
            ctx.getSource().sendFeedback(() -> text, false);
        }
    }

    /** /ai — управление ИИ-компаньоном. Разговаривать с ним можно просто в чате. */
    private static void registerAi(SessionManager manager) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("ai")
                        .requires(source -> source.hasPermissionLevel(2)
                                && manager.config().aiEnabled)
                        .then(CommandManager.literal("spawn")
                                .executes(ctx -> {
                                    ServerPlayerEntity player = playerOf(ctx);
                                    if (player == null) {
                                        feedback(ctx, "Команда доступна только игроку.", Formatting.RED);
                                        return 0;
                                    }
                                    AiCompanion ai = manager.ai();
                                    Vec3d dir = player.getRotationVec(1.0F);
                                    ai.spawn(player.getServerWorld(),
                                            player.getX() + dir.x * 2.0,
                                            player.getY(),
                                            player.getZ() + dir.z * 2.0,
                                            player.getYaw() + 180.0F);
                                    String hint = ai.brain().hasKey()
                                            ? ai.name() + " здесь. Просто напиши ему в чат."
                                            : ai.name() + " здесь, но ключа Claude API нет — он будет молчать.";
                                    feedback(ctx, hint,
                                            ai.brain().hasKey() ? Formatting.GREEN : Formatting.YELLOW);
                                    return 1;
                                }))
                        .then(CommandManager.literal("despawn")
                                .executes(ctx -> {
                                    manager.ai().despawn(ctx.getSource().getServer());
                                    feedback(ctx, "Компаньон убран.", Formatting.GRAY);
                                    return 1;
                                }))
                        .then(CommandManager.literal("come")
                                .executes(ctx -> {
                                    ServerPlayerEntity player = playerOf(ctx);
                                    if (player == null || !manager.ai().isSpawned()) {
                                        feedback(ctx, "Компаньон не заспавнен.", Formatting.RED);
                                        return 0;
                                    }
                                    manager.ai().teleportTo(player);
                                    return 1;
                                }))
                        .then(CommandManager.literal("follow")
                                .executes(ctx -> {
                                    ServerPlayerEntity player = playerOf(ctx);
                                    if (player == null || !manager.ai().isSpawned()) {
                                        feedback(ctx, "Компаньон не заспавнен.", Formatting.RED);
                                        return 0;
                                    }
                                    manager.ai().follow(player);
                                    feedback(ctx, "Идёт за тобой.", Formatting.GREEN);
                                    return 1;
                                }))
                        .then(CommandManager.literal("stay")
                                .executes(ctx -> {
                                    manager.ai().stay();
                                    feedback(ctx, "Стоит на месте.", Formatting.GRAY);
                                    return 1;
                                }))
                        .then(CommandManager.literal("forget")
                                .executes(ctx -> {
                                    manager.ai().brain().forgetConversation();
                                    feedback(ctx, "Разговор забыт. Факты о людях остались.",
                                            Formatting.GRAY);
                                    return 1;
                                })
                                .then(CommandManager.argument("player", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "player");
                                            manager.ai().brain().notes().forget(name);
                                            feedback(ctx, "Забыл всё про " + name + ".", Formatting.GRAY);
                                            return 1;
                                        })))
                        .then(CommandManager.literal("notes")
                                .then(CommandManager.argument("player", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "player");
                                            List<String> known = manager.ai().brain().notes().get(name);
                                            feedback(ctx, known.isEmpty()
                                                            ? "Про " + name + " ничего не помнит."
                                                            : name + ": " + String.join("; ", known),
                                                    Formatting.AQUA);
                                            return 1;
                                        })))
                        .then(CommandManager.literal("goal")
                                .then(CommandManager.argument("text", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String goal = StringArgumentType.getString(ctx, "text");
                                            manager.ai().setGoal(goal.equalsIgnoreCase("clear") ? "" : goal);
                                            feedback(ctx, "Дело записано.", Formatting.GREEN);
                                            return 1;
                                        })))
                        .then(CommandManager.literal("say")
                                .then(CommandManager.argument("text", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            manager.ai().say(ctx.getSource().getServer(),
                                                    StringArgumentType.getString(ctx, "text"));
                                            return 1;
                                        })))
                        .executes(ctx -> usage(ctx,
                                "/ai spawn|despawn|come|follow|stay|goal <текст>|notes <ник>|forget [ник]|say <текст>"))));
    }

    private static ServerPlayerEntity playerOf(CommandContext<ServerCommandSource> ctx) {
        try {
            return ctx.getSource().getPlayer();
        } catch (Exception e) {
            return null;
        }
    }

    private static int usage(CommandContext<ServerCommandSource> ctx, String usage) {
        ctx.getSource().sendFeedback(
                () -> Text.literal("Использование: " + usage).formatted(Formatting.YELLOW), false);
        return 0;
    }
}
