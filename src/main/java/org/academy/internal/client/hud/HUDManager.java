package org.academy.internal.client.hud;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.logging.LogUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.academy.api.client.Render;
import org.academy.api.client.render.post.BlurEffect;
import org.academy.api.client.thread.MainThread;
import org.academy.api.client.thread.RenderThread;
import org.academy.api.client.vanilla.MainLoopEvent;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 负责管理本模组内置的 UI 帧缓冲区与 HUD 喵
 */
@EventBusSubscriber(Dist.CLIENT)
public final class HUDManager {
    public static final Logger LOGGER = LogUtils.getLogger();

    @RenderThread
    public static void initRender() {
        DataTerminalHUD.initRender();
    }

    @MainThread
    public static void initMain() {
        DataTerminalHUD.initMain();
    }

    @RenderThread
    public static void resize(int width, int height) {
        DataTerminalHUD.resize();
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

    public static void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        var mc = Minecraft.getInstance();
        var main = mc.getMainRenderTarget();
        var width = main.width;
        var height = main.height;

        RenderTarget renderTarget = null;
        var pool = Render.Buffers.getResourcePool();
        var desc = new RenderTargetDescriptor(width, height, true, 0, true);

        try {
            renderTarget = pool.acquire(desc);

            var mainColorView = main.getColorTextureView();
            var uiColorView = renderTarget.getColorTextureView();
            var uiDepthView = renderTarget.getDepthTextureView();
            if (mainColorView == null || uiColorView == null || uiDepthView == null) return;

            var drew = new AtomicBoolean();

            DataTerminalHUD.render(width, height, uiColorView, uiDepthView, drew);

            if (!drew.get()) return;

            BlurEffect.apply(
                    width, height,
                    mainColorView,
                    mainColorView,
                    uiDepthView,
                    20.0f
            );

            guiGraphics.submitBlit(
                    Render.RenderPipelines.IMAGE, uiColorView,
                    0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight(),
                    0, 1, 1, 0,
                    -1
            );
        } finally {
            if (renderTarget != null) pool.release(desc, renderTarget);
        }
    }

    private HUDManager() {
    }
}