package org.academy.mixin.common;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.internal.common.structure.transaction.PayloadIsolation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Refuse external state writes while a durable transfer owns this exact cell. */
@Mixin(LevelChunk.class)
public abstract class MixinTransferCellIsolation {
    @Inject(method = "setBlockState", at = @At("HEAD"), cancellable = true)
    private void academy$pendingImage(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> result) {
        if (PayloadIsolation.denied(((LevelChunk) (Object) this).getLevel(), pos)) result.setReturnValue(null);
    }
}
