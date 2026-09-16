package ru.simpleauth.mixin;

import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
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
            }
            return;
        }

        if (CommandRegistry.isBlocked(manager, packet.command())) {
            player.sendMessage(Text.literal(manager.config().messages.commandBlocked)
                    .formatted(Formatting.RED), false);
            ci.cancel();
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
