package org.academy.internal.client.gui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.academy.api.client.gui.ImageResources;
import org.academy.api.client.gui.WirelessPanelHelper;
import org.academy.api.client.gui.animation.AnimationTopToBottom;
import org.academy.api.client.gui.framework.CGuiContainerScreen;
import org.academy.api.client.gui.widget.*;
import org.academy.internal.common.world.inventory.WindGenMenu;
import org.academy.internal.common.world.level.block.entity.WindGenBaseBlockEntity;

public class WindGenScreen extends CGuiContainerScreen<WindGenMenu> implements WirelessPanelHelper.WirelessPanel {
    public final BlockPos mainPos;
    public final WindGenBaseBlockEntity windGenBaseBlockEntity;
    public ImageWidget topIcon;
    public ImageWidget pillarIcon;
    public ImageWidget baseIcon;
    public static final String AF = "%d AF";
    private String connectedNodeName = "None";
    private PanelWidget wirelessPanel;
    private SmoothScrollPanelWidget nodeListPanel;
    private LabelWidget bufferValueLabel;
    private final HistogramWidget.Value histogramValue = new HistogramWidget.Value(25, 5, 0,
            37f / 255f, 247f / 255f, 1, 1);

    private WindGenScreen(WindGenMenu menu, Inventory playerInventory, Component title, WindGenBaseBlockEntity windGenBaseBlockEntity) {
        super(menu, playerInventory, title);
        this.windGenBaseBlockEntity = windGenBaseBlockEntity;
        this.mainPos = windGenBaseBlockEntity.getBlockPos();
    }

