package org.academy.internal.common.structure.transaction;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.util.ProblemReporter;
import org.academy.api.common.structure.StructureBlockData;
import org.academy.internal.common.structure.BlockStructureTransferState;
import org.jspecify.annotations.Nullable;

import java.util.*;

/** Complete transfer images; coordinates are canonicalized, never included in inventory identity. */
public final class StructureCellPayload {
    private StructureCellPayload() {}

    public static boolean container(BlockState state) {
        return state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST) || state.is(Blocks.BARREL);
    }

    public static @Nullable String rejection(ServerLevel level, BlockPos pos, BlockState state, @Nullable BlockEntity entity) {
        if (state.hasBlockEntity() || entity != null) {
            if (!container(state)) return "block_entity_not_supported";
            if (entity == null || !entity.getType().isValid(state)) return "block_entity_missing_or_invalid";
            if (entity instanceof net.minecraft.world.level.block.entity.ChestBlockEntity
                    && net.minecraft.world.level.block.entity.ChestBlockEntity.getOpenCount(level, pos) > 0
                    || state.is(Blocks.BARREL) && state.getValue(BlockStateProperties.OPEN)) return "container_in_use";
        }
        return classifyAdmission(state.hasBlockEntity() || entity != null, container(state),
                state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED),
                !state.getFluidState().isEmpty(), level.getBlockTicks().hasScheduledTick(pos, state.getBlock()), state.getDestroySpeed(level, pos));
    }

    static @Nullable String classifyAdmission(boolean entity, boolean supportedContainer, boolean waterlogged,
                                               boolean fluid, boolean scheduledTick, float hardness) {
        if (entity && !supportedContainer) return "block_entity_not_supported";
        if (fluid && !waterlogged) return "fluid_not_supported";
        // Chest/barrel scheduled ticks only recheck openers; open containers are rejected above.
        if (scheduledTick && !supportedContainer) return "scheduled_tick_pending";
        return hardness < 0 ? "unbreakable" : null;
    }

    /** A double chest is one inventory ownership group; never silently expand a skill selection. */
    public static boolean completePairs(ServerLevel level, Collection<BlockPos> positions) {
        var selected = new HashSet<>(positions);
        for (var pos : positions) {
            if (!level.isLoaded(pos)) return false;
            var state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) continue;
            var other = pos.relative(ChestBlock.getConnectedDirection(state));
            if (!selected.contains(other) || !level.isLoaded(other)) return false;
            var partner = level.getBlockState(other);
            if (!partner.is(state.getBlock()) || partner.getValue(ChestBlock.TYPE) == ChestType.SINGLE
                    || partner.getValue(ChestBlock.TYPE) == state.getValue(ChestBlock.TYPE)
                    || partner.getValue(ChestBlock.FACING) != state.getValue(ChestBlock.FACING)
                    || !other.relative(ChestBlock.getConnectedDirection(partner)).equals(pos)) return false;
        }
        return true;
    }

    public static StructureBlockData normalize(StructureBlockData data) {
        var tag = data.blockEntityData();
        if (tag != null) { tag.putInt("x", 0); tag.putInt("y", 0); tag.putInt("z", 0); }
        return new StructureBlockData(data.state(), tag);
    }

    public static StructureBlockData read(ServerLevel level, BlockPos pos) {
        var entity = level.getBlockEntity(pos);
        return normalize(new StructureBlockData(level.getBlockState(pos), entity == null ? null : entity.saveWithFullMetadata(level.registryAccess())));
    }

    public static CompoundTag encode(ServerLevel level, StructureBlockData data) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        output.store("state", BlockState.CODEC, data.state());
        var result = output.buildResult();
        var entity = normalize(data).blockEntityData();
        if (entity != null) result.put("block_entity", entity);
        return result;
    }

    /** State-only v1/v2 journals remain readable. A BE state without its image is never accepted. */
    public static StructureBlockData decode(ServerLevel level, CompoundTag tag) {
        var input = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag);
        var state = input.read("state", BlockState.CODEC).orElseThrow();
        var entity = tag.getCompound("block_entity").orElse(null);
        if (state.hasBlockEntity() != (entity != null)) throw new IllegalStateException("Incomplete block entity transfer image");
        return normalize(new StructureBlockData(state, entity));
    }

    public static void write(ServerLevel level, BlockPos pos, StructureBlockData data, int flags) {
        PayloadIsolation.write(level, pos, () -> BlockStructureTransferState.write(level, pos, data.state(), data.blockEntityData(), flags));
        if (!read(level, pos).equals(normalize(data))) throw new IllegalStateException("Payload write mismatch at " + pos);
    }

    public static String digest(StructureBlockData data) {
        return digestEntity(data.blockEntityData());
    }

    static String digestEntity(@Nullable CompoundTag entity) {
        var tag = entity == null ? null : entity.copy();
        if (tag == null) return "";
        tag.putInt("x", 0); tag.putInt("y", 0); tag.putInt("z", 0);
        try {
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(canonical(tag).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static String canonical(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            var out = new StringBuilder("{");
            for (var key : new TreeSet<>(compound.keySet())) out.append(key.length()).append(':').append(key).append(canonical(compound.get(key)));
            return out.append('}').toString();
        }
        if (tag instanceof ListTag list) {
            var out = new StringBuilder("[");
            for (var item : list) out.append(canonical(item)).append(';');
            return out.append(']').toString();
        }
        return tag.getId() + ":" + tag;
    }
}
