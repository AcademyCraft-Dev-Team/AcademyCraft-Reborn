package org.academy.mixin.common;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.academy.internal.common.attribute.BlockLootPlayerContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public abstract class MixinServerPlayerGameMode {
    @Shadow
    protected ServerPlayer player;

    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void academy$pushBlockLootPlayer(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BlockLootPlayerContext.push(player);
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void academy$popBlockLootPlayer(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BlockLootPlayerContext.pop();
    }
}
