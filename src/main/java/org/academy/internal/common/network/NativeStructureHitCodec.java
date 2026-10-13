package org.academy.internal.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.SpaceBlockRef;
import org.academy.api.common.structure.StructureHit;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.common.structure.space.NativeStructureDimensions;

/** Bounded observation value shared by requests, without action, hand, sequence or timing. */
public final class NativeStructureHitCodec {
    public static final StreamCodec<ByteBuf, StructureHit> CODEC = new StreamCodec<>() {
        @Override public StructureHit decode(ByteBuf input) {
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
                    || position.getY() < -64 || position.getY() > 319) {
                throw new IllegalArgumentException("Invalid structure cell");
            }
            return new StructureHit(new SpaceBlockRef(reference, position), runtimeId, revision, pose,
                    Direction.values()[face], readPoint(buffer), readPoint(buffer), buffer.readBoolean());
        }

        @Override public void encode(ByteBuf output, StructureHit hit) {
            var buffer = new FriendlyByteBuf(output);
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
    };

    private NativeStructureHitCodec() {}

    private static Vec3 readPoint(FriendlyByteBuf buffer) {
        var point = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)
                || Math.abs(point.x) > 30_000_000 || Math.abs(point.y) > 30_000_000
                || Math.abs(point.z) > 30_000_000) throw new IllegalArgumentException("Invalid structure point");
        return point;
    }

    private static void writePoint(FriendlyByteBuf buffer, Vec3 point) {
        buffer.writeDouble(point.x); buffer.writeDouble(point.y); buffer.writeDouble(point.z);
    }
}
