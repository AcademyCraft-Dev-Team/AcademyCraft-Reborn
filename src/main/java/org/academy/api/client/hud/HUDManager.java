package org.academy.api.client.hud;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.logging.LogUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.academy.api.client.Render;
import org.academy.api.client.hud.terminal.DataTerminalHUD;
import org.academy.api.client.render.TextureBinding;
import org.academy.api.client.render.post.BlurEffect;
import org.academy.api.client.thread.MainThread;
import org.academy.api.client.thread.RenderThread;
import org.academy.api.client.vanilla.MainLoopEvent;
import org.slf4j.Logger;

import java.util.List;
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

        RenderTarget temp = null;
        RenderTarget last = null;
        var pool = Render.Buffers.getResourcePool();
        var descTemp = new RenderTargetDescriptor(width, height, true, 0, true);
        var descLast = new RenderTargetDescriptor(width, height, false, 0, false);

        try {
            temp = pool.acquire(descTemp);
            last = pool.acquire(descLast);

            var mainColor = main.getColorTextureView();
            var uiColor = temp.getColorTextureView();
            var uiDepth = temp.getDepthTextureView();
            var lastColor = last.getColorTextureView();
            if (mainColor == null || uiColor == null || uiDepth == null || lastColor == null) return;

            var drew = new AtomicBoolean();

            DataTerminalHUD.render(width, height, uiColor, uiDepth, drew);

            if (!drew.get()) return;

            BlurEffect.apply(
                    width, height,
                    mainColor,
                    lastColor,
                    uiDepth,
                    20.0f
            );

            Render.runBlitPassNDC(
                    lastColor, Render.RenderPipelines.BLIT_SCREEN_WITH_BLEND,
                    List.of(new TextureBinding("DiffuseSampler", uiColor,
                            RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST))
                    ),
                    List.of(),
                    false
            );

            guiGraphics.submitBlit(
                    Render.RenderPipelines.IMAGE, lastColor,
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST),
                    0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight(),
                    0, 1, 1, 0,
                    -1
            );
        } finally {
            if (temp != null) pool.release(descTemp, temp);
            if (last != null) pool.release(descLast, last);
        }
    }

    private HUDManager() {
    }
}