package org.academy.api.client.hud;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.api.client.Render;
import org.academy.api.client.hud.ability.AbilityInfoHUD;
import org.academy.api.client.hud.terminal.TerminalHUD;
import org.academy.api.client.render.TextureBinding;
import org.academy.api.client.render.post.BlurEffect;
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
    public static final Logger LOGGER = AcademyCraft.getLogger();

    private HUDManager() {
    }

    public static void initRender() {
    }

    public static void initMain() {
        TerminalHUD.initMain();
        AbilityInfoHUD.initMain();
    }

    @RenderThread
    public static void resize(int width, int height) {
    }

    /**
     * 由 Main 线程调用喵, 生成 SubmittedCommand 列表喵
     */
    @SubscribeEvent
    public static void onMainLoop(MainLoopEvent event) {
        if (!AcademyCraftClient.isRenderInitialized()) return;

        var mc = Minecraft.getInstance();
        var w = mc.getWindow();
        var m = mc.mouseHandler;
        var mouseX = m.getScaledXPos(w);
        var mouseY = m.getScaledYPos(w);
        var deltaPartialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        TerminalHUD.getInstance().perform(mouseX, mouseY, deltaPartialTick);
        AbilityInfoHUD.getInstance().perform(mouseX, mouseY, deltaPartialTick);
    }

    public static void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (!AcademyCraftClient.isRenderInitialized()) return;

        var mc = Minecraft.getInstance();
        var main = mc.getMainRenderTarget();
        var width = main.width;
        var height = main.height;

        RenderTarget ui = null;
        RenderTarget last = null;
        var pool = Render.Buffers.getResourcePool();
        var descTemp = new RenderTargetDescriptor(width, height, true, 0, true);
        var descLast = new RenderTargetDescriptor(width, height, false, 0, false);

        try {
            ui = pool.acquire(descTemp);
            last = pool.acquire(descLast);

            var mainColor = main.getColorTextureView();
            var uiColor = ui.getColorTextureView();
            var uiDepth = ui.getDepthTextureView();
            var lastColor = last.getColorTextureView();
            if (mainColor == null || uiColor == null || uiDepth == null || lastColor == null) return;

            var drewStencil = new AtomicBoolean();

            TerminalHUD.getInstance().render(width, height, uiColor, uiDepth, drewStencil);
            AbilityInfoHUD.getInstance().render(ui);

            if (drewStencil.get()) {
                BlurEffect.apply(
                        width, height,
                        mainColor,
                        lastColor,
                        uiDepth,
                        20.0f
                );
            }

            /*
             * BlurEffect 仅支持渲染到 TextureView 喵
             * 为了兼容原版 GUI 层级需要最终使用 GuiGraphics 渲染喵
             * 如果 blur 渲染到 main, ui 通过 GuiGraphics 渲染, 会导致 blur 与 ui 有一单位左右的像素偏差喵
             * 解决方案为将 blur 和 ui 绘制到 last, last 最终通过 GuiGraphics 渲染喵
             */
            var textures = List.of(new TextureBinding("DiffuseSampler", uiColor,
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST))
            );
            Render.runBlitPass(
                    lastColor, Render.RenderPipelines.BLIT_SCREEN_WITH_BLEND,
                    Render.Buffers.getInstance().getFSQuadVBNDC(),
                    textures, List.of(),
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
            if (ui != null) pool.release(descTemp, ui);
            if (last != null) pool.release(descLast, last);
        }
    }
}