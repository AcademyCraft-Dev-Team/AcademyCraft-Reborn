package org.academy.api.client.gui.command;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.renderer.LightTexture;
import org.academy.api.client.render.TextureBinding;
import org.academy.api.client.render.UniformBinding;
import org.joml.Matrix4f;

import java.util.List;

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
    public List<TextureBinding> getTextures() {
        return List.of(
                new TextureBinding(
                        "Sampler0",
                        renderable.textureView(),
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
                ),
                new TextureBinding(
                        "Sampler2",
                        Minecraft.getInstance().gameRenderer.lightTexture().getTextureView(),
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
                )
        );
    }

    @Override
    public List<UniformBinding> getUniforms() {
        return List.of();
    }
}