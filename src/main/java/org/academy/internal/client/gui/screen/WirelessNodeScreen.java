package org.academy.internal.client.gui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.AcademyCraftClient;
import org.academy.api.client.gui.animation.EasingFunctions;
import org.academy.api.client.gui.animation.ObjectAnimator;
import org.academy.api.client.gui.framework.CGuiContainerScreen;
import org.academy.api.client.gui.framework.Orientation;
import org.academy.api.client.gui.widget.*;
import org.academy.api.client.render.RenderTypes;
import org.academy.api.client.util.ScreenAnimationUtil;
import org.academy.api.common.network.packet.C2SPacket;
import org.academy.api.common.wireless.SetNodeNamePacket;
import org.academy.api.common.wireless.SetNodePassPacket;
import org.academy.internal.common.world.inventory.WirelessNodeMenu;
import org.academy.internal.common.world.level.block.entity.WirelessNodeBlockEntity;

public final class WirelessNodeScreen extends CGuiContainerScreen<WirelessNodeMenu> {
    private final BlockPos mainPos;
    private WirelessNodeBlockEntity wirelessNodeBlockEntity;
    private PanelWidget wirelessPanel;
    private SpriteSheetWidget state;
    private int ticks;
    private final HistogramWidget.Value histogramEnergyValue = new HistogramWidget.Value(25, 5, 0,
            37f / 255f, 196f / 255f, 1, 1);
    private final HistogramWidget.Value histogramCapacityValue = new HistogramWidget.Value(35, 5, 0,
            1, 108f / 255f, 0, 1);
    private LabelWidget energyValueLabel;
    private LabelWidget capacityValueLabel;
    private LabelWidget rangeValueLabel;

    public WirelessNodeScreen(WirelessNodeMenu menu, Inventory playerInventory, Component title, BlockPos newMainPos) {
        super(menu, playerInventory, title);
        mainPos = newMainPos;
        assert Minecraft.getInstance().level != null;
        if (Minecraft.getInstance().level.getBlockEntity(newMainPos) instanceof WirelessNodeBlockEntity blockEntity) {
            wirelessNodeBlockEntity = blockEntity;
        } else {
            onClose();
        }
    }

