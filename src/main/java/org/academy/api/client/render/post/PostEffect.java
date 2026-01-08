package org.academy.api.client.render.post;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.academy.api.client.Render;
import org.academy.api.client.render.TextureBinding;

import java.util.List;
import java.util.SequencedMap;

public final class PostEffect {
    public static final RenderTarget MAIN_SCENE;
    public static final SequencedMap<RenderType, ByteBufferBuilder> FIXED_BUFFERS = new Object2ObjectLinkedOpenHashMap<>();
    public static final MultiBufferSource.BufferSource BUFFER_SOURCE_PRE = MultiBufferSource.immediateWithBuffers(FIXED_BUFFERS, Render.Buffers.getByteBufferBuilder());
    public static final MultiBufferSource.BufferSource BUFFER_SOURCE_POST = MultiBufferSource.immediateWithBuffers(FIXED_BUFFERS, Render.Buffers.getByteBufferBuilder());

    static {
        var mc = Minecraft.getInstance();
        var mainRenderTarget = mc.getMainRenderTarget();
        var width = mainRenderTarget.width;
        var height = mainRenderTarget.height;
        MAIN_SCENE = new TextureTarget(null, width, height, true);
    }

    public static void addFixedBuffer(RenderType type) {
        FIXED_BUFFERS.put(type, new ByteBufferBuilder(type.bufferSize()));
    }

    public static void resize(int width, int height) {
        MAIN_SCENE.resize(width, height);
    }

    public static void pre() {
        var mc = Minecraft.getInstance();
        var mainRenderTarget = mc.getMainRenderTarget();
        var sceneColorView = MAIN_SCENE.getColorTextureView();
        var sceneDepthView = MAIN_SCENE.getDepthTextureView();
        var main = mainRenderTarget.getColorTextureView();

        if (main == null || sceneColorView == null || sceneDepthView == null) return;

        var sceneColor = sceneColorView.texture();
        var sceneDepth = sceneDepthView.texture();

        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(sceneColor, 0, sceneDepth, 1);
        var textures = List.of(new TextureBinding("DiffuseSampler", main, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)));
        Render.runBlitPass(
                sceneColorView, Render.RenderPipelines.BLIT_SCREEN_WITHOUT_BLEND,
                Render.Buffers.getInstance().getFSQuadVBNDC(),
                textures, List.of(),
                false
        );
        BUFFER_SOURCE_PRE.endBatch();
    }

    public static void post() {
        BUFFER_SOURCE_POST.endBatch();
    }

    /**
     * 我也不知道怎么命名好喵
     * <br>
     * 用途为创建一个 BufferSource 用于后处理的某个阶段喵
     * <br>
     * 可以参考 BloomEffect 喵
     * <br>
     * 共享 BYTE_BUFFER_BUILDER 喵
     */
    public static MultiBufferSource.BufferSource createPostEffectPassBuffer(SequencedMap<RenderType, ByteBufferBuilder> fixedBuffers) {
        return MultiBufferSource.immediateWithBuffers(fixedBuffers, Render.Buffers.getByteBufferBuilder());
    }

    private PostEffect() {
    }
}