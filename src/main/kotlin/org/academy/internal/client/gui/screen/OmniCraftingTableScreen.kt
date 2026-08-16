package org.academy.internal.client.gui.screen

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import org.academy.api.client.gui.animation.EasingFunctions
import org.academy.api.client.gui.animation.ObjectAnimator
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.screen.ContainerUiScreen
import org.academy.api.client.gui.util.WirelessPanelUtil.create
import org.academy.api.client.gui.widget.FrameLayoutWidget
import org.academy.api.client.gui.widget.FillWidget
import org.academy.api.client.gui.widget.ImageWidget
import org.academy.api.client.gui.widget.RadioButtonWidget
import org.academy.api.client.gui.widget.RadioGroupWidget
import org.academy.api.client.gui.widget.Widget
import org.academy.api.client.gui.widget.WidgetContainer
import org.academy.api.client.resources.R
import org.academy.api.client.util.AnimationUtil
import org.academy.internal.common.world.inventory.OmniCraftingMenu
import org.academy.internal.common.world.level.block.entity.OmniCraftingTableBlockEntity

class OmniCraftingTableScreen(
    menu: OmniCraftingMenu,
    playerInventory: Inventory,
    title: Component,
    private val mainPos: BlockPos
) : ContainerUiScreen<OmniCraftingMenu>(menu, playerInventory, title) {
    private var fluidFill: FillWidget? = null

    override fun onInit(
        pageButtons: RadioGroupWidget,
        invButton: RadioButtonWidget,
        content: FrameLayoutWidget,
        invPage: FrameLayoutWidget
    ) {
        val duration = 600L

        val fluidFill = FillWidget(0xFF88D8FF.toInt())
        this.fluidFill = fluidFill
        fluidFill.layoutParams = FrameLayoutWidget.LayoutParams()
            .size(FLUID_WIDTH, FLUID_HEIGHT)
            .margin(FLUID_X, FLUID_Y, 0f, 0f)
        invPage.addChild("imag_phase_fluid", fluidFill)

        val workArea = ImageWidget(R.textures.gui.omni_crafting.ui_omni_crafting)
        workArea.layoutParams = FrameLayoutWidget.LayoutParams()
            .sizeMode(SizeMode.MATCH_PARENT)
        invPage.addChild("omni_crafting_work_area", workArea)

        val wirelessPage = create(mainPos, true)
        wirelessPage.visibility = Widget.Visibility.GONE
        wirelessPage.isEnabled = false
        content.addChild("page_wireless", wirelessPage)

        val wirelessButton = createButton(R.textures.gui.icon.icon_wireless)
        wirelessButton.layoutParams = WidgetContainer.LayoutParams()
            .widthMode(SizeMode.MATCH_PARENT)
            .height(16f)
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
        pageButtons.startAnimation(
            ObjectAnimator.ofFloat({ pageButtons.alpha = it }, 0f, 1f)
                .setDuration(duration - 100)
        )
        pageButtons.startAnimation(
            ObjectAnimator.ofFloat({ pageButtons.translationY = it }, 20f, 0f)
                .setDuration(duration)
                .setInterpolator(EasingFunctions.EASE_OUT_CUBIC)
        )
    }

    override fun containerTick() {
        super.containerTick()
        val fill = fluidFill ?: return
        val stored = (minecraft.level?.getBlockEntity(mainPos)
                as? OmniCraftingTableBlockEntity)?.imagPhaseFluidStored ?: 0
        val ratio = (stored.toFloat() / OmniCraftingTableBlockEntity.MAX_FLUID_STORAGE)
            .coerceIn(0f, 1f)
        fill.height = FLUID_HEIGHT * ratio
        fill.translationY = FLUID_HEIGHT - fill.height
        fill.alpha = if (stored > 0) 0.82f else 0f
    }

    companion object {
        private const val FLUID_X = 29f
        private const val FLUID_Y = 12f
        private const val FLUID_WIDTH = 7f
        private const val FLUID_HEIGHT = 59f
    }
}
