package org.academy.internal.client.gui.screen

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import org.academy.api.client.gui.screen.ContainerUiScreen
import org.academy.api.client.gui.widget.FrameLayoutWidget
import org.academy.api.client.gui.widget.RadioButtonWidget
import org.academy.api.client.gui.widget.RadioGroupWidget
import org.academy.internal.common.world.inventory.OmniCraftingMenu

class OmniCraftingTableScreen(
    menu: OmniCraftingMenu,
    playerInventory: Inventory,
    title: Component,
    private val mainPos: BlockPos
) : ContainerUiScreen<OmniCraftingMenu>(menu, playerInventory, title) {
    override fun onInit(
        pageButtons: RadioGroupWidget,
        invButton: RadioButtonWidget,
        content: FrameLayoutWidget,
        invPage: FrameLayoutWidget
    ) {
    }
}