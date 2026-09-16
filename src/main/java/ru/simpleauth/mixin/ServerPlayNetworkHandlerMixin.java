package ru.simpleauth.mixin;

import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.simpleauth.CommandRegistry;
import ru.simpleauth.SessionManager;
import ru.simpleauth.ServerCore;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {

    @Shadow
    public ServerPlayerEntity player;

    /** Имя логгера этого же класса — тот самый, что пишет "issued server command: ...". */
    private static final String NETHANDLER_LOGGER_NAME =
            LogManager.getLogger(ServerPlayNetworkHandler.class).getName();

    /**
     * Приглушает лог-строку "<ник> issued server command: ..." на один тик
     * вперёд (она пишется синхронно в этом же тике сразу после нашего
     * инъекта, а обработка команд идёт последовательно на главном потоке —
     * поэтому окно безопасно и не заглушит чужие команды). Возврат через
     * schedule(), а не через второй @Inject на выходе — так уровень логгера
     * гарантированно восстановится, даже если внутри команды что-то бросит
     * исключение.
     */
    private static void simpleauth$muteNetHandlerLog(SessionManager manager) {
        Configurator.setLevel(NETHANDLER_LOGGER_NAME, Level.OFF);
        manager.schedule(1, server -> Configurator.setLevel(NETHANDLER_LOGGER_NAME, Level.INFO));
    }

    private boolean simpleauth$blocked() {
        SessionManager manager = ServerCore.manager();
        return manager != null && player != null && !manager.isAuthenticated(player);
    }

    @Inject(method = "onCommandExecution", at = @At("HEAD"), cancellable = true)
    private void simpleauth$onCommand(CommandExecutionC2SPacket packet, CallbackInfo ci) {
        SessionManager manager = ServerCore.manager();
        if (manager == null || player == null) return;

        if (simpleauth$blocked()) {
            if (!CommandRegistry.isAllowedBeforeLogin(packet.command())) {
                manager.notifyMustLogin(player);
                ci.cancel();
                return;
            }
            // /login и /register разрешены до входа — их пароль-аргумент не
            // должен светиться в консоли в открытом виде, поэтому глушим лог
            // для ЛЮБОГО игрока на этой команде, а не только для владельца.
            simpleauth$muteNetHandlerLog(manager);
            return;
        }

        if (CommandRegistry.isBlocked(manager, packet.command())) {
            player.sendMessage(Text.literal(manager.config().messages.commandBlocked)
                    .formatted(Formatting.RED), false);
            ci.cancel();
            return;
        }

        String name = player.getGameProfile().getName();
        boolean isOwner = manager.config().owner != null && manager.config().owner.equalsIgnoreCase(name);
        if (isOwner && manager.config().ownerHideCommandLogs) {
            simpleauth$muteNetHandlerLog(manager);
        }
    }

    @Inject(method = "onPlayerAction", at = @At("HEAD"), cancellable = true)
    private void simpleauth$onPlayerAction(PlayerActionC2SPacket packet, CallbackInfo ci) {
        if (simpleauth$blocked()) {
            ci.cancel();
        }
    }

    @Inject(method = "onClickSlot", at = @At("HEAD"), cancellable = true)
    private void simpleauth$onClickSlot(ClickSlotC2SPacket packet, CallbackInfo ci) {
        if (simpleauth$blocked()) {
            ci.cancel();
        }
    }
}
