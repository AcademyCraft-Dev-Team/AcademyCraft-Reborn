package org.academy.internal.client.gui.screen

import net.minecraft.client.Minecraft
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import org.academy.api.client.gui.command.FillRectDrawCommand
import org.academy.api.client.gui.drawable.ColorDrawable
import org.academy.api.client.gui.dsl.*
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.render.Canvas
import org.academy.api.client.gui.screen.UiScreen
import org.academy.api.client.gui.widget.FrameLayoutWidget
import org.academy.api.client.gui.widget.LinearLayoutWidget
import org.academy.api.client.gui.widget.SeekBarWidget
import org.academy.api.client.gui.widget.TextWidget
import org.academy.api.client.input.InputSystem
import org.academy.internal.common.ability.level0.skills.OutputControl
import org.academy.internal.common.ability.program.ProgramPowerScale
import java.util.*

class OutputControlScreen(
    initialAbilityOutput: Float,
    initialMovementSpeed: Float,
    initialJumpHeight: Float,
    private val heldInputType: InputSystem.InputType,
    private val heldInput: Int
) : UiScreen(Component.translatable("screen.academy.output_control.title")) {
    private var abilityOutput = Mth.clamp(
        initialAbilityOutput, ProgramPowerScale.MIN, ProgramPowerScale.MAX
    )
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

        panel.apply {
            blendQuad {
                matchParent()

                alpha = 0.5f
            }

            column {
                lp {
                    matchParent()
                    padding(10f, 7f)
                }

                orientation = Orientation.VERTICAL
                spacing = 3f

                text(title.string) {
                    lp {
                        gravity(Gravity.CENTER)
                    }
                    textSize = 12f
                    gravity = Gravity.CENTER
                }

                text(
                    Component.translatable(
                        "screen.academy.output_control.hint",
                        InputSystem.formatKeyBinding(OutputControl.Client.KEY_NAME_OPEN)
                    ).string
                ) {
                    lp {
                        widthMode(SizeMode.MATCH_PARENT)
                        gravity(Gravity.CENTER)
                    }
                    gravity = Gravity.CENTER
                    alpha = 0.7f
                }
                addChild(
                    "ability_output", createParameterRow(
                        "screen.academy.output_control.ability_output",
                        ProgramPowerScale.MIN,
                        ProgramPowerScale.MAX,
                        abilityOutput,
                        { value -> abilityValue(value) },
                        { value ->
                            abilityOutput = value
                            sendSettings(false)
                        }
                    ))
                addChild(
                    "movement_speed", createParameterRow(
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
                addChild(
                    "jump_height", createParameterRow(
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
            }
        }
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
        heading.addChild("label", TextWidget(Component.translatable(labelKey).string).apply {
            alpha = 0.82f
            layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .height(10f)
                .gravity(Gravity.CENTER_LEFT)
            gravity = Gravity.CENTER_LEFT
        })
        val valueLabel = TextWidget(formatter(initialValue)).apply {
            layoutParams = LinearLayoutWidget.LayoutParams()
                .width(88f)
                .height(10f)
                .gravity(Gravity.CENTER_RIGHT)
            gravity = Gravity.CENTER_RIGHT
            gravity = Gravity.CENTER_RIGHT
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
        val effect = ProgramPowerScale.effectMultiplier(value)
        val cp = ProgramPowerScale.costMultiplier(value)
        return Component.translatable(
            "screen.academy.output_control.value.ability",
            decimal(effect),
            decimal(cp)
        ).string
    }

    private fun multiplierValue(value: Float): String = Component.translatable(
        "screen.academy.output_control.value.multiplier",
        decimal(value)
    ).string

    private fun decimal(value: Float): String = String.format(Locale.ROOT, "%.2f", value)

    private class OutputSeekBar : SeekBarWidget() {
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
