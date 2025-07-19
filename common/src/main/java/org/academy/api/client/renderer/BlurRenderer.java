package org.academy.api.client.renderer;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.shaders.Uniform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.bus.api.SubscribeEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.vanilla.ResizeDisplayEvent;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.academy.AcademyCraft.getResourceLocation;

public final class BlurRenderer {
    private static Window window;
    private static float blurRadius = 20f;
    private static PostChain blurPostChain;
    private static RenderTarget mainRenderTarget;
    private static RenderTarget maskInputRenderTarget;
    private static final List<Uniform> blurRadiusUniforms = new ArrayList<>();

    public static void init() {
        AcademyCraft.EVENT_BUS.register(BlurRenderer.class);
        var mc = Minecraft.getInstance();
        window = mc.getWindow();
        try {
            blurPostChain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(),
                    getResourceLocation("shaders/post/blur_mask.json")) {
                @Override
                public @NotNull PostPass addPass(@NotNull String programName, @NotNull RenderTarget framebuffer, @NotNull RenderTarget framebufferOut) throws IOException {
                    if (programName.equals("academy:blur_mask")) {
                        var blurMaskPostPass = super.addPass(programName, framebuffer, framebufferOut);
                        var uniform = blurMaskPostPass.getEffect().getUniform("Radius");
                        if (uniform != null) {
                            blurRadiusUniforms.add(uniform);
                        }
                        return blurMaskPostPass;
                    } else {
                        return super.addPass(programName, framebuffer, framebufferOut);
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
        BlurRenderer.blurRadius = blurRadius;
        for (var uniform : blurRadiusUniforms) {
            uniform.set(blurRadius);
        }
    }

    @SubscribeEvent
    public static void onResizeDisplay(ResizeDisplayEvent event) {
        blurPostChain.resize(window.getWidth(), window.getHeight());
    }

    private BlurRenderer() {
    }
}