package org.academy.api.client.gui.widget

import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import org.academy.api.client.gui.render.RenderContext

class ParallaxImageWidget(x: Float, y: Float, width: Float, height: Float, texture: Identifier) : ImageWidget(texture) {
    private var parallaxFactorX: Float = 0.5f
    private var parallaxFactorY: Float = 0.5f

    private var imageToViewRatioWidth: Float = 0.9f
    private var imageToViewRatioHeight: Float = 0.9f

    override fun render(context: RenderContext) {
        val anchorX = getAbsoluteX() + width / 2.0f
        val anchorY = getAbsoluteY() + height / 2.0f

        val mc = Minecraft.getInstance()
        val mh = mc.mouseHandler
        val w = mc.window
        val mouseX = mh.getScaledXPos(w)
        val mouseY = mh.getScaledYPos(w)

        val deviationX = ((mouseX - anchorX) / anchorX).toFloat()
        val deviationY = ((mouseY - anchorY) / anchorY).toFloat()

        val motionX = Mth.clamp(deviationX * parallaxFactorX, -1.0f, 1.0f)
        val motionY = Mth.clamp(deviationY * parallaxFactorY, -1.0f, 1.0f)

        val maxUOffset = 1.0f - imageToViewRatioWidth
        val maxVOffset = 1.0f - imageToViewRatioHeight

        val uOffset = (motionX + 1.0f) / 2.0f * maxUOffset
        val vOffset = (motionY + 1.0f) / 2.0f * maxVOffset

        setUv(uOffset, vOffset, uOffset + imageToViewRatioWidth, vOffset + imageToViewRatioHeight)

        super.render(context)
    }

    fun setParallaxFactor(parallaxFactorX: Float, parallaxFactorY: Float): ParallaxImageWidget {
        this.parallaxFactorX = parallaxFactorX
        this.parallaxFactorY = parallaxFactorY
        return this
    }

    fun setImageToViewRatio(imageToViewRatioWidth: Float, imageToViewRatioHeight: Float): ParallaxImageWidget {
        this.imageToViewRatioWidth = Mth.clamp(imageToViewRatioWidth, 0f, 1f)
        this.imageToViewRatioHeight = Mth.clamp(imageToViewRatioHeight, 0f, 1f)
        return this
    }
}