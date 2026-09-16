package ru.simpleauth.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.simpleauth.ServerCore;
import ru.simpleauth.SessionManager;

/**
 * Прямая блокировка рассылки "[Ник: сделал то-то]" операторам и в лог —
 * это надёжнее, чем полагаться на ванильный gamerule logAdminCommands,
 * у которого в некоторых сборках бывают баги (он просто не всегда
 * срабатывает). Здесь перехватываем сам метод, который формирует и
 * рассылает эту строку, и для владельца просто отменяем вызов целиком.
 */
@Mixin(ServerCommandSource.class)
public abstract class ServerCommandSourceMixin {

    @Inject(method = "sendToOps", at = @At("HEAD"), cancellable = true)
    private void simpleauth$sendToOps(Text message, CallbackInfo ci) {
        ServerCommandSource self = (ServerCommandSource) (Object) this;
        Entity entity = self.getEntity();
        if (!(entity instanceof ServerPlayerEntity player)) return;

        SessionManager manager = ServerCore.manager();
        if (manager == null) return;

        String name = player.getGameProfile().getName();
        boolean isOwner = manager.config().owner != null
                && manager.config().owner.equalsIgnoreCase(name);
        if (isOwner && manager.config().ownerHideCommandLogs) {
            ci.cancel();
        }
    }
}
