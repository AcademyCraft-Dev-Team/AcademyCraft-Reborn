package org.academy.internal.common.ability.aeromanip;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Server-authoritative moving-entity observations used by the flow-sense overlay. */
@PacketTarget(ThreadType.CLIENT)
public final class FlowSensePacket extends Packet<ClientPacketListener, FlowSensePacket> {
    public static final StreamCodec<ByteBuf, FlowSensePacket> CODEC = StreamCodec.of(
            FlowSensePacket::write,
            FlowSensePacket::read
    );

    private final int entityId;
    private final double dx;
    private final double dy;
    private final double dz;
    private final double speed;

    public FlowSensePacket(int entityId, Vec3 direction, double speed) {
        this(entityId, direction.x, direction.y, direction.z, speed);
    }

    private FlowSensePacket(int entityId, double dx, double dy, double dz, double speed) {
        this.entityId = entityId;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.speed = speed;
    }

    public static void initClient() {
        MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
    }

    public void sendTo(ServerPlayer player) {
        MisakaNetworkServer.send(player, this);
    }

    @Override
    public PacketType<ClientPacketListener, FlowSensePacket> getPacketType() {
        return PacketTypes.FLOW_SENSE_SYNC.get();
    }

    private static void write(ByteBuf buf, FlowSensePacket packet) {
        ByteBufCodecs.VAR_INT.encode(buf, packet.entityId);
        buf.writeDouble(packet.dx);
        buf.writeDouble(packet.dy);
        buf.writeDouble(packet.dz);
        buf.writeDouble(packet.speed);
    }

    private static FlowSensePacket read(ByteBuf buf) {
        return new FlowSensePacket(
                ByteBufCodecs.VAR_INT.decode(buf),
                buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble()
        );
    }

    public static final class Client {
        private static final Map<Integer, Observation> OBSERVATIONS = new ConcurrentHashMap<>();

        private Client() { }

        @SubscribePacket
        public static void receive(FlowSensePacket packet) {
            OBSERVATIONS.put(packet.entityId, new Observation(
                    new Vec3(packet.dx, packet.dy, packet.dz), packet.speed,
                    System.currentTimeMillis()
            ));
        }

        public static Map<Integer, Observation> snapshot() {
            var cutoff = System.currentTimeMillis() - 500L;
            OBSERVATIONS.entrySet().removeIf(entry -> entry.getValue().receivedAtMillis() < cutoff);
            return Map.copyOf(OBSERVATIONS);
        }
    }

    public record Observation(Vec3 direction, double speed, long receivedAtMillis) { }
}
