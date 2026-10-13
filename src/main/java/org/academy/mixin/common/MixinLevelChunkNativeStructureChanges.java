package org.academy.mixin.common;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.academy.internal.common.structure.space.NativeStructureChanges;
import org.academy.internal.common.structure.space.NativeStructureDimensions;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Captures ordinary block, scheduled tick and direct chunk mutations in native containers. */
@Mixin(LevelChunk.class)
public abstract class MixinLevelChunkNativeStructureChanges {
    @Shadow @Final private Level level;

    @Inject(method = "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("RETURN"))
    private void academy$trackNativeCell(BlockPos position, BlockState state, int flags,
                                         CallbackInfoReturnable<BlockState> callback) {
        var previous = callback.getReturnValue();
        if (previous != null && previous != state && level instanceof ServerLevel serverLevel) {
            if (NativeStructureDimensions.isStorageLevel(serverLevel.dimension())) {
                NativeStructureChanges.mark(serverLevel, position);
            }
            StructureBackendRouter.onBlockChanged(serverLevel, position);
        }
    }
}
