package org.academy.api.client.render.post;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.Uniform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.api.client.vanilla.ResizeDisplayEvent;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.academy.AcademyCraft.getResourceLocation;

public final class BlurEffect {
    private static float blurRadius = 20f;
    private static PostChain blurPostChain;
    private static RenderTarget mainRenderTarget;
    private static RenderTarget maskInputRenderTarget;
    private static final List<Uniform> blurRadiusUniforms = new ArrayList<>();

    public static void init() {
        NeoForge.EVENT_BUS.register(BlurEffect.class);
        var mc = Minecraft.getInstance();
        var window = mc.getWindow();
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
            blurPostChain.resize(window.getWidth(), window.getHeight());
            maskInputRenderTarget = blurPostChain.getTempTarget("mask_input_target");
            maskInputRenderTarget.clear(Minecraft.ON_OSX);
            mainRenderTarget = mc.getMainRenderTarget();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
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

    @SubscribeEvent
    public static void onResizeDisplay(ResizeDisplayEvent event) {
        var window = event.getWindow();
        blurPostChain.resize(window.getWidth(), window.getHeight());
    }

    private BlurEffect() {
    }
}