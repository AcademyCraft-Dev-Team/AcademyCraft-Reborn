package org.academy.internal.client.gui.screen;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import org.academy.AcademyCraftClient;
import org.academy.api.common.network.annotation.SubscribePacket;
import org.academy.api.common.vanilla.OpenScreenPacket;
import org.academy.internal.common.world.inventory.MenuTypes;
import org.academy.internal.common.world.level.block.AbilityDeveloperBlock;
import org.academy.internal.common.world.level.block.OmniCraftingTableBlock;
import org.academy.internal.common.world.level.block.WindGenBaseBlock;
import org.academy.internal.common.world.level.block.WirelessNodeBlock;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

@SuppressWarnings("unused")
public final class Screens {
    public static final Map<String, BiConsumer<ClientGamePacketListener, FriendlyByteBuf>> SCREEN_HANDLERS = new HashMap<>();

    static {
        SCREEN_HANDLERS.put(WindGenBaseBlock.WIND_GEN_SCREEN,
                (listener, buf) -> {
                    var containerId = buf.readVarInt();
                    var title = buf.readUtf();
                    var pos = buf.readBlockPos();
                    if (Minecraft.getInstance().player != null) {
                        var inventory = Minecraft.getInstance().player.getInventory();
                        var menuType = MenuTypes.WIND_GEN_MENU.get();
                        var windGenMenu = menuType.create(containerId, inventory);
                        var windGenScreen = WindGenScreen.create(windGenMenu, inventory, Component.literal(title), pos);
                        if (windGenScreen != null && Minecraft.getInstance().player != null) {
                            Minecraft.getInstance().player.containerMenu = windGenMenu;
                        }
                        Minecraft.getInstance().setScreen(windGenScreen);
                    }
                });
        SCREEN_HANDLERS.put(AbilityDeveloperBlock.ABILITY_DEVELOPER_SCREEN,
                (listener, buf) -> {
                    var pos = buf.readBlockPos();
                    Minecraft.getInstance().setScreen(new AbilityDeveloperScreen(pos));
                });
        SCREEN_HANDLERS.put(WirelessNodeBlock.WIRELESS_NODE_SCREEN,
                (ClientGamePacketListener, buf) -> {
                    var containerId = buf.readVarInt();
                    var title = buf.readUtf();
                    var pos = buf.readBlockPos();
                    if (Minecraft.getInstance().player != null) {
                        var inventory = Minecraft.getInstance().player.getInventory();
                        var menu = MenuTypes.NODE_MENU.get().create(containerId, inventory);
                        Minecraft.getInstance().player.containerMenu = menu;
                        Minecraft.getInstance().setScreen(new WirelessNodeScreen(menu, inventory, Component.literal(title), pos));
                    }
                });
        SCREEN_HANDLERS.put(OmniCraftingTableBlock.OMNI_CRAFTING_TABLE_SCREEN,
                (ClientGamePacketListener, buf) -> {
                    var containerId = buf.readVarInt();
                    var title = buf.readUtf();
                    var pos = buf.readBlockPos();
                    if (Minecraft.getInstance().player != null) {
                        var inventory = Minecraft.getInstance().player.getInventory();
                        var menu = MenuTypes.OMNI_CRAFTING_TABLE_MENU.get().create(containerId, inventory);
                        Minecraft.getInstance().player.containerMenu = menu;
                        Minecraft.getInstance().setScreen(new OmniCraftingTableScreen(menu, inventory, Component.literal(title), pos));
                    }
                });
    }


    private Screens() {
    }

    public static void register() {
        AcademyCraftClient.CLIENT_NETWORK_MANAGER.registerPacketListener(Screens.class);
    }

    @SubscribePacket
    public static void handle(OpenScreenPacket packet) {
        var handler = SCREEN_HANDLERS.get(packet.getScreenName());
        if (handler != null && packet.getDataPayload() != null) {
            var buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(packet.getDataPayload()));
            handler.accept(packet.getPacketListener(), buffer);
        }
    }
}