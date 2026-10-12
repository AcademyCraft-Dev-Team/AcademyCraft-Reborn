package org.academy.internal.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.*;
import org.academy.internal.common.structure.space.NativeStructureDimensions;
import org.academy.internal.common.structure.space.NativeStructureInteraction;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.Objects;

/** A bounded observation only. The server derives the actionable ray from its actual player. */
@PacketTarget(ThreadType.SERVER)
public final class NativeStructureUsePacket extends Packet<ServerGamePacketListenerImpl, NativeStructureUsePacket> {
    public static final StreamCodec<ByteBuf, NativeStructureUsePacket> CODEC = new StreamCodec<>() {
        @Override public NativeStructureUsePacket decode(ByteBuf input) {
            var buffer = new FriendlyByteBuf(input);
            var reference = new StructureRef(buffer.readUUID(), Identifier.parse(buffer.readUtf(256)),
                    Identifier.parse(buffer.readUtf(256)), buffer.readLong());
            var runtimeId = buffer.readUUID();
            long revision = buffer.readLong();
            var pose = new StructurePose(readPoint(buffer), buffer.readDouble(), buffer.readDouble(),
                    buffer.readDouble(), buffer.readDouble());
            var position = buffer.readBlockPos();
            int face = buffer.readUnsignedByte();
            if (face >= Direction.values().length || !NativeStructureDimensions.contains(position)
                    || position.getY() < -64 || position.getY() > 319) throw new IllegalArgumentException("Invalid structure cell");
            return new NativeStructureUsePacket(new StructureHit(new SpaceBlockRef(reference, position),
                    runtimeId, revision, pose, Direction.values()[face], readPoint(buffer), readPoint(buffer), buffer.readBoolean()));
        }

        @Override public void encode(ByteBuf output, NativeStructureUsePacket packet) {
            var buffer = new FriendlyByteBuf(output);
            var hit = packet.hit;
            var reference = hit.block().structure();
            buffer.writeUUID(reference.id());
            buffer.writeUtf(reference.dimension().toString(), 256);
            buffer.writeUtf(reference.backend().toString(), 256);
            buffer.writeLong(reference.generation());
            buffer.writeUUID(hit.runtimeId());
            buffer.writeLong(hit.geometryRevision());
            writePoint(buffer, hit.pose().worldOrigin());
            buffer.writeDouble(hit.pose().rotationX());
            buffer.writeDouble(hit.pose().rotationY());
            buffer.writeDouble(hit.pose().rotationZ());
            buffer.writeDouble(hit.pose().rotationW());
            buffer.writeBlockPos(hit.block().localPosition());
            buffer.writeByte(hit.localFace().ordinal());
            writePoint(buffer, hit.localPoint());
            writePoint(buffer, hit.worldPoint());
            buffer.writeBoolean(hit.inside());
        }

        private Vec3 readPoint(FriendlyByteBuf buffer) {
            var point = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
            if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)
                    || Math.abs(point.x) > 30_000_000 || Math.abs(point.y) > 30_000_000
                    || Math.abs(point.z) > 30_000_000) throw new IllegalArgumentException("Invalid structure point");
            return point;
        }

        private void writePoint(FriendlyByteBuf buffer, Vec3 point) {
            buffer.writeDouble(point.x); buffer.writeDouble(point.y); buffer.writeDouble(point.z);
        }
    };
    private static boolean initialized;
    private final StructureHit hit;

    public NativeStructureUsePacket(StructureHit hit) { this.hit = Objects.requireNonNull(hit); }
    public StructureHit hit() { return hit; }

    public static synchronized void initServer() {
        if (initialized) return;
        initialized = true;
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    @Override public PacketType<ServerGamePacketListenerImpl, NativeStructureUsePacket> getPacketType() {
        return PacketTypes.NATIVE_STRUCTURE_USE.get();
    }

    public static final class Server {
        @SubscribePacket public static void handle(NativeStructureUsePacket packet) {
            NativeStructureInteraction.useLever(packet.getPacketListener().getPlayer(), packet.hit);
        }
    }
}
