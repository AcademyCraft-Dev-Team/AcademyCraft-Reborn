package org.academy.internal.common.structure.space;

import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Public lid targets only. No opener identities, item stacks, block entities, or NBT are sent. */
public record NativeStructureLidTargets(Set<BlockPos> openPositions) {
    public static final int MAX_OPEN_CHESTS = NativeStructureView.MAX_CELLS;
    private static final Comparator<BlockPos> POSITION_ORDER = Comparator.<BlockPos>comparingInt(BlockPos::getY)
            .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ);
    public static final NativeStructureLidTargets EMPTY = new NativeStructureLidTargets(Set.of());
    public static final StreamCodec<RegistryFriendlyByteBuf, NativeStructureLidTargets> STREAM_CODEC =
            StreamCodec.of(NativeStructureLidTargets::encode, NativeStructureLidTargets::decode);

    public NativeStructureLidTargets {
        Objects.requireNonNull(openPositions, "openPositions");
        if (openPositions.size() > MAX_OPEN_CHESTS) {
            throw new IllegalArgumentException("Too many open native structure chests");
        }
        var copy = new TreeSet<>(POSITION_ORDER);
        for (var position : openPositions) {
            requirePosition(position);
            copy.add(position.immutable());
        }
        openPositions = Collections.unmodifiableSet(copy);
    }

    public boolean isOpen(BlockPos position) {
        return openPositions.contains(position);
    }

    private static void requirePosition(BlockPos position) {
        Objects.requireNonNull(position, "position");
        if (position.getX() < -32 || position.getX() >= 32
                || position.getZ() < -32 || position.getZ() >= 32
                || position.getY() < NativeStructureView.MIN_Y
                || position.getY() >= NativeStructureView.MIN_Y + NativeStructureView.HEIGHT) {
            throw new IllegalArgumentException("Chest lid target is outside the native storage domain");
        }
    }

    private static void encode(RegistryFriendlyByteBuf buffer, NativeStructureLidTargets targets) {
        buffer.writeVarInt(targets.openPositions.size());
        for (var position : targets.openPositions) {
            buffer.writeByte(position.getX());
            buffer.writeShort(position.getY());
            buffer.writeByte(position.getZ());
        }
    }

    private static NativeStructureLidTargets decode(RegistryFriendlyByteBuf buffer) {
        var size = buffer.readVarInt();
        if (size < 0 || size > MAX_OPEN_CHESTS) {
            throw new DecoderException("Native chest lid target count is out of bounds: " + size);
        }
        if (size == 0) return EMPTY;
        var positions = new HashSet<BlockPos>(size);
        try {
            for (var index = 0; index < size; index++) {
                var position = new BlockPos(buffer.readByte(), buffer.readShort(), buffer.readByte());
                requirePosition(position);
                if (!positions.add(position)) throw new DecoderException("Duplicate native chest lid target");
            }
            return new NativeStructureLidTargets(positions);
        } catch (IllegalArgumentException failure) {
            throw new DecoderException("Invalid native chest lid target", failure);
        }
    }
}
