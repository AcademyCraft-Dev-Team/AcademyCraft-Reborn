package org.academy.mixin.common;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.internal.common.structure.space.NativeStructureBreakDrops;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

/** The ordinary path is untouched; only an explicit mining scope avoids global drop capture. */
@Mixin(Block.class)
public abstract class MixinBlockNativeStructureDrops {
    @Inject(method = "popResource(Lnet/minecraft/world/level/Level;Ljava/util/function/Supplier;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"), cancellable = true)
    private static void academy$nativeResource(Level level, Supplier<ItemEntity> entityFactory, ItemStack stack,
                                               CallbackInfo callback) {
        if (NativeStructureBreakDrops.tryPopResource(level, entityFactory, stack)) callback.cancel();
    }

    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V",
            at = @At("HEAD"), cancellable = true)
    private static void academy$nativeDrops(BlockState state, Level level, BlockPos position, CallbackInfo callback) {
        if (NativeStructureBreakDrops.tryDropResources(state, level, position, null, null, ItemStack.EMPTY)) callback.cancel();
    }

    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)V",
            at = @At("HEAD"), cancellable = true)
    private static void academy$nativeEntityDrops(BlockState state, LevelAccessor level, BlockPos position,
                                                 BlockEntity blockEntity, CallbackInfo callback) {
        if (level instanceof Level actual && NativeStructureBreakDrops.tryDropResources(state, actual, position,
                blockEntity, null, ItemStack.EMPTY)) callback.cancel();
    }

    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"), cancellable = true)
    private static void academy$nativePlayerDrops(BlockState state, Level level, BlockPos position,
                                                 BlockEntity blockEntity, Entity breaker, ItemStack tool,
                                                 CallbackInfo callback) {
        if (NativeStructureBreakDrops.tryDropResources(state, level, position, blockEntity, breaker, tool)) callback.cancel();
    }
}
