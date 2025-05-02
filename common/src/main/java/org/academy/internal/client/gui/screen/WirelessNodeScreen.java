package org.academy.internal.client.gui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.academy.AcademyCraft;
import org.academy.api.client.gui.ImageResources;
import org.academy.api.client.gui.WirelessPanelHelper;
import org.academy.api.client.gui.animation.AnimationTopToBottom;
import org.academy.api.client.gui.framework.CGuiContainerScreen;
import org.academy.api.client.gui.widget.*;
import org.academy.internal.common.world.inventory.WirelessNodeMenu;
import org.academy.internal.common.world.level.block.entity.WirelessNodeBlockEntity;

public class WirelessNodeScreen extends CGuiContainerScreen<WirelessNodeMenu> implements WirelessPanelHelper.WirelessPanel {
    public final BlockPos mainPos;
    public WirelessNodeBlockEntity wirelessNodeBlockEntity;
    private String connectedNodeName = "None";
    private PanelWidget wirelessPanel;
    private SmoothScrollPanelWidget nodeListPanel;
    private VerticalSpriteWidget state;
    private int ticks;

    public WirelessNodeScreen(WirelessNodeMenu menu, Inventory playerInventory, Component title, BlockPos mainPos) {
        super(menu, playerInventory, title);
        this.mainPos = mainPos;
        assert Minecraft.getInstance().level != null;
        if (Minecraft.getInstance().level.getBlockEntity(mainPos) instanceof WirelessNodeBlockEntity blockEntity) {
            wirelessNodeBlockEntity = blockEntity;
        } else {
            onClose();
        }
    }

    @Override
    protected void onInit() {
        PanelWidget invPage = new PanelWidget(0, 0, width, height);
        invPage.animation = new AnimationTopToBottom(invPage);
        rootContainer.addChild("page_inv", invPage);
        {
            ImageWidget ui = new ImageWidget(leftPos, topPos - 22, imageWidth, 187, ImageResources.RenderTypes.RENDER_TYPE_WIRELESS_NODE_UI);
            ui.animation = new AnimationTopToBottom(ui);
            invPage.addChild("ui", ui);

            state = new VerticalSpriteWidget(
                    leftPos + 42, topPos - 22 + 33.5f, 186 / 2f, 75 / 2f,
                    ImageResources.RenderTypes.RENDER_TYPE_WIRELESS_NODE_STATE,
                    186, 750, 186, 75, 10);
            state.animation = new AnimationTopToBottom(state);
            invPage.addChild("state", state);
        }

        wirelessPanel = WirelessPanelHelper.getWirelessPanel(leftPos, topPos - 22);
        nodeListPanel = wirelessPanel.getChildUnSafe("node_list");
        wirelessPanel.setZ(100);
        wirelessPanel.setVisible(false);
        wirelessPanel.setEnabled(false);
        rootContainer.addChild(WirelessPanelHelper.PANEL_WIRELESS_NAME, wirelessPanel);
        requestCurrentNodeStatus();
        requestAvailableNodes(nodeListPanel);

        RadioGroupWidget radioGroupWidget = new RadioGroupWidget(leftPos - 16.8f, topPos - 22, 24, 48);
        radioGroupWidget.setOnSelectionChanged(imageRadioButtonWidget -> {
            switch (imageRadioButtonWidget.getId()) {
                case 0:
                    handleContainer = true;
                    renderInventory = true;
                    invPage.setVisible(true);
                    wirelessPanel.setVisible(false);
                    wirelessPanel.setEnabled(false);
                    break;
                case 1:
                    handleContainer = false;
                    renderInventory = false;
                    invPage.setVisible(false);
                    wirelessPanel.setVisible(true);
                    wirelessPanel.setEnabled(true);
                    break;
            }
        });
        rootContainer.addChild("radio_group", radioGroupWidget);
        {
            ImageRadioButtonWidget inv = new ImageRadioButtonWidget(0, 0, 16.8f, 16.8f, ImageResources.RenderTypes.RENDER_TYPE_ICON_INV, () -> AcademyCraft.LOGGER.info("W"));
            inv.animation = new AnimationTopToBottom(inv);
            radioGroupWidget.addChild("inv", inv);
            radioGroupWidget.selectButton(inv);

            ImageRadioButtonWidget wireless = new ImageRadioButtonWidget(0, 22, 16.8f, 16.8f, ImageResources.RenderTypes.RENDER_TYPE_ICON_WIRELESS, () -> AcademyCraft.LOGGER.info("WindGenScreen: wireless"));
            wireless.animation = new AnimationTopToBottom(wireless);
            radioGroupWidget.addChild("wireless", wireless);
            wireless.setSelected(false);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (wirelessNodeBlockEntity != null) {
            ticks++;

            float progress =
                    (float) wirelessNodeBlockEntity.connectedUsersCount
                            /
                            (float) wirelessNodeBlockEntity.maxConnectedUsers;

            int index;
            if (wirelessNodeBlockEntity.connectedUsersCount == 0) {
                index = (ticks / 20) % 2 == 0 ? 8 : 9;
            } else {
                index = Math.max(0, Math.min((int) (progress * 8), 7));
            }

            state.setFrameIndex(index);
        }
    }

    @Override
    public SmoothScrollPanelWidget getNodeList() {
        return nodeListPanel;
    }

    @Override
    public PanelWidget getWirelessPanel() {
        return wirelessPanel;
    }

    @Override
    public String getConnectedNodeName() {
        return connectedNodeName;
    }

    @Override
    public void setConnectedNodeName(String connectedNodeName) {
        this.connectedNodeName = connectedNodeName;
    }

    @Override
    public BlockPos getPosition() {
        return mainPos;
    }
}