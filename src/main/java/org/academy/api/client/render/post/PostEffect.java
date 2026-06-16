package org.academy.api.client.render.post;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import org.academy.api.client.Render;
import org.academy.api.client.compatibility.IrisCompat;
import org.academy.api.client.render.TextureBinding;
import org.joml.Vector4f;

import java.util.List;

public final class PostEffect {
    public static final RenderTarget MAIN_SCENE;
    public static final Phase PRE_PHASE = new Phase("pre");
    public static final Phase POST_PHASE = new Phase("post");

    static {
        var mc = Minecraft.getInstance();
        var mainRenderTarget = mc.gameRenderer.mainRenderTarget();
        MAIN_SCENE = new TextureTarget(
                null, mainRenderTarget.width, mainRenderTarget.height, true, GpuFormat.RGBA8_UNORM
        );
    }

    public static void resize(int width, int height) {
        MAIN_SCENE.resize(width, height);
    }

    public static void pre() {
        var mc = Minecraft.getInstance();
        var mainRenderTarget = mc.gameRenderer.mainRenderTarget();
        var sceneColorView = MAIN_SCENE.getColorTextureView();
        var sceneDepthView = MAIN_SCENE.getDepthTextureView();
        var main = mainRenderTarget.getColorTextureView();
        if (main == null || sceneColorView == null || sceneDepthView == null) return;

        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(sceneColorView.texture(), new Vector4f(0), sceneDepthView.texture(), 1);
        Render.runBlitPass(
                sceneColorView, Render.RenderPipelines.BLIT_SCREEN_WITHOUT_BLEND,
                Render.Buffers.getInstance().getFSQuadVBNDC(),
                List.of(new TextureBinding("Sampler0", main, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST))),
                List.of(),
                false
        );
        IrisCompat.enableBypass();
        PRE_PHASE.draw();
        IrisCompat.resetBypass();
    }

    public static void post() {
        IrisCompat.enableBypass();
        POST_PHASE.draw();
        IrisCompat.resetBypass();
    }

    public static Phase getPre(){
        return PRE_PHASE;
    }

    public static Phase getPost(){
        return POST_PHASE;
    }

    public static void close() {
        PRE_PHASE.close();
        POST_PHASE.close();
    }

    private PostEffect() {
    }
}