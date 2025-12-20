package org.academy.internal.client.gui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.academy.api.client.Resource;
import org.academy.api.client.gui.animation.EasingFunctions;
import org.academy.api.client.gui.animation.ObjectAnimator;
import org.academy.api.client.gui.layout.Gravity;
import org.academy.api.client.gui.layout.Orientation;
import org.academy.api.client.gui.layout.SizeMode;
import org.academy.api.client.gui.screen.ContainerUiScreen;
import org.academy.api.client.gui.util.WirelessPanelUtil;
import org.academy.api.client.gui.widget.*;
import org.academy.api.client.util.AnimationUtil;
import org.academy.internal.common.world.inventory.SolarGenMenu;
import org.academy.internal.common.world.level.block.entity.SolarGenBlockEntity;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

public final class SolarGenScreen extends ContainerUiScreen<SolarGenMenu> {
    private final BlockPos mainPos;
    private final SolarGenBlockEntity blockEntity;
    private Consumer<SolarGenBlockEntity.State> stateConsumer = _ -> {
    };

    private SolarGenScreen(SolarGenMenu menu, Inventory playerInventory, Component title, SolarGenBlockEntity blockEntity) {
        super(menu, playerInventory, title);
        this.blockEntity = blockEntity;
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
    protected void onInit(RadioGroupWidget pageButtons, RadioButtonWidget invButton, FrameLayoutWidget content, FrameLayoutWidget invPage) {
        var duration = 600L;
        var childDuration = duration - 100;

        var ui = new ImageWidget(Resource.Textures.UI_GEN);
        ui.setLayoutParams(
                new FrameLayoutWidget.LayoutParams()
                        .sizeMode(SizeMode.MATCH_PARENT)
        );
        invPage.addChild("ui", ui);

        var effect = new SpriteSheetWidget(
                Resource.Textures.ICON_SOLAR_GEN_SUNNY,
                Orientation.VERTICAL,
                48, 96,
                48, 48,
                2
        ) {
            private int ticks;

            @Override
            public void tick() {
                ticks++;
                if (ticks == 10){
                    nextFrame();
                    ticks = 0;
                }
            }
        };
        stateConsumer = state -> {
            switch (state) {
                case SUNNY -> effect.setTexture(Resource.Textures.ICON_SOLAR_GEN_SUNNY);
                case RAINY -> effect.setTexture(Resource.Textures.ICON_SOLAR_GEN_RAINY);
                case NIGHT -> effect.setTexture(Resource.Textures.ICON_SOLAR_GEN_NIGHT);
            }
        };
        effect.setLayoutParams(
                new FrameLayoutWidget.LayoutParams()
                        .heightMode(SizeMode.MATCH_PARENT)
                        .width(48)
                        .gravity(Gravity.CENTER_HORIZONTAL)
                        .padding(0, 21, 0, 118)
        );
        invPage.addChild("effect", effect);

        var wirelessPage = WirelessPanelUtil.create(mainPos, true);
        wirelessPage.setVisibility(Widget.Visibility.GONE);
        wirelessPage.setEnabled(false);
        content.addChild("page_wireless", wirelessPage);

        var wirelessButton = createButton(Resource.Textures.ICON_WIRELESS);
        wirelessButton.setLayoutParams(
                new WidgetContainer.LayoutParams()
                        .widthMode(SizeMode.MATCH_PARENT)
                        .height(16)
        );
        pageButtons.addChild("wireless", wirelessButton);
        pageButtons.setOnSelectionChanged(button -> {
            switch (button.getName()) {
                case "inv" -> {
                    AnimationUtil.hide(wirelessPage);
                    AnimationUtil.show(invPage);
                    setHandleContainer(true);
                    setRenderInventory(true);
                }
                case "wireless" -> {
                    AnimationUtil.hide(invPage);
                    AnimationUtil.show(wirelessPage);
                    setHandleContainer(false);
                    setRenderInventory(false);
                }
            }
        });
        pageButtons.selectButton(invButton);

        pageButtons.startAnimation(ObjectAnimator.ofFloat(pageButtons::setAlpha, 0f, 1f).setDuration(childDuration));
        pageButtons.startAnimation(ObjectAnimator.ofFloat(pageButtons::setTranslationY, 20, 0).setDuration(duration).setInterpolator(EasingFunctions.EASE_OUT_CUBIC));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        stateConsumer.accept(blockEntity.getState());
    }
}