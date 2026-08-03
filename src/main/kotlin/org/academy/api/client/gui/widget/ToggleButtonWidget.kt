package org.academy.api.client.gui.widget

import net.minecraft.util.ARGB
import org.academy.api.client.gui.animation.EasingFunctions
import org.academy.api.client.gui.animation.ValueAnimator
import org.academy.api.client.gui.command.FillRectDrawCommand
import org.academy.api.client.gui.event.MouseEvent
import org.academy.api.client.gui.layout.MeasureSpec
import org.academy.api.client.gui.render.RenderContext
import org.academy.api.client.util.ClientUtil

/**
 * A toggle switch. It draws a track with a sliding thumb and switches between
 * checked/unchecked states on click. Colors follow the terminal's monochrome style.
 */
open class ToggleButtonWidget : AbstractWidget() {
    var isChecked: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                animateThumb()
                invalidate()
                onCheckedChangeListener?.onCheckedChanged(this, value)
            }
        }

    var onCheckedChangeListener: OnCheckedChangeListener? = null

    var trackColor: Int = 0x66000000
    var checkedTrackColor: Int = -0x1
    var thumbColor: Int = -0x1
    var checkedThumbColor: Int = 0xFF000000.toInt()

    private var animatedOffset: Float = 0f
    private var thumbPadding: Float = 1f

    init {
        isClickable = true
    }

    override fun renderInternal(context: RenderContext) {
        val width = width
        val height = height
        if (width <= 0 || height <= 0) return

        val track = if (isChecked) checkedTrackColor else trackColor
        val thumb = if (isChecked) checkedThumbColor else thumbColor

        val trackAlpha = ARGB.alpha(track) / 255.0f
        context.submit(
            FillRectDrawCommand(
                width, height,
                ARGB.red(track) / 255.0f,
                ARGB.green(track) / 255.0f,
                ARGB.blue(track) / 255.0f,
                trackAlpha * context.accumulatedAlpha
            )
        )

        val thumbSize = height - thumbPadding * 2
        if (thumbSize <= 0) return

        context.pose().pushPose()
        run {
            context.pose().translate(thumbPadding + animatedOffset, thumbPadding)
            context.submit(
                FillRectDrawCommand(
                    thumbSize, thumbSize,
                    ARGB.red(thumb) / 255.0f,
                    ARGB.green(thumb) / 255.0f,
                    ARGB.blue(thumb) / 255.0f,
                    ARGB.alpha(thumb) / 255.0f * context.accumulatedAlpha
                )
            )
        }
        context.pose().popPose()
    }

    override fun onMousePressed(event: MouseEvent) {
        if (event.button == 0 && (isHovered || isMouseOver(event.x, event.y))) {
            event.consume()
            ClientUtil.playDownSound()
            isChecked = !isChecked
        }
    }

    override fun onMeasure(widthMeasureSpec: MeasureSpec, heightMeasureSpec: MeasureSpec) {
        setMeasuredDimension(
            resolveSize(layoutParams.paddingLeft + layoutParams.paddingRight, widthMeasureSpec),
            resolveSize(layoutParams.paddingTop + layoutParams.paddingBottom, heightMeasureSpec)
        )
    }

    private fun animateThumb() {
        val maxOffset = (width - (height - thumbPadding * 2) - thumbPadding * 2).coerceAtLeast(0f)
        val target = if (isChecked) maxOffset else 0f
        cancelAnimations()

        val animator = ValueAnimator.ofFloat(animatedOffset, target)
            .setDuration(150)
            .setInterpolator(EasingFunctions.EASE_OUT_CUBIC)
        animator.addUpdateListener { anim ->
            animatedOffset = anim.animatedValue
            invalidate()
        }
        startAnimation(animator)
    }

    fun setChecked(checked: Boolean): ToggleButtonWidget {
        isChecked = checked
        return this
    }

    fun setOnCheckedChangeListener(listener: OnCheckedChangeListener?): ToggleButtonWidget {
        onCheckedChangeListener = listener
        return this
    }

    fun setTrackColor(color: Int): ToggleButtonWidget {
        if (trackColor != color) {
            trackColor = color
            invalidate()
        }
        return this
    }

    fun setCheckedTrackColor(color: Int): ToggleButtonWidget {
        if (checkedTrackColor != color) {
            checkedTrackColor = color
            invalidate()
        }
        return this
    }

    fun setThumbColor(color: Int): ToggleButtonWidget {
        if (thumbColor != color) {
            thumbColor = color
            invalidate()
        }
        return this
    }

    fun setCheckedThumbColor(color: Int): ToggleButtonWidget {
        if (checkedThumbColor != color) {
            checkedThumbColor = color
            invalidate()
        }
        return this
    }

    interface OnCheckedChangeListener {
        fun onCheckedChanged(toggle: ToggleButtonWidget, isChecked: Boolean)
    }
}
