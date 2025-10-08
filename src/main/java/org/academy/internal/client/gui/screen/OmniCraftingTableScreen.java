package org.academy.internal.client.gui.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.academy.api.client.Resource;
import org.academy.api.client.gui.animation.EasingFunctions;
import org.academy.api.client.gui.animation.ObjectAnimator;
import org.academy.api.client.gui.framework.CGuiContainerScreen;
import org.academy.api.client.gui.widget.*;
import org.academy.api.client.util.ScreenAnimationUtil;
import org.academy.internal.common.world.inventory.OmniCraftingMenu;

public final class OmniCraftingTableScreen extends CGuiContainerScreen<OmniCraftingMenu> {
    private final BlockPos mainPos;

    public OmniCraftingTableScreen(OmniCraftingMenu menu, Inventory playerInventory, Component title, BlockPos blockPos) {
        super(menu, playerInventory, title);
        mainPos = blockPos;
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
            var ui = new ImageWidget(0, 0, imageWidth, 187, Resource.Textures.OMNI_CRAFTING_UI);
            invPage.addChild("ui", ui);
        }

        var wirelessPanel = new WirelessPanelWidget(leftPos, topPos - 22, mainPos);
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
            var inv = new ImageRadioButtonWidget(0, 0, 16.8f, 16.8f, Resource.Textures.ICON_INV, () -> {
            });
            radioGroupWidget.addChild("inv", inv);
            radioGroupWidget.selectButton(inv);

            var wireless = new ImageRadioButtonWidget(0, 22, 16.8f, 16.8f, Resource.Textures.ICON_WIRELESS, () -> {
            });
            radioGroupWidget.addChild("wireless", wireless);
            wireless.setSelected(false);
        }

        var infoArea = new PanelWidget(leftPos + imageWidth + 3, topPos - 22, 110, 140);
        infoArea.setAlpha(0);
        rootContainer.addChild("area_info", infoArea);

        var radioFinalY = radioGroupWidget.getY();
        radioGroupWidget.setY(radioFinalY + startYOffset);
        radioGroupWidget.setAlpha(0f);
        playAnimation(ObjectAnimator.ofFloat(radioGroupWidget::setY, radioGroupWidget.getY(), radioFinalY).setDuration(duration).setInterpolator(EasingFunctions.EASE_OUT_CUBIC).setStartDelay(delay));
        playAnimation(ObjectAnimator.ofFloat(radioGroupWidget::setAlpha, 0f, 1f).setDuration(childDuration).setInterpolator(EasingFunctions.LINEAR).setStartDelay(delay));
    }
}