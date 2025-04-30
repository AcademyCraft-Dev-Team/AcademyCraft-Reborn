package org.academy.internal.client.gui.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.academy.api.client.network.NetworkSystemClient;
import org.academy.api.client.network.S2CPacketHandler;
import org.academy.api.common.network.Packets;
import org.academy.internal.common.world.inventory.MenuTypes;
import org.academy.internal.common.world.inventory.WindGenMenu;
import org.academy.internal.common.world.level.block.AbilityDeveloperBlock;
import org.academy.internal.common.world.level.block.WindGenBaseBlock;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
public class Screens {
    public static final Map<String, S2CPacketHandler> SCREEN_IDS = new HashMap<>();
    public static final S2CPacketHandler WIND_GEN_SCREEN = registerScreen(WindGenBaseBlock.WIND_GEN_SCREEN,
            (listener, packet) -> {
                FriendlyByteBuf buf = packet.friendlyByteBuf;
                int containerId = buf.readVarInt();
                Component title = buf.readComponent();
                BlockPos pos = buf.readBlockPos();
                assert Minecraft.getInstance().player != null;
                Inventory inventory = Minecraft.getInstance().player.getInventory();
                MenuType<WindGenMenu> menuType = MenuTypes.WIND_GEN_MENU;
                WindGenMenu windGenMenu = menuType.create(containerId, inventory);
                WindGenScreen windGenScreen = new WindGenScreen(windGenMenu, inventory, title, pos);
                Minecraft.getInstance().player.containerMenu = windGenMenu;
                Minecraft.getInstance().setScreen(windGenScreen);
            });
    public static final S2CPacketHandler ABILITY_DEVELOPER_SCREEN = registerScreen(AbilityDeveloperBlock.ABILITY_DEVELOPER_SCREEN, (listener, packet) -> {
        BlockPos pos = packet.friendlyByteBuf.readBlockPos();
        Minecraft.getInstance().setScreen(new AbilityDeveloperScreen(pos));
    });

    public static S2CPacketHandler registerScreen(String id, S2CPacketHandler handler) {
        SCREEN_IDS.put(id, handler);
        return handler;
    }

    public static void register() {
        NetworkSystemClient.registerS2CPacketHandler(Packets.S2C_OPEN_SCREEN, (listener, packet) -> {
            FriendlyByteBuf buf = packet.friendlyByteBuf;
            S2CPacketHandler s2CPacketHandler = SCREEN_IDS.get(buf.readUtf());
            if (s2CPacketHandler != null) {
                s2CPacketHandler.handle(listener, packet);
            }
        });
    }

    private Screens() {
    }
}