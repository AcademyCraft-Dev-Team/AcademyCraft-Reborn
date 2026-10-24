package org.academy.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.academy.api.client.render.vfxgraph.shape.ThirdPersonItemSurface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
public abstract class MixinItemInHandLayer {
    @Inject(method = "submit", at = @At("HEAD"))
    private void academy$beginThirdPersonItems(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            ArmedEntityRenderState state,
            float yRot,
            float xRot,
            CallbackInfo ci
    ) {
        if (state instanceof AvatarRenderState avatar) {
            ThirdPersonItemSurface.beginOwner(avatar.id, avatar.x, avatar.y, avatar.z);
        }
    }

    @Inject(method = "submit", at = @At("RETURN"))
    private void academy$endThirdPersonItems(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            ArmedEntityRenderState state,
            float yRot,
            float xRot,
            CallbackInfo ci
    ) {
        ThirdPersonItemSurface.endOwner();
    }
}
