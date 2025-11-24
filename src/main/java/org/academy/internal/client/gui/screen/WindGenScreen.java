package org.academy.internal.client.gui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.bus.api.SubscribeEvent;
import org.academy.api.client.Resource;
import org.academy.api.client.gui.animation.EasingFunctions;
import org.academy.api.client.gui.animation.ObjectAnimator;
import org.academy.api.client.gui.layout.Gravity;
import org.academy.api.client.gui.layout.SizeMode;
import org.academy.api.client.gui.screen.ContainerUIScreen;
import org.academy.api.client.gui.util.InfoAreaUtil;
import org.academy.api.client.gui.util.WirelessPanelUtil;
import org.academy.api.client.gui.widget.*;
import org.academy.api.client.util.AnimationUtil;
import org.academy.internal.common.world.inventory.WindGenMenu;
import org.academy.internal.common.world.level.block.entity.WindGenBaseBlockEntity;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

import static org.academy.api.client.gui.util.InfoAreaUtil.createAttributeRow;
import static org.academy.api.client.gui.util.InfoAreaUtil.createInfoRow;

public final class WindGenScreen extends ContainerUIScreen<WindGenMenu> {
    private final BlockPos mainPos;
    public final WindGenBaseBlockEntity blockEntity;
    private Consumer<Float> topAlphaSetter = ignored -> {
    };
    private Consumer<Float> pillarAlphaSetter = ignored -> {
    };
    private Consumer<Float> baseAlphaSetter = ignored -> {
    };
    public static final String AF = "%d AF";
    private Consumer<String> bufferValueSetter = ignored -> {
    };

    public WindGenScreen(WindGenMenu menu, Inventory playerInventory, Component title, WindGenBaseBlockEntity blockEntity) {
        super(menu, playerInventory, title);
        mainPos = blockEntity.getBlockPos();
        this.blockEntity = blockEntity;
    }

    @Nullable
    public static WindGenScreen create(WindGenMenu menu, Inventory playerInventory, Component title, BlockPos mainPos) {
        if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.getBlockEntity(mainPos) instanceof WindGenBaseBlockEntity blockEntity) {
            return new WindGenScreen(menu, playerInventory, title, blockEntity);
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

        var effect = new FrameLayoutWidget();
        effect.setLayoutParams(
                new FrameLayoutWidget.LayoutParams()
                        .heightMode(SizeMode.MATCH_PARENT)
                        .width(24)
                        .gravity(Gravity.CENTER_HORIZONTAL)
                        .padding(0, 12, 0, 103)
        );
        invPage.addChild("effect", effect);
        {
            var topIcon = new ImageWidget(Resource.Textures.ICON_WIND_GEN_TOP);
            topAlphaSetter = topIcon::setAlpha;
            topIcon.setLayoutParams(
                    new FrameLayoutWidget.LayoutParams()
                            .sizeMode(SizeMode.MATCH_PARENT)
                            .padding(0, 0, 0, 48)
            );
            effect.addChild("icon_top", topIcon);

            var pillarIcon = new ImageWidget(Resource.Textures.ICON_WIND_GEN_PILLAR);
            pillarAlphaSetter = pillarIcon::setAlpha;
            pillarIcon.setLayoutParams(
                    new FrameLayoutWidget.LayoutParams()
                            .sizeMode(SizeMode.MATCH_PARENT)
                            .padding(0, 18, 0, 30)
            );
            effect.addChild("icon_pillar", pillarIcon);

            var baseIcon = new ImageWidget(Resource.Textures.ICON_WIND_GEN_BASE);
            baseAlphaSetter = baseIcon::setAlpha;
            baseIcon.setLayoutParams(
                    new FrameLayoutWidget.LayoutParams()
                            .sizeMode(SizeMode.MATCH_PARENT)
                            .padding(0, 36, 0, 12)
            );
            effect.addChild("icon_base", baseIcon);
        }

        var wirelessPage = WirelessPanelUtil.create(mainPos, true);
        wirelessPage.setVisible(false);
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

        var info = InfoAreaUtil.create(this, leftPos + imageWidth, topPos - 22);
        {
            var bufferValueLabel = new LabelWidget("0 AF");
            bufferValueSetter = bufferValueLabel::setText;
            var bufferLayout = createInfoRow("BUFFER", "icon_buffer", 0xFF25C4FF, bufferValueLabel);
            info.addChild("energy_layout", bufferLayout);

            var infoLabel = new LabelWidget("Information");
            infoLabel.setLayoutParams(
                    new LinearLayoutWidget.LayoutParams()
                            .padding(6.5f, 0, 0, 0)
            );
            infoLabel.setScale(0.75f);
            info.addChild("label_info", infoLabel);

            var altitudeValue = blockEntity.altitude + "";
            var altitudeValueLabel = new LabelWidget(altitudeValue);
            altitudeValueLabel.setScale(0.75f);
            var altitudeLayout = createAttributeRow("Altitude", altitudeValueLabel, 0.65f);
            info.addChild("altitude_layout", altitudeLayout);
        }

        pageButtons.startAnimation(ObjectAnimator.ofFloat(pageButtons::setAlpha, 0f, 1f).setDuration(childDuration));
        pageButtons.startAnimation(ObjectAnimator.ofFloat(pageButtons::setTranslationY, 20, 0).setDuration(duration).setInterpolator(EasingFunctions.EASE_OUT_CUBIC));

        updateInfo();
    }

    private void updateInfo() {
        bufferValueSetter.accept(String.format(AF, blockEntity.energyStored));

        switch (blockEntity.completeness) {
            case NO_TOP -> {
                baseAlphaSetter.accept(1f);
                pillarAlphaSetter.accept(1f);
                topAlphaSetter.accept(0.2f);
            }
            case BASE_ONLY -> {
                baseAlphaSetter.accept(1f);
                pillarAlphaSetter.accept(0.2f);
                topAlphaSetter.accept(0.2f);
            }
            case COMPLETE -> {
                baseAlphaSetter.accept(1f);
                pillarAlphaSetter.accept(1f);
                topAlphaSetter.accept(1f);
            }
            case COMPLETE_NOT_WORKING -> {
                baseAlphaSetter.accept(1f);
                pillarAlphaSetter.accept(1f);
                topAlphaSetter.accept(0.6f);
            }
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateInfo();
    }

    @SubscribeEvent
    public void onFocusGainedEvent(TextBoxWidget.FocusGainedEvent event) {
        setHandleContainer(false);
    }

    @SubscribeEvent
    public void onFocusLostEvent(TextBoxWidget.FocusLostEvent event) {
        setHandleContainer(true);
    }
}