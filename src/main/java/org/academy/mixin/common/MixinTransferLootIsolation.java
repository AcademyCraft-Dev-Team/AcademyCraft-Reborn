package org.academy.mixin.common;

import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.academy.internal.common.structure.transaction.PayloadIsolation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Inspection through a hopper must not resolve a locked chest's still-unopened loot table. */
@Mixin(RandomizableContainer.class)
public interface MixinTransferLootIsolation {
    @Inject(method = "unpackLootTable", at = @At("HEAD"), cancellable = true)
    default void academy$deferLoot(Player player, CallbackInfo callback) {
        if (this instanceof BlockEntity entity && PayloadIsolation.denied(entity)) callback.cancel();
    }
}
