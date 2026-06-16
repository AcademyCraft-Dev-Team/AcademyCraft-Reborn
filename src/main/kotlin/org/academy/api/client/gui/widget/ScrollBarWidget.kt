package org.academy.api.client.gui.widget

import net.minecraft.util.ARGB
import net.minecraft.util.Mth
import org.academy.api.client.gui.command.FillRectDrawCommand
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.render.RenderContext

open class ScrollBarWidget(protected val panel: ScrollPanelWidget, orientation: Orientation) :
    DragBarWidget(orientation) {
    override fun render(context: RenderContext) {
        if (!isVisible()) return

        val finalAlpha = getAbsoluteAlpha() * context.accumulatedAlpha

        context.pose().pushPose()
        run {
            if (isShowBackground) renderTrack(context, finalAlpha)
            renderThumb(context, finalAlpha)
        }
        context.pose().popPose()
    }

    private fun renderTrack(context: RenderContext, finalAlpha: Float) {
        val trackAlpha = ARGB.alpha(trackColor) / 255.0f * finalAlpha
        val r = ARGB.red(trackColor) / 255.0f
        val g = ARGB.green(trackColor) / 255.0f
        val b = ARGB.blue(trackColor) / 255.0f
        val trackCommand = FillRectDrawCommand(width, height, r, g, b, trackAlpha)
        context.submit(trackCommand)
    }

    private fun renderThumb(context: RenderContext, finalAlpha: Float) {
        val thumbStart = thumbPosition
        val thumbSize = thumbSize

        val thumbAlpha = ARGB.alpha(thumbColor) / 255.0f * finalAlpha
        val r = ARGB.red(thumbColor) / 255.0f
        val g = ARGB.green(thumbColor) / 255.0f
        val b = ARGB.blue(thumbColor) / 255.0f

        context.pose().pushPose()
        context.pose().translate(0.0f, 0.0f)

        if (orientation == Orientation.HORIZONTAL) {
            context.pose().translate(thumbStart, 0.0f)
            val thumbCommand = FillRectDrawCommand(thumbSize, height, r, g, b, thumbAlpha)
            context.submit(thumbCommand)
        } else {
            context.pose().translate(0.0f, thumbStart)
            val thumbCommand = FillRectDrawCommand(width, thumbSize, r, g, b, thumbAlpha)
            context.submit(thumbCommand)
        }

        context.pose().popPose()
    }

    override val thumbSize: Float
        get() {
            val maxScroll = panel.maxScroll

            val viewSize = if (orientation == Orientation.HORIZONTAL) panel.width else panel.height
            val contentSize = maxScroll + viewSize
            val ratio = viewSize / contentSize
            return Mth.clamp(ratio * trackSize, 16.0f, trackSize)
        }

    override val thumbPosition: Float
        get() {
            val maxScroll = panel.maxScroll
            if (maxScroll <= 0.0f) return 0.0f

            val track = trackSize - thumbSize
            val ratio = panel.scrollY / maxScroll
            return Mth.clamp(ratio * track, 0f, track)
        }

    override fun updateTargetFromMouse(mouse: Float) {
        val maxScroll = panel.maxScroll
        if (maxScroll <= 0.0f) return

        val track = trackSize - thumbSize
        if (track <= 0.0f) return

        val ratio = Mth.clamp((mouse - dragOffset) / track, 0.0f, 1.0f)
        panel.setScrollTarget(ratio * maxScroll)
    }
}