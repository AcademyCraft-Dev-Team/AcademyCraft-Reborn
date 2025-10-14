package org.academy.internal.client.gui.imgui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import imgui.ImGui;
import imgui.extension.implot.ImPlot;
import imgui.flag.ImGuiConfigFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.ApiStatus;

import java.util.OptionalInt;

/**
 * 你必须通过 ImGuiUtilApi 间接调用喵, 不能直接使用喵
 */
@ApiStatus.Internal
public final class ImGuiUtilInternal {
    private static final ImGuiImplGl3 IM_GUI_IMPL_GL_3 = new ImGuiImplGl3();
    private static final ImGuiImplGlfw IM_GUI_IMPL_GLFW = new ImGuiImplGlfw();

    public static ImGuiImplGl3 getImGuiImplGl3() {
        return IM_GUI_IMPL_GL_3;
    }

    public static ImGuiImplGlfw getImGuiImplGlfw() {
        return IM_GUI_IMPL_GLFW;
    }

    public static void init() {
        var handle = Minecraft.getInstance().getWindow().handle();
        ImGui.createContext();
        ImPlot.createContext();

        var data = ImGui.getIO();
        data.setFontGlobalScale(1F);
        data.setConfigFlags(ImGuiConfigFlags.DockingEnable | ImGuiConfigFlags.NavEnableKeyboard);

        getImGuiImplGl3().init();
        getImGuiImplGlfw().init(handle, true);
    }

    public static void render(RenderTarget renderTarget, Runnable renderCommand) {
        var colorTextureView = renderTarget.getColorTextureView();
        if (colorTextureView == null) return;
        try (
                var ignored = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                        () -> "ImGui",
                        colorTextureView,
                        OptionalInt.empty()
                )
        ) {
            getImGuiImplGl3().newFrame();
            getImGuiImplGlfw().newFrame();
            ImGui.newFrame();

            renderCommand.run();

            ImGui.render();
            getImGuiImplGl3().renderDrawData(ImGui.getDrawData());
        }
    }

    public static void clearEventsQueue() {
        ImGui.getIO().clearEventsQueue();
    }

    public static void dispose() {
        getImGuiImplGl3().shutdown();
        getImGuiImplGlfw().shutdown();

        ImPlot.destroyContext();
        ImGui.destroyContext();
    }

    private ImGuiUtilInternal() {
    }
}