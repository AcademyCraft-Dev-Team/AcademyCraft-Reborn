package org.academy.api.client.hud

import com.mojang.blaze3d.resource.RenderTargetDescriptor
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import net.minecraft.client.Minecraft
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import org.academy.AcademyCraft
import org.academy.AcademyCraftClient
import org.academy.api.client.Render
import org.academy.api.client.hud.ability.AbilityInfoHUD
import org.academy.api.client.hud.ability.AbilityInfoHUD.Companion.instance
import org.academy.api.client.hud.terminal.TerminalHUD
import org.academy.api.client.render.TextureBinding
import org.academy.api.client.render.post.BlurEffect
import org.academy.api.client.thread.RenderThread
import org.academy.api.client.vanilla.MainLoopEvent
import org.slf4j.Logger
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 负责管理本模组内置的 UI 帧缓冲区与 HUD 喵
 */
@EventBusSubscriber(Dist.CLIENT)
object HUDManager {
    val logger: Logger = AcademyCraft.getLogger()

    fun initRender() {
    }

    fun initMain() {
        TerminalHUD.initMain()
        AbilityInfoHUD.initMain()
    }

    @RenderThread
    fun resize(width: Int, height: Int) {
    }

    /**
     * 由 Main 线程调用喵, 生成 SubmittedCommand 列表喵
     */
    @SubscribeEvent
    fun onMainLoop(event: MainLoopEvent?) {
        if (!AcademyCraftClient.isRenderInitialized()) return

        val mc = Minecraft.getInstance()
        val w = mc.window
        val m = mc.mouseHandler
        val mouseX = m.getScaledXPos(w)
        val mouseY = m.getScaledYPos(w)
        val deltaPartialTick = mc.deltaTracker.getGameTimeDeltaPartialTick(false)
        TerminalHUD.instance.perform(mouseX, mouseY, deltaPartialTick)
        instance.perform(mouseX, mouseY, deltaPartialTick)
    }

    fun render() {
        if (!AcademyCraftClient.isRenderInitialized()) return

        val mc = Minecraft.getInstance()
        val main = mc.mainRenderTarget
        val width = main.width
        val height = main.height


        val pool = Render.Buffers.getResourcePool()
        val descTemp = RenderTargetDescriptor(width, height, true, 0, true)
        val descBlur = RenderTargetDescriptor(width, height, false, 0, false)

        val ui = pool.acquire(descTemp)
        val blur = pool.acquire(descBlur)

        try {
            val mainColor = main.getColorTextureView()
            val uiColor = ui.getColorTextureView()
            val uiDepth = ui.getDepthTextureView()
            val blurColor = blur.getColorTextureView()
            if (mainColor == null || uiColor == null || uiDepth == null || blurColor == null) return

            val drewStencil = AtomicBoolean()

            TerminalHUD.instance.render(width, height, uiColor, uiDepth, drewStencil)
            instance.render(ui)

            if (drewStencil.get()) {
                BlurEffect.apply(
                    width, height,
                    mainColor,
                    blurColor,
                    uiDepth,
                    20
                )
                Render.runBlitPass(
                    mainColor, Render.RenderPipelines.BLIT_SCREEN_WITH_BLEND,
                    Render.Buffers.getInstance().fsQuadVBNDC,
                    listOf(
                        TextureBinding(
                            "DiffuseSampler", blurColor,
                            RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
                        )
                    ), mutableListOf(),
                    false
                )
            }

            Render.runBlitPass(
                mainColor, Render.RenderPipelines.BLIT_SCREEN_PREMULTIPLIED_ALPHA,
                Render.Buffers.getInstance().fsQuadVBNDC,
                listOf(
                    TextureBinding(
                        "DiffuseSampler", uiColor,
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
                    )
                ), mutableListOf(),
                false
            )
        } finally {
            pool.release(descTemp, ui)
            pool.release(descBlur, blur)
        }
    }
}