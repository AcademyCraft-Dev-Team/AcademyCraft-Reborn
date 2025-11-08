package org.academy.internal.client.gui.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.academy.api.client.gui.screen.ContainerUIScreen;
import org.academy.api.client.gui.widget.*;
import org.academy.internal.common.world.inventory.OmniCraftingMenu;

public final class OmniCraftingTableScreen extends ContainerUIScreen<OmniCraftingMenu> {
    private final BlockPos mainPos;

    public OmniCraftingTableScreen(OmniCraftingMenu menu, Inventory playerInventory, Component title, BlockPos blockPos) {
        super(menu, playerInventory, title);
        mainPos = blockPos;
    }

    @Override
    protected void onInit(RadioGroupWidget pageButtons, ImageRadioButtonWidget invButton, FrameLayoutWidget content, FrameLayoutWidget invPage) {
       /* var duration = 600L;
        var delay = 250L;
        var childDuration = duration - 100;

        var mainLayout = new LinearLayoutWidget();
        mainLayout.setOrientation(Orientation.HORIZONTAL);
        mainLayout.setLayoutParams(new WidgetContainer.LayoutParams().gravity(Gravity.CENTER));
        mainLayout.setSpacing(3.0f);
        root.addChild("main_layout", mainLayout);

        var radioGroupWidget = new RadioGroupWidget();
        radioGroupWidget.setSpacing(5.2f);
        mainLayout.addChild("radio_group", radioGroupWidget);
        {
            var inv = new ImageRadioButtonWidget(Resource.Textures.ICON_INV, () -> {});
            radioGroupWidget.addChild("inv", inv);
            radioGroupWidget.selectButton(inv);

            var wireless = new ImageRadioButtonWidget(Resource.Textures.ICON_WIRELESS, () -> {});
            radioGroupWidget.addChild("wireless", wireless);
        }

        var centralArea = new PanelWidget();
        mainLayout.addChild("central_area", centralArea);

        centralArea.addChild("page_inv", main);

        var ui = new ImageWidget(Resource.Textures.OMNI_CRAFTING_UI);
        ui.setLayoutParams(new WidgetContainer.LayoutParams().widthMode(SizeMode.MATCH_PARENT).heightMode(SizeMode.MATCH_PARENT));
        invContent.addChild("ui", ui);

        var wirelessPanel = WirelessPanelUtil.create(mainPos, false);
        wirelessPanel.setLayoutParams(new WidgetContainer.LayoutParams().widthMode(SizeMode.MATCH_PARENT).heightMode(SizeMode.MATCH_PARENT));
        wirelessPanel.setZ(100);
        wirelessPanel.setVisible(false);
        wirelessPanel.setEnabled(false);
        centralArea.addChild("panel_wireless", wirelessPanel);

        radioGroupWidget.setOnSelectionChanged(imageRadioButtonWidget -> {
            var showInv = imageRadioButtonWidget.getName().equals("inv");
            if (showInv) {
                setHandleContainer(true);
                ScreenAnimationUtil.show(this, main);
                ScreenAnimationUtil.hide(this, wirelessPanel);
            } else {
                setHandleContainer(false);
                ScreenAnimationUtil.show(this, wirelessPanel);
                ScreenAnimationUtil.hide(this, main);
            }
        });

        var infoArea = new PanelWidget();
        mainLayout.addChild("area_info", infoArea);

        radioGroupWidget.setAlpha(0f);
        playAnimation(ObjectAnimator.ofFloat(radioGroupWidget::setAlpha, 0f, 1f).setDuration(childDuration).setInterpolator(EasingFunctions.LINEAR).setStartDelay(delay));
*/
    }
}