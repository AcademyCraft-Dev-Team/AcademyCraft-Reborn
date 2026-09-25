package org.academy.api.client.gui.screen

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.renderpearl.api.GpuFormat
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppedEvent
import net.neoforged.neoforge.common.NeoForge
import org.academy.AcademyCraft
import org.academy.api.client.gui.command.SubmittedCommand
import org.academy.api.client.gui.environment.UiEnvironment
import org.academy.api.client.gui.imgui.ImGuiUIDebugger
import org.academy.api.client.gui.imgui.ImGuiUtilApi
import org.academy.api.client.gui.render.BlurRegion
import org.academy.api.client.gui.render.UiCompositor
import org.academy.api.client.gui.render.UiContext
import org.academy.api.client.render.post.BackdropBlurEngine
import org.academy.api.client.thread.RenderThread
import org.academy.api.client.vanilla.RenderLoopEvent
import org.academy.api.client.vanilla.ResizeDisplayEvent
import org.academy.api.client.vanilla.WorldCompositeEvent

class ScreenDispatcher private constructor() {
    private val renderTarget: RenderTarget
    private val aboveTarget: RenderTarget
    private val backdropBlur = BackdropBlurEngine()
    private val uiContext: UiContext

    @Volatile
    private var pendingLayers: List<Pair<List<SubmittedCommand>, List<BlurRegion>>> = emptyList()

    init {
        val window = Minecraft.getInstance().window
        renderTarget = TextureTarget("Screen", window.width, window.height, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT)
        aboveTarget =
            TextureTarget("Screen-above", window.width, window.height, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT)
        uiContext = UiContext()
    }

    @SubscribeEvent
    fun onResizeDisplay(event: ResizeDisplayEvent) {
        renderTarget.resize(event.width, event.height)
        aboveTarget.resize(event.width, event.height)
    }

    @SubscribeEvent
    fun onScreenRenderPre(event: ScreenEvent.Render.Pre) {
        val mc = Minecraft.getInstance()
        val screen = mc.gui.screen()
        if (screen == null || screen !== event.screen || screen !is RenderRoot) return
        val w = mc.window
        uiContext.perform(
            screen.root,
            mc.mouseHandler.getScaledXPos(w), mc.mouseHandler.getScaledYPos(w),
            mc.deltaTracker.gameTimeDeltaTicks
        )
    }

    @SubscribeEvent
    fun onRenderLoop(@Suppress("unused") event: RenderLoopEvent) {
        val mc = Minecraft.getInstance()
        val screen = mc.gui.screen()
        if (screen is RenderRoot) {
            val env = UiEnvironment.get()
            val regions = uiContext.blurRegions()
            if (regions.isEmpty()) {
                pendingLayers = emptyList()
                uiContext.upload(renderTarget, true)
            } else {
                val result = uiContext.splitSegments(regions)
                if (result != null) {
                    val (allCommands, segments) = result
                    if (segments.size >= 2) {
                        val (firstCommands, _) = segments[0]
                        val guiW = env.physicalWidth / env.guiScale
                        val guiH = env.physicalHeight / env.guiScale
                        uiContext.drawCommands(renderTarget, firstCommands, true, guiW, guiH)
                        pendingLayers = segments.drop(1)
                    } else {
                        pendingLayers = emptyList()
                        uiContext.drawCommands(
                            renderTarget, allCommands, true,
                            env.physicalWidth / env.guiScale,
                            env.physicalHeight / env.guiScale
                        )
                    }
                } else {
                    pendingLayers = emptyList()
                    uiContext.upload(renderTarget, true)
                }
            }
        }
    }

    @SubscribeEvent
    fun onWorldComposite(@Suppress("unused") event: WorldCompositeEvent) {
        val mc = Minecraft.getInstance()
        val screen = mc.gui.screen() as? RenderRoot ?: return
        val mainTarget = mc.gameRenderer.mainRenderTarget()
        val layers = pendingLayers
        if (layers.isNotEmpty()) {
            val guiW = UiEnvironment.get().physicalWidth / UiEnvironment.get().guiScale
            val guiH = UiEnvironment.get().physicalHeight / UiEnvironment.get().guiScale
            for ((commands, regions) in layers) {
                if (regions.isNotEmpty()) {
                    val mainView = mainTarget.getColorTextureView() ?: continue
                    backdropBlur.capture(mainView, regions.maxOf { it.radius })
                    for ((x, y, width, height, radius) in regions) {
                        backdropBlur.fillRegion(
                            mainView,
                            x, y, width, height,
                            radius,
                            UiCompositor.NEUTRAL_TINT
                        )
                    }
                }
                if (commands.isNotEmpty()) {
                    uiContext.drawCommands(aboveTarget, commands, true, guiW, guiH)
                    val mainView = mainTarget.getColorTextureView() ?: continue
                    val aboveView = aboveTarget.getColorTextureView() ?: continue
                    UiCompositor.blitSource(mainView, aboveView)
                }
            }
            pendingLayers = emptyList()
        }
        renderImGuiOverlay(mainTarget, screen)
    }

    private fun renderImGuiOverlay(target: RenderTarget, screen: RenderRoot) {
        if (!ImGuiUIDebugger.enabled) return
        ImGuiUtilApi.render(target) {
            ImGuiUIDebugger.renderContent(
                screen.root,
                Component.translatable(
                    "screen.academy.ui_debug.inspector.live_title",
                    screen.javaClass.simpleName
                ).string
            )
        }
    }

    @SubscribeEvent
    fun onClientStopped(@Suppress("unused") event: ClientStoppedEvent) {
        uiContext.close()
        backdropBlur.close()
        renderTarget.destroyBuffers()
        aboveTarget.destroyBuffers()
    }

    companion object {
        val logger = AcademyCraft.getLogger()
        private lateinit var INSTANCE: ScreenDispatcher

        @RenderThread
        fun init() {
            INSTANCE = ScreenDispatcher()
            NeoForge.EVENT_BUS.register(INSTANCE)
        }

        fun getRenderTarget(): RenderTarget {
            return INSTANCE.renderTarget
        }
    }
}
