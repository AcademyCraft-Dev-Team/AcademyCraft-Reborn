package org.academy.api.client.gui.screen

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.pipeline.TextureTarget
import net.minecraft.client.Minecraft
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppedEvent
import net.neoforged.neoforge.common.NeoForge
import org.academy.AcademyCraft
import org.academy.api.client.gui.imgui.ImGuiUIDebugger
import org.academy.api.client.gui.render.UiContext
import org.academy.api.client.thread.RenderThread
import org.academy.api.client.vanilla.MainLoopEvent
import org.academy.api.client.vanilla.RenderLoopEvent
import org.academy.api.client.vanilla.ResizeDisplayEvent

class ScreenDispatcher private constructor() {
    private val renderTarget: RenderTarget
    private val uiContext: UiContext

    init {
        val window = Minecraft.getInstance().window
        renderTarget = TextureTarget("Screen", window.width, window.height, true)
        uiContext = UiContext()
    }

    @SubscribeEvent
    fun onResizeDisplay(event: ResizeDisplayEvent) {
        renderTarget.resize(event.width, event.height)
    }

    /**
     * 由 Main 线程调用喵, 生成 SubmittedCommand 列表喵
     */
    @SubscribeEvent
    fun onMainLoop(@Suppress("unused") event: MainLoopEvent) {
        val mc = Minecraft.getInstance()
        val screen = mc.screen
        if (screen is RenderRoot) {
            val w = mc.window
            val m = mc.mouseHandler
            uiContext.perform(
                screen.root, m.getScaledXPos(w), m.getScaledYPos(w),
                mc.deltaTracker.gameTimeDeltaTicks
            )
        }
    }

    /**
     * 由 Render 线程调用喵, 解析命令并绘制喵
     */
    @SubscribeEvent
    fun onRenderLoop(@Suppress("unused") event: RenderLoopEvent) {
        val mc = Minecraft.getInstance()
        val screen = mc.screen
        if (screen is RenderRoot) {
            uiContext.upload(renderTarget, true)
            ImGuiUIDebugger.render(renderTarget, screen.root)
        }
    }

    @SubscribeEvent
    fun onClientStopped(@Suppress("unused") event: ClientStoppedEvent) {
        uiContext.close()
        renderTarget.destroyBuffers()
    }

    companion object {
        val logger = AcademyCraft.getLogger()
        private var INSTANCE: ScreenDispatcher? = null

        @RenderThread
        fun init() {
            INSTANCE = ScreenDispatcher()
            NeoForge.EVENT_BUS.register(INSTANCE)
        }

        fun getRenderTarget(): RenderTarget? {
            return INSTANCE?.renderTarget
        }
    }
}