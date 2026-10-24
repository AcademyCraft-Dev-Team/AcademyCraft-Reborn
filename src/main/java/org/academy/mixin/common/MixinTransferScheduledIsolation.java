package org.academy.mixin.common;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.block.Block;
import org.academy.internal.common.structure.transaction.PayloadIsolation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class MixinTransferScheduledIsolation {
    @Inject(method = "tickFluid", at = @At("HEAD"), cancellable = true)
    private void academy$deferFluid(BlockPos pos, Fluid type, CallbackInfo callback) {
        var level = (ServerLevel) (Object) this;
        if (PayloadIsolation.locked(level, pos)) { level.scheduleTick(pos, type, 5); callback.cancel(); }
    }
    @Inject(method = "tickBlock", at = @At("HEAD"), cancellable = true)
    private void academy$deferBlock(BlockPos pos, Block type, CallbackInfo callback) {
        var level = (ServerLevel) (Object) this;
        if (PayloadIsolation.locked(level, pos)) { level.scheduleTick(pos, type, 5); callback.cancel(); }
    }
}
