package org.academy.api.client.gui.drawable

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.textures.GpuTextureView
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import org.academy.AcademyCraft
import org.academy.api.client.gui.command.ImageDrawCommand
import org.academy.api.client.gui.render.RenderContext
import org.academy.api.client.gui.widget.Widget

open class TextureDrawable : Drawable {
    protected val textureLocation: Identifier?

    protected var texture: GpuTextureView?

    protected var sampler: GpuSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)

    var tintColor: Int = -0x1

    constructor(textureLocation: Identifier?) {
        this.textureLocation = textureLocation
        texture = null
    }

    constructor(texture: GpuTextureView?) {
        textureLocation = null
        this.texture = texture
    }

    override fun draw(context: RenderContext, widget: Widget) {
        resolveAndPrepareTexture()
        if (texture == null) return

        val lp = widget.layoutParams
        val paddedWidth = widget.width - lp.paddingLeft - lp.paddingRight
        val paddedHeight = widget.height - lp.paddingTop - lp.paddingBottom

        if (paddedWidth <= 0 || paddedHeight <= 0) return

        val baseAlpha = ARGB.alpha(tintColor) / 255.0f
        val finalAlpha = baseAlpha * widget.getAbsoluteAlpha()

        if (finalAlpha <= 0) return

        val r = ARGB.red(tintColor) / 255.0f
        val g = ARGB.green(tintColor) / 255.0f
        val b = ARGB.blue(tintColor) / 255.0f

        context.pose().pushPose()
        context.pose().translate(lp.paddingLeft, lp.paddingTop)

        val command =
            ImageDrawCommand(texture!!, sampler, paddedWidth, paddedHeight, 0f, 0f, 1f, 1f, r, g, b, finalAlpha)
        context.submit(command)

        context.pose().popPose()
    }

    private fun resolveAndPrepareTexture() {
        if (texture != null && !texture!!.isClosed) return
        if (textureLocation == null) {
            texture = null
            return
        }

        try {
            val texture = Minecraft.getInstance().textureManager.getTexture(textureLocation)
            this.texture = texture.getTextureView()
        } catch (e: Exception) {
            logger.error("Failed to resolve texture view for {}", textureLocation, e)
            texture = null
        }
    }

    companion object {
        private val logger = AcademyCraft.getLogger()
    }
}