package org.academy.internal.client.gui.screen

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import org.academy.api.client.gui.animation.EasingFunctions
import org.academy.api.client.gui.animation.ObjectAnimator
import org.academy.api.client.gui.dsl.fill
import org.academy.api.client.gui.dsl.image
import org.academy.api.client.gui.dsl.lp
import org.academy.api.client.gui.dsl.matchParent
import org.academy.api.client.gui.screen.ContainerUiScreen
import org.academy.api.client.gui.widget.FillWidget
import org.academy.api.client.gui.widget.FrameLayoutWidget
import org.academy.api.client.gui.widget.RadioButtonWidget
import org.academy.api.client.gui.widget.RadioGroupWidget
import org.academy.api.client.resources.R
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

        val fluidFill = invPage.fill(0xFF88D8FF.toInt(), "imag_phase_fluid") {
            lp {
                size(FLUID_WIDTH, FLUID_HEIGHT)
                margin(FLUID_X, FLUID_Y, 0f, 0f)
            }
        }
        this.fluidFill = fluidFill

        invPage.image(R.textures.gui.omni_crafting.ui_omni_crafting, "omni_crafting_work_area") {
            matchParent()
        }

        setupWirelessPage(
            pageButtons,
            invButton,
            content,
            invPage,
            mainPos,
            createButton(R.textures.gui.icon.icon_wireless)
        )
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
