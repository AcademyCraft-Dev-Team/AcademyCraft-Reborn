package org.academy.internal.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.internal.common.sublevel.SubLevelId;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.Objects;
import java.util.UUID;

/** Pose-only delta for an already synchronized SubLevel view; avoids resending cells every tick. */
@PacketTarget(ThreadType.CLIENT)
public final class SubLevelPosePacket extends Packet<ClientPacketListener, SubLevelPosePacket> {
    public static final StreamCodec<ByteBuf, SubLevelPosePacket> CODEC = new StreamCodec<>() {
        @Override public SubLevelPosePacket decode(ByteBuf input) {
            var dimension = Identifier.STREAM_CODEC.decode(input);
            var id = new SubLevelId(new UUID(input.readLong(), input.readLong()));
            var pose = new StructurePose(new Vec3(input.readDouble(), input.readDouble(), input.readDouble()),
                    input.readDouble(), input.readDouble(), input.readDouble(), input.readDouble());
            if (input.isReadable()) throw new IllegalArgumentException("Unexpected SubLevel pose payload");
            return new SubLevelPosePacket(dimension, id, pose);
        }

        @Override public void encode(ByteBuf output, SubLevelPosePacket packet) {
            Identifier.STREAM_CODEC.encode(output, packet.dimension);
            output.writeLong(packet.id.value().getMostSignificantBits());
            output.writeLong(packet.id.value().getLeastSignificantBits());
            output.writeDouble(packet.pose.worldOrigin().x);
            output.writeDouble(packet.pose.worldOrigin().y);
            output.writeDouble(packet.pose.worldOrigin().z);
            output.writeDouble(packet.pose.rotationX());
            output.writeDouble(packet.pose.rotationY());
            output.writeDouble(packet.pose.rotationZ());
            output.writeDouble(packet.pose.rotationW());
        }
    };

    private static boolean initialized;
    private final Identifier dimension;
    private final SubLevelId id;
    private final StructurePose pose;

    public SubLevelPosePacket(Identifier dimension, SubLevelId id, StructurePose pose) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.id = Objects.requireNonNull(id, "id");
        this.pose = Objects.requireNonNull(pose, "pose");
    }

    public static void send(ServerPlayer recipient, SubLevelId id, StructurePose pose) {
        MisakaNetworkServer.send(recipient,
                new SubLevelPosePacket(recipient.level().dimension().identifier(), id, pose));
    }

    public Identifier dimension() { return dimension; }
    public SubLevelId id() { return id; }
    public StructurePose pose() { return pose; }

    public static synchronized void initClient() {
        if (initialized) return;
        initialized = true;
        MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
    }

    @Override public PacketType<ClientPacketListener, SubLevelPosePacket> getPacketType() {
        return PacketTypes.SUBLEVEL_POSE.get();
    }

    public static final class Client {
        @SubscribePacket public static void handle(SubLevelPosePacket packet) {
            net.minecraft.client.Minecraft.getInstance().execute(
                    () -> org.academy.internal.client.sublevel.SubLevelClientState.acceptPose(packet));
        }
    }
}
