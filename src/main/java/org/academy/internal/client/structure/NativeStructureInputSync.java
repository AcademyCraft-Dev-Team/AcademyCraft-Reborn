package org.academy.internal.client.structure;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;

/** Publish this input frame before the custom request enters the same server packet queue. */
final class NativeStructureInputSync {
    private NativeStructureInputSync() {}

    static boolean send(Minecraft minecraft) {
        var player = minecraft.player;
        if (player == null || player.isPassenger() || minecraft.getCameraEntity() != player) return false;
        player.connection.send(new ServerboundSetCarriedItemPacket(player.getInventory().getSelectedSlot()));
        player.connection.send(new ServerboundMovePlayerPacket.Rot(player.getYRot(), player.getXRot(),
                player.onGround(), player.horizontalCollision));
        return true;
    }
}
