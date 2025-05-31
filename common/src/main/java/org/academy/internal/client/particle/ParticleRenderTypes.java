package org.academy.internal.client.particle;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import org.jetbrains.annotations.NotNull;

public class ParticleRenderTypes {
    public static final ParticleRenderType IMAG_PHASE = new ParticleRenderType() {
        @Override
        public void begin(@NotNull BufferBuilder builder, @NotNull TextureManager textureManager) {
            RenderTarget renderTarget = Minecraft.getInstance().levelRenderer.getTranslucentTarget();
            if (renderTarget != null) {
                renderTarget.bindWrite(false);
            }
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            RenderSystem.disableCull();
            RenderSystem.disableDepthTest();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public void end(@NotNull Tesselator tesselator) {
            tesselator.end();
        }
    };

    private ParticleRenderTypes() {
    }
}