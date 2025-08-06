package org.academy.api.client.render.post;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.academy.AcademyCraft.getResourceLocation;

public final class BlurEffect {
    private static final RenderType RENDER_TYPE = RenderType.create(
            "blur_mask",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            256,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .createCompositeState(false)
    );
    private static float blurRadius = 20f;
    private static final PostChain blurPostChain;
    private static final RenderTarget mainRenderTarget;
    private static final RenderTarget maskInputRenderTarget;
    private static final List<Uniform> blurRadiusUniforms = new ArrayList<>();

    static {
        var mc = Minecraft.getInstance();
        mainRenderTarget = mc.getMainRenderTarget();
        try {
            blurPostChain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(),
                    getResourceLocation("shaders/post/masked_blur.json")) {
                @Override
                public @NotNull PostPass addPass(@NotNull String name, @NotNull RenderTarget inTarget, @NotNull RenderTarget outTarget, boolean useLinearFilter) throws IOException {
                    if (name.equals("academy:masked_blur")) {
                        var blurMaskPostPass = super.addPass(name, inTarget, outTarget, useLinearFilter);
                        var uniform = blurMaskPostPass.getEffect().getUniform("Radius");
                        if (uniform != null) {
                            blurRadiusUniforms.add(uniform);
                        }
                        return blurMaskPostPass;
                    } else {
                        return super.addPass(name, inTarget, outTarget, useLinearFilter);
                    }
                }
            };
            resize(mainRenderTarget.width, mainRenderTarget.height);
            maskInputRenderTarget = blurPostChain.getTempTarget("mask_input_target");
            maskInputRenderTarget.clear(Minecraft.ON_OSX);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static void resize(int width, int height) {
        blurPostChain.resize(width, height);
    }

    public static void start(MultiBufferSource.BufferSource bufferSource, RenderType blurMaskRenderType) {
        bufferSource.endBatch(blurMaskRenderType);
        maskInputRenderTarget.clear(Minecraft.ON_OSX);
        maskInputRenderTarget.bindWrite(false);
    }

    public static void stop(MultiBufferSource.BufferSource bufferSource, RenderType blurMaskRenderType) {
        bufferSource.endBatch(blurMaskRenderType);
        blurPostChain.process(0);
        mainRenderTarget.bindWrite(false);
    }

    public static float getBlurRadius() {
        return blurRadius;
    }

    public static void setBlurRadius(float blurRadius) {
        BlurEffect.blurRadius = blurRadius;
        for (var uniform : blurRadiusUniforms) {
            uniform.set(blurRadius);
        }
    }

    public static RenderType getBlurMaskRenderType() {
        return RENDER_TYPE;
    }

    private BlurEffect() {
    }
}