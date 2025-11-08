package org.academy.internal.client.hud;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.academy.api.client.Render;
import org.academy.api.client.render.post.BlurEffect;
import org.academy.api.client.thread.MainThread;
import org.academy.api.client.thread.RenderThread;
import org.academy.api.client.vanilla.MainLoopEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.Map;

/**
 * 负责管理本模组内置的 UI 帧缓冲区与 HUD 喵
 */
@EventBusSubscriber(Dist.CLIENT)
public final class HUDManager {
    public static final Logger LOGGER = LogUtils.getLogger();
    /**
     * 有深度, 模板
     */
    @Nullable
    private static RenderTarget renderTarget;

    @RenderThread
    public static void initRender() {
        var mc = Minecraft.getInstance();
        var mainRenderTarget = mc.getMainRenderTarget();
        var width = mainRenderTarget.width;
        var height = mainRenderTarget.height;

        renderTarget = new TextureTarget("UI", width, height, true, true);

        DataTerminalHUD.initRender();
    }

    @MainThread
    public static void initMain() {
        DataTerminalHUD.initMain();
    }

    @RenderThread
    public static void resize(int width, int height) {
        if (renderTarget != null) {
            renderTarget.resize(width, height);
        } else {
            hasNotBeenInitialized();
        }
        DataTerminalHUD.resize(width, height);
    }

    /**
     * 由 Main 线程调用喵, 生成 SubmittedCommand 列表喵
     */
    @SubscribeEvent
    public static void onMainLoop(MainLoopEvent event) {
        var mc = Minecraft.getInstance();
        var w = mc.getWindow();
        var m = mc.mouseHandler;
        var mouseX = m.getScaledXPos(w);
        var mouseY = m.getScaledYPos(w);
        var deltaPartialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        DataTerminalHUD.perform(mouseX, mouseY, deltaPartialTick);
    }

    /**
     * 由 Render 线程调用喵, 解析命令并绘制喵
     */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        var mc = Minecraft.getInstance();
        var main = mc.getMainRenderTarget();

        RenderTarget renderTarget = null;
        var pool = Render.Buffers.getResourcePool();
        var desc = new RenderTargetDescriptor(1314,1314, true, 0, true);

        try {
            renderTarget = pool.acquire(desc);

            var mainColorView = main.getColorTextureView();
            var uiColorView = renderTarget.getColorTextureView();
            var uiDepth = renderTarget.getDepthTexture();
            if (mainColorView == null || uiColorView == null || uiDepth == null) return;

            DataTerminalHUD.render(renderTarget, true);

            BlurEffect.apply(main, main, 20.0f);

            Render.runBlitPassNDC(mainColorView, Render.RenderPipelines.BLIT_SCREEN_WITH_BLEND,
                    Map.of("DiffuseSampler", uiColorView),
                    Collections.emptyMap(),
                    false
            );
        } finally {
            if (renderTarget != null) pool.release(desc, renderTarget);
        }
    }

    private static void hasNotBeenInitialized() {
        LOGGER.warn("ScreenDispatcher has not been initialized.");
    }

    private HUDManager() {
    }
}