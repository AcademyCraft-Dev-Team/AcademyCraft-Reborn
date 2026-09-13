package org.academy.api.client.gui.drawable

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.textures.GpuTextureView
import net.minecraft.resources.Identifier
import org.academy.api.client.gui.command.ImageDrawCommand
import org.academy.api.client.gui.render.Canvas
import org.academy.api.client.gui.texture.GpuTextureViewSource
import org.academy.api.client.gui.texture.IdentifierTextureSource
import org.academy.api.client.gui.texture.TextureSource
import org.academy.api.client.gui.widget.Widget

open class TextureDrawable : Drawable {
    private val textureSource: TextureSource?

    var sampler: GpuSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)

    var tintColor: Int = -0x1

    constructor(textureLocation: Identifier?) {
        textureSource = textureLocation?.let { IdentifierTextureSource(it) }
    }

    constructor(texture: GpuTextureView?) {
        textureSource = GpuTextureViewSource(texture)
    }

    override fun draw(context: Canvas, widget: Widget) {
        val source = textureSource ?: return
        val view = source.getTextureView()
        if (view == null || view.isClosed) return

        drawPaddedContent(context, widget, tintColor) { ctx, width, height, r, g, b, alpha ->
            ctx.submit(ImageDrawCommand(view, sampler, width, height, 0f, 0f, 1f, 1f, r, g, b, alpha))
        }
    }
}