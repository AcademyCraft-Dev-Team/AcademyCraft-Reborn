package org.academy.internal.client.gui.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import org.academy.AcademyCraft;
import org.academy.api.client.gui.ImageResources;
import org.academy.api.client.gui.WirelessPanelHelper;
import org.academy.api.client.gui.framework.CGuiContainerScreen;
import org.academy.api.client.gui.widgets.*;
import org.academy.internal.common.world.inventory.WindGenMenu;
import org.academy.internal.common.world.level.block.entity.WindGenBaseBlockEntity;
import org.apache.commons.lang3.tuple.Pair;

import static org.academy.api.client.gui.ImageResources.RenderTypes.RENDER_TYPE_ELEMENT_BACK_DARK;
import static org.academy.api.client.gui.ImageResources.RenderTypes.RENDER_TYPE_ELEMENT_BACK_LIGHT;

public class WindGenScreen extends CGuiContainerScreen<WindGenMenu> implements WirelessPanelHelper.WirelessPanel {
    public final ContainerLevelAccess access;
    public final BlockPos mainPos;
    public WindGenBaseBlockEntity windGenBaseBlockEntity;
    public ImageWidget topIcon;
    public ImageWidget pillarIcon;
    public ImageWidget baseIcon;
    private String connectedNodeName = "None";
    private PanelWidget wirelessPanel;
    private SmoothScrollPanelWidget nodeListPanel;
    private ImageWidget ui;

    public WindGenScreen(WindGenMenu menu, Inventory playerInventory, Component title, BlockPos mainPos) {
        super(menu, playerInventory, title);
        this.mainPos = mainPos;
        assert Minecraft.getInstance().level != null;
        if (Minecraft.getInstance().level.getBlockEntity(mainPos) instanceof WindGenBaseBlockEntity blockEntity) {
            windGenBaseBlockEntity = blockEntity;
        } else {
            onClose();
        }
        access = menu.access;
    }

    @Override
    protected void onInit() {
        PanelWidget windgenPage = new PanelWidget(0, 0, width, height);
        rootContainer.addChild("page_windgen", windgenPage);
        {
            ui = new ImageWidget(leftPos, topPos - 22, imageWidth, imageHeight, ImageResources.RenderTypes.RENDER_TYPE_WIND_GEN_UI);
            windgenPage.addChild("ui", ui);
            PanelWidget statePanel = new PanelWidget(leftPos, topPos - 22, imageWidth, imageHeight);
            statePanel.setHorizontalGravity(PanelWidget.HorizontalGravity.CENTER);
            windgenPage.addChild("panel_state", statePanel);
            {
                topIcon = new ImageWidget(0, 13, 24, 24, ImageResources.RenderTypes.RENDER_TYPE_ICON_WIND_GEN_TOP);
                statePanel.addChild("icon_top", topIcon);
                pillarIcon = new ImageWidget(0, 31, 24, 24, ImageResources.RenderTypes.RENDER_TYPE_ICON_WIND_GEN_PILLAR);
                statePanel.addChild("icon_pillar", pillarIcon);
                baseIcon = new ImageWidget(0, 49, 24, 24, ImageResources.RenderTypes.RENDER_TYPE_ICON_WIND_GEN_BASE);
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
                    containerActive = true;
                    windgenPage.setVisible(true);
                    wirelessPanel.setVisible(false);
                    wirelessPanel.setEnabled(false);
                    break;
                case 1:
                    containerActive = false;
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
            ImageRadioButtonWidget wireless = new ImageRadioButtonWidget(0, 22, 16.8f, 16.8f, ImageResources.RenderTypes.RENDER_TYPE_ICON_WIRELESS, () -> AcademyCraft.LOGGER.info("WindGenScreen: wireless"));
            radioGroupWidget.addChild("wireless", wireless);
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
    public boolean isContainerActive() {
        return containerActive;
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