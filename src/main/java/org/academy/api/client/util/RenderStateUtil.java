package org.academy.api.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import org.academy.api.client.render.post.BloomEffect;
import org.academy.internal.client.renderer.Shaders;

@SuppressWarnings("unused")
public class RenderStateUtil {
    // Only for UI
    public static final RenderStateShard.ShaderStateShard POSITION_COLOR_TEX_SHADER_FULL = new RenderStateShard.ShaderStateShard(() -> Shaders.POSITION_COLOR_TEX);
    public static final RenderStateShard.ShaderStateShard POSITION_TEX_COLOR_SHADER = new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader);
    public static final RenderStateShard.ShaderStateShard GLOW_CIRCLE = new RenderStateShard.ShaderStateShard(() -> Shaders.GLOW_CIRCLE);
    public static final RenderStateShard.OutputStateShard BLOOM_TARGET = new RenderStateShard.OutputStateShard(
            "bloom_target",
            () -> BloomEffect.getInput().bindWrite(false),
            () -> Minecraft.getInstance().getMainRenderTarget().bindWrite(false)
    );
}