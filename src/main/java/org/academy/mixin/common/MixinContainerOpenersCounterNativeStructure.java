package org.academy.mixin.common;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.internal.common.structure.space.NativeStructureChestSessions;
import org.academy.internal.common.structure.space.NativeStructureDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/** Adds real host-world viewers only for an exact registered native chest menu. */
@Mixin(ContainerOpenersCounter.class)
public abstract class MixinContainerOpenersCounterNativeStructure {
    @Inject(method = "decrementOpeners", at = @At("HEAD"), cancellable = true)
    private void academy$preventNativeCounterUnderflow(LivingEntity entity, Level level, BlockPos position,
            BlockState state, CallbackInfo callback) {
        // Closing callbacks may recheck the container before vanilla stopOpen executes.
        // A zero count has already been reconciled; decrementing again would become negative.
        if (NativeStructureDimensions.isStorageLevel(level.dimension()) && state.is(Blocks.CHEST)
                && level.hasChunkAt(position) && level.getBlockEntity(position) instanceof ChestBlockEntity chest
                && chest.getClass() == ChestBlockEntity.class
                && ((ContainerOpenersCounter) (Object) this).getOpenerCount() <= 0) callback.cancel();
    }

    @Inject(method = "getEntitiesWithContainerOpen", at = @At("RETURN"), cancellable = true)
    private void academy$includeNativeChestViewers(Level level, BlockPos position,
            CallbackInfoReturnable<List<ContainerUser>> callback) {
        var additional = NativeStructureChestSessions.viewers(level, position);
        if (additional.isEmpty()) return;
        var counter = (ContainerOpenersCounter) (Object) this;
        var result = new ArrayList<>(callback.getReturnValue());
        for (var user : additional) {
            if (user instanceof net.minecraft.world.entity.player.Player player
                    && counter.isOwnContainer(player) && !result.contains(user)) result.add(user);
        }
        callback.setReturnValue(result);
    }
}
