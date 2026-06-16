package org.academy.internal.client.gui.imgui

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import imgui.ImGui
import imgui.extension.implot.ImPlot
import imgui.flag.ImGuiConfigFlags
import imgui.gl3.ImGuiImplGl3
import imgui.glfw.ImGuiImplGlfw
import net.minecraft.client.Minecraft
import org.jetbrains.annotations.ApiStatus
import java.util.*

/**
 * 你必须通过 ImGuiUtilApi 间接调用喵, 不能直接使用喵
 */
@ApiStatus.Internal
object ImGuiUtilInternal {
    val imGuiImplGl3: ImGuiImplGl3 = ImGuiImplGl3()
    val imGuiImplGlfw: ImGuiImplGlfw = ImGuiImplGlfw()

    fun init() {
        val handle = Minecraft.getInstance().window.handle()
        ImGui.createContext()
        ImPlot.createContext()

        val data = ImGui.getIO()
        data.fontGlobalScale = 1f
        data.configFlags = ImGuiConfigFlags.DockingEnable or ImGuiConfigFlags.NavEnableKeyboard

        imGuiImplGl3.init()
        imGuiImplGlfw.init(handle, true)
    }

    fun render(renderTarget: RenderTarget, renderCommand: () -> Unit) {
        val colorTextureView = renderTarget.getColorTextureView() ?: return
        RenderSystem.getDevice().createCommandEncoder().createRenderPass(
            { "ImGui" },
            colorTextureView,
            Optional.empty()
        ).use { _ ->
            imGuiImplGl3.newFrame()
            imGuiImplGlfw.newFrame()
            ImGui.newFrame()

            renderCommand()

            ImGui.render()
            imGuiImplGl3.renderDrawData(ImGui.getDrawData())
        }
    }

    fun clearEventsQueue() {
        ImGui.getIO().clearEventsQueue()
    }

    fun wantCaptureMouse(): Boolean {
        return ImGui.getIO().wantCaptureMouse
    }

    fun wantCaptureKeyboard(): Boolean {
        return ImGui.getIO().wantCaptureKeyboard
    }

    fun dispose() {
        imGuiImplGl3.shutdown()
        imGuiImplGlfw.shutdown()

        ImPlot.destroyContext()
        ImGui.destroyContext()
    }
}