package org.academy.internal.client.gui.world

import net.minecraft.client.renderer.MultiBufferSource
import org.academy.api.client.gui.widget.AbstractWidgetContainer
import org.academy.api.client.gui.widget.FrameLayoutWidget
import org.academy.api.client.render.MatrixStack

class WindGenWorldGui {
    val rootContainer: AbstractWidgetContainer = FrameLayoutWidget()
    var mouseX: Double = 0.0
    var mouseY: Double = 0.0

    fun render(stack: MatrixStack, bufferSource: MultiBufferSource, partialTicks: Float) {
    }

    fun onInit() {
    }

    companion object {
        const val WIDTH: Float = 640f
        const val HEIGHT: Float = 400f
    }
}