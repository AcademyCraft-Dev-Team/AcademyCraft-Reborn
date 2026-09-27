package org.academy.api.client.gui.drawable

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.textures.FilterMode
import com.mojang.renderpearl.api.textures.GpuSampler
import com.mojang.renderpearl.api.textures.GpuTextureView
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

    var u0: Float = 0f
    var v0: Float = 0f

    var u1: Float = 0f
    var v1: Float = 1f

    var u2: Float = 1f
    var v2: Float = 1f

    var u3: Float = 1f
    var v3: Float = 0f

    constructor(texture: TextureSource) {
        textureSource = texture
    }

    constructor(texture: Identifier) {
        textureSource = IdentifierTextureSource(texture)
    }

    constructor(texture: GpuTextureView) {
        textureSource = GpuTextureViewSource(texture)
    }

    override fun draw(context: Canvas, widget: Widget) {
        val source = textureSource ?: return
        val view = source.getTextureView()
        if (view == null || view.isClosed) return

        drawPaddedContent(context, widget, tintColor) { ctx, width, height, r, g, b, alpha ->
            ctx.submit(
                ImageDrawCommand(
                    view, sampler, width, height,
                    u0, v0, u1, v1, u2, v2, u3, v3,
                    r, g, b, alpha
                )
            )
        }
    }

    fun setUv(u0: Float, v0: Float, u1: Float, v1: Float): TextureDrawable {
        return setUv(u0, v0, u0, v1, u1, v1, u1, v0)
    }

    fun setUv(u0: Float, v0: Float, u1: Float, v1: Float, u2: Float, v2: Float, u3: Float, v3: Float): TextureDrawable {
        this.u0 = u0
        this.v0 = v0

        this.u1 = u1
        this.v1 = v1

        this.u2 = u2
        this.v2 = v2

        this.u3 = u3
        this.v3 = v3
        return this
    }

    fun rotateUv(): TextureDrawable {
        return setUv(
            u0 = 1f - v0, v0 = u0,
            u1 = 1f - v1, v1 = u1,
            u2 = 1f - v2, v2 = u2,
            u3 = 1f - v3, v3 = u3
        )
    }

    fun flipUvX(): TextureDrawable {
        return setUv(
            u0 = u3, v0 = v0,
            u1 = u2, v1 = v1,
            u2 = u1, v2 = v2,
            u3 = u0, v3 = v3
        )
    }

    fun flipUvY(): TextureDrawable {
        return setUv(
            u0 = u0, v0 = v1,
            u1 = u1, v1 = v0,
            u2 = u2, v2 = v3,
            u3 = u3, v3 = v2
        )
    }
}
