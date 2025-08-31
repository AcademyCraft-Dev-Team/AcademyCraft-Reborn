package org.academy.internal.client.gui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.bus.api.SubscribeEvent;
import org.academy.api.client.Resource;
import org.academy.api.client.gui.animation.EasingFunctions;
import org.academy.api.client.gui.animation.ObjectAnimator;
import org.academy.api.client.gui.framework.CGuiContainerScreen;
import org.academy.api.client.gui.widget.*;
import org.academy.api.client.util.ScreenAnimationUtil;
import org.academy.internal.common.world.inventory.WindGenMenu;
import org.academy.internal.common.world.level.block.entity.WindGenBaseBlockEntity;

public final class WindGenScreen extends CGuiContainerScreen<WindGenMenu> {
    public final BlockPos mainPos;
    public final WindGenBaseBlockEntity windGenBaseBlockEntity;
    public ImageWidget topIcon;
    public ImageWidget pillarIcon;
    public ImageWidget baseIcon;
    public static final String AF = "%d AF";
    private PanelWidget wirelessPanel;
    private LabelWidget bufferValueLabel;
    private final HistogramWidget.Value histogramValue = new HistogramWidget.Value(25, 5, 0,
            37f / 255f, 247f / 255f, 1, 1);

    public WindGenScreen(WindGenMenu menu, Inventory playerInventory, Component title, WindGenBaseBlockEntity newWindGenBaseBlockEntity) {
        super(menu, playerInventory, title);
        mainPos = newWindGenBaseBlockEntity.getBlockPos();
        windGenBaseBlockEntity = newWindGenBaseBlockEntity;
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
        var startYOffset = 20f;
        var duration = 600L;
        var delay = 250L;
        var childDuration = duration - 100;

        var invPage = new PanelWidget(leftPos, topPos - 22, imageWidth, 187);
        rootContainer.addChild("page_inv", invPage);
        {
            var ui = new ImageWidget(0, 0, imageWidth, 187, Resource.Textures.WIND_GEN_UI);
            invPage.addChild("ui", ui);
            var statePanel = new PanelWidget(0, 0, imageWidth, 187);
            invPage.addChild("panel_state", statePanel);
            {
                topIcon = new ImageWidget(0, 13, 24, 24, Resource.Textures.ICON_WIND_GEN_TOP);
                statePanel.addChild("icon_top", topIcon);
                pillarIcon = new ImageWidget(0, 31, 24, 24, Resource.Textures.ICON_WIND_GEN_PILLAR);
                statePanel.addChild("icon_pillar", pillarIcon);
                baseIcon = new ImageWidget(0, 49, 24, 24, Resource.Textures.ICON_WIND_GEN_BASE);
                statePanel.addChild("icon_base", baseIcon);
            }
        }
        invPage.setY(getTopPos() - 22);

        wirelessPanel = new WirelessPanelWidget(leftPos, topPos - 22, mainPos);
        wirelessPanel.setZ(100);
        wirelessPanel.setVisible(false);
        wirelessPanel.setEnabled(false);
        rootContainer.addChild("panel_wireless", wirelessPanel);

        var radioGroupWidget = new RadioGroupWidget(leftPos - 16.8f, topPos - 22, 24, 48);
        radioGroupWidget.setOnSelectionChanged(imageRadioButtonWidget -> {
            var showInv = imageRadioButtonWidget.getId() == 0;
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
            var inv = new ImageRadioButtonWidget(0, 0, 16.8f, 16.8f,
                    Resource.Textures.ICON_INV, () -> {
            });
            radioGroupWidget.addChild("inv", inv);
            radioGroupWidget.selectButton(inv);

            var wireless = new ImageRadioButtonWidget(0, 22, 16.8f, 16.8f,
                    Resource.Textures.ICON_WIRELESS, () -> {
            });
            radioGroupWidget.addChild("wireless", wireless);
            wireless.setSelected(false);
        }

        var infoArea = new PanelWidget(leftPos + imageWidth + 3, topPos - 22, 110, 105);
        infoArea.setAlpha(0);
        rootContainer.addChild("area_info", infoArea);
        {
            var back = new BlendQuadWidget(0, 0, infoArea.getWidth(), infoArea.getHeight());
            back.setAlpha(0.5f);
            infoArea.addChild("back", back);

            var histogramWidget = new HistogramWidget(0, 0, 84, 84);
            histogramWidget.addValue(histogramValue);
            infoArea.addChild("histogram", histogramWidget);

            var bufferIcon = new FillWidget(6.5f, 73, 6.5f, 6.5f, 0xFF25F7FF);
            infoArea.addChild("icon_buffer", bufferIcon);

            var bufferLabel = new LabelWidget("BUFFER", 15, 72);
            bufferLabel.setScale(0.75f);
            infoArea.addChild("label_buffer", bufferLabel);

            bufferValueLabel = new LabelWidget(AF, 50, 72);
            bufferValueLabel.setScale(0.75f);
            infoArea.addChild("label_buffer_value", bufferValueLabel);

            var infoLabel = new LabelWidget("Information", 8, 82);
            infoLabel.setScale(0.75f);
            infoArea.addChild("label_info", infoLabel);

            var altitudeLabel = new LabelWidget("Altitude", 10, 90);
            altitudeLabel.setScale(0.75f);
            infoArea.addChild("label_altitude", altitudeLabel);

            var altitudeValue = "N/A";
            if (windGenBaseBlockEntity != null) {
                altitudeValue = windGenBaseBlockEntity.altitude + "";
            }
            var altitudeValueLabel = new LabelWidget(altitudeValue, 50, 90);
            altitudeValueLabel.setScale(0.75f);
            infoArea.addChild("label_altitude_value", altitudeValueLabel);
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
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (baseIcon != null && pillarIcon != null && topIcon != null && bufferValueLabel != null) {
            bufferValueLabel.setText(String.format(AF, windGenBaseBlockEntity.energyStored));
            var progress = (float) windGenBaseBlockEntity.energyStored / (float) windGenBaseBlockEntity.getMaxEnergyStorage();
            if (Float.isNaN(progress)) {
                progress = 0;
            }
            histogramValue.height = progress * 60;

            switch (windGenBaseBlockEntity.completeness) {
                case NO_TOP -> {
                    baseIcon.setAlpha(1f);
                    pillarIcon.setAlpha(1f);
                    topIcon.setAlpha(0.2f);
                }
                case BASE_ONLY -> {
                    baseIcon.setAlpha(1f);
                    pillarIcon.setAlpha(0.2f);
                    topIcon.setAlpha(0.2f);
                }
                case COMPLETE -> {
                    baseIcon.setAlpha(1f);
                    pillarIcon.setAlpha(1f);
                    topIcon.setAlpha(1f);
                }
                case COMPLETE_NOT_WORKING -> {
                    baseIcon.setAlpha(1f);
                    pillarIcon.setAlpha(1f);
                    topIcon.setAlpha(0.6f);
                }
            }
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
}