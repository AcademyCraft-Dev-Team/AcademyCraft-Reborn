package org.academy.api.client.gui.command;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public class GlyphDrawCommand extends DrawCommand {
    private final TextRenderable renderable;

    public GlyphDrawCommand(TextRenderable renderable ) {
        super(renderable.guiPipeline());
        this.renderable = renderable;
    }

    @Override
    public void generateVertices(VertexConsumer consumer, Matrix4f pose) {
        renderable.render(pose, consumer, LightTexture.FULL_BRIGHT, true);
    }

    @Override
    public Map<String, GpuTextureView> getSamplers() {
        return Map.of("Sampler0", Objects.requireNonNull(renderable.textureView()), "Sampler2", Minecraft.getInstance().gameRenderer.lightTexture().getTextureView());
    }

    @Override
    public Map<String, GpuBufferSlice> getUniforms() {
        return Collections.emptyMap();
    }
}