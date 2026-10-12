package org.academy.mixin.common;

import net.minecraft.advancements.triggers.ItemUsedOnLocationTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.internal.common.structure.space.NativeStructurePlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Only an explicit native context changes these three coordinate-sensitive placement side effects. */
@Mixin(BlockItem.class)
public abstract class MixinBlockItemNativeStructurePlacement {
    @Redirect(method = "placeBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean academy$observeNativePlacementWrite(Level level, BlockPos position, BlockState state, int flags,
                                                       BlockPlaceContext context, BlockState placementState) {
        return context instanceof NativeStructurePlaceContext nativeContext
                ? nativeContext.writeBlock(level, position, state, flags) : level.setBlock(position, state, flags);
    }

    @Redirect(method = "place", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V"))
    private void academy$deferNativePlacementSound(Level level, Entity except, BlockPos position, SoundEvent sound,
                                                  SoundSource source, float volume, float pitch,
                                                  BlockPlaceContext context) {
        if (context instanceof NativeStructurePlaceContext nativeContext) {
            nativeContext.captureSound(position, sound, source, volume, pitch);
        } else {
            level.playSound(except, position, sound, source, volume, pitch);
        }
    }

    @Redirect(method = "place", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/advancements/triggers/ItemUsedOnLocationTrigger;trigger(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemInstance;)V"))
    private void academy$avoidHostCriterionAtLocalCoordinates(ItemUsedOnLocationTrigger trigger, ServerPlayer player,
                                                             BlockPos position, ItemInstance item,
                                                             BlockPlaceContext context) {
        // This trigger reads player.level().getBlockState(position). A local position must never
        // inspect an unrelated host block; structure-aware placement advancements remain deferred.
        if (!(context instanceof NativeStructurePlaceContext)) trigger.trigger(player, position, item);
    }
}
