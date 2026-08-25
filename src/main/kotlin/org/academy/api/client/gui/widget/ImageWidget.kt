package org.academy.api.client.gui.widget

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.textures.GpuTextureView
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import org.academy.AcademyCraft
import org.academy.api.client.gui.command.DrawCommand
import org.academy.api.client.gui.command.ImageDrawCommand
import org.academy.api.client.gui.environment.UiEnvironment
import org.academy.api.client.gui.render.RenderContext

open class ImageWidget : AbstractWidget {
    var textureIdentifier: Identifier? = null

    protected var textureView: GpuTextureView? = null

    private var sampler: GpuSampler? = null

    fun getSampler(): GpuSampler {
        return sampler ?: RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST).also {
            sampler = it
        }
    }

    var u0: Float = 0f
    var v0: Float = 0f

    var u1: Float = 0f
    var v1: Float = 1f

    var u2: Float = 1f
    var v2: Float = 1f

    var u3: Float = 1f
    var v3: Float = 0f

    var brightness: Float = 1.0f
    var green: Float = 1.0f
    var blue: Float = 1.0f

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
            textureView = UiEnvironment.get().loadTexture(textureIdentifier!!)
        } catch (e: Exception) {
            logger.error("Failed to resolve texture view for {}", textureIdentifier, e)
            textureView = null
        }
    }

    override fun renderInternal(context: RenderContext) {
        background?.draw(context, this)
        resolveAndPrepareTexture()
        val textureView = textureView ?: return

        val lp = layoutParams
        val paddedWidth = width - lp.paddingLeft - lp.paddingRight
        val paddedHeight = height - lp.paddingTop - lp.paddingBottom

        if (paddedWidth <= 0 || paddedHeight <= 0) return

        val finalAlpha = alpha * context.accumulatedAlpha

        context.pose().pushPose()
        run {
            context.pose().translate(lp.paddingLeft, lp.paddingTop)
            val command = generateDrawCommand(
                textureView, getSampler(), paddedWidth, paddedHeight, u0, v0, u1, v1, u2, v2, u3, v3,
                this.brightness, green, blue, finalAlpha
            )
            context.submit(command)
        }
        context.pose().popPose()
        foreground?.draw(context, this)
    }

    protected open fun generateDrawCommand(
        texture: GpuTextureView, sampler: GpuSampler,
        width: Float, height: Float,
        u0: Float, v0: Float, u1: Float, v1: Float, u2: Float, v2: Float, u3: Float, v3: Float,
        red: Float, green: Float, blue: Float, alpha: Float
    ): DrawCommand {
        return ImageDrawCommand(
            texture, sampler, width, height, u0, v0, u1, v1, u2, v2, u3, v3, red, green, blue, alpha
        )
    }

    fun setRed(red: Float): ImageWidget {
        if (this.brightness != red) {
            this.brightness = red
            invalidate()
        }
        return this
    }

    fun setGreen(green: Float): ImageWidget {
        if (this.green != green) {
            this.green = green
            invalidate()
        }
        return this
    }

    fun setBlue(blue: Float): ImageWidget {
        if (this.blue != blue) {
            this.blue = blue
            invalidate()
        }
        return this
    }

    fun setSampler(mode: FilterMode, useMipmap: Boolean): ImageWidget {
        return setSampler(RenderSystem.getSamplerCache().getClampToEdge(mode, useMipmap))
    }

    fun setSampler(sampler: GpuSampler?): ImageWidget {
        if (this.sampler != sampler) {
            this.sampler = sampler
            invalidate()
        }
        return this
    }

    fun getTextureLocation(): Identifier? = textureIdentifier

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
        return setUv(u0, v0, u0, v1, u1, v1, u1, v0)
    }

    fun setUv(u0: Float, v0: Float, u1: Float, v1: Float, u2: Float, v2: Float, u3: Float, v3: Float): ImageWidget {
        if (this.u0 != u0 || this.v0 != v0 || this.u1 != u1 || this.v1 != v1 ||
            this.u2 != u2 || this.v2 != v2 || this.u3 != u3 || this.v3 != v3
        ) {
            this.u0 = u0
            this.v0 = v0

            this.u1 = u1
            this.v1 = v1

            this.u2 = u2
            this.v2 = v2

            this.u3 = u3
            this.v3 = v3
            invalidate()
        }
        return this
    }

    fun setColor(red: Float, green: Float, blue: Float): ImageWidget {
        if (this.brightness != red || this.green != green || this.blue != blue) {
            this.brightness = red
            this.green = green
            this.blue = blue
            invalidate()
        }
        return this
    }

    fun setColor(color: Int): ImageWidget {
        return setColor(
            ARGB.red(color) / 255.0f,
            ARGB.green(color) / 255.0f,
            ARGB.blue(color) / 255.0f
        )
    }

    fun setBrightness(value: Float): ImageWidget {
        if (this.brightness != value || green != value || blue != value) {
            this.brightness = value
            green = value
            blue = value
            invalidate()
        }
        return this
    }

    fun rotateUv(): ImageWidget {
        return setUv(
            u0 = 1f - v0, v0 = u0,
            u1 = 1f - v1, v1 = u1,
            u2 = 1f - v2, v2 = u2,
            u3 = 1f - v3, v3 = u3
        )
    }

    companion object {
        private val logger = AcademyCraft.getLogger()
    }
}
