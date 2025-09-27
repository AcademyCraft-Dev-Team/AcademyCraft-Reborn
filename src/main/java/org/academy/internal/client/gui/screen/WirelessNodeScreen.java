package org.academy.internal.client.gui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.AcademyCraftClient;
import org.academy.api.client.Resource;
import org.academy.api.client.gui.animation.EasingFunctions;
import org.academy.api.client.gui.animation.ObjectAnimator;
import org.academy.api.client.gui.framework.CGuiContainerScreen;
import org.academy.api.client.gui.framework.Orientation;
import org.academy.api.client.gui.widget.*;
import org.academy.api.client.util.ScreenAnimationUtil;
import org.academy.api.common.wireless.SetNodeNamePacket;
import org.academy.api.common.wireless.SetNodePassPacket;
import org.academy.internal.common.world.inventory.WirelessNodeMenu;
import org.academy.internal.common.world.level.block.entity.WirelessNodeBlockEntity;

import javax.annotation.Nullable;

public final class WirelessNodeScreen extends CGuiContainerScreen<WirelessNodeMenu> {
    private final BlockPos mainPos;
    @Nullable
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
        this.mainPos = newMainPos;
        assert Minecraft.getInstance().level != null;
        if (Minecraft.getInstance().level.getBlockEntity(newMainPos) instanceof WirelessNodeBlockEntity blockEntity) {
            this.wirelessNodeBlockEntity = blockEntity;
        } else {
            this.onClose();
        }
    }

    @Override
    protected void onInit() {
        var startYOffset = 20f;
        var duration = 600L;
        var delay = 250L;
        var childDuration = duration - 100;

        var invPage = new PanelWidget(leftPos, topPos - 22, imageWidth, 187);
        invPage.setZ(1);
        rootContainer.addChild("page_inv", invPage);
        {
            var ui = new ImageWidget(0, 0, imageWidth, 187, Resource.Textures.WIRELESS_NODE_UI);
            invPage.addChild("ui", ui);

            state = new SpriteSheetWidget(
                    42, 33.5f, 186 / 2f, 75 / 2f,
                    Resource.Textures.WIRELESS_NODE_STATE,
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

        var radioGroupWidget = new RadioGroupWidget(leftPos - 16, topPos - 22, 16, 40);
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
            var inv = new ImageRadioButtonWidget(0, 0, 16, 16, Resource.Textures.ICON_INV, () -> {
            });
            radioGroupWidget.addChild("inv", inv);
            radioGroupWidget.selectButton(inv);

            var wireless = new ImageRadioButtonWidget(0, 22, 16, 16, Resource.Textures.ICON_WIRELESS, () -> {
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

            var infoTextArea = new PanelWidget(0, 0, infoArea.getWidth(), infoArea.getHeight());
            infoTextArea.setZ(1);
            infoArea.addChild("area_info_text", infoTextArea);
            {
                var histogramWidget = new HistogramWidget(0, 0, 84, 84);
                histogramWidget.addValue(this.histogramEnergyValue);
                histogramWidget.addValue(this.histogramCapacityValue);
                infoTextArea.addChild("histogram", histogramWidget);

                var energyIcon = new FillWidget(6.5f, 73, 6.5f, 6.5f, 0xFF25C4FF);
                infoTextArea.addChild("icon_energy", energyIcon);

                var energyLabel = new LabelWidget("ENERGY", 15, 72);
                energyLabel.setScale(0.75f);
                infoTextArea.addChild("label_energy", energyLabel);

                energyValueLabel = new LabelWidget("0 AF", 50, 72);
                energyValueLabel.setScale(0.75f);
                infoTextArea.addChild("label_energy_value", energyValueLabel);

                var capacityIcon = new FillWidget(6.5f, 82, 6.5f, 6.5f, 0xFFFF6C00);
                infoTextArea.addChild("icon_capacity", capacityIcon);

                var capacityLabel = new LabelWidget("CAPACITY", 15, 81);
                capacityLabel.setScale(0.75f);
                infoTextArea.addChild("label_capacity", capacityLabel);

                this.capacityValueLabel = new LabelWidget("0 / 0", 50, 81);
                this.capacityValueLabel.setScale(0.75f);
                infoTextArea.addChild("label_capacity_value", this.capacityValueLabel);

                var infoLabel = new LabelWidget("Information", 8, 92);
                infoLabel.setScale(0.75f);
                infoTextArea.addChild("label_info", infoLabel);

                var rangeLabel = new LabelWidget("Trans. Range", 10, 102);
                rangeLabel.setScale(0.65f);
                infoTextArea.addChild("label_range", rangeLabel);

                this.rangeValueLabel = new LabelWidget("0", 60, 102);
                this.rangeValueLabel.setScale(0.65f);
                infoTextArea.addChild("label_range_value", this.rangeValueLabel);

                var nameLabel = new LabelWidget("Node Name", 10, 112);
                nameLabel.setScale(0.65f);
                infoTextArea.addChild("label_name", nameLabel);

                var inputNameLabelLeft = new LabelWidget("[", 50, 112);
                inputNameLabelLeft.setScale(0.85f);
                infoTextArea.addChild("label_input_name_left", inputNameLabelLeft);

                var inputNameLabelRight = new LabelWidget("]", 100, 112);
                inputNameLabelRight.setScale(0.85f);
                infoTextArea.addChild("label_input_name_right", inputNameLabelRight);

                var nameTextBox = new TextBoxWidget(12, 53, 110, 45, inputNameLabelLeft.getHeight());
                nameTextBox.setWhenEnter(s -> {
                    if (this.wirelessNodeBlockEntity != null) {
                        AcademyCraftClient.sendPacket(new SetNodeNamePacket(this.wirelessNodeBlockEntity.getBlockPos(), s));
                    }
                });
                infoTextArea.addChild("label_name_text_box", nameTextBox);

                var passLabel = new LabelWidget("Password", 10, 122);
                passLabel.setScale(0.6f);
                infoTextArea.addChild("label_pass", passLabel);

                var inputPassLabelLeft = new LabelWidget("[", 50, 122);
                inputPassLabelLeft.setScale(0.85f);
                infoTextArea.addChild("label_input_pass_left", inputPassLabelLeft);

                var inputPassLabelRight = new LabelWidget("]", 100, 122);
                inputPassLabelRight.setScale(0.85f);
                infoTextArea.addChild("label_input_pass_right", inputPassLabelRight);

                var passTextBox = new TextBoxWidget(12, 53, 120, 45, inputPassLabelLeft.getHeight());
                passTextBox.setWhenEnter(s -> {
                    if (this.wirelessNodeBlockEntity != null) {
                        AcademyCraftClient.sendPacket(new SetNodePassPacket(this.wirelessNodeBlockEntity.getBlockPos(), s));
                    }
                });
                infoTextArea.addChild("label_pass_text_box", passTextBox);
            }
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
        if (this.wirelessNodeBlockEntity != null) {
            this.ticks++;

            if (this.capacityValueLabel != null) {
                this.capacityValueLabel.setText(this.wirelessNodeBlockEntity.connectedUsersCount + " / " + this.wirelessNodeBlockEntity.maxConnectedUsers);
            }
            if (this.energyValueLabel != null) {
                this.energyValueLabel.setText(WindGenScreen.AF.formatted(this.wirelessNodeBlockEntity.getEnergyStored()));
            }
            if (this.rangeValueLabel != null) {
                this.rangeValueLabel.setText(this.wirelessNodeBlockEntity.radius + "");
            }

            var progressCapacity =
                    (float) this.wirelessNodeBlockEntity.connectedUsersCount
                            /
                            (float) this.wirelessNodeBlockEntity.maxConnectedUsers;

            var progressEnergy =
                    (float) this.wirelessNodeBlockEntity.getEnergyStored()
                            /
                            (float) this.wirelessNodeBlockEntity.getMaxEnergyStorage();

            if (Float.isNaN(progressCapacity)) {
                progressCapacity = 0;
            }
            if (Float.isNaN(progressEnergy)) {
                progressEnergy = 0;
            }

            this.histogramCapacityValue.height = progressCapacity * 60;
            this.histogramEnergyValue.height = progressEnergy * 60;

            int index;
            if (this.wirelessNodeBlockEntity.connectedUsersCount == 0) {
                index = (this.ticks / 20) % 2 == 0 ? 8 : 9;
            } else {
                index = Math.max(0, Math.min((int) (progressCapacity * 8 - 1), 7));
            }

            this.state.setFrameIndex(index);
        }
    }

    @SubscribeEvent
    public void onFocusGainedEvent(TextBoxWidget.FocusGainedEvent event) {
        this.handleContainer = false;
    }

    @SubscribeEvent
    public void onFocusLostEvent(TextBoxWidget.FocusLostEvent event) {
        this.handleContainer = true;
    }

    @Override
    public void onClose() {
        super.onClose();
        NeoForge.EVENT_BUS.unregister(this);
    }
}