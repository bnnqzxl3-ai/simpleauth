package ru.simpleauth;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
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
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleAuth implements DedicatedServerModInitializer {

    public static final String MOD_ID = "simpleauth";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static AuthManager manager;

    public static AuthManager manager() {
        return manager;
    }

    @Override
    public void onInitializeServer() {
        Config config = Config.load();
        PlayerDatabase database = new PlayerDatabase();
        manager = new AuthManager(database, config);

        AuthCommands.register(manager);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                manager.onJoin(handler.player));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                manager.onDisconnect(handler.player));

        ServerTickEvents.END_SERVER_TICK.register(server -> manager.tick(server));

        // --- запрет любых действий с миром до входа ---

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, entity) -> !blocked(player));

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) ->
                blocked(player) ? ActionResult.FAIL : ActionResult.PASS);

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
                blocked(player) ? ActionResult.FAIL : ActionResult.PASS);

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) ->
                blocked(player) ? ActionResult.FAIL : ActionResult.PASS);

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
                blocked(player) ? ActionResult.FAIL : ActionResult.PASS);

        UseItemCallback.EVENT.register((player, world, hand) ->
                blocked(player)
                        ? TypedActionResult.fail(player.getStackInHand(hand))
                        : TypedActionResult.pass(player.getStackInHand(hand)));

        // --- неуязвимость до входа ---
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity serverPlayer) {
                if (manager.config().invulnerableUntilLogin && !manager.isAuthenticated(serverPlayer)) {
                    return false;
                }
                // короткая передышка сразу после входа, пока идёт проверка
                if (manager.config().verifyInvulnerable && manager.isVerifying(serverPlayer)) {
                    return false;
                }
                // во время шоу игрок обездвижен, поэтому урон ему не проходит
                if (manager.shows().isActive(serverPlayer)) {
                    return false;
                }
            }
            return true;
        });

        // --- чат до входа ---
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            if (manager.config().muteChatUntilLogin && !manager.isAuthenticated(sender)) {
                manager.notifyMustLogin(sender);
                return false;
            }
            return true;
        });

        // вознесение при смерти
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (!(entity instanceof ServerPlayerEntity victim)) return;
            ServerPlayerEntity killer = null;
            if (damageSource.getAttacker() instanceof ServerPlayerEntity attacker
                    && attacker != victim) {
                killer = attacker;
            }
            manager.deathShow().onDeath(victim, killer);
        });

        LOGGER.info("[SimpleAuth] Мод загружен. Аккаунтов в базе: {}", database.size());
    }

    private static boolean blocked(PlayerEntity player) {
        return player instanceof ServerPlayerEntity serverPlayer
                && manager != null
                && !manager.isAuthenticated(serverPlayer);
    }
}
