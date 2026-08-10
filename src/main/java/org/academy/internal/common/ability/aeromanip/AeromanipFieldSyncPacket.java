package org.academy.internal.common.ability.aeromanip;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative lifecycle data for client-side field visuals.
 */
@PacketTarget(ThreadType.CLIENT)
public final class AeromanipFieldSyncPacket extends Packet<ClientPacketListener, AeromanipFieldSyncPacket> {
    public static final StreamCodec<ByteBuf, AeromanipFieldSyncPacket> CODEC = StreamCodec.of(
            AeromanipFieldSyncPacket::write,
            AeromanipFieldSyncPacket::read
    );

    private final UUID fieldId;
    private final UUID ownerId;
    private final String dimension;
    private final int type;
    private final int shape;
    private final double x;
    private final double y;
    private final double z;
    private final double dx;
    private final double dy;
    private final double dz;
    private final double radius;
    private final double length;
    private final float strength;
    private final int durationTicks;
    private final boolean active;

    public AeromanipFieldSyncPacket(AirflowField field, boolean active) {
        this(field.id(), field.ownerId(), field.dimension().identifier().toString(), field.type().ordinal(), field.shape().ordinal(),
                field.center().x, field.center().y, field.center().z,
                field.direction().x, field.direction().y, field.direction().z,
                field.radius(), field.length(), field.strength(), field.durationTicks(), active);
    }

    private AeromanipFieldSyncPacket(
            UUID fieldId, UUID ownerId, String dimension, int type, int shape,
            double x, double y, double z, double dx, double dy, double dz,
            double radius, double length, float strength, int durationTicks, boolean active
    ) {
        this.fieldId = fieldId;
        this.ownerId = ownerId;
        this.dimension = dimension;
        this.type = type;
        this.shape = shape;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.radius = radius;
        this.length = length;
        this.strength = strength;
        this.durationTicks = durationTicks;
        this.active = active;
    }

    public static void initClient() {
        MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
    }

    public static void sendToTracking(ServerPlayer owner, AirflowField field, boolean active) {
        var server = owner.level().getServer();
        if (server == null) return;
        var packet = new AeromanipFieldSyncPacket(field, active);
        var trackingRadius = Math.max(32.0, field.radius() + field.length() + 8.0);
        var center = field.center();
        for (var viewer : server.getPlayerList().getPlayers()) {
            if (viewer != owner && !viewer.level().dimension().equals(field.dimension())) continue;
            if (viewer == owner || viewer.distanceToSqr(center) <= trackingRadius * trackingRadius)
                packet.sendTo(viewer);
        }
    }

    private static void write(ByteBuf buf, AeromanipFieldSyncPacket packet) {
        buf.writeLong(packet.fieldId.getMostSignificantBits());
        buf.writeLong(packet.fieldId.getLeastSignificantBits());
        buf.writeLong(packet.ownerId.getMostSignificantBits());
        buf.writeLong(packet.ownerId.getLeastSignificantBits());
        ByteBufCodecs.STRING_UTF8.encode(buf, packet.dimension);
        ByteBufCodecs.VAR_INT.encode(buf, packet.type);
        ByteBufCodecs.VAR_INT.encode(buf, packet.shape);
        buf.writeDouble(packet.x);
        buf.writeDouble(packet.y);
        buf.writeDouble(packet.z);
        buf.writeDouble(packet.dx);
        buf.writeDouble(packet.dy);
        buf.writeDouble(packet.dz);
        buf.writeDouble(packet.radius);
        buf.writeDouble(packet.length);
        buf.writeFloat(packet.strength);
        ByteBufCodecs.VAR_INT.encode(buf, packet.durationTicks);
        ByteBufCodecs.BOOL.encode(buf, packet.active);
    }

    private static AeromanipFieldSyncPacket read(ByteBuf buf) {
        var fieldId = new UUID(buf.readLong(), buf.readLong());
        var ownerId = new UUID(buf.readLong(), buf.readLong());
        return new AeromanipFieldSyncPacket(
                fieldId, ownerId, ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readFloat(),
                ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.BOOL.decode(buf)
        );
    }

    public void sendTo(ServerPlayer player) {
        MisakaNetworkServer.send(player, this);
    }

    public AirflowField toField() {
        var safeType = Math.max(0, Math.min(AirflowField.Type.values().length - 1, type));
        var safeShape = Math.max(0, Math.min(AirflowField.Shape.values().length - 1, shape));
        return new AirflowField(fieldId, ownerId,
                ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimension)),
                AirflowField.Type.values()[safeType], AirflowField.Shape.values()[safeShape],
                new Vec3(x, y, z), new Vec3(dx, dy, dz),
                radius, length, strength, durationTicks);
    }

    public boolean active() {
        return active;
    }

    @Override
    public PacketType<ClientPacketListener, AeromanipFieldSyncPacket> getPacketType() {
        return PacketTypes.AEROMANIP_FIELD_SYNC.get();
    }

    public static final class Client {
        private static final Map<UUID, AirflowField> FIELDS = new ConcurrentHashMap<>();

        private Client() {
        }

        @SubscribePacket
        public static void receive(AeromanipFieldSyncPacket packet) {
            if (packet.active()) FIELDS.put(packet.fieldId, packet.toField());
            else FIELDS.remove(packet.fieldId);
        }

        public static Map<UUID, AirflowField> snapshot() {
            return Map.copyOf(FIELDS);
        }
    }
}
