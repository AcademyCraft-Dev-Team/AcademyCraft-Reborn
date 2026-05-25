package org.academy.api.client.gui.widget

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.textures.GpuTextureView
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import org.academy.AcademyCraft
import org.academy.api.client.gui.command.DrawCommand
import org.academy.api.client.gui.command.ImageDrawCommand
import org.academy.api.client.gui.render.RenderContext

open class ImageWidget : AbstractWidget {
    protected var textureIdentifier: Identifier? = null

    protected var textureView: GpuTextureView? = null

    private var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
    protected var u0: Float = 0.0f
    protected var v0: Float = 0.0f
    protected var u1: Float = 1.0f
    protected var v1: Float = 1.0f
    var brightness: Float = 1.0f
        protected set
    var green: Float = 1.0f
        protected set
    var blue: Float = 1.0f
        protected set

    constructor()

    constructor(textureView: GpuTextureView?) {
        this.textureView = textureView
        textureIdentifier = null
    }

    constructor(textureIdentifier: Identifier?) {
        this.textureIdentifier = textureIdentifier
        textureView = null
    }

    fun resolveAndPrepareTexture() {
        if (textureView != null && !textureView!!.isClosed) return

        if (textureIdentifier == null) {
            textureView = null
            return
        }

        try {
            val texture = Minecraft.getInstance().textureManager.getTexture(textureIdentifier!!)
            textureView = texture.getTextureView()
        } catch (e: Exception) {
            logger.error("Failed to resolve texture view for {}", textureIdentifier, e)
            textureView = null
        }
    }

    override fun renderInternal(context: RenderContext) {
        super.renderInternal(context)
        resolveAndPrepareTexture()
        if (textureView == null) return

        val lp = layoutParams
        val paddedWidth = width - lp.paddingLeft - lp.paddingRight
        val paddedHeight = height - lp.paddingTop - lp.paddingBottom

        if (paddedWidth <= 0 || paddedHeight <= 0) return

        val finalAlpha = alpha * context.accumulatedAlpha

        context.pose().pushPose()
        run {
            context.pose().translate(lp.paddingLeft, lp.paddingTop, 0f)
            val command = generateDrawCommand(
                textureView!!, sampler, paddedWidth, paddedHeight, u0, v0, u1, v1,
                this.brightness, green, blue, finalAlpha
            )
            context.submit(command)
        }
        context.pose().popPose()
    }

    protected open fun generateDrawCommand(
        texture: GpuTextureView, sampler: GpuSampler,
        width: Float, height: Float,
        u0: Float, v0: Float, u1: Float, v1: Float,
        red: Float, green: Float, blue: Float, alpha: Float
    ): DrawCommand {
        return ImageDrawCommand(
            texture, sampler, width, height, u0, v0, u1, v1, red, green, blue, alpha
        )
    }

    fun setRed(red: Float): ImageWidget {
        this.brightness = red
        return this
    }

    fun setGreen(green: Float): ImageWidget {
        this.green = green
        return this
    }

    fun setBlue(blue: Float): ImageWidget {
        this.blue = blue
        return this
    }

    fun setSampler(mode: FilterMode, useMipmap: Boolean): ImageWidget {
        return setSampler(RenderSystem.getSamplerCache().getClampToEdge(mode, useMipmap))
    }

    fun setSampler(sampler: GpuSampler): ImageWidget {
        this.sampler = sampler
        return this
    }

    fun setTexture(textureView: GpuTextureView?): ImageWidget {
        this.textureView = textureView
        textureIdentifier = null
        requestLayout()
        return this
    }

    fun setTexture(textureLocation: Identifier?): ImageWidget {
        textureIdentifier = textureLocation
        textureView = null
        requestLayout()
        return this
    }

    fun setUv(u0: Float, v0: Float, u1: Float, v1: Float): ImageWidget {
        this.u0 = u0
        this.v0 = v0
        this.u1 = u1
        this.v1 = v1
        return this
    }

    fun setColor(red: Float, green: Float, blue: Float): ImageWidget {
        this.brightness = red
        this.green = green
        this.blue = blue
        return this
    }

    fun setColor(color: Int): ImageWidget {
        this.brightness = ARGB.red(color) / 255.0f
        green = ARGB.green(color) / 255.0f
        blue = ARGB.blue(color) / 255.0f
        return this
    }

    fun setBrightness(`val`: Float): ImageWidget {
        this.brightness = `val`
        green = `val`
        blue = `val`
        return this
    }

    companion object {
        private val logger = AcademyCraft.getLogger()
    }
}