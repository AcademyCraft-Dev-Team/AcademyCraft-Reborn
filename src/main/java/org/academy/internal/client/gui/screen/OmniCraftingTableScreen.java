package org.academy.internal.client.gui.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.academy.api.client.gui.screen.ContainerUiScreen;
import org.academy.api.client.gui.widget.FrameLayoutWidget;
import org.academy.api.client.gui.widget.RadioButtonWidget;
import org.academy.api.client.gui.widget.RadioGroupWidget;
import org.academy.internal.common.world.inventory.OmniCraftingMenu;

public final class OmniCraftingTableScreen extends ContainerUiScreen<OmniCraftingMenu> {
    private final BlockPos mainPos;

    public OmniCraftingTableScreen(OmniCraftingMenu menu, Inventory playerInventory, Component title, BlockPos blockPos) {
        super(menu, playerInventory, title);
        mainPos = blockPos;
    }

    @Override
    protected void onInit(RadioGroupWidget pageButtons, RadioButtonWidget invButton, FrameLayoutWidget content, FrameLayoutWidget invPage) {

    }
}