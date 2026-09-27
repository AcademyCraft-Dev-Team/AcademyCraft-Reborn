package org.academy.internal.client.profiler

import com.mojang.blaze3d.platform.InputConstants
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.NeoForge
import org.academy.api.client.input.InputSystem
import org.academy.api.client.thread.RenderThread
import org.academy.api.client.vanilla.RenderLoopEvent
import org.academy.api.common.profiler.AcademyProfiler
import org.academy.api.common.profiler.FrameStats

/**
 * 客户端剖析接入：注册渲染线程、记录帧时间、注册窗口开关键位。
 */
object ProfilerClientHooks {
    const val KEY_PROFILER_WINDOW: String = "profiler_window_toggle"

    private var lastFrameNanos = 0L

    fun initMain() {
        AcademyProfiler.registerThread(Thread.currentThread())
        NeoForge.EVENT_BUS.register(this)

        InputSystem.addKeyBinding(
            KEY_PROFILER_WINDOW,
            InputSystem.combo(
                InputSystem.InputType.KEYBOARD,
                InputConstants.KEY_F8,
                InputConstants.PRESS
            )
        ) { ImGuiProfilerWindow.toggle() }
    }

    @SubscribeEvent
    fun onRenderLoop(@Suppress("unused") event: RenderLoopEvent) {
        val now = System.nanoTime()
        if (lastFrameNanos != 0L) {
            val runtime = Runtime.getRuntime()
            FrameStats.recordFrame(now - lastFrameNanos, runtime.totalMemory() - runtime.freeMemory())
        }
        lastFrameNanos = now
    }

    @RenderThread
    fun renderOverlay() {
        ImGuiProfilerWindow.renderToMainScreen()
    }
}
