package org.academy.mixin.client.compatibility;

import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.academy.api.client.compatibility.IrisCompat;
import org.academy.internal.client.render.vfx.PlatinumCosmosPass;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.pathways.HandRenderer", remap = false)
public abstract class MixinIrisHandRenderer {
    @Shadow
    private FeatureRenderDispatcher featureRenderDispatcher;

    @Inject(
            method = "renderTranslucent",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;renderAllFeatures(Lnet/minecraft/client/renderer/SubmitNodeStorage;)V",
                    shift = At.Shift.AFTER
            ),
            require = 0
    )
    private void academy$renderPlatinumCosmosAfterTranslucentHand(
            Matrix4fc modelViewMatrix,
            float partialTick,
            Camera camera,
            CameraRenderState cameraState,
            GameRenderer gameRenderer,
            WorldRenderingPipeline pipeline,
            CallbackInfo ci
    ) {
        IrisCompat.markHandBridgeMounted();
        PlatinumCosmosPass.renderFirstPersonHand(featureRenderDispatcher, partialTick);
    }
}
