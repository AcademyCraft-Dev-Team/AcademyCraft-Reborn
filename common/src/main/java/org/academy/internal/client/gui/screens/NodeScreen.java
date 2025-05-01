package org.academy.internal.client.gui.screens;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.academy.api.client.gui.framework.CGuiContainerScreen;
import org.academy.internal.common.world.inventory.NodeMenu;

public class NodeScreen extends CGuiContainerScreen<NodeMenu> {
    public final BlockPos mainPos;

    public NodeScreen(NodeMenu menu, Inventory playerInventory, Component title, BlockPos mainPos) {
        super(menu, playerInventory, title);
        this.mainPos = mainPos;
    }

    @Override
    protected void onInit() {
    }
}