package org.academy.mixin.client;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.api.client.render.LevelRenderEvent;
import org.academy.api.client.render.MatrixStack;
import org.academy.api.client.render.post.BloomEffect;
import org.academy.api.client.render.post.PostEffect;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {
    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/joml/Matrix4fStack;popMatrix()Lorg/joml/Matrix4fStack;"
            )
    )
    private void renderLevel(
            GraphicsResourceAllocator resourceAllocator,
            DeltaTracker deltaTracker,
            boolean renderOutline,
            CameraRenderState cameraState,
            Matrix4fc modelViewMatrix,
            GpuBufferSlice terrainFog,
            Vector4f fogColor,
            boolean shouldRenderSky,
            CallbackInfo ci
    ) {
        BloomEffect.getInstance().process();
        PostEffect.pre();
        PostEffect.post();
    }

    @Inject(method = "submitEntities", at = @At("HEAD"))
    private void submitEntities(
            PoseStack poseStack,
            LevelRenderState levelRenderState,
            SubmitNodeCollector output,
            CallbackInfo ci
    ) {
        var event = new LevelRenderEvent(
                Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaTicks(),
                new MatrixStack().setFrom(poseStack.last())
        );
        NeoForge.EVENT_BUS.post(event);
    }
}
