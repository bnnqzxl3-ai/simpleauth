package ru.simpleauth.mixin;

import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.simpleauth.AuthCommands;
import ru.simpleauth.AuthManager;
import ru.simpleauth.SimpleAuth;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {

    @Shadow
    public ServerPlayerEntity player;

    private boolean simpleauth$blocked() {
        AuthManager manager = SimpleAuth.manager();
        return manager != null && player != null && !manager.isAuthenticated(player);
    }

    @Inject(method = "onCommandExecution", at = @At("HEAD"), cancellable = true)
    private void simpleauth$onCommand(CommandExecutionC2SPacket packet, CallbackInfo ci) {
        if (simpleauth$blocked() && !AuthCommands.isAllowedBeforeLogin(packet.command())) {
            SimpleAuth.manager().notifyMustLogin(player);
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