    @Override
    protected void onInit() {
        NeoForge.EVENT_BUS.unregister(this);

        var startYOffset = 20f;
        var duration = 600L;
        var delay = 250L;
        var childDuration = duration - 100;

        var invPage = new PanelWidget(leftPos, topPos - 22, imageWidth, 187);
        rootContainer.addChild("page_inv", invPage);
        {
            var ui = new ImageWidget(0, 0, imageWidth, 187, RenderTypes.WIRELESS_NODE_UI);
            invPage.addChild("ui", ui);

            state = new SpriteSheetWidget(
                    42, 33.5f, 186 / 2f, 75 / 2f,
                    RenderTypes.WIRELESS_NODE_STATE,
                    Orientation.VERTICAL,
                    186, 750, 186, 75, 10);
            invPage.addChild("state", state);
        }
        invPage.setY(getTopPos() - 22);

        wirelessPanel = new WirelessPanelWidget(leftPos, topPos - 22, mainPos);
        wirelessPanel.setZ(100);
        wirelessPanel.setVisible(false);
        wirelessPanel.setEnabled(false);
        rootContainer.addChild("panel_wireless", wirelessPanel);

        var radioGroupWidget = new RadioGroupWidget(leftPos - 16.8f, topPos - 22, 24, 48);
        radioGroupWidget.setOnSelectionChanged(imageRadioButtonWidget -> {
            var showInv = "inv".equals(imageRadioButtonWidget.getName());
            var panelY = getTopPos() - 22;
            if (showInv) {
                renderInventory = true;
                ScreenAnimationUtil.show(this, invPage, panelY);
                ScreenAnimationUtil.hide(this, wirelessPanel, panelY);
            } else {
                renderInventory = false;
                ScreenAnimationUtil.show(this, wirelessPanel, panelY);
                ScreenAnimationUtil.hide(this, invPage, panelY);
            }
        });
        rootContainer.addChild("radio_group", radioGroupWidget);
        {
            var inv = new ImageRadioButtonWidget(0, 0, 16.8f, 16.8f, RenderTypes.ICON_INV, () -> {
            });
            radioGroupWidget.addChild("inv", inv);
            radioGroupWidget.selectButton(inv);

            var wireless = new ImageRadioButtonWidget(0, 22, 16.8f, 16.8f, RenderTypes.ICON_WIRELESS, () -> {
            });
            radioGroupWidget.addChild("wireless", wireless);
            wireless.setSelected(false);
        }

        var infoArea = new PanelWidget(leftPos + imageWidth + 3, topPos - 22, 110, 140);
        infoArea.setAlpha(0);
        rootContainer.addChild("area_info", infoArea);
        {
            var back = new BlendQuadWidget(0, 0, infoArea.getWidth(), infoArea.getHeight());
            infoArea.addChild("back", back);
            playAnimation(ObjectAnimator.ofFloat(back::setAlpha, 0, 0.5f).setDuration(duration));

            var histogramWidget = new HistogramWidget(0, 0, 84, 84);
            histogramWidget.addValue(histogramEnergyValue);
            histogramWidget.addValue(histogramCapacityValue);
            infoArea.addChild("histogram", histogramWidget);

            var energyIcon = new FillWidget(6.5f, 73, 6.5f, 6.5f, 0xFF25C4FF);
            infoArea.addChild("icon_energy", energyIcon);

            var energyLabel = new LabelWidget("ENERGY", 15, 72);
            energyLabel.setScale(0.75f);
            infoArea.addChild("label_energy", energyLabel);

            energyValueLabel = new LabelWidget("0 AF", 50, 72);
            energyValueLabel.setScale(0.75f);
            infoArea.addChild("label_energy_value", energyValueLabel);

            var capacityIcon = new FillWidget(6.5f, 82, 6.5f, 6.5f, 0xFFFF6C00);
            infoArea.addChild("icon_capacity", capacityIcon);

            var capacityLabel = new LabelWidget("CAPACITY", 15, 81);
            capacityLabel.setScale(0.75f);
            infoArea.addChild("label_capacity", capacityLabel);

            capacityValueLabel = new LabelWidget("0 / 0", 50, 81);
            capacityValueLabel.setScale(0.75f);
            infoArea.addChild("label_capacity_value", capacityValueLabel);

            var infoLabel = new LabelWidget("Information", 8, 92);
            infoLabel.setScale(0.75f);
            infoArea.addChild("label_info", infoLabel);

            var rangeLabel = new LabelWidget("Trans. Range", 10, 102);
            rangeLabel.setScale(0.65f);
            infoArea.addChild("label_range", rangeLabel);

            rangeValueLabel = new LabelWidget("0", 60, 102);
            rangeValueLabel.setScale(0.65f);
            infoArea.addChild("label_range_value", rangeValueLabel);

            var nameLabel = new LabelWidget("Node Name", 10, 112);
            nameLabel.setScale(0.65f);
            infoArea.addChild("label_name", nameLabel);

            var inputNameLabelLeft = new LabelWidget("[", 50, 112);
            inputNameLabelLeft.setScale(0.65f);
            infoArea.addChild("label_input_name_left", inputNameLabelLeft);

            var inputNameLabelRight = new LabelWidget("]", 100, 112);
            inputNameLabelRight.setScale(0.65f);
            infoArea.addChild("label_input_name_right", inputNameLabelRight);

            var nameTextBox = new TextBoxWidget(12, 53, 110, 45, inputNameLabelLeft.getHeight());
            nameTextBox.setWhenEnter(s -> {
                if (wirelessNodeBlockEntity != null) {
                    AcademyCraftClient.sendPacket(new C2SPacket(new SetNodeNamePacket(wirelessNodeBlockEntity.getBlockPos(), s)));
                }
            });
            infoArea.addChild("label_name_text_box", nameTextBox);

            var passLabel = new LabelWidget("Password", 10, 122);
            passLabel.setScale(0.6f);
            infoArea.addChild("label_pass", passLabel);

            var inputPassLabelLeft = new LabelWidget("[", 50, 122);
            inputPassLabelLeft.setScale(0.65f);
            infoArea.addChild("label_input_pass_left", inputPassLabelLeft);

            var inputPassLabelRight = new LabelWidget("]", 100, 122);
            inputPassLabelRight.setScale(0.65f);
            infoArea.addChild("label_input_pass_right", inputPassLabelRight);

            var passTextBox = new TextBoxWidget(12, 53, 120, 45, inputPassLabelLeft.getHeight());
            passTextBox.setWhenEnter(s -> {
                if (wirelessNodeBlockEntity != null) {
                    AcademyCraftClient.sendPacket(new C2SPacket(new SetNodePassPacket(wirelessNodeBlockEntity.getBlockPos(), s)));
                }
            });
            infoArea.addChild("label_pass_text_box", passTextBox);
        }

        var radioFinalY = radioGroupWidget.getY();
        radioGroupWidget.setY(radioFinalY + startYOffset);
        radioGroupWidget.setAlpha(0f);
        playAnimation(ObjectAnimator.ofFloat(radioGroupWidget::setY, radioGroupWidget.getY(), radioFinalY).setDuration(duration).setInterpolator(EasingFunctions.EASE_OUT_CUBIC).setStartDelay(delay));
        playAnimation(ObjectAnimator.ofFloat(radioGroupWidget::setAlpha, 0f, 1f).setDuration(childDuration).setInterpolator(EasingFunctions.LINEAR).setStartDelay(delay));

        var infoFinalY = infoArea.getY();
        infoArea.setY(infoFinalY + startYOffset);
        playAnimation(ObjectAnimator.ofFloat(infoArea::setY, infoArea.getY(), infoFinalY).setDuration(duration).setInterpolator(EasingFunctions.EASE_OUT_CUBIC).setStartDelay(delay));
        playAnimation(ObjectAnimator.ofFloat(infoArea::setAlpha, 0, 1).setStartDelay(delay).setDuration(duration));

        NeoForge.EVENT_BUS.register(this);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (wirelessNodeBlockEntity != null) {
            ticks++;

            if (capacityValueLabel != null) {
                capacityValueLabel.setText(wirelessNodeBlockEntity.connectedUsersCount + " / " + wirelessNodeBlockEntity.maxConnectedUsers);
            }
            if (energyValueLabel != null) {
                energyValueLabel.setText(WindGenScreen.AF.formatted(wirelessNodeBlockEntity.getEnergyStored()));
            }
            if (rangeValueLabel != null) {
                rangeValueLabel.setText(wirelessNodeBlockEntity.radius + "");
            }

            var progressCapacity =
                    (float) wirelessNodeBlockEntity.connectedUsersCount
                            /
                            (float) wirelessNodeBlockEntity.maxConnectedUsers;

            var progressEnergy =
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

    @SubscribeEvent
    public void onFocusGainedEvent(TextBoxWidget.FocusGainedEvent event) {
        handleContainer = false;
    }

    @SubscribeEvent
    public void onFocusLostEvent(TextBoxWidget.FocusLostEvent event) {
        handleContainer = true;
    }

    @Override
    public void onClose() {
        super.onClose();
        NeoForge.EVENT_BUS.unregister(this);
    }
}