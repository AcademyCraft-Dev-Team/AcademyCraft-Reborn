package org.academy.api.client.gui.command;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public class GlyphDrawCommand extends DrawCommand {
    private final BakedGlyph.GlyphInstance glyphInstance;

    public GlyphDrawCommand(BakedGlyph.GlyphInstance glyphInstance) {
        super(glyphInstance.glyph().guiPipeline());
        this.glyphInstance = glyphInstance;
    }

    @Override
    public void generateVertices(VertexConsumer consumer, Matrix4f pose) {
        this.glyphInstance.glyph().renderChar(this.glyphInstance, pose, consumer, LightTexture.FULL_BRIGHT, true);
    }

    @Override
    public Map<String, GpuTextureView> getSamplers() {
        return Map.of("Sampler0", Objects.requireNonNull(this.glyphInstance.glyph().textureView()), "Sampler2", Minecraft.getInstance().gameRenderer.lightTexture().getTextureView());
    }

    @Override
    public Map<String, GpuBufferSlice> getUniforms() {
        return Collections.emptyMap();
    }
}