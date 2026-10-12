package org.academy.internal.common.structure.space;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;

import java.util.Objects;
import java.util.UUID;

/** Small view header, synchronized independently so pose changes never resend cell contents. */
public record NativeStructureViewState(
        StructureRef reference, UUID runtimeId, long revision, StructurePose pose
) {
    private static final UUID NIL_UUID = new UUID(0, 0);
    private static final Identifier UNASSIGNED = Identifier.fromNamespaceAndPath("academy", "unassigned");
    public static final NativeStructureViewState EMPTY = new NativeStructureViewState(
            new StructureRef(NIL_UUID, UNASSIGNED, UNASSIGNED, 0), NIL_UUID, 0,
            StructurePose.identity(Vec3.ZERO));
    public static final StreamCodec<RegistryFriendlyByteBuf, NativeStructureViewState> STREAM_CODEC =
            StreamCodec.of(NativeStructureViewState::encode, NativeStructureViewState::decode);

    public NativeStructureViewState {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(runtimeId, "runtimeId");
        Objects.requireNonNull(pose, "pose");
        if (revision < 0) throw new IllegalArgumentException("revision cannot be negative");
    }

    private static void encode(RegistryFriendlyByteBuf buffer, NativeStructureViewState state) {
        buffer.writeUUID(state.reference.id());
        buffer.writeIdentifier(state.reference.dimension());
        buffer.writeIdentifier(state.reference.backend());
        buffer.writeVarLong(state.reference.generation());
        buffer.writeUUID(state.runtimeId);
        buffer.writeVarLong(state.revision);
        var origin = state.pose.worldOrigin();
        buffer.writeDouble(origin.x);
        buffer.writeDouble(origin.y);
        buffer.writeDouble(origin.z);
        buffer.writeDouble(state.pose.rotationX());
        buffer.writeDouble(state.pose.rotationY());
        buffer.writeDouble(state.pose.rotationZ());
        buffer.writeDouble(state.pose.rotationW());
    }

    private static NativeStructureViewState decode(RegistryFriendlyByteBuf buffer) {
        try {
            var reference = new StructureRef(buffer.readUUID(), buffer.readIdentifier(),
                    buffer.readIdentifier(), buffer.readVarLong());
            var runtimeId = buffer.readUUID();
            var revision = buffer.readVarLong();
            var origin = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
            var pose = new StructurePose(origin, buffer.readDouble(), buffer.readDouble(),
                    buffer.readDouble(), buffer.readDouble());
            return new NativeStructureViewState(reference, runtimeId, revision, pose);
        } catch (IllegalArgumentException failure) {
            throw new DecoderException("Invalid native structure view header", failure);
        }
    }
}
