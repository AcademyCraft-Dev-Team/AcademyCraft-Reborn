package org.academy.mixin.client;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.academy.internal.client.ability.mentalout.WideAreaInterferenceClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class MixinBlockEntityRenderDispatcher {
    @Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
    private <E extends BlockEntity, S extends BlockEntityRenderState> void academy$hideWideAreaBlockEntity(
            E blockEntity,
            float partialTick,
            ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
            boolean force,
            CallbackInfoReturnable<S> cir
    ) {
        if (WideAreaInterferenceClientState.isBlockHidden(blockEntity.getBlockPos())) {
            cir.setReturnValue(null);
        }
    }
}
