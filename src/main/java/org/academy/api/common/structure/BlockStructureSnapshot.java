package org.academy.api.common.structure;

import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/** Immutable, origin-relative block and block-entity data for a movable structure. */
public final class BlockStructureSnapshot {
    public static final int MAX_BLOCKS = 4_096;
    public static final BlockStructureSnapshot EMPTY = new BlockStructureSnapshot(List.of(), true);
    public static final StreamCodec<RegistryFriendlyByteBuf, BlockStructureSnapshot> STREAM_CODEC =
            StreamCodec.of(BlockStructureSnapshot::encode, BlockStructureSnapshot::decode);

    private static final Comparator<BlockData> BLOCK_ORDER = Comparator
            .comparingInt((BlockData value) -> value.relativePosition().getY())
            .thenComparingInt(value -> value.relativePosition().getX())
            .thenComparingInt(value -> value.relativePosition().getZ());

    private final List<BlockData> blocks;
    private final int width;
    private final int height;
    private final int depth;

    public BlockStructureSnapshot(List<BlockData> blocks) {
        this(blocks, false);
    }

    private BlockStructureSnapshot(List<BlockData> blocks, boolean allowEmpty) {
        if (blocks == null || blocks.size() > MAX_BLOCKS || !allowEmpty && blocks.isEmpty()) {
            throw new IllegalArgumentException("A structure must contain 1 to " + MAX_BLOCKS + " blocks");
        }
        var positions = new HashSet<BlockPos>();
        var copy = new ArrayList<BlockData>(blocks.size());
        var maximumX = 0;
        var maximumY = 0;
        var maximumZ = 0;
        for (var block : blocks) {
            if (block == null) throw new IllegalArgumentException("Structure blocks cannot be null");
            var position = block.relativePosition();
            if (position.getX() < 0 || position.getY() < 0 || position.getZ() < 0) {
                throw new IllegalArgumentException("Structure positions must be relative to their minimum corner");
            }
            if (!positions.add(position)) {
                throw new IllegalArgumentException("Duplicate structure position " + position);
            }
            copy.add(block.copy());
            maximumX = Math.max(maximumX, position.getX());
            maximumY = Math.max(maximumY, position.getY());
            maximumZ = Math.max(maximumZ, position.getZ());
        }
        copy.sort(BLOCK_ORDER);
        this.blocks = List.copyOf(copy);
        width = copy.isEmpty() ? 1 : maximumX + 1;
        height = copy.isEmpty() ? 1 : maximumY + 1;
        depth = copy.isEmpty() ? 1 : maximumZ + 1;
    }

    public List<BlockData> blocks() {
        return blocks;
    }

    public int blockCount() {
        return blocks.size();
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int depth() {
        return depth;
    }

    public double pivotX() {
        return width * 0.5;
    }

    public double pivotY() {
        return height * 0.5;
    }

    public double pivotZ() {
        return depth * 0.5;
    }

    public void save(ValueOutput output) {
        var list = output.childrenList("blocks");
        for (var block : blocks) {
            var child = list.addChild();
            var position = block.relativePosition();
            child.putInt("x", position.getX());
            child.putInt("y", position.getY());
            child.putInt("z", position.getZ());
            child.store("state", BlockState.CODEC, block.state());
            if (block.blockEntityData != null) {
                child.store("block_entity", CompoundTag.CODEC, block.blockEntityData);
            }
        }
    }

    public static BlockStructureSnapshot load(ValueInput input) {
        var blocks = new ArrayList<BlockData>();
        for (var child : input.childrenListOrEmpty("blocks")) {
            if (blocks.size() >= MAX_BLOCKS) return EMPTY;
            var state = child.read("state", BlockState.CODEC).orElse(Blocks.AIR.defaultBlockState());
            if (state.isAir()) continue;
            var position = new BlockPos(
                    child.getIntOr("x", 0),
                    child.getIntOr("y", 0),
                    child.getIntOr("z", 0)
            );
            var blockEntityData = child.read("block_entity", CompoundTag.CODEC).orElse(null);
            blocks.add(new BlockData(position, state, blockEntityData));
        }
        try {
            return blocks.isEmpty() ? EMPTY : new BlockStructureSnapshot(blocks);
        } catch (IllegalArgumentException ignored) {
            return EMPTY;
        }
    }

    private static void encode(RegistryFriendlyByteBuf buffer, BlockStructureSnapshot snapshot) {
        if (snapshot.blockCount() > MAX_BLOCKS) {
            throw new EncoderException("Block structure exceeds " + MAX_BLOCKS + " blocks");
        }
        buffer.writeVarInt(snapshot.blockCount());
        for (var block : snapshot.blocks) {
            var position = block.relativePosition();
            buffer.writeVarInt(position.getX());
            buffer.writeVarInt(position.getY());
            buffer.writeVarInt(position.getZ());
            buffer.writeVarInt(Block.getId(block.state()));
            buffer.writeBoolean(block.blockEntityData != null);
            if (block.blockEntityData != null) {
                ByteBufCodecs.COMPOUND_TAG.encode(buffer, block.blockEntityData);
            }
        }
    }

    private static BlockStructureSnapshot decode(RegistryFriendlyByteBuf buffer) {
        var count = buffer.readVarInt();
        if (count < 0 || count > MAX_BLOCKS) {
            throw new DecoderException("Invalid block structure size " + count);
        }
        if (count == 0) return EMPTY;
        var blocks = new ArrayList<BlockData>(count);
        for (var index = 0; index < count; index++) {
            var position = new BlockPos(
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt()
            );
            var state = Block.stateById(buffer.readVarInt());
            var blockEntityData = buffer.readBoolean()
                    ? ByteBufCodecs.COMPOUND_TAG.decode(buffer)
                    : null;
            blocks.add(new BlockData(position, state, blockEntityData));
        }
        try {
            return new BlockStructureSnapshot(blocks);
        } catch (IllegalArgumentException exception) {
            throw new DecoderException("Invalid block structure snapshot", exception);
        }
    }

    public static final class BlockData {
        private final BlockPos relativePosition;
        private final BlockState state;
        private final @Nullable CompoundTag blockEntityData;

        public BlockData(
                BlockPos relativePosition,
                BlockState state,
                @Nullable CompoundTag blockEntityData
        ) {
            if (relativePosition == null || state == null) {
                throw new IllegalArgumentException("Block position and state cannot be null");
            }
            this.relativePosition = relativePosition.immutable();
            this.state = state;
            this.blockEntityData = blockEntityData == null ? null : blockEntityData.copy();
        }

        public BlockPos relativePosition() {
            return relativePosition;
        }

        public BlockState state() {
            return state;
        }

        public Optional<CompoundTag> blockEntityData() {
            return Optional.ofNullable(blockEntityData).map(CompoundTag::copy);
        }

        private BlockData copy() {
            return new BlockData(relativePosition, state, blockEntityData);
        }
    }
}
