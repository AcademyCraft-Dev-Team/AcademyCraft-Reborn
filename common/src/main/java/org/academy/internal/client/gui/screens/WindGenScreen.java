package org.academy.internal.client.gui.screens;

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
import org.academy.internal.common.world.inventory.WindGenMenu;
import org.academy.internal.common.world.level.block.entity.WindGenBaseBlockEntity;

public class WindGenScreen extends CGuiContainerScreen<WindGenMenu> implements WirelessPanelHelper.WirelessPanel {
    public final BlockPos mainPos;
    public WindGenBaseBlockEntity windGenBaseBlockEntity;
    public ImageWidget topIcon;
    public ImageWidget pillarIcon;
    public ImageWidget baseIcon;
    private String connectedNodeName = "None";
    private PanelWidget wirelessPanel;
    private SmoothScrollPanelWidget nodeListPanel;

    public WindGenScreen(WindGenMenu menu, Inventory playerInventory, Component title, BlockPos mainPos) {
        super(menu, playerInventory, title);
        this.mainPos = mainPos;
        assert Minecraft.getInstance().level != null;
        if (Minecraft.getInstance().level.getBlockEntity(mainPos) instanceof WindGenBaseBlockEntity blockEntity) {
            windGenBaseBlockEntity = blockEntity;
        } else {
            onClose();
        }
    }

    @Override
    protected void onInit() {
        PanelWidget windgenPage = new PanelWidget(0, 0, width, height);
        windgenPage.animation = new AnimationTopToBottom(windgenPage);
        rootContainer.addChild("page_windgen", windgenPage);
        {
            ImageWidget ui = new ImageWidget(leftPos, topPos - 22, imageWidth, imageHeight, ImageResources.RenderTypes.RENDER_TYPE_WIND_GEN_UI);
            windgenPage.addChild("ui", ui);
            ui.animation = new AnimationTopToBottom(ui);
            PanelWidget statePanel = new PanelWidget(leftPos, topPos - 22, imageWidth, imageHeight);
            statePanel.setHorizontalGravity(PanelWidget.HorizontalGravity.CENTER);
            statePanel.animation = new AnimationTopToBottom(statePanel);
            windgenPage.addChild("panel_state", statePanel);
            {
                topIcon = new ImageWidget(0, 13, 24, 24, ImageResources.RenderTypes.RENDER_TYPE_ICON_WIND_GEN_TOP);
                topIcon.animation = new AnimationTopToBottom(topIcon);
                statePanel.addChild("icon_top", topIcon);
                pillarIcon = new ImageWidget(0, 31, 24, 24, ImageResources.RenderTypes.RENDER_TYPE_ICON_WIND_GEN_PILLAR);
                pillarIcon.animation = new AnimationTopToBottom(pillarIcon);
                statePanel.addChild("icon_pillar", pillarIcon);
                baseIcon = new ImageWidget(0, 49, 24, 24, ImageResources.RenderTypes.RENDER_TYPE_ICON_WIND_GEN_BASE);
                baseIcon.animation = new AnimationTopToBottom(baseIcon);
                statePanel.addChild("icon_base", baseIcon);
            }
        }

        wirelessPanel = WirelessPanelHelper.getWirelessPanel(leftPos,topPos - 22);
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
                    windgenPage.setVisible(true);
                    wirelessPanel.setVisible(false);
                    wirelessPanel.setEnabled(false);
                    break;
                case 1:
                    handleContainer = false;
                    renderInventory = false;
                    windgenPage.setVisible(false);
                    wirelessPanel.setVisible(true);
                    wirelessPanel.setEnabled(true);
                    break;
            }
        });
        rootContainer.addChild("radio_group", radioGroupWidget);
        {
            ImageRadioButtonWidget inv = new ImageRadioButtonWidget(0, 0, 16.8f, 16.8f, ImageResources.RenderTypes.RENDER_TYPE_ICON_INV, () -> AcademyCraft.LOGGER.info("W"));
            radioGroupWidget.addChild("inv", inv);
            inv.setSelected(true);
            radioGroupWidget.selectButton(inv);

            ImageRadioButtonWidget wireless = new ImageRadioButtonWidget(0, 22, 16.8f, 16.8f, ImageResources.RenderTypes.RENDER_TYPE_ICON_WIRELESS, () -> AcademyCraft.LOGGER.info("WindGenScreen: wireless"));
            radioGroupWidget.addChild("wireless", wireless);
            wireless.setSelected(false);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (windGenBaseBlockEntity != null && baseIcon != null && pillarIcon != null && topIcon != null) {
            switch (windGenBaseBlockEntity.completeness) {
                case NO_TOP -> {
                    baseIcon.alpha = 1f;
                    pillarIcon.alpha = 1f;
                    topIcon.alpha = 0.2f;
                }
                case BASE_ONLY -> {
                    baseIcon.alpha = 1f;
                    pillarIcon.alpha = 0.2f;
                    topIcon.alpha = 0.2f;
                }
                case COMPLETE -> {
                    baseIcon.alpha = 1f;
                    pillarIcon.alpha = 1f;
                    topIcon.alpha = 1f;
                }
                case COMPLETE_NOT_WORKING -> {
                    baseIcon.alpha = 1f;
                    pillarIcon.alpha = 1f;
                    topIcon.alpha = 0.6f;
                }
            }
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