package org.academy.api.client.gui.screen;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppedEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.api.client.gui.imgui.ImGuiUIDebugger;
import org.academy.api.client.gui.render.UiContext;
import org.academy.api.client.thread.RenderThread;
import org.academy.api.client.vanilla.MainLoopEvent;
import org.academy.api.client.vanilla.RenderLoopEvent;
import org.academy.api.client.vanilla.ResizeDisplayEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class ScreenDispatcher {
    public static final Logger LOGGER = LogUtils.getLogger();
    @Nullable
    private static ScreenDispatcher INSTANCE;
    private final RenderTarget renderTarget;
    private final UiContext uiContext;

    @RenderThread
    public static void init() {
        INSTANCE = new ScreenDispatcher();
        NeoForge.EVENT_BUS.register(INSTANCE);
    }

    @Nullable
    public static RenderTarget getRenderTarget() {
        return INSTANCE == null ? null : INSTANCE.renderTarget;
    }

    private ScreenDispatcher() {
        var window = Minecraft.getInstance().getWindow();
        renderTarget = new TextureTarget("Screen", window.getWidth(), window.getHeight(), true);
        uiContext = new UiContext();
    }

    @SubscribeEvent
    public void onResizeDisplay(ResizeDisplayEvent event) {
        renderTarget.resize(event.getWidth(), event.getHeight());
    }

    /**
     * 由 Main 线程调用喵, 生成 SubmittedCommand 列表喵
     */
    @SubscribeEvent
    public void onMainLoop(MainLoopEvent event) {
        var mc = Minecraft.getInstance();

        if (mc.screen instanceof RenderRoot screen) {
            var w = mc.getWindow();
            var m = mc.mouseHandler;
            uiContext.perform(screen.getRoot(), m.getScaledXPos(w), m.getScaledYPos(w),
                    mc.getDeltaTracker().getGameTimeDeltaTicks());
        }
    }

    /**
     * 由 Render 线程调用喵, 解析命令并绘制喵
     */
    @SubscribeEvent
    public void onRenderLoop(RenderLoopEvent event) {
        var mc = Minecraft.getInstance();

        if (mc.screen instanceof RenderRoot screen) {
            uiContext.upload(renderTarget, true, false);
            ImGuiUIDebugger.render(renderTarget, screen.getRoot());
        }
    }

    @SubscribeEvent
    public void onClientStopped(ClientStoppedEvent event) {
        uiContext.close();
        renderTarget.destroyBuffers();
    }
}