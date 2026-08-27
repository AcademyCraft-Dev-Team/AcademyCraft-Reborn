package org.academy.api.client.gui.render

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTextureView
import org.academy.api.client.render.Render
import org.academy.api.client.render.TextureBinding
import org.academy.api.client.render.post.BackdropBlurEngine
import org.joml.Vector4f

/**
 * 通用合成器喵: 在已绘制完「背景 + 下方内容」的 [RenderTarget] 上就地完成
 * 「逐区域模糊 + 上方内容叠加」喵. 不认识 MC 主缓冲 / host / GameRenderer 时序;
 * 调用方保证 [composite] 执行时 [RenderTarget] 内容即模糊面板背后的完整背景.
 */
object UiCompositor {
    internal val NEUTRAL_TINT = Vector4f(1f, 1f, 1f, 1f)

    /**
     * 就地合成: 把 [target] 中每个 [BlurRegion] 区域替换为其内容的模糊版,
     * 最后把上方内容 [above] 整层叠回, 产出最终帧.
     *
     * @param target 已含背景与下方内容的帧缓冲 (就地读改写).
     * @param above  上方内容纹理 (由 UiContext 用 above 命令渲染).
     */
    fun composite(
        target: RenderTarget,
        above: GpuTextureView,
        regions: List<BlurRegion>,
        blur: BackdropBlurEngine,
    ) {
        if (regions.isEmpty()) return
        val targetView = target.getColorTextureView() ?: return

        blur.capture(targetView, regions.maxOf { it.radius })

        for (region in regions) {
            blur.fillRegion(
                targetView,
                region.x, region.y, region.width, region.height,
                region.radius,
                NEUTRAL_TINT
            )
        }
        blitSource(targetView, above)
    }

    /**
     * 逐层合成: 把 [source] 中每个 [BlurRegion] 区域替换为 [target] 累积内容的模糊版,
     * 然后把 source 整层叠到 [target] 上喵.
     *
     * 嵌套模糊时, [target] 已含之前各层的累积结果,
     * pyramid 从 [target] 采样保证嵌套正确.
     *
     * @param target 已含之前各层累积结果的帧缓冲 (就地读改写).
     * @param source 当前层的内容纹理.
     */
    fun compositeLayer(
        target: RenderTarget,
        source: GpuTextureView,
        regions: List<BlurRegion>,
        blur: BackdropBlurEngine,
    ) {
        if (regions.isEmpty()) {
            val targetView = target.getColorTextureView() ?: return
            blitSource(targetView, source)
            return
        }

        val targetView = target.getColorTextureView() ?: return

        blur.capture(targetView, regions.maxOf { it.radius })

        for (region in regions) {
            blur.fillRegion(
                targetView,
                region.x, region.y, region.width, region.height,
                region.radius,
                NEUTRAL_TINT
            )
        }
        blitSource(targetView, source)
    }

    fun blitSource(target: GpuTextureView, source: GpuTextureView) {
        Render.runBlitPass(
            target, null, false, false,
            Render.RenderPipelines.BLIT_SCREEN_PREMULTIPLIED_ALPHA,
            Render.Buffers.getInstance().fsQuadVBNDC,
            listOf(
                TextureBinding(
                    "Sampler0", source,
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
                )
            ),
            emptyList()
        )
    }
}
