package org.academy.mixin.client;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.api.client.render.LevelRenderEvent;
import org.academy.api.client.render.MatrixStack;
import org.academy.api.client.render.post.DirectionalFieldRenderer;
import org.academy.api.client.render.post.GlowEffect;
import org.academy.api.client.render.post.PostEffect;
import org.academy.api.client.render.vfxgraph.render.GraphCamera;
import org.academy.api.client.render.vfxgraph.runtime.VfxGraphManager;
import org.academy.internal.client.ability.aeromanip.HighSpeedJetHighlightClient;
import org.academy.internal.client.ability.mentalout.MentaloutRosterClientState;
import org.academy.internal.client.ability.mentalout.WideAreaInterferenceClientState;
import org.academy.internal.client.render.vfxgraph.VfxContexts;
import org.academy.internal.common.ability.darkmatter.Darkmatter;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;





@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {
    @Inject(method = "render", at = @At("HEAD"))
    private void academy$beginDirectionalFields(CallbackInfo ci) {
        Darkmatter.Client.InterferenceFieldClient.beginFrame();
    }

    @Inject(
            // 在 iris$endLevelRender 之后调用以兼容 Iris 喵
            order = Integer.MAX_VALUE,
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/joml/Matrix4fStack;popMatrix()Lorg/joml/Matrix4fStack;"
            )
    )
    private void renderLevel(
            GraphicsResourceAllocator resourceAllocator,
            boolean renderOutline,
            CameraRenderState cameraState,
            GpuBufferSlice terrainFog,
            Vector4f fogColor,
            boolean shouldRenderSky,
            boolean consistentDepthRequired,
            CallbackInfo ci
    ) {

    }

    @Inject(method = "submitEntities", at = @At("RETURN"))
    private void submitEntities(
            PoseStack poseStack,
            LevelRenderState levelRenderState,
            SubmitNodeCollector output,
            CallbackInfo ci
    ) {
        if (WideAreaInterferenceClientState.isRtsView()
                || MentaloutRosterClientState.hasControlledTargets()
                || HighSpeedJetHighlightClient.hasEntityHighlights()) {
            levelRenderState.shouldShowEntityOutlines = true;
        }
        VfxContexts.submit(
                Minecraft.getInstance().getDeltaTracker(),
                levelRenderState.cameraRenderState
        );
        VfxGraphManager.INSTANCE.submitWorldGeometry(
                poseStack,
                output,
                new Vector3f((float) levelRenderState.cameraRenderState.pos.x,
                        (float) levelRenderState.cameraRenderState.pos.y,
                        (float) levelRenderState.cameraRenderState.pos.z)
        );
        var event = new LevelRenderEvent(
                Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false),
                new MatrixStack().setFrom(poseStack.last()),
                poseStack,
                output,
                levelRenderState.cameraRenderState.pos
        );
        NeoForge.EVENT_BUS.post(event);
    }
}
