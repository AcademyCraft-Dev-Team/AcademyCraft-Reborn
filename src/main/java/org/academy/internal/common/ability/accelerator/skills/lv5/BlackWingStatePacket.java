package org.academy.internal.common.ability.accelerator.skills.lv5;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.academy.internal.client.render.vfx.WingVfx;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

/** Activation/cancellation and tracking baseline; no periodic animation progress traffic. */
@PacketTarget(ThreadType.CLIENT)
public final class BlackWingStatePacket extends Packet<ClientPacketListener, BlackWingStatePacket> {
    public static final StreamCodec<ByteBuf, BlackWingStatePacket> CODEC = StreamCodec.of((buf, packet) -> {
        ByteBufCodecs.VAR_INT.encode(buf, packet.entityId);
        buf.writeLong(packet.epoch);
        buf.writeLong(packet.sequenceFloor);
        buf.writeBoolean(packet.active);
    }, buf -> new BlackWingStatePacket(ByteBufCodecs.VAR_INT.decode(buf), buf.readLong(), buf.readLong(), buf.readBoolean()));
    private static boolean initialized;
    private final int entityId;
    private final long epoch, sequenceFloor;
    private final boolean active;

    public BlackWingStatePacket(int entityId, long epoch, long sequenceFloor, boolean active) {
        this.entityId = entityId;
        this.epoch = epoch;
        this.sequenceFloor = sequenceFloor;
        this.active = active;
    }

    public static void initClient() {
        if (initialized) return;
        initialized = true;
        MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
    }

    @Override
    public PacketType<ClientPacketListener, BlackWingStatePacket> getPacketType() { return PacketTypes.BLACK_WING_STATE.get(); }

    public static final class Client {
        private Client() { }
        @SubscribePacket
        public static void handle(BlackWingStatePacket packet) {
            WingVfx.syncBlackState(packet.entityId, packet.epoch, packet.sequenceFloor, packet.active);
        }
    }
}
