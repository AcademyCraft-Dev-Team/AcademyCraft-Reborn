package org.academy.internal.common.ability.aeromanip.network;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Bounded, authoritative deltas. Absence from one batch never means removal. */
@PacketTarget(ThreadType.CLIENT)
public final class FlowSensePacket extends Packet<ClientPacketListener, FlowSensePacket> {
    public static final int MAX_BATCH = 96;
    public static final StreamCodec<ByteBuf, FlowSensePacket> CODEC = StreamCodec.of(FlowSensePacket::write, FlowSensePacket::read);
    public final Identifier dimension;
    public final long session, tick;
    public final int leaseTicks;
    public final float range;
    public final boolean clear;
    public final List<Sample> updates;
    public final List<Integer> removed;

    public record Sample(int id, UUID uuid, int x, int y, int z, float width) {
        public Vec3 position() { return new Vec3(x / 32.0, y / 32.0, z / 32.0); }
        public static Sample of(net.minecraft.world.entity.LivingEntity entity) {
            var position = entity.getBoundingBox().getCenter();
            return new Sample(entity.getId(), entity.getUUID(), quantize(position.x), quantize(position.y),
                    quantize(position.z), Math.clamp(entity.getBbWidth(), .2f, 32));
        }
        private static int quantize(double value) { return (int) Math.round(Math.clamp(value, -30_000_000, 30_000_000) * 32); }
    }
    public FlowSensePacket(Identifier dimension, long session, long tick, int leaseTicks, float range,
                           boolean clear, List<Sample> updates, List<Integer> removed) {
        if (updates.size() > MAX_BATCH || removed.size() > MAX_BATCH || leaseTicks < 1 || leaseTicks > 600
                || !Float.isFinite(range) || range <= 0 || range > 512) throw new IllegalArgumentException("Invalid flow batch");
        this.dimension = dimension; this.session = session; this.tick = tick;
        this.leaseTicks = leaseTicks; this.range = range; this.clear = clear;
        this.updates = List.copyOf(updates); this.removed = List.copyOf(removed);
    }
    private static void write(ByteBuf buf, FlowSensePacket packet) {
        Identifier.STREAM_CODEC.encode(buf, packet.dimension);
        buf.writeLong(packet.session).writeLong(packet.tick).writeBoolean(packet.clear);
        ByteBufCodecs.VAR_INT.encode(buf, packet.leaseTicks); buf.writeFloat(packet.range);
        ByteBufCodecs.VAR_INT.encode(buf, packet.updates.size());
        for (var sample : packet.updates) {
            ByteBufCodecs.VAR_INT.encode(buf, sample.id);
            buf.writeLong(sample.uuid.getMostSignificantBits()).writeLong(sample.uuid.getLeastSignificantBits());
            buf.writeInt(sample.x).writeInt(sample.y).writeInt(sample.z).writeFloat(sample.width);
        }
        ByteBufCodecs.VAR_INT.encode(buf, packet.removed.size());
        for (int id : packet.removed) ByteBufCodecs.VAR_INT.encode(buf, id);
    }
    private static FlowSensePacket read(ByteBuf buf) {
        var dimension = Identifier.STREAM_CODEC.decode(buf);
        long session = buf.readLong(), tick = buf.readLong();
        boolean clear = buf.readBoolean();
        int lease = ByteBufCodecs.VAR_INT.decode(buf);
        float range = buf.readFloat();
        var updates = new ArrayList<Sample>();
        for (int n = count(buf); n > 0; n--) {
            int id = ByteBufCodecs.VAR_INT.decode(buf);
            var uuid = new UUID(buf.readLong(), buf.readLong());
            int x = buf.readInt(), y = buf.readInt(), z = buf.readInt();
            float width = buf.readFloat();
            if (!Float.isFinite(width) || width < .2f || width > 32) throw new DecoderException("Invalid flow marker");
            updates.add(new Sample(id, uuid, x, y, z, width));
        }
        var removed = new ArrayList<Integer>();
        for (int n = count(buf); n > 0; n--) removed.add(ByteBufCodecs.VAR_INT.decode(buf));
        return new FlowSensePacket(dimension, session, tick, lease, range, clear, updates, removed);
    }
    private static int count(ByteBuf buf) {
        int count = ByteBufCodecs.VAR_INT.decode(buf);
        if (count < 0 || count > MAX_BATCH) throw new DecoderException("Oversized flow batch");
        return count;
    }
    public static void initClient() {
        MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
        org.academy.internal.client.ability.aeromanip.FlowSenseClient.init();
    }
    public void sendTo(ServerPlayer player) { MisakaNetworkServer.send(player, this); }
    @Override public PacketType<ClientPacketListener, FlowSensePacket> getPacketType() { return PacketTypes.FLOW_SENSE_SYNC.get(); }
    public static final class Client {
        @SubscribePacket public static void receive(FlowSensePacket packet) {
            org.academy.internal.client.ability.aeromanip.FlowSenseClient.receive(packet);
        }
    }
}
