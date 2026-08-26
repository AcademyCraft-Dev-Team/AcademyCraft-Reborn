package org.academy.api.client.gui.screen

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.pipeline.TextureTarget
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppedEvent
import net.neoforged.neoforge.common.NeoForge
import org.academy.AcademyCraft
import org.academy.AcademyCraftClient
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
import org.academy.internal.client.gui.debug.SerializedUiDebugHost
import org.academy.internal.client.gui.debug.UiDebugSession

class ScreenDispatcher private constructor() {
    private val renderTarget: RenderTarget
    private val aboveTarget: RenderTarget
    private val backdropBlur = BackdropBlurEngine()
    private val uiContext: UiContext

    /**
     * 本帧模糊区域快照喵. [onRenderLoop] 写入, [onWorldComposite] 消费,
     * 保证同帧内单 pass/split 与合成的判定一致 (主线程并发更新 uiContext 时不串帧).
     */
    @Volatile
    private var frameBlurRegions: List<BlurRegion> = emptyList()

    init {
        val window = Minecraft.getInstance().window
        renderTarget = TextureTarget("Screen", window.width, window.height, true, GpuFormat.RGBA8_UNORM)
        aboveTarget = TextureTarget("Screen-above", window.width, window.height, true, GpuFormat.RGBA8_UNORM)
        uiContext = UiContext()
    }

    @SubscribeEvent
    fun onResizeDisplay(event: ResizeDisplayEvent) {
        renderTarget.resize(event.width, event.height)
        aboveTarget.resize(event.width, event.height)
    }

    /**
     * 由主线程在每帧输入/setScreen 处理完毕、GUI 提取开始前调用喵 ([ScreenEvent.Render.Pre]),
     * 为当前 screen 生成 SubmittedCommand 列表与模糊区域喵.
     * 相位晚于输入, 保证切屏首帧即生成本帧内容, 不残留上一 screen 的缓存.
     */
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

    /**
     * 由 Render 线程调用喵, 解析命令并绘制喵.
     *
     * 无模糊区时直接单 pass 渲染到 [renderTarget] (自包含帧); 有模糊区时按模糊区域切分:
     * 下方内容照常进 [renderTarget] (经 vanilla GUI 管线叠加在原版屏幕背景上),
     * 上方内容暂存 [aboveTarget], 待 [WorldCompositeEvent] (GUI 渲染完成后) 就地合成.
     */
    @SubscribeEvent
    fun onRenderLoop(@Suppress("unused") event: RenderLoopEvent) {
        val mc = Minecraft.getInstance()
        val screen = mc.gui.screen()
        if (screen is RenderRoot) {
            val env = UiEnvironment.get()
            val regions = uiContext.blurRegions()
            frameBlurRegions = regions
            if (regions.isEmpty()) {
                uiContext.upload(renderTarget, true)
            } else {
                uiContext.uploadSplit(
                    renderTarget, true,
                    env.physicalWidth / env.guiScale,
                    env.physicalHeight / env.guiScale,
                    aboveTarget, regions
                )
            }
        }
    }

    /**
     * GUI 渲染完成 (主缓冲已含世界 + 原版屏幕背景 + Academy 下方内容):
     * 以主缓冲为模糊源就地烘焙模糊区域并叠回上方内容, 再叠加 ImGui 调试层.
     */
    @SubscribeEvent
    fun onWorldComposite(@Suppress("unused") event: WorldCompositeEvent) {
        val mc = Minecraft.getInstance()
        val screen = mc.gui.screen() as? RenderRoot ?: return
        val mainTarget = mc.gameRenderer.mainRenderTarget()
        val regions = frameBlurRegions
        if (regions.isNotEmpty()) {
            val aboveView = aboveTarget.getColorTextureView() ?: return
            UiCompositor.composite(mainTarget, aboveView, regions, backdropBlur)
        }
        renderImGuiOverlay(mainTarget, screen)
    }

    private fun renderImGuiOverlay(target: RenderTarget, screen: RenderRoot) {
        ImGuiUtilApi.render(target) {
            val host = screen as? SerializedUiDebugHost
            if (AcademyCraftClient.isUiDebugEnvironment() && host != null && UiDebugSession.shouldAttach(host)) {
                ImGuiUIDebugger.renderContent(
                    host.debugLayoutRoot(),
                    true,
                    Component.translatable(
                        "screen.academy.ui_debug.inspector.live_title",
                        host.debugLayoutId()
                    ).string
                )
                UiDebugSession.capture(host)
            }
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
