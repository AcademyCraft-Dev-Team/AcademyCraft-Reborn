package org.academy.mixin.common;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.academy.internal.common.structure.space.NativeStructureDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Bounds ordinary Level access without changing host dimensions or intercepting raw chunk writes. */
@Mixin(Level.class)
public abstract class MixinNativeStructureBoundary {
    @Unique
    private boolean academy$isOutsideStructureSpace(BlockPos position) {
        var level = (Level) (Object) this;
        return NativeStructureDimensions.isStorageLevel(level.dimension())
                && !NativeStructureDimensions.isPlotLevel(level.dimension())
                && (!NativeStructureDimensions.contains(position) || level.isOutsideBuildHeight(position));
    }

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"), cancellable = true)
    private void academy$rejectOutsideStructureWrite(BlockPos position, BlockState state,
                                                       int flags, int updateLimit,
                                                       CallbackInfoReturnable<Boolean> callback) {
        if (academy$isOutsideStructureSpace(position)) callback.setReturnValue(false);
    }

    @Inject(method = "getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("HEAD"), cancellable = true)
    private void academy$encloseStructureBlockReads(BlockPos position, CallbackInfoReturnable<BlockState> callback) {
        if (academy$isOutsideStructureSpace(position)) callback.setReturnValue(Blocks.BARRIER.defaultBlockState());
    }

    @Inject(method = "getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;",
            at = @At("HEAD"), cancellable = true)
    private void academy$encloseStructureFluidReads(BlockPos position, CallbackInfoReturnable<FluidState> callback) {
        if (academy$isOutsideStructureSpace(position)) callback.setReturnValue(Fluids.EMPTY.defaultFluidState());
    }

    @Inject(method = "getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
            at = @At("HEAD"), cancellable = true)
    private void academy$encloseStructureBlockEntityReads(BlockPos position,
                                                          CallbackInfoReturnable<BlockEntity> callback) {
        if (academy$isOutsideStructureSpace(position)) callback.setReturnValue(null);
    }
}
