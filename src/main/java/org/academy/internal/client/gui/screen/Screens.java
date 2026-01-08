package org.academy.internal.client.gui.screen;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.academy.api.common.vanilla.OpenScreenPacket;
import org.academy.internal.common.world.inventory.MenuTypes;
import org.academy.internal.common.world.level.block.*;
import org.misaka.MisakaNetworkClient;
import org.misaka.api.common.network.annotation.SubscribePacket;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public final class Screens {
    public static final Map<String, BiConsumer<ClientPacketListener, FriendlyByteBuf>> SCREEN_HANDLERS = new HashMap<>();

    static {
        SCREEN_HANDLERS.put(WindGenBaseBlock.WIND_GEN_SCREEN,
                (_, buf) -> {
                    var containerId = buf.readVarInt();
                    var title = buf.readUtf();
                    var pos = buf.readBlockPos();
                    if (Minecraft.getInstance().player != null) {
                        var inventory = Minecraft.getInstance().player.getInventory();
                        var menuType = MenuTypes.WIND_GEN.get();
                        var windGenMenu = menuType.create(containerId, inventory);
                        var windGenScreen = WindGenScreen.create(windGenMenu, inventory, Component.literal(title), pos);
                        if (windGenScreen != null && Minecraft.getInstance().player != null) {
                            Minecraft.getInstance().player.containerMenu = windGenMenu;
                        }
                        Minecraft.getInstance().setScreen(windGenScreen);
                    }
                });
        SCREEN_HANDLERS.put(AbilityDeveloperBlock.ABILITY_DEVELOPER_SCREEN,
                (_, buf) -> {
                    var pos = buf.readBlockPos();
                    Minecraft.getInstance().setScreen(new AbilityDeveloperScreen(pos));
                });
        SCREEN_HANDLERS.put(WirelessNodeBlock.WIRELESS_NODE_SCREEN,
                (_, buf) -> {
                    var containerId = buf.readVarInt();
                    var title = buf.readUtf();
                    var pos = buf.readBlockPos();
                    if (Minecraft.getInstance().player != null) {
                        var inventory = Minecraft.getInstance().player.getInventory();
                        var menu = MenuTypes.NODE.get().create(containerId, inventory);
                        Minecraft.getInstance().player.containerMenu = menu;
                        var screen = WirelessNodeScreen.create(menu, inventory, Component.literal(title), pos);
                        if (screen != null && Minecraft.getInstance().player != null) {
                            Minecraft.getInstance().player.containerMenu = menu;
                        }
                        Minecraft.getInstance().setScreen(screen);
                    }
                });
        SCREEN_HANDLERS.put(OmniCraftingTableBlock.OMNI_CRAFTING_TABLE_SCREEN,
                (_, buf) -> {
                    var containerId = buf.readVarInt();
                    var title = buf.readUtf();
                    var pos = buf.readBlockPos();
                    if (Minecraft.getInstance().player != null) {
                        var inventory = Minecraft.getInstance().player.getInventory();
                        var menu = MenuTypes.OMNI_CRAFTING_TABLE.get().create(containerId, inventory);
                        Minecraft.getInstance().player.containerMenu = menu;
                        Minecraft.getInstance().setScreen(new OmniCraftingTableScreen(menu, inventory, Component.literal(title), pos));
                    }
                });
        SCREEN_HANDLERS.put(SolarGenBlock.SOLAR_GEN_SCREEN,
                (_, buf) -> {
                    var containerId = buf.readVarInt();
                    var title = buf.readUtf();
                    var pos = buf.readBlockPos();
                    if (Minecraft.getInstance().player != null) {
                        var inventory = Minecraft.getInstance().player.getInventory();
                        var menuType = MenuTypes.SOLAR_GEN.get();
                        var windGenMenu = menuType.create(containerId, inventory);
                        var screen = SolarGenScreen.create(windGenMenu, inventory, Component.literal(title), pos);
                        if (screen != null && Minecraft.getInstance().player != null) {
                            Minecraft.getInstance().player.containerMenu = windGenMenu;
                        }
                        Minecraft.getInstance().setScreen(screen);
                    }
                });
    }

    private Screens() {
    }

    public static void register() {
        MisakaNetworkClient.NETWORK_MANAGER.registerPacketListener(Screens.class);
    }

    @SubscribePacket
    public static void handle(OpenScreenPacket packet) {
        var handler = SCREEN_HANDLERS.get(packet.getScreenName());
        if (handler != null) {
            var buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(packet.getDataPayload()));
            handler.accept(packet.getPacketListener(), buffer);
        }
    }
}