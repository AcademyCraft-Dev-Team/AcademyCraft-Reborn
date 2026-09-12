package org.academy.internal.client.gui.screen

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.AbstractContainerMenu
import org.academy.api.client.gui.animation.EasingFunctions
import org.academy.api.client.gui.event.OnClickListener
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.widget.*
import org.academy.api.client.util.AnimationUtil

/**
 * Shared compact-machine chrome for Misaka device screens.
 *
 * Text actions use the same resting / hover / pressed plane stack as
 * [MisakaNetworkPanelScreen.actionBackground], not a static BlendQuad fill.
 */
internal object MisakaMachineUi {
    const val SCALE_BODY = 0.75f
    const val RAIL_BUTTON_HEIGHT = 16f
    /** Primary action labels stay at full white; the plane carries rest/hover state. */
    const val LABEL_ALPHA = 1f

    fun sizeRailButton(button: Widget) {
        button.layoutParams = WidgetContainer.LayoutParams()
            .widthMode(SizeMode.MATCH_PARENT)
            .height(RAIL_BUTTON_HEIGHT)
    }

    fun playOpenReveal(
        widget: Widget,
        targetAlpha: Float,
        duration: Long,
        childDuration: Long
    ) {
        AnimationUtil.reveal(
            widget = widget,
            targetAlpha = targetAlpha,
            alphaDuration = childDuration,
            translationDuration = duration,
            yInterpolator = EasingFunctions.EASE_OUT_CUBIC,
            applyShowFlags = false
        )
    }

    fun menuActionButton(
        menu: AbstractContainerMenu,
        labelKey: String,
        buttonId: Int,
        literal: Boolean = false,
        scale: Float = SCALE_BODY,
        selected: Boolean = false
    ): ButtonWidget {
        val text = if (literal) labelKey else Component.translatable(labelKey).string
        return localActionButton(text, scale, selected) {
            Minecraft.getInstance().gameMode?.handleInventoryButtonClick(menu.containerId, buttonId)
        }
    }

    /** Left-aligned inventory-button row used by cabin ops and launch-pad laser pick lists. */
    fun menuListSelectButton(
        menu: AbstractContainerMenu,
        label: String,
        buttonId: Int,
        enabled: Boolean = true,
        selected: Boolean = false,
        scale: Float = 0.7f
    ): ButtonWidget {
        lateinit var button: ButtonWidget
        button = textButton(
            text = label,
            scale = scale,
            selected = selected,
            labelGravity = Gravity.CENTER_LEFT,
            labelMarginH = 4f
        ) {
            if (!button.isEnabled) {
                return@textButton
            }
            Minecraft.getInstance().gameMode?.handleInventoryButtonClick(menu.containerId, buttonId)
        }
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else 0.45f
        button.layoutParams = LinearLayoutWidget.LayoutParams()
            .widthMode(SizeMode.MATCH_PARENT)
            .height(14f)
        return button
    }

    fun localActionButton(
        labelKey: String,
        scale: Float = SCALE_BODY,
        selected: Boolean = false,
        translate: Boolean = true,
        onClick: () -> Unit
    ): ButtonWidget {
        val text = if (translate) Component.translatable(labelKey).string else labelKey
        return localActionButton(text, scale, selected, onClick)
    }

    private fun localActionButton(
        text: String,
        scale: Float,
        selected: Boolean,
        onClick: () -> Unit
    ): ButtonWidget = textButton(
        text = text,
        scale = scale,
        selected = selected,
        labelGravity = Gravity.CENTER,
        labelMarginH = 0f,
        onClick = onClick
    )

    private fun textButton(
        text: String,
        scale: Float,
        selected: Boolean,
        labelGravity: Int,
        labelMarginH: Float,
        onClick: () -> Unit
    ): ButtonWidget {
        val button = ButtonWidget()
        button.background = MisakaNetworkPanelScreen.actionBackground(selected)
        button.isSelected = selected
        button.onClickListener = OnClickListener {
            if (button.isEnabled) {
                onClick()
            }
        }
        button.addChild("label", LabelWidget(text).apply {
            this.scale = scale
            alpha = LABEL_ALPHA
            layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .gravity(labelGravity)
                .margin(labelMarginH, 0f, labelMarginH, 0f)
        })
        return button
    }
}
