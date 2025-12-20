package org.academy.api.client.gui.screen;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.academy.api.client.gui.imgui.ImGuiUIDebugger;
import org.academy.api.client.gui.render.UiContext;
import org.academy.api.client.thread.RenderThread;
import org.academy.api.client.vanilla.MainLoopEvent;
import org.academy.api.client.vanilla.RenderLoopEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@EventBusSubscriber(Dist.CLIENT)
public final class ScreenDispatcher {
    public static final Logger LOGGER = LogUtils.getLogger();
    @Nullable
    private static RenderTarget renderTarget;
    @Nullable
    private static UiContext UI_RENDER_CONTEXT;

    @RenderThread
    public static void init() {
        var window = Minecraft.getInstance().getWindow();
        renderTarget = new TextureTarget("Screen", window.getWidth(), window.getHeight(), true);
        UI_RENDER_CONTEXT = new UiContext();
    }

    @RenderThread
    public static void resize(int width, int height) {
        if (renderTarget != null) {
            renderTarget.resize(width, height);
        } else {
            LOGGER.warn("ScreenDispatcher has not been initialized.");
        }
    }

    /**
     * 由 Main 线程调用喵, 生成 SubmittedCommand 列表喵
     */
    @SubscribeEvent
    public static void onMainLoop(MainLoopEvent event) {
        if (UI_RENDER_CONTEXT == null) return;

        var mc = Minecraft.getInstance();

        if (mc.screen instanceof RenderRoot screen) {
            var w = mc.getWindow();
            var m = mc.mouseHandler;
            UI_RENDER_CONTEXT.perform(screen.getRoot(), m.getScaledXPos(w), m.getScaledYPos(w),
                    mc.getDeltaTracker().getGameTimeDeltaTicks());
        }
    }

    /**
     * 由 Render 线程调用喵, 解析命令并绘制喵
     */
    @SubscribeEvent
    public static void onRenderLoop(RenderLoopEvent event) {
        if (UI_RENDER_CONTEXT == null) return;

        var mc = Minecraft.getInstance();

        if (mc.screen instanceof RenderRoot screen) {
            if (renderTarget == null) return;
            UI_RENDER_CONTEXT.upload(renderTarget, true, false);
            ImGuiUIDebugger.render(renderTarget, screen.getRoot());
        }
    }

    @Nullable
    public static RenderTarget getRenderTarget() {
        return renderTarget;
    }

    private ScreenDispatcher() {
    }
}