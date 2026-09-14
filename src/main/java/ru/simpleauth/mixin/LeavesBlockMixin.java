package ru.simpleauth.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.LeavesBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.simpleauth.AuthManager;
import ru.simpleauth.SimpleAuth;

/**
 * Пока включён режим без физики, листва не осыпается. Иначе она успевает
 * исчезнуть прямо во время вставки схемы, до того как её закрепят.
 */
@Mixin(LeavesBlock.class)
public abstract class LeavesBlockMixin {

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void simpleauth$keepLeaves(BlockState state, ServerWorld world,
                                       BlockPos pos, Random random, CallbackInfo ci) {
        AuthManager manager = SimpleAuth.manager();
        if (manager != null && manager.isPhysicsDisabled()) {
            ci.cancel();
        }
    }
}
