package org.academy.api.client.render.post;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.bus.api.SubscribeEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.client.vanilla.ResizeDisplayEvent;
import org.academy.internal.client.renderer.Shaders;
import org.lwjgl.opengl.GL20;

import static com.mojang.blaze3d.platform.GlConst.*;
import static org.academy.api.client.util.RenderUtil.blitScreen;
import static org.academy.internal.client.renderer.Shaders.BLOOM_BLEND;
import static org.academy.internal.client.renderer.Shaders.GAUSSIAN_BLUR;
import static org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT1;

/*
 * Adapted from the Photon project (commit 08db839ccb01a741b21c70741c1015719992602a)
 * https://github.com/Low-Drag-MC/Photon/commit/08db839ccb01a741b21c70741c1015719992602a
 * Licensed under GNU GPLv3, copyright Low-Drag-MC and contributors.
 * Modified for this project.
 */
public final class BloomEffect {
    private static final RenderTarget INPUT, OUTPUT;
    private static final RenderTarget SWAP2A, SWAP4A, SWAP8A, SWAP2B, SWAP4B, SWAP8B;
    public static final MultiBufferSource.BufferSource BUFFER_SOURCE = MultiBufferSource.immediate(new BufferBuilder(256));

    static {
        var window = Minecraft.getInstance().getWindow();
        int width = window.getWidth();
        int height = window.getHeight();
        INPUT = new TextureTarget(width, height, true, Minecraft.ON_OSX) {
            @Override
            public void createBuffers(int width, int height, boolean clearError) {
                RenderSystem.assertOnRenderThreadOrInit();
                int i = RenderSystem.maxSupportedTextureSize();
                if (width > 0 && width <= i && height > 0 && height <= i) {
                    viewWidth = width;
                    viewHeight = height;
                    this.width = width;
                    this.height = height;
                    frameBufferId = GlStateManager.glGenFramebuffers();
                    colorTextureId = TextureUtil.generateTextureId();
                    depthBufferId = -1;

                    var mainRenderTarget = Minecraft.getInstance().getMainRenderTarget();
                    var mainDepthBufferId = mainRenderTarget.getDepthTextureId();
                    var mainColorTextureId = mainRenderTarget.getColorTextureId();

                    GlStateManager._bindTexture(colorTextureId);
                    GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                    GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                    GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                    GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                    GlStateManager._texImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, null);
                    GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, frameBufferId);
                    GlStateManager._glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorTextureId, 0);
                    GlStateManager._glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, mainDepthBufferId, 0);
                    GlStateManager._glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, mainColorTextureId, 0);

                    GL20.glDrawBuffers(new int[]{GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1});

                    // 不知道什么原因, 在着色器中没有设置 location, 却直接向全部 COLOR_ATTACHMENT 输出了

                    checkStatus();
                    clear(clearError);
                    unbindRead();
                } else {
                    throw new IllegalArgumentException("Window " + width + "x" + height + " size out of bounds (max. size: " + i + ")");
                }
            }
        };
        INPUT.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);

        OUTPUT = getRenderTarget(width, height);

        SWAP2A = getRenderTarget(width / 2, height / 2);
        SWAP4A = getRenderTarget(width / 4, height / 4);
        SWAP8A = getRenderTarget(width / 8, height / 8);
        SWAP2B = getRenderTarget(width / 2, height / 2);
        SWAP4B = getRenderTarget(width / 4, height / 4);
        SWAP8B = getRenderTarget(width / 8, height / 8);

        AcademyCraft.EVENT_BUS.register(BloomEffect.class);
    }

    private static RenderTarget getRenderTarget(int width, int height) {
        var renderTarget = new TextureTarget(width, height, false, Minecraft.ON_OSX) {
            @Override
            public void createBuffers(int width, int height, boolean clearError) {
                RenderSystem.assertOnRenderThreadOrInit();
                var maxSupportedTextureSize = RenderSystem.maxSupportedTextureSize();
                if (width > 0 && width <= maxSupportedTextureSize && height > 0 && height <= maxSupportedTextureSize) {
                    viewWidth = width;
                    viewHeight = height;
                    this.width = width;
                    this.height = height;
                    frameBufferId = GlStateManager.glGenFramebuffers();
                    colorTextureId = TextureUtil.generateTextureId();

                    setFilterMode(GlConst.GL_LINEAR);

                    GlStateManager._bindTexture(this.colorTextureId);
                    GlStateManager._texParameter(3553, 10242, 33071);
                    GlStateManager._texParameter(3553, 10243, 33071);
                    GlStateManager._texImage2D(3553, 0, 32856, this.width, this.height, 0, 6408, 5121, null);
                    GlStateManager._glBindFramebuffer(36160, this.frameBufferId);
                    GlStateManager._glFramebufferTexture2D(36160, 36064, 3553, this.colorTextureId, 0);

                    checkStatus();
                    clear(clearError);
                    unbindRead();
                } else {
                    throw new IllegalArgumentException("Window " + width + "x" + height + " size out of bounds (max. size: " + maxSupportedTextureSize + ")");
                }
            }
        };
        renderTarget.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);

        return renderTarget;
    }

    public static RenderTarget getInput() {
        return INPUT;
    }

    public static RenderTarget getOutput() {
        return OUTPUT;
    }

    public static void updateScreenSize(int width, int height) {
        INPUT.resize(width, height, Minecraft.ON_OSX);
        OUTPUT.resize(width, height, Minecraft.ON_OSX);

        SWAP2A.resize(width / 2, height / 2, Minecraft.ON_OSX);
        SWAP4A.resize(width / 4, height / 4, Minecraft.ON_OSX);
        SWAP8A.resize(width / 8, height / 8, Minecraft.ON_OSX);
        SWAP2B.resize(width / 2, height / 2, Minecraft.ON_OSX);
        SWAP4B.resize(width / 4, height / 4, Minecraft.ON_OSX);
        SWAP8B.resize(width / 8, height / 8, Minecraft.ON_OSX);
    }

    public static void process() {
        BUFFER_SOURCE.endBatch();

        var mainRenderTarget = Minecraft.getInstance().getMainRenderTarget();
        var mainRenderTargetColorTextureId = mainRenderTarget.getColorTextureId();

        GAUSSIAN_BLUR.setSampler("DiffuseSampler", INPUT.getColorTextureId());
        GAUSSIAN_BLUR.safeGetUniform("BlurDir").set(1f, 0f);
        GAUSSIAN_BLUR.safeGetUniform("Radius").set(3);
        GAUSSIAN_BLUR.safeGetUniform("OutSize").set((float) SWAP2A.width, (float) SWAP2A.height);
        blitScreen(GAUSSIAN_BLUR, SWAP2A);

        GAUSSIAN_BLUR.setSampler("DiffuseSampler", SWAP2A);
        GAUSSIAN_BLUR.safeGetUniform("BlurDir").set(0f, 1f);
        GAUSSIAN_BLUR.safeGetUniform("Radius").set(3);
        GAUSSIAN_BLUR.safeGetUniform("OutSize").set((float) SWAP2B.width, (float) SWAP2B.height);
        blitScreen(GAUSSIAN_BLUR, SWAP2B);

        GAUSSIAN_BLUR.setSampler("DiffuseSampler", SWAP2B);
        GAUSSIAN_BLUR.safeGetUniform("BlurDir").set(1f, 0f);
        GAUSSIAN_BLUR.safeGetUniform("Radius").set(5);
        GAUSSIAN_BLUR.safeGetUniform("OutSize").set((float) SWAP4A.width, (float) SWAP4A.height);
        blitScreen(GAUSSIAN_BLUR, SWAP4A);

        GAUSSIAN_BLUR.setSampler("DiffuseSampler", SWAP4A);
        GAUSSIAN_BLUR.safeGetUniform("BlurDir").set(0f, 1f);
        GAUSSIAN_BLUR.safeGetUniform("Radius").set(5);
        GAUSSIAN_BLUR.safeGetUniform("OutSize").set((float) SWAP4B.width, (float) SWAP4B.height);
        blitScreen(GAUSSIAN_BLUR, SWAP4B);

        GAUSSIAN_BLUR.setSampler("DiffuseSampler", SWAP4B);
        GAUSSIAN_BLUR.safeGetUniform("BlurDir").set(1f, 0f);
        GAUSSIAN_BLUR.safeGetUniform("Radius").set(7);
        GAUSSIAN_BLUR.safeGetUniform("OutSize").set((float) SWAP8A.width, (float) SWAP8A.height);
        blitScreen(GAUSSIAN_BLUR, SWAP8A);

        GAUSSIAN_BLUR.setSampler("DiffuseSampler", SWAP8A);
        GAUSSIAN_BLUR.safeGetUniform("BlurDir").set(0f, 1f);
        GAUSSIAN_BLUR.safeGetUniform("Radius").set(7);
        GAUSSIAN_BLUR.safeGetUniform("OutSize").set((float) SWAP8B.width, (float) SWAP8B.height);
        blitScreen(GAUSSIAN_BLUR, SWAP8B);

        BLOOM_BLEND.setSampler("DiffuseSampler", mainRenderTargetColorTextureId);
        BLOOM_BLEND.setSampler("BlurTexture1", SWAP2B);
        BLOOM_BLEND.setSampler("BlurTexture2", SWAP4B);
        BLOOM_BLEND.setSampler("BlurTexture3", SWAP8B);
        BLOOM_BLEND.safeGetUniform("BloomRadius").set(1f);
        blitScreen(BLOOM_BLEND, OUTPUT);

        INPUT.clear(Minecraft.ON_OSX);

        Shaders.SCREEN_BLIT.setSampler("DiffuseSampler", OUTPUT.getColorTextureId());
        RenderUtil.blitScreen(Shaders.SCREEN_BLIT, mainRenderTarget);
    }

    @SubscribeEvent
    public static void onResizeDisplayEvent(ResizeDisplayEvent event) {
        var window = event.getWindow();
        updateScreenSize(window.getWidth(), window.getHeight());
    }
}