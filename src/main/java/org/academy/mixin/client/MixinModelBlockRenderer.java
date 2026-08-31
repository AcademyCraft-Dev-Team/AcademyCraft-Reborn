package org.academy.mixin.client;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.internal.client.ability.mentalout.WideAreaInterferenceClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelBlockRenderer.class)
public abstract class MixinModelBlockRenderer {
    @Inject(method = "tesselateBlock", at = @At("HEAD"), cancellable = true)
    private void academy$hideWideAreaBlock(
            BlockQuadOutput output,
            float offsetX,
            float offsetY,
            float offsetZ,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            BlockStateModel model,
            long seed,
            CallbackInfo ci
    ) {
        if (WideAreaInterferenceClientState.isBlockHidden(pos)) ci.cancel();
    }
}
