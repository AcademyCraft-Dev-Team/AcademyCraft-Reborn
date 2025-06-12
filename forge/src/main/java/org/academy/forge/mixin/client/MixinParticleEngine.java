package org.academy.forge.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.TextureManager;
import org.academy.internal.client.particle.ParticleRenderTypes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Objects;
import java.util.Queue;

@Mixin(ParticleEngine.class)
public class MixinParticleEngine {
    @Shadow
    @Final
    private Map<ParticleRenderType, Queue<Particle>> particles;

    @Shadow
    @Final
    private TextureManager textureManager;

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"))
    private void render(PoseStack poseStack, MultiBufferSource.BufferSource buffer, LightTexture lightTexture, Camera activeRenderInfo, float partialTicks, Frustum clippingHelper, CallbackInfo ci) {
        Iterable<Particle> iterable = particles.get(ParticleRenderTypes.IMAG_PHASE);
        if (iterable != null) {
            RenderSystem.setShader(GameRenderer::getParticleShader);
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder bufferbuilder = tesselator.getBuilder();
            ParticleRenderTypes.IMAG_PHASE.begin(bufferbuilder, textureManager);

            for (Particle particle : iterable) {
                try {
                    particle.render(bufferbuilder, activeRenderInfo, partialTicks);
                } catch (Throwable throwable) {
                    CrashReport crashreport = CrashReport.forThrowable(throwable, "Rendering Particle");
                    CrashReportCategory crashreportcategory = crashreport.addCategory("Particle being rendered");
                    Objects.requireNonNull(particle);
                    crashreportcategory.setDetail("Particle", particle::toString);
                    crashreportcategory.setDetail("Particle Type", ParticleRenderTypes.IMAG_PHASE::toString);
                    throw new ReportedException(crashreport);
                }
            }

            ParticleRenderTypes.IMAG_PHASE.end(tesselator);
        }
    }
}