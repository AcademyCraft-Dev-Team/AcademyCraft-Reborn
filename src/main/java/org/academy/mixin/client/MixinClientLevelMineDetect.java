package org.academy.mixin.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.internal.client.ability.electromaster.mine.MineDetectClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class MixinClientLevelMineDetect {
    @Inject(method = "setBlocksDirty", at = @At("TAIL"))
    private void academy$updateOre(BlockPos pos, BlockState before, BlockState after, CallbackInfo ci) {
        MineDetectClient.blockChanged((ClientLevel) (Object) this, pos, after);
    }
}
