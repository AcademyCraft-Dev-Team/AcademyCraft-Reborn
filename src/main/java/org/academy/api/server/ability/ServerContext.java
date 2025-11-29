package org.academy.api.server.ability;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.Context;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.packet.Packet;

public abstract class ServerContext implements Context {
    protected final ServerPlayer player;

    protected ServerContext(ServerPlayer player) {
        this.player = player;
    }

    public final <P extends Packet<ClientPacketListener, P>> void sendPacket(P packet) {
        MisakaNetworkServer.sendPacket(player, packet);
    }

    @Override
    public void unregister() {
        AbilitySystemServer.unregisterContext(this);
    }
}