package org.academy.api.client.gui.screen;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.academy.AcademyCraft;
import org.academy.api.client.gui.imgui.ImGuiUIDebugger;
import org.academy.api.client.vanilla.MainLoopEvent;
import org.academy.api.client.vanilla.RenderLoopEvent;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class ScreenRenderer {
    /**
     * 由 Main 线程调用喵, 生成 SubmittedCommand 列表喵
     */
    @SubscribeEvent
    public static void onMainLoop(MainLoopEvent event) {
        var mc = Minecraft.getInstance();

        if (mc.screen instanceof IUIScreen screen) {
            var w = mc.getWindow();
            var m = mc.mouseHandler;

            screen.getUIRenderContext().perform(screen.getRootContainer(), m.getScaledXPos(w), m.getScaledYPos(w), mc.getDeltaTracker().getGameTimeDeltaTicks());
        }
    }

    /**
     * 由 Render 线程调用喵, 解析命令并绘制喵
     */
    @SubscribeEvent
    public static void onRenderLoop(RenderLoopEvent event) {
        var mc = Minecraft.getInstance();

        if (mc.screen instanceof IUIScreen screen) {
            if (screen.getRenderTarget() == null) return;
            screen.getUIRenderContext().upload(screen.getRenderTarget());
            ImGuiUIDebugger.render(screen.getRenderTarget(), screen.getRootContainer());
        }
    }

    private ScreenRenderer() {
    }
}