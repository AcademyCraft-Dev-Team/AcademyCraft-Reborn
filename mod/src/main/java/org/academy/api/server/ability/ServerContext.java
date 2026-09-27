package org.academy.api.server.ability;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.Context;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.packet.Packet;

public abstract class ServerContext implements Context {
    protected final ServerPlayer player;

    protected ServerContext(ServerPlayer player) {
        this.player = player;
    }

    public final <P extends Packet<ClientPacketListener, P>> void send(P packet) {
        MisakaNetworkServer.send(player, packet);
    }

    public final ServerLevel level() {
        return player.level();
    }

    /**
     * The owning player; exposed for the server-side ability registry.
     */
    public final ServerPlayer player() {
        return player;
    }

    /**
     * Event-bus listener object; exposed for the server-side ability registry.
     */
    public final Object busListener() {
        return eventListener();
    }

    /**
     * Invokes the one-time removal hook; exposed for the server-side ability registry.
     */
    public final void notifyUnregistered() {
        onUnregistered();
    }

    @Override
    public void unregister() {
        AbilityServerAccess.of(player).unregisterContext(this);
    }

    /**
     * Called exactly once when the ability system removes this registered context.
     */
    protected void onUnregistered() {
    }

    /**
     * Object registered on the NeoForge event bus for this context. Contexts whose reusable
     * behavior lives in a superclass can return a dedicated listener object, because the event
     * bus intentionally rejects inherited {@code @SubscribeEvent} methods.
     */
    protected Object eventListener() {
        return this;
    }
}
