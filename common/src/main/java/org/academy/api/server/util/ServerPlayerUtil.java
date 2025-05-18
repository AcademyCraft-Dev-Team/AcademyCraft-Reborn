package org.academy.api.server.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.academy.api.common.network.packet.S2CPacket;
import org.academy.api.common.util.FriendlyByteBufUtil;
import org.academy.api.common.vanilla.OpenScreenPacket;

public class ServerPlayerUtil {
    public static void openMenuScreen(ServerPlayer serverPlayer, MenuProvider menuProvider, String screenName, Object... objects) {
        if (serverPlayer.containerMenu != serverPlayer.inventoryMenu) {
            serverPlayer.closeContainer();
        }
        serverPlayer.nextContainerCounter();
        AbstractContainerMenu abstractcontainermenu = menuProvider.createMenu(serverPlayer.containerCounter, serverPlayer.getInventory(), serverPlayer);
        if (abstractcontainermenu == null) {
            if (serverPlayer.isSpectator()) {
                serverPlayer.displayClientMessage(Component.translatable("container.spectatorCantOpen").withStyle(ChatFormatting.RED), true);
            }
        } else {
            Object[] allValues = new Object[3 + objects.length];
            allValues[0] = screenName;
            allValues[1] = abstractcontainermenu.containerId;
            allValues[2] = menuProvider.getDisplayName();
            System.arraycopy(objects, 0, allValues, 3, objects.length);
            serverPlayer.connection.send(new S2CPacket(new OpenScreenPacket(screenName, FriendlyByteBufUtil.autoSerializable(allValues))));
            serverPlayer.initMenu(abstractcontainermenu);
            serverPlayer.containerMenu = abstractcontainermenu;
        }
    }

    private ServerPlayerUtil() {
    }
}