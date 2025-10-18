package org.academy.internal.client.gui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.academy.api.client.Resource;
import org.academy.api.client.gui.animation.EasingFunctions;
import org.academy.api.client.gui.animation.ObjectAnimator;
import org.academy.api.client.gui.framework.CGuiContainerScreen;
import org.academy.api.client.gui.util.WirelessPanelUtil;
import org.academy.api.client.gui.widget.ImageRadioButtonWidget;
import org.academy.api.client.gui.widget.ImageWidget;
import org.academy.api.client.gui.widget.PanelWidget;
import org.academy.api.client.gui.widget.RadioGroupWidget;
import org.academy.api.client.util.ScreenAnimationUtil;
import org.academy.internal.common.world.inventory.SolarGenMenu;
import org.academy.internal.common.world.level.block.entity.SolarGenBlockEntity;
import org.jetbrains.annotations.Nullable;

public final class SolarGenScreen extends CGuiContainerScreen<SolarGenMenu> {
    private final BlockPos mainPos;

    public SolarGenScreen(SolarGenMenu menu, Inventory playerInventory, Component title, SolarGenBlockEntity blockEntity) {
        super(menu, playerInventory, title);
        mainPos = blockEntity.getBlockPos();
    }

    @Nullable
    public static SolarGenScreen create(SolarGenMenu menu, Inventory playerInventory, Component title, BlockPos mainPos) {
        if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.getBlockEntity(mainPos) instanceof SolarGenBlockEntity blockEntity) {
            return new SolarGenScreen(menu, playerInventory, title, blockEntity);
        } else {
            return null;
        }
    }

    @Override
    protected void onInit(PanelWidget main, PanelWidget invContent) {
        var startYOffset = 20f;
        var duration = 600L;
        var childDuration = duration - 100;

        var ui = new ImageWidget(0, 0, invContent.getWidth(), invContent.getHeight(), Resource.Textures.UI_GEN);
        invContent.addChild("ui", ui);

        var wirelessPanel = WirelessPanelUtil.create(leftPos, topPos - 22, mainPos, true);
        wirelessPanel.setZ(1);
        wirelessPanel.setEnabled(false);
        wirelessPanel.setVisible(false);
        rootContainer.addChild("panel_wireless", wirelessPanel);

        var radioGroupWidget = new RadioGroupWidget(leftPos - 16.8f, topPos - 22, 24, 48);
        rootContainer.addChild("radio_group", radioGroupWidget);
        {
            var inv = new ImageRadioButtonWidget(0, 0, 16.8f, 16.8f,
                    Resource.Textures.ICON_INV, () -> {
            });
            radioGroupWidget.addChild("inv", inv);

            var wireless = new ImageRadioButtonWidget(0, 22, 16.8f, 16.8f,
                    Resource.Textures.ICON_WIRELESS, () -> {
            });
            radioGroupWidget.addChild("wireless", wireless);

            radioGroupWidget.selectButton(inv);
        }
        radioGroupWidget.setOnSelectionChanged(imageRadioButtonWidget -> {
            var showInv = imageRadioButtonWidget.getId() == 0;
            var topY = topPos - 22;

            if (showInv) {
                setRenderInventory(true);
                ScreenAnimationUtil.show(this, main, topY);
                ScreenAnimationUtil.hide(this, wirelessPanel, topY);
            } else {
                setRenderInventory(false);
                ScreenAnimationUtil.show(this, wirelessPanel, topY);
                ScreenAnimationUtil.hide(this, main, topY);
            }
        });

        var radioFinalY = radioGroupWidget.getY();
        playAnimation(ObjectAnimator.ofFloat(radioGroupWidget::setY, radioFinalY + startYOffset, radioFinalY).setDuration(duration).setInterpolator(EasingFunctions.EASE_OUT_CUBIC));
        playAnimation(ObjectAnimator.ofFloat(radioGroupWidget::setAlpha, 0f, 1f).setDuration(childDuration).setInterpolator(EasingFunctions.LINEAR));
    }
}