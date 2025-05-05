package org.academy.internal.client.gui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.bus.api.SubscribeEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.gui.framework.ContainerSetFocusedChildEvent;
import org.academy.api.client.gui.ImageResources;
import org.academy.api.client.gui.WirelessPanelHelper;
import org.academy.api.client.gui.animation.AnimationTopToBottom;
import org.academy.api.client.gui.framework.CGuiContainerScreen;
import org.academy.api.client.gui.widget.*;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.common.network.Packets;
import org.academy.api.common.network.packet.C2SPacket;
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
    private final HistogramWidget.Value histogramEnergyValue = new HistogramWidget.Value(25, 5, 0,
            37f / 255f, 196f / 255f, 1, 1);
    private final HistogramWidget.Value histogramCapacityValue = new HistogramWidget.Value(35, 5, 0,
            1, 108f / 255f, 0, 1);
    private LabelWidget energyValueLabel;
    private LabelWidget capacityValueLabel;
    private LabelWidget rangeValueLabel;

    public WirelessNodeScreen(WirelessNodeMenu menu, Inventory playerInventory, Component title, BlockPos mainPos) {
        super(menu, playerInventory, title);
        this.mainPos = mainPos;
        assert Minecraft.getInstance().level != null;
        if (Minecraft.getInstance().level.getBlockEntity(mainPos) instanceof WirelessNodeBlockEntity blockEntity) {
            wirelessNodeBlockEntity = blockEntity;
            AcademyCraft.EVENT_BUS.register(this);
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
                    requestAvailableNodes(nodeListPanel);
                    wirelessPanel.setVisible(true);
                    wirelessPanel.setEnabled(true);
                    break;
            }
        });
        rootContainer.addChild("radio_group", radioGroupWidget);
        {
            ImageRadioButtonWidget inv = new ImageRadioButtonWidget(0, 0, 16.8f, 16.8f, ImageResources.RenderTypes.RENDER_TYPE_ICON_INV, () -> {
            });
            inv.animation = new AnimationTopToBottom(inv);
            radioGroupWidget.addChild("inv", inv);
            radioGroupWidget.selectButton(inv);

            ImageRadioButtonWidget wireless = new ImageRadioButtonWidget(0, 22, 16.8f, 16.8f, ImageResources.RenderTypes.RENDER_TYPE_ICON_WIRELESS, () -> {
            });
            wireless.animation = new AnimationTopToBottom(wireless);
            radioGroupWidget.addChild("wireless", wireless);
            wireless.setSelected(false);
        }

        PanelWidget infoArea = new PanelWidget(leftPos + imageWidth, topPos - 19.5f, 110, 140);
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
            histogramWidget.addValue(histogramEnergyValue);
            histogramWidget.addValue(histogramCapacityValue);
            AnimationTopToBottom animationHistogramWidget = new AnimationTopToBottom(histogramWidget);
            animationHistogramWidget.animationTime = 0.75f;
            histogramWidget.animation = animationHistogramWidget;
            AnimationTopToBottom animationHistogramWidgetBack = new AnimationTopToBottom(histogramWidget.back);
            animationHistogramWidgetBack.animationTime = 0.75f;
            histogramWidget.back.animation = animationHistogramWidgetBack;
            infoArea.addChild("histogram", histogramWidget);

            FillWidget energyIcon = new FillWidget(6.5f, 73, 6.5f, 6.5f, 0xFF25C4FF);
            AnimationTopToBottom animationBufferIcon = new AnimationTopToBottom(energyIcon);
            animationBufferIcon.animationTime = 0.75f;
            energyIcon.animation = animationBufferIcon;
            infoArea.addChild("icon_energy", energyIcon);

            LabelWidget energyLabel = new LabelWidget("ENERGY", 15, 72);
            AnimationTopToBottom animationEnergyLabel = new AnimationTopToBottom(energyLabel);
            animationEnergyLabel.animationTime = 0.75f;
            energyLabel.scale = 0.75f;
            energyLabel.animation = animationEnergyLabel;
            infoArea.addChild("label_energy", energyLabel);

            energyValueLabel = new LabelWidget("0 AF", 50, 72);
            AnimationTopToBottom animationEnergyValueLabel = new AnimationTopToBottom(energyValueLabel);
            animationEnergyValueLabel.animationTime = 0.75f;
            energyValueLabel.scale = 0.75f;
            energyValueLabel.animation = animationEnergyValueLabel;
            infoArea.addChild("label_energy_value", energyValueLabel);

            FillWidget capacityIcon = new FillWidget(6.5f, 82, 6.5f, 6.5f, 0xFFFF6C00);
            AnimationTopToBottom animationCapacityIcon = new AnimationTopToBottom(capacityIcon);
            animationCapacityIcon.animationTime = 0.75f;
            capacityIcon.animation = animationCapacityIcon;
            infoArea.addChild("icon_capacity", capacityIcon);

            LabelWidget capacityLabel = new LabelWidget("CAPACITY", 15, 81);
            AnimationTopToBottom animationCapacityLabel = new AnimationTopToBottom(capacityLabel);
            animationCapacityLabel.animationTime = 0.75f;
            capacityLabel.scale = 0.75f;
            capacityLabel.animation = animationCapacityLabel;
            infoArea.addChild("label_capacity", capacityLabel);

            capacityValueLabel = new LabelWidget("0 / 0", 50, 81);
            AnimationTopToBottom animationCapacityValueLabel = new AnimationTopToBottom(capacityValueLabel);
            animationCapacityValueLabel.animationTime = 0.75f;
            capacityValueLabel.scale = 0.75f;
            capacityValueLabel.animation = animationCapacityValueLabel;
            infoArea.addChild("label_capacity_value", capacityValueLabel);

            LabelWidget infoLabel = new LabelWidget("Information", 8, 92);
            AnimationTopToBottom animationInfoLabel = new AnimationTopToBottom(infoLabel);
            animationInfoLabel.animationTime = 0.75f;
            infoLabel.scale = 0.75f;
            infoLabel.animation = animationInfoLabel;
            infoArea.addChild("label_info", infoLabel);

            LabelWidget rangeLabel = new LabelWidget("Trans. Range", 10, 102);
            AnimationTopToBottom animationRangeLabel = new AnimationTopToBottom(infoLabel);
            animationRangeLabel.animationTime = 0.75f;
            rangeLabel.scale = 0.65f;
            rangeLabel.animation = animationRangeLabel;
            infoArea.addChild("label_range", rangeLabel);

            rangeValueLabel = new LabelWidget("0", 60, 102);
            AnimationTopToBottom animationRangeValueLabel = new AnimationTopToBottom(rangeValueLabel);
            animationRangeValueLabel.animationTime = 0.75f;
            rangeValueLabel.scale = 0.65f;
            rangeValueLabel.animation = animationRangeValueLabel;
            infoArea.addChild("label_range_value", rangeValueLabel);

            LabelWidget nameLabel = new LabelWidget("Node Name", 10, 112);
            AnimationTopToBottom animationNameLabel = new AnimationTopToBottom(nameLabel);
            animationNameLabel.animationTime = 0.75f;
            nameLabel.scale = 0.65f;
            nameLabel.animation = animationNameLabel;
            infoArea.addChild("label_name", nameLabel);

            LabelWidget inputNameLabelLeft = new LabelWidget("[", 50, 112);
            AnimationTopToBottom animationInputNameLabelLeft = new AnimationTopToBottom(inputNameLabelLeft);
            animationInputNameLabelLeft.animationTime = 0.75f;
            inputNameLabelLeft.scale = 0.65f;
            inputNameLabelLeft.animation = animationInputNameLabelLeft;
            infoArea.addChild("label_input_name_left", inputNameLabelLeft);

            LabelWidget inputNameLabelRight = new LabelWidget("]", 100, 112);
            AnimationTopToBottom animationInputNameLabelRight = new AnimationTopToBottom(inputNameLabelRight);
            animationInputNameLabelRight.animationTime = 0.75f;
            inputNameLabelRight.scale = 0.65f;
            inputNameLabelRight.animation = animationInputNameLabelRight;
            infoArea.addChild("label_input_name_right", inputNameLabelRight);

            TextBoxWidget nameTextBox = new TextBoxWidget(12, 53, 110, 45, inputNameLabelLeft.getHeight());
            nameTextBox.whenEnter = s -> {
                if (wirelessNodeBlockEntity != null) {
                    NetworkSystemClient.sendPacket(new C2SPacket(Packets.C2S_SET_NODE_NAME, wirelessNodeBlockEntity.getBlockPos(), s));
                }
            };
            infoArea.addChild("label_name_text_box", nameTextBox);

            LabelWidget passLabel = new LabelWidget("Password", 10, 122);
            AnimationTopToBottom animationPassLabel = new AnimationTopToBottom(passLabel);
            animationPassLabel.animationTime = 0.75f;
            passLabel.scale = 0.6f;
            passLabel.animation = animationPassLabel;
            infoArea.addChild("label_pass", passLabel);

            LabelWidget inputPassLabelLeft = new LabelWidget("[", 50, 122);
            AnimationTopToBottom animationInputPassLabelLeft = new AnimationTopToBottom(inputPassLabelLeft);
            animationInputPassLabelLeft.animationTime = 0.75f;
            inputPassLabelLeft.scale = 0.65f;
            inputPassLabelLeft.animation = animationInputPassLabelLeft;
            infoArea.addChild("label_input_pass_left", inputPassLabelLeft);

            LabelWidget inputPassLabelRight = new LabelWidget("]", 100, 122);
            AnimationTopToBottom animationInputPassLabelRight = new AnimationTopToBottom(inputPassLabelRight);
            animationInputPassLabelRight.animationTime = 0.75f;
            inputPassLabelRight.scale = 0.65f;
            inputPassLabelRight.animation = animationInputPassLabelRight;
            infoArea.addChild("label_input_pass_right", inputPassLabelRight);

            TextBoxWidget passTextBox = new TextBoxWidget(12, 53, 120, 45, inputPassLabelLeft.getHeight());
            passTextBox.whenEnter = s -> {
                if (wirelessNodeBlockEntity != null) {
                    NetworkSystemClient.sendPacket(new C2SPacket(Packets.C2S_SET_NODE_PASS, wirelessNodeBlockEntity.getBlockPos(), s));
                }
            };
            passTextBox.onFocusGained();
            infoArea.addChild("label_pass_text_box", passTextBox);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (wirelessNodeBlockEntity != null) {
            ticks++;

            if (capacityValueLabel != null) {
                capacityValueLabel.value = wirelessNodeBlockEntity.connectedUsersCount + " / " + wirelessNodeBlockEntity.maxConnectedUsers;
            }
            if (energyValueLabel != null) {
                energyValueLabel.value = WindGenScreen.AF.formatted(wirelessNodeBlockEntity.getEnergyStored());
            }
            if (rangeValueLabel != null) {
                rangeValueLabel.value = wirelessNodeBlockEntity.radius + "";
            }

            float progressCapacity =
                    (float) wirelessNodeBlockEntity.connectedUsersCount
                            /
                            (float) wirelessNodeBlockEntity.maxConnectedUsers;

            float progressEnergy =
                    (float) wirelessNodeBlockEntity.getEnergyStored()
                            /
                            (float) wirelessNodeBlockEntity.getMaxEnergyStorage();

            if (Float.isNaN(progressCapacity)) {
                progressCapacity = 0;
            }
            if (Float.isNaN(progressEnergy)) {
                progressEnergy = 0;
            }

            histogramCapacityValue.height = progressCapacity * 60;
            histogramEnergyValue.height = progressEnergy * 60;

            int index;
            if (wirelessNodeBlockEntity.connectedUsersCount == 0) {
                index = (ticks / 20) % 2 == 0 ? 8 : 9;
            } else {
                index = Math.max(0, Math.min((int) (progressCapacity * 8 - 1), 7));
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

    @SubscribeEvent
    public void onContainerSetFocusedChild(ContainerSetFocusedChildEvent event) {
        handleContainer = !(event.child instanceof TextBoxWidget);
    }

    @Override
    public void onClose() {
        super.onClose();
        AcademyCraft.EVENT_BUS.unregister(this);
    }
}