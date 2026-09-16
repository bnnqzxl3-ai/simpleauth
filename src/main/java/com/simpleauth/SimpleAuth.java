package com.simpleauth;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SimpleAuth implements ModInitializer {

    public static final String MOD_ID = "simpleauth";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Через сколько секунд кикать игрока, который не вошёл. */
    public static final int KICK_SECONDS = 90;
    /** Минимальная длина пароля. */
    public static final int MIN_PASSWORD_LENGTH = 4;
    /** Сколько неверных попыток пароля до кика. */
    public static final int MAX_ATTEMPTS = 5;

    public static final AuthManager ACCOUNTS = new AuthManager();

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private static final class Pending {
        final Vec3d pos;
        final float yaw;
        final float pitch;
        final boolean wasInvulnerable;
        int ticks = 0;
        int attempts = 0;

        Pending(ServerPlayerEntity player) {
            this.pos = player.getPos();
            this.yaw = player.getYaw();
            this.pitch = player.getPitch();
            this.wasInvulnerable = player.isInvulnerable();
        }
    }

    @Override
    public void onInitialize() {
        ACCOUNTS.load();

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> AuthCommands.register(dispatcher));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> lock(handler.player));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Pending pending = PENDING.remove(handler.player.getUuid());
            if (pending != null) {
                handler.player.setInvulnerable(pending.wasInvulnerable);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ACCOUNTS.save());

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (PENDING.isEmpty()) {
                return;
            }
            for (ServerPlayerEntity player : new ArrayList<>(server.getPlayerManager().getPlayerList())) {
                Pending pending = PENDING.get(player.getUuid());
                if (pending == null) {
                    continue;
                }
                pending.ticks++;

                if (player.squaredDistanceTo(pending.pos) > 0.35D) {
                    player.networkHandler.requestTeleport(pending.pos.x, pending.pos.y, pending.pos.z,
                            pending.yaw, pending.pitch);
                }

                if (pending.ticks % 100 == 0) {
                    player.sendMessage(prompt(player), false);
                }

                if (pending.ticks > KICK_SECONDS * 20) {
                    kick(player, "Вы не вошли в аккаунт за " + KICK_SECONDS + " секунд");
                }
            }
        });

        // Чат заблокирован до входа
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            if (isLocked(sender)) {
                sender.sendMessage(prompt(sender), false);
                return false;
            }
            return true;
        });

        // Любое взаимодействие с миром заблокировано до входа
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) ->
                blocked(player) ? ActionResult.FAIL : ActionResult.PASS);

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) ->
                blocked(player) ? ActionResult.FAIL : ActionResult.PASS);

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
                blocked(player) ? ActionResult.FAIL : ActionResult.PASS);

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
                blocked(player) ? ActionResult.FAIL : ActionResult.PASS);

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (blocked(player)) {
                return TypedActionResult.fail(player.getStackInHand(hand));
            }
            return TypedActionResult.pass(ItemStack.EMPTY);
        });

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> !blocked(player));

        LOGGER.info("SimpleAuth запущен");
    }

    private static boolean blocked(PlayerEntity player) {
        return player instanceof ServerPlayerEntity serverPlayer && isLocked(serverPlayer);
    }

    public static boolean isLocked(ServerPlayerEntity player) {
        return PENDING.containsKey(player.getUuid());
    }

    private static void lock(ServerPlayerEntity player) {
        PENDING.put(player.getUuid(), new Pending(player));
        player.setInvulnerable(true);
        player.sendMessage(Text.literal("=== Подиумский сервер ===").formatted(Formatting.GOLD), false);
        player.sendMessage(prompt(player), false);
    }

    public static void unlock(ServerPlayerEntity player) {
        Pending pending = PENDING.remove(player.getUuid());
        if (pending != null) {
            player.setInvulnerable(pending.wasInvulnerable);
        }
    }

    /** Возвращает true, если после неудачной попытки игрока кикнули. */
    public static boolean failedAttempt(ServerPlayerEntity player) {
        Pending pending = PENDING.get(player.getUuid());
        if (pending == null) {
            return false;
        }
        pending.attempts++;
        if (pending.attempts >= MAX_ATTEMPTS) {
            kick(player, "Слишком много неверных попыток ввода пароля");
            return true;
        }
        return false;
    }

    private static void kick(ServerPlayerEntity player, String reason) {
        Pending pending = PENDING.remove(player.getUuid());
        if (pending != null) {
            player.setInvulnerable(pending.wasInvulnerable);
        }
        player.networkHandler.disconnect(Text.literal(reason).formatted(Formatting.RED));
    }

    public static Text prompt(ServerPlayerEntity player) {
        String name = player.getGameProfile().getName();
        if (ACCOUNTS.isRegistered(name)) {
            return Text.literal("Войдите: /login <пароль>").formatted(Formatting.YELLOW);
        }
        return Text.literal("Зарегистрируйтесь: /register <пароль> <пароль ещё раз>")
                .formatted(Formatting.YELLOW);
    }
}