    public static WindGenScreen create(WindGenMenu menu, Inventory playerInventory, Component title, BlockPos mainPos) {
        if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.getBlockEntity(mainPos) instanceof WindGenBaseBlockEntity blockEntity) {
            return new WindGenScreen(menu, playerInventory, title, blockEntity);
        } else {
            return null;
        }
    }

    @Override
    protected void onInit() {
        PanelWidget invPage = new PanelWidget(0, 0, width, height);
        invPage.animation = new AnimationTopToBottom(invPage);
        rootContainer.addChild("page_inv", invPage);
        {
            ImageWidget ui = new ImageWidget(leftPos, topPos - 22, imageWidth, 187, ImageResources.RenderTypes.RENDER_TYPE_WIND_GEN_UI);
            invPage.addChild("ui", ui);
            ui.animation = new AnimationTopToBottom(ui);
            PanelWidget statePanel = new PanelWidget(leftPos, topPos - 22, imageWidth, 187);
            statePanel.setHorizontalGravity(PanelWidget.HorizontalGravity.CENTER);
            statePanel.animation = new AnimationTopToBottom(statePanel);
            invPage.addChild("panel_state", statePanel);
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
            ImageRadioButtonWidget inv = new ImageRadioButtonWidget(0, 0, 16.8f, 16.8f,
                    ImageResources.RenderTypes.RENDER_TYPE_ICON_INV, () -> {
            });
            inv.animation = new AnimationTopToBottom(inv);
            radioGroupWidget.addChild("inv", inv);
            radioGroupWidget.selectButton(inv);

            ImageRadioButtonWidget wireless = new ImageRadioButtonWidget(0, 22, 16.8f, 16.8f,
                    ImageResources.RenderTypes.RENDER_TYPE_ICON_WIRELESS, () -> {
            });
            wireless.animation = new AnimationTopToBottom(wireless);
            radioGroupWidget.addChild("wireless", wireless);
            wireless.setSelected(false);
        }

        PanelWidget infoArea = new PanelWidget(leftPos + imageWidth, topPos - 19.5f, 110, 105);
        rootContainer.addChild("area_info", infoArea);
        {
            BlendQuadWidget back = new BlendQuadWidget(0, 0, infoArea.getWidth(), infoArea.getHeight());
            back.red = 0;
            back.green = 0;
            back.blue = 0;
            back.alpha = 0.5f;
            back.animation = new AnimationTopToBottom(back);
            infoArea.addChild("back", back);

            HistogramWidget histogramWidget = new HistogramWidget(0, 0, 84, 84);
            histogramWidget.addValue(histogramValue);
            AnimationTopToBottom animationHistogramWidget = new AnimationTopToBottom(histogramWidget);
            animationHistogramWidget.animationTime = 0.75f;
            histogramWidget.animation = animationHistogramWidget;
            AnimationTopToBottom animationHistogramWidgetBack = new AnimationTopToBottom(histogramWidget.back);
            animationHistogramWidgetBack.animationTime = 0.75f;
            histogramWidget.back.animation = animationHistogramWidgetBack;
            infoArea.addChild("histogram", histogramWidget);

            FillWidget bufferIcon = new FillWidget(6.5f, 73, 6.5f, 6.5f, 0xFF25F7FF);
            AnimationTopToBottom animationBufferIcon = new AnimationTopToBottom(bufferIcon);
            animationBufferIcon.animationTime = 0.75f;
            bufferIcon.animation = animationBufferIcon;
            infoArea.addChild("icon_buffer", bufferIcon);

            LabelWidget bufferLabel = new LabelWidget("BUFFER", 15, 72);
            AnimationTopToBottom animationBufferLabel = new AnimationTopToBottom(bufferLabel);
            animationBufferLabel.animationTime = 0.75f;
            bufferLabel.scale = 0.75f;
            bufferLabel.animation = animationBufferLabel;
            infoArea.addChild("label_buffer", bufferLabel);

            bufferValueLabel = new LabelWidget(AF, 50, 72);
            AnimationTopToBottom animationBufferValueLabel = new AnimationTopToBottom(bufferValueLabel);
            animationBufferValueLabel.animationTime = 0.75f;
            bufferValueLabel.scale = 0.75f;
            bufferValueLabel.animation = animationBufferValueLabel;
            infoArea.addChild("label_buffer_value", bufferValueLabel);

            LabelWidget infoLabel = new LabelWidget("Information", 8, 82);
            AnimationTopToBottom animationInfoLabel = new AnimationTopToBottom(infoLabel);
            animationInfoLabel.animationTime = 0.75f;
            infoLabel.scale = 0.75f;
            infoLabel.animation = animationInfoLabel;
            infoArea.addChild("label_info", infoLabel);

            LabelWidget altitudeLabel = new LabelWidget("Altitude", 10, 90);
            AnimationTopToBottom animationAltitudeLabel = new AnimationTopToBottom(altitudeLabel);
            animationAltitudeLabel.animationTime = 0.75f;
            altitudeLabel.scale = 0.75f;
            altitudeLabel.animation = animationAltitudeLabel;
            infoArea.addChild("label_altitude", altitudeLabel);

            String altitudeValue = "N/A";
            if (windGenBaseBlockEntity != null) {
                altitudeValue = windGenBaseBlockEntity.altitude + "";
            }
            LabelWidget altitudeValueLabel = new LabelWidget(altitudeValue, 50, 90);
            AnimationTopToBottom animationAltitudeValueLabel = new AnimationTopToBottom(altitudeValueLabel);
            animationAltitudeValueLabel.animationTime = 0.75f;
            altitudeValueLabel.scale = 0.75f;
            altitudeValueLabel.animation = animationAltitudeValueLabel;
            infoArea.addChild("label_altitude_value", altitudeValueLabel);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (baseIcon != null && pillarIcon != null && topIcon != null && bufferValueLabel != null) {
            bufferValueLabel.value = String.format(AF, windGenBaseBlockEntity.energyStored);
            float progress = (float) windGenBaseBlockEntity.energyStored / (float) windGenBaseBlockEntity.getMaxEnergyStorage();
            if (Float.isNaN(progress)) {
                progress = 0;
            }
            histogramValue.height = progress * 60;

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