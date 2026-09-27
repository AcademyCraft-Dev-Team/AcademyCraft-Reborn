package org.academy.api.client.gui.render

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.textures.FilterMode
import com.mojang.renderpearl.api.textures.GpuTextureView
import org.academy.api.client.render.Render
import org.academy.api.client.render.TextureBinding
import org.academy.api.client.render.post.BackdropBlurEngine
import org.joml.Vector4f

object UiCompositor {
    internal val NEUTRAL_TINT = Vector4f(1f, 1f, 1f, 1f)

    fun composite(
        target: RenderTarget,
        above: GpuTextureView,
        regions: List<BlurRegion>,
        blur: BackdropBlurEngine,
    ) {
        if (regions.isEmpty()) return
        val targetView = applyBlurRegions(target, regions, blur) ?: return

        blitSource(targetView, above)
    }

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

        val targetView = applyBlurRegions(target, regions, blur) ?: return

        blitSource(targetView, source)
    }

    private fun applyBlurRegions(
        target: RenderTarget,
        regions: List<BlurRegion>,
        blur: BackdropBlurEngine,
    ): GpuTextureView? {
        val targetView = target.getColorTextureView() ?: return null

        blur.capture(targetView, regions.maxOf { it.radius })

        for ((x, y, width, height, radius) in regions) {
            blur.fillRegion(
                targetView,
                x, y, width, height,
                radius,
                NEUTRAL_TINT
            )
        }
        return targetView
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
