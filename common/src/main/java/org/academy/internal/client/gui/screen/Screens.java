package org.academy.internal.client.gui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.academy.api.common.network.SubscribePacket;
import org.academy.api.common.network.NetworkSystem;
import org.academy.api.common.vanilla.OpenScreenPacket;
import org.academy.internal.common.world.inventory.MenuTypes;
import org.academy.internal.common.world.inventory.WindGenMenu;
import org.academy.internal.common.world.inventory.WirelessNodeMenu;
import org.academy.internal.common.world.level.block.AbilityDeveloperBlock;
import org.academy.internal.common.world.level.block.WindGenBaseBlock;
import org.academy.internal.common.world.level.block.WirelessNodeBlock;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

@SuppressWarnings("unused")
public class Screens {
    public static final Map<String, BiConsumer<ClientPacketListener, FriendlyByteBuf>> SCREEN_HANDLERS = new HashMap<>();

    static {
        SCREEN_HANDLERS.put(WindGenBaseBlock.WIND_GEN_SCREEN,
                (listener, buf) -> {
                    int containerId = buf.readVarInt();
                    Component title = buf.readComponent();
                    BlockPos pos = buf.readBlockPos();
                    if (Minecraft.getInstance().player != null) {
                        Inventory inventory = Minecraft.getInstance().player.getInventory();
                        MenuType<WindGenMenu> menuType = MenuTypes.WIND_GEN_MENU;
                        WindGenMenu windGenMenu = menuType.create(containerId, inventory);
                        WindGenScreen windGenScreen = WindGenScreen.create(windGenMenu, inventory, title, pos);
                        if (windGenScreen != null && Minecraft.getInstance().player != null) {
                            Minecraft.getInstance().player.containerMenu = windGenMenu;
                        }
                        Minecraft.getInstance().setScreen(windGenScreen);
                    }
                });
        SCREEN_HANDLERS.put(AbilityDeveloperBlock.ABILITY_DEVELOPER_SCREEN,
                (listener, buf) -> {
                    BlockPos pos = buf.readBlockPos();
                    Minecraft.getInstance().setScreen(new AbilityDeveloperScreen(pos));
                });
        SCREEN_HANDLERS.put(WirelessNodeBlock.WIRELESS_NODE_SCREEN,
                (clientPacketListener, buf) -> {
                    int containerId = buf.readVarInt();
                    Component title = buf.readComponent();
                    BlockPos pos = buf.readBlockPos();
                    assert Minecraft.getInstance().player != null;
                    Inventory inventory = Minecraft.getInstance().player.getInventory();
                    WirelessNodeMenu menu = MenuTypes.NODE_MENU.create(containerId, inventory);
                    Minecraft.getInstance().player.containerMenu = menu;
                    Minecraft.getInstance().setScreen(new WirelessNodeScreen(menu, inventory, title, pos));
                });
    }


    public static void register() {
        NetworkSystem.registerPacketType(OpenScreenPacket.class);
        NetworkSystem.registerPacketListener(Screens.class);
    }

    @SubscribePacket
    public static void handle(OpenScreenPacket packet) {
        BiConsumer<ClientPacketListener, FriendlyByteBuf> handler = SCREEN_HANDLERS.get(packet.screenName);
        if (handler != null && packet.packetListenerSupplier != null && packet.packetListenerSupplier.get() != null) {
            handler.accept(packet.packetListenerSupplier.get(), packet.dataPayload);
        }
    }

    private Screens() {
    }
}