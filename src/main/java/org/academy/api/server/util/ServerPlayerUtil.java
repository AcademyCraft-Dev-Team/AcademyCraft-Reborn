package org.academy.api.server.util;

import io.netty.buffer.Unpooled;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import org.misaka.api.common.network.packet.S2CPacket;
import org.academy.api.common.vanilla.OpenScreenPacket;

import java.util.function.Consumer;

public final class ServerPlayerUtil {
    public static void openMenuScreen(ServerPlayer serverPlayer, MenuProvider menuProvider, String screenName, Consumer<FriendlyByteBuf> payloadWriter) {
        if (serverPlayer.containerMenu != serverPlayer.inventoryMenu) {
            serverPlayer.closeContainer();
        }
        serverPlayer.nextContainerCounter();
        var abstractContainerMenu = menuProvider.createMenu(serverPlayer.containerCounter, serverPlayer.getInventory(), serverPlayer);
        if (abstractContainerMenu == null) {
            if (serverPlayer.isSpectator()) {
                serverPlayer.displayClientMessage(Component.translatable("container.spectatorCantOpen").withStyle(ChatFormatting.RED), true);
            }
        } else {
            var payload = new FriendlyByteBuf(Unpooled.buffer());
            payload.writeVarInt(abstractContainerMenu.containerId);
            payload.writeUtf(menuProvider.getDisplayName().getString());
            payloadWriter.accept(payload);

            var readableBytes = payload.readableBytes();
            var payloadBytes = new byte[readableBytes];
            payload.readBytes(payloadBytes);

            serverPlayer.connection.send(new S2CPacket(new OpenScreenPacket(screenName, payloadBytes)));
            serverPlayer.initMenu(abstractContainerMenu);
            serverPlayer.containerMenu = abstractContainerMenu;
        }
    }

    private ServerPlayerUtil() {
    }
}