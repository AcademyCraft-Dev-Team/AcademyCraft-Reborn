package org.academy.internal.client.gui.screen

import net.minecraft.client.Minecraft
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import org.academy.api.client.gui.command.FillRectDrawCommand
import org.academy.api.client.gui.drawable.ColorDrawable
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.render.RenderContext
import org.academy.api.client.gui.screen.UiScreen
import org.academy.api.client.gui.widget.FillWidget
import org.academy.api.client.gui.widget.FrameLayoutWidget
import org.academy.api.client.gui.widget.LabelWidget
import org.academy.api.client.gui.widget.LinearLayoutWidget
import org.academy.api.client.gui.widget.SeekBarWidget
import org.academy.api.client.gui.widget.WidgetContainer
import org.academy.api.client.input.InputSystem
import org.academy.internal.common.ability.level0.skills.OutputControl
import java.util.Locale

class OutputControlScreen(
    initialAbilityOutput: Float,
    initialMovementSpeed: Float,
    initialJumpHeight: Float,
    private val heldInputType: InputSystem.InputType,
    private val heldInput: Int
) : UiScreen(Component.translatable("screen.academy.output_control.title")) {
    private var abilityOutput = Mth.clamp(initialAbilityOutput, 0f, 2f)
    private var movementSpeed = Mth.clamp(initialMovementSpeed, 0f, 1f)
    private var jumpHeight = Mth.clamp(initialJumpHeight, 0f, 1f)
    private var lastSendNanos = 0L
    private var closing = false

    override fun isPauseScreen(): Boolean = false

    override fun onInit() {
        val panel = FrameLayoutWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams()
                .gravity(Gravity.CENTER)
                .size(PANEL_WIDTH, PANEL_HEIGHT)
        }
        root.addChild("panel", panel)

        panel.addChild("background", FillWidget(ROOT_PLANE).apply {
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        })
        panel.addChild("top_rule", FillWidget(PRIMARY_FOREGROUND).apply {
            layoutParams = FrameLayoutWidget.LayoutParams()
                .gravity(Gravity.TOP)
                .height(1f)
                .widthMode(SizeMode.MATCH_PARENT)
                .marginHorizontal(4f)
        })

        val content = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            spacing = 3f
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .padding(10f, 7f)
        }
        panel.addChild("content", content)

        content.addChild("title", LabelWidget(title.string).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(10f)
                .gravity(Gravity.CENTER)
        })
        content.addChild("hint", LabelWidget(
            Component.translatable(
                "screen.academy.output_control.hint",
                InputSystem.formatKeyBinding(OutputControl.Client.KEY_NAME_OPEN)
            ).string
        ).apply {
            scale = 0.7f
            alpha = 0.7f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(8f)
                .gravity(Gravity.CENTER)
        })
        content.addChild("separator", FillWidget(PRIMARY_FOREGROUND).apply {
            alpha = 0.8f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(1f)
        })

        content.addChild("ability_output", createParameterRow(
            "screen.academy.output_control.ability_output",
            0f,
            2f,
            abilityOutput,
            { value -> abilityValue(value) },
            { value ->
                abilityOutput = value
                sendSettings(false)
            }
        ))
        content.addChild("movement_speed", createParameterRow(
            "screen.academy.output_control.movement_speed",
            0f,
            1f,
            movementSpeed,
            ::multiplierValue,
            { value ->
                movementSpeed = value
                sendSettings(false)
            }
        ))
        content.addChild("jump_height", createParameterRow(
            "screen.academy.output_control.jump_height",
            0f,
            1f,
            jumpHeight,
            ::multiplierValue,
            { value ->
                jumpHeight = value
                sendSettings(false)
            }
        ))

        panel.addChild("bottom_rule", FillWidget(PRIMARY_FOREGROUND).apply {
            alpha = 0.7f
            layoutParams = FrameLayoutWidget.LayoutParams()
                .gravity(Gravity.BOTTOM)
                .height(1f)
                .widthMode(SizeMode.MATCH_PARENT)
                .marginHorizontal(4f)
        })
    }

    override fun tick() {
        super.tick()
        if (!InputSystem.isDown(heldInputType, heldInput)) {
            closeForRelease()
        }
    }

    override fun keyReleased(event: KeyEvent): Boolean {
        if (heldInputType == InputSystem.InputType.KEYBOARD && event.key() == heldInput) {
            closeForRelease()
            return true
        }
        return super.keyReleased(event)
    }

    override fun removed() {
        sendSettings(true)
        super.removed()
    }

    private fun closeForRelease() {
        if (closing) return
        closing = true
        sendSettings(true)
        Minecraft.getInstance().gui.setScreen(null)
    }

    private fun createParameterRow(
        labelKey: String,
        min: Float,
        max: Float,
        initialValue: Float,
        formatter: (Float) -> String,
        onValueChanged: (Float) -> Unit
    ): FrameLayoutWidget {
        val row = FrameLayoutWidget().apply {
            background = ColorDrawable(ROW_PLANE)
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(PARAMETER_ROW_HEIGHT)
        }
        val column = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            spacing = 3f
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .padding(7f, 3f)
        }
        row.addChild("content", column)

        val heading = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            spacing = 4f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(10f)
        }
        column.addChild("heading", heading)
        heading.addChild("label", LabelWidget(Component.translatable(labelKey).string).apply {
            alpha = 0.82f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .height(10f)
                .gravity(Gravity.CENTER_LEFT)
        })
        val valueLabel = LabelWidget(formatter(initialValue)).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .width(88f)
                .height(10f)
                .gravity(Gravity.CENTER_RIGHT)
        }
        heading.addChild("value", valueLabel)

        val slider = OutputSeekBar().apply {
            setMin(min)
            setMax(max)
            setProgress(initialValue)
            setBackgroundColor(SLIDER_TRACK)
            setProgressColor(PRIMARY_FOREGROUND)
            layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(SLIDER_HEIGHT)
            setOnSeekBarChangeListener(object : SeekBarWidget.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBarWidget,
                    progress: Float,
                    fromUser: Boolean
                ) {
                    if (!fromUser) return
                    val value = Mth.clamp(progress, min, max)
                    valueLabel.text = formatter(value)
                    onValueChanged(value)
                }

                override fun onStartTrackingTouch(seekBar: SeekBarWidget) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBarWidget) {
                    sendSettings(true)
                }
            })
        }
        column.addChild("slider", slider)
        return row
    }

    private fun sendSettings(force: Boolean) {
        val now = System.nanoTime()
        if (!force && now - lastSendNanos < SEND_INTERVAL_NANOS) return
        lastSendNanos = now
        OutputControl.Client.sendSettings(abilityOutput, movementSpeed, jumpHeight)
    }

    private fun abilityValue(value: Float): String {
        val cp = 0.5f + 0.5f * value * value * value
        return Component.translatable(
            "screen.academy.output_control.value.ability",
            decimal(value),
            decimal(cp)
        ).string
    }

    private fun multiplierValue(value: Float): String = Component.translatable(
        "screen.academy.output_control.value.multiplier",
        decimal(value)
    ).string

    private fun decimal(value: Float): String = String.format(Locale.ROOT, "%.2f", value)

    private class OutputSeekBar : SeekBarWidget() {
        override fun canFocus(): Boolean = true

        override fun renderInternal(context: RenderContext) {
            super.renderInternal(context)
            val range = max - min
            if (width <= 0f || height <= 0f || range <= 0f) return
            val ratio = Mth.clamp((progress - min) / range, 0f, 1f)
            val markerX = Mth.clamp(width * ratio - MARKER_WIDTH * 0.5f, 0f, width - MARKER_WIDTH)
            context.pose().pushPose()
            context.pose().translate(markerX, -2f)
            context.submit(FillRectDrawCommand(
                MARKER_WIDTH,
                height + 4f,
                1f,
                1f,
                1f,
                context.accumulatedAlpha
            ))
            context.pose().popPose()
        }
    }

    companion object {
        private const val PANEL_WIDTH = 284f
        private const val PANEL_HEIGHT = 144f
        private const val PARAMETER_ROW_HEIGHT = 29f
        private const val SLIDER_HEIGHT = 9f
        private const val MARKER_WIDTH = 6f
        private const val SEND_INTERVAL_NANOS = 50_000_000L
        private const val ROOT_PLANE = 0x70000000
        private const val ROW_PLANE = 0x28000000
        private const val SLIDER_TRACK = 0x40FFFFFF
        private const val PRIMARY_FOREGROUND = 0xFFFFFFFF.toInt()
    }
}
