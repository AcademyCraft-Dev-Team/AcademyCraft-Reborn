package org.academy.mixin.common;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.internal.common.ability.mentalout.control.GroupControlRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Successful world mutations invalidate only work groups indexed in the affected chunks. */
@Mixin(Level.class)
public abstract class MixinLevelWorkTargets {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("RETURN"))
    private void academy$invalidateWorkTargets(BlockPos pos, BlockState state, int flags, int recursion,
                                               CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ() && (Object) this instanceof ServerLevel level) GroupControlRuntime.blockChanged(level, pos);
    }
}
