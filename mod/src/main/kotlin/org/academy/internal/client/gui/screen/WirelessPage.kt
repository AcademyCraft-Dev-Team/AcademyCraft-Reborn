package org.academy.internal.client.gui.screen

import net.minecraft.core.BlockPos
import org.academy.api.client.gui.dsl.height
import org.academy.api.client.gui.dsl.matchWidth
import org.academy.api.client.gui.screen.ContainerUiScreen
import org.academy.api.client.gui.util.wirelessPanel
import org.academy.api.client.gui.widget.FrameLayoutWidget
import org.academy.api.client.gui.widget.RadioButtonWidget
import org.academy.api.client.gui.widget.RadioGroupWidget
import org.academy.api.client.gui.widget.Widget
import org.academy.api.client.util.AnimationUtil

internal fun ContainerUiScreen<*>.setupWirelessPage(
    pageButtons: RadioGroupWidget,
    invButton: RadioButtonWidget,
    content: FrameLayoutWidget,
    invPage: FrameLayoutWidget,
    mainPos: BlockPos,
    wirelessButton: RadioButtonWidget
) {
    val wirelessPage = content.wirelessPanel(mainPos, true, "page_wireless") {
        visibility = Widget.Visibility.GONE
        isEnabled = false
    }

    wirelessButton.matchWidth()
    wirelessButton.height(16f)

    pageButtons.addChild("wireless", wirelessButton)
    pageButtons.onSelectionChanged = {
        when (it.name) {
            "inv" -> {
                AnimationUtil.hide(wirelessPage)
                AnimationUtil.show(invPage)
                isHandleContainer = true
                isRenderInventory = true
            }

            "wireless" -> {
                AnimationUtil.hide(invPage)
                AnimationUtil.show(wirelessPage)
                isHandleContainer = false
                isRenderInventory = false
            }
        }
    }
    pageButtons.selectButton(invButton)
}
