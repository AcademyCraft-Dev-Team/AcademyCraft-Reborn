package org.academy.api.server.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import org.academy.api.common.network.packet.S2CPacket;
import org.academy.api.common.util.FriendlyByteBufUtil;
import org.academy.api.common.vanilla.OpenScreenPacket;

public class ServerPlayerUtil {
    public static void openMenuScreen(ServerPlayer serverPlayer, MenuProvider menuProvider, String screenName, Object... objects) {
        if (serverPlayer.containerMenu != serverPlayer.inventoryMenu) {
            serverPlayer.closeContainer();
        }
        serverPlayer.nextContainerCounter();
        var abstractcontainermenu = menuProvider.createMenu(serverPlayer.containerCounter, serverPlayer.getInventory(), serverPlayer);
        if (abstractcontainermenu == null) {
            if (serverPlayer.isSpectator()) {
                serverPlayer.displayClientMessage(Component.translatable("container.spectatorCantOpen").withStyle(ChatFormatting.RED), true);
            }
        } else {
            var allValuesForPayload = new Object[2 + objects.length];
            allValuesForPayload[0] = abstractcontainermenu.containerId;
            allValuesForPayload[1] = menuProvider.getDisplayName();
            System.arraycopy(objects, 0, allValuesForPayload, 2, objects.length);

            serverPlayer.connection.send(new S2CPacket(new OpenScreenPacket(screenName, FriendlyByteBufUtil.autoSerializable(allValuesForPayload))));
            serverPlayer.initMenu(abstractcontainermenu);
            serverPlayer.containerMenu = abstractcontainermenu;
        }
    }

    private ServerPlayerUtil() {
    }
}