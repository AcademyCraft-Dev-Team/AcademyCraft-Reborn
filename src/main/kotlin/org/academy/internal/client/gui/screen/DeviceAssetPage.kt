package org.academy.internal.client.gui.screen

import net.minecraft.core.BlockPos
import org.academy.api.client.gui.widget.FrameLayoutWidget
import org.academy.api.client.gui.widget.RadioButtonWidget
import org.academy.api.client.gui.widget.RadioGroupWidget
import org.academy.api.client.gui.widget.Widget
import org.academy.api.client.util.AnimationUtil

/**
 * Rail-mounted owner-only asset page for compact Misaka machine screens.
 * Cabin keeps a pane inside Ops instead, because that screen already has an ops stack.
 */
internal class DeviceAssetPage(
    val ui: DeviceAssetUi,
    val page: FrameLayoutWidget,
    val button: RadioButtonWidget,
    private val pageButtons: RadioGroupWidget,
    private val invButton: RadioButtonWidget
) {
    private var lastOwner: Boolean? = null

    fun hide() {
        AnimationUtil.hide(page)
    }

    fun show() {
        ui.refresh(force = true)
        AnimationUtil.show(page)
    }

    /** Show or hide the rail button when ownership syncs; bounce to inventory if it is lost. */
    fun tick(owner: Boolean) {
        if (lastOwner != owner) {
            lastOwner = owner
            button.visibility = if (owner) Widget.Visibility.VISIBLE else Widget.Visibility.GONE
            button.isEnabled = owner
            if (!owner && page.visibility == Widget.Visibility.VISIBLE) {
                pageButtons.selectButton(invButton)
            }
        }
        if (owner && page.visibility == Widget.Visibility.VISIBLE) {
            ui.refresh()
        }
    }

    companion object {
        fun attach(
            pageButtons: RadioGroupWidget,
            invButton: RadioButtonWidget,
            content: FrameLayoutWidget,
            devicePos: BlockPos,
            isOwner: () -> Boolean,
            createRailButton: () -> RadioButtonWidget
        ): DeviceAssetPage {
            val ui = DeviceAssetUi(devicePos, isOwner) { pageButtons.selectButton(invButton) }
            val page = ui.build()
            page.visibility = Widget.Visibility.GONE
            page.isEnabled = false
            content.addChild("page_asset", page)

            val button = createRailButton()
            MisakaMachineUi.sizeRailButton(button)
            button.visibility = Widget.Visibility.GONE
            button.isEnabled = false
            pageButtons.addChild("asset", button)

            return DeviceAssetPage(ui, page, button, pageButtons, invButton)
        }
    }
}
