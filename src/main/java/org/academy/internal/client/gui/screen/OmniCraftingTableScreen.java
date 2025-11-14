package org.academy.internal.client.gui.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.academy.api.client.gui.screen.ContainerUIScreen;
import org.academy.api.client.gui.widget.*;
import org.academy.internal.common.world.inventory.OmniCraftingMenu;

public final class OmniCraftingTableScreen extends ContainerUIScreen<OmniCraftingMenu> {
    private final BlockPos mainPos;

    public OmniCraftingTableScreen(OmniCraftingMenu menu, Inventory playerInventory, Component title, BlockPos blockPos) {
        super(menu, playerInventory, title);
        mainPos = blockPos;
    }

    @Override
    protected void onInit(RadioGroupWidget pageButtons, RadioButtonWidget invButton, FrameLayoutWidget content, FrameLayoutWidget invPage) {

    }
}