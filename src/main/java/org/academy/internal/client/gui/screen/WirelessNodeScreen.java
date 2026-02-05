package org.academy.internal.client.gui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.api.client.Resource;
import org.academy.api.client.gui.animation.EasingFunctions;
import org.academy.api.client.gui.animation.ObjectAnimator;
import org.academy.api.client.gui.layout.Gravity;
import org.academy.api.client.gui.layout.Orientation;
import org.academy.api.client.gui.layout.SizeMode;
import org.academy.api.client.gui.msdf.atlas.MsdfAtlasDebugger;
import org.academy.api.client.gui.msdf.atlas.MsdfAtlasManager;
import org.academy.api.client.gui.msdf.font.MsdfFontService;
import org.academy.api.client.gui.screen.ContainerUiScreen;
import org.academy.api.client.gui.util.InfoAreaUtil;
import org.academy.api.client.gui.util.WirelessPanelUtil;
import org.academy.api.client.gui.widget.*;
import org.academy.api.client.util.AnimationUtil;
import org.academy.api.common.wireless.SetNodeNamePacket;
import org.academy.api.common.wireless.SetNodePassPacket;
import org.academy.internal.common.world.inventory.WirelessNodeMenu;
import org.academy.internal.common.world.level.block.entity.WirelessNodeBlockEntity;
import org.jspecify.annotations.Nullable;
import org.misaka.MisakaNetworkClient;

import java.util.ArrayList;
import java.util.function.Consumer;

import static org.academy.api.client.gui.util.InfoAreaUtil.*;

public final class WirelessNodeScreen extends ContainerUiScreen<WirelessNodeMenu> {
    private final BlockPos mainPos;
    private final WirelessNodeBlockEntity wirelessNodeBlockEntity;
    private int ticks;
    private Consumer<String> energyValueSetter = _ -> {
    };
    private Consumer<String> capacityValueSetter = _ -> {
    };
    private Consumer<String> rangeValueSetter = _ -> {
    };

    public WirelessNodeScreen(WirelessNodeMenu menu, Inventory playerInventory, Component title, WirelessNodeBlockEntity blockEntity) {
        super(menu, playerInventory, title);
        mainPos = blockEntity.getBlockPos();

        wirelessNodeBlockEntity = blockEntity;
        NeoForge.EVENT_BUS.register(this);
    }

    @Override
    public void onClose() {
        super.onClose();
        NeoForge.EVENT_BUS.unregister(this);
    }

    @Nullable
    public static WirelessNodeScreen create(WirelessNodeMenu menu, Inventory playerInventory, Component title, BlockPos mainPos) {
        var set = new ArrayList<>(MsdfFontService.getInstance().getLoadedFonts().keySet());
        for (var i = 0; i < set.size(); i++) {
            MsdfAtlasDebugger.dumpAtlas(MsdfAtlasManager.getInstance().getAtlas(set.get(i)), "" + i);
        }

        if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.getBlockEntity(mainPos) instanceof WirelessNodeBlockEntity blockEntity) {
            return new WirelessNodeScreen(menu, playerInventory, title, blockEntity);
        } else {
            return null;
        }
    }

    @Override
    protected void onInit(RadioGroupWidget pageButtons, RadioButtonWidget invButton, FrameLayoutWidget content, FrameLayoutWidget invPage) {
        var duration = 600L;
        var childDuration = duration - 100;

        var ui = new ImageWidget(Resource.Textures.WIRELESS_NODE_UI);
        ui.setLayoutParams(
                new FrameLayoutWidget.LayoutParams()
                        .sizeMode(SizeMode.MATCH_PARENT)
        );
        invPage.addChild("ui", ui);

        var effect = new SpriteSheetWidget(
                Resource.Textures.WIRELESS_NODE_STATE,
                Orientation.VERTICAL,
                186, 750,
                186, 75,
                10
        ) {
            @Override
            public void tick() {
                var progressCapacity = (float) wirelessNodeBlockEntity.connectedUsersCount / wirelessNodeBlockEntity.maxConnectedUsers;

                if (Float.isNaN(progressCapacity)) progressCapacity = 0;

                int index;
                if (wirelessNodeBlockEntity.connectedUsersCount == 0) {
                    index = (ticks / 20) % 2 == 0 ? 8 : 9;
                } else {
                    index = Math.max(0, Math.min((int) (progressCapacity * 8 - 1), 7));
                }

                setFrameIndex(index);
            }
        };
        effect.setLayoutParams(
                new FrameLayoutWidget.LayoutParams()
                        .heightMode(SizeMode.MATCH_PARENT)
                        .width(186 / 2f)
                        .gravity(Gravity.CENTER_HORIZONTAL)
                        .padding(0, 33.5f, 0, 116)
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

        var info = InfoAreaUtil.create(this, leftPos + imageWidth, topPos - 22);
        {
            var energyValueLabel = new LabelWidget("0 AF");
            energyValueSetter = energyValueLabel::setText;
            var energyLayout = createInfoRow("ENERGY", "icon_energy", 0xFF25C4FF, energyValueLabel);
            info.addChild("energy_layout", energyLayout);

            var capacityValueLabel = new LabelWidget("0 / 0");
            capacityValueSetter = capacityValueLabel::setText;
            var capacityLayout = createInfoRow("CAPACITY", "icon_capacity", 0xFFFF6C00, capacityValueLabel);
            info.addChild("capacity_layout", capacityLayout);

            var infoLabel = new LabelWidget("Information");
            infoLabel.setLayoutParams(
                    new LinearLayoutWidget.LayoutParams()
                            .padding(6.5f, 0, 0, 0)
            );
            infoLabel.setScale(0.75f);
            info.addChild("label_info", infoLabel);

            var rangeValueLabel = new LabelWidget("0");
            rangeValueSetter = rangeValueLabel::setText;
            rangeValueLabel.setLayoutParams(
                    new WidgetContainer.LayoutParams()
                            .size(12, 12)
                            .gravity(Gravity.CENTER)
            );
            var rangeLayout = createAttributeRow("Trans. Range", rangeValueLabel, 52);
            info.addChild("range_layout", rangeLayout);

            var nameTextBox = new TextBoxWidget(12);
            nameTextBox.setBackground(null);
            nameTextBox.setWhenEnter(s -> MisakaNetworkClient.sendPacket(new SetNodeNamePacket(wirelessNodeBlockEntity.getBlockPos(), s)));
            var nameLayout = createAttributeRow("Node Name", createInputRow(nameTextBox), 48);
            info.addChild("name_layout", nameLayout);

            var passTextBox = new TextBoxWidget(12);
            passTextBox.setBackground(null);
            passTextBox.setWhenEnter(s -> MisakaNetworkClient.sendPacket(new SetNodePassPacket(wirelessNodeBlockEntity.getBlockPos(), s)));
            var passLayout = createAttributeRow("Password", createInputRow(passTextBox), 42);
            info.addChild("pass_layout", passLayout);
        }

        pageButtons.startAnimation(ObjectAnimator.ofFloat(pageButtons::setAlpha, 0f, 1f).setDuration(childDuration));
        pageButtons.startAnimation(ObjectAnimator.ofFloat(pageButtons::setTranslationY, 20, 0).setDuration(duration).setInterpolator(EasingFunctions.EASE_OUT_CUBIC));

        updateInfo();
    }

    private void updateInfo() {
        ticks++;

        capacityValueSetter.accept(wirelessNodeBlockEntity.connectedUsersCount + " / " + wirelessNodeBlockEntity.maxConnectedUsers);
        energyValueSetter.accept(WindGenScreen.AF.formatted(wirelessNodeBlockEntity.getEnergyStored()));
        rangeValueSetter.accept(wirelessNodeBlockEntity.radius + "");
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