package org.academy.internal.common.sublevel;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.academy.AcademyCraft;
import org.academy.internal.common.structure.transaction.StructureTransactionJournal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Durable write-ahead journal for source removal during SubLevel capture. */
public final class SubLevelCaptureJournal {
    public static final Identifier BACKEND = Identifier.fromNamespaceAndPath("academy", "sublevel_capture");

    private SubLevelCaptureJournal() {
    }

    public record Source(BlockPos position, BlockState state, CompoundTag tag) {
    }

    public static StructureTransactionJournal.Record begin(StructureTransactionJournal journal,
                                                            ServerLevel host, BlockPos origin,
                                                            SubLevelId id, List<Source> sources)
            throws IOException {
        int[] xs = new int[sources.size()];
        int[] ys = new int[sources.size()];
        int[] zs = new int[sources.size()];
        int[] states = new int[sources.size()];
        int[] beIndices = new int[sources.size()];
        var blockEntities = new ListTag();
        for (int index = 0; index < sources.size(); index++) {
            var source = sources.get(index);
            xs[index] = source.position().getX();
            ys[index] = source.position().getY();
            zs[index] = source.position().getZ();
            states[index] = Block.getId(source.state());
            if (source.tag() == null) {
                beIndices[index] = -1;
            } else {
                beIndices[index] = blockEntities.size();
                blockEntities.add(source.tag().copy());
            }
        }
        var payload = new CompoundTag();
        payload.putString("kind", "capture_sources");
        payload.put("x", new net.minecraft.nbt.IntArrayTag(xs));
        payload.put("y", new net.minecraft.nbt.IntArrayTag(ys));
        payload.put("z", new net.minecraft.nbt.IntArrayTag(zs));
        payload.put("states", new net.minecraft.nbt.IntArrayTag(states));
        payload.put("be_index", new net.minecraft.nbt.IntArrayTag(beIndices));
        payload.put("block_entities", blockEntities);
        var record = new StructureTransactionJournal.Record(UUID.randomUUID(), BACKEND,
                host.dimension().identifier(), origin, StructureTransactionJournal.Stage.TARGET_ALLOCATED,
                id.value(), List.of(new StructureTransactionJournal.Cell(origin, payload)), null);
        journal.persist(record);
        return record;
    }

    public static void commit(StructureTransactionJournal journal,
                              StructureTransactionJournal.Record record) throws IOException {
        journal.persist(record.withStage(StructureTransactionJournal.Stage.COMMIT_DECIDED));
    }

    public static void recover(MinecraftServer server, SubLevelRuntime runtime) {
        var journal = SubLevelStaticizeJournal.journal(server).orElse(null);
        if (journal == null) return;
        for (var record : journal.records()) {
            if (!BACKEND.equals(record.backend())) continue;
            if (!record.hostDimension().equals(runtime.host().dimension().identifier())) continue;
            try {
                var host = server.getLevel(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION, record.hostDimension()));
                if (host == null) {
                    AcademyCraft.LOGGER.error("SubLevel capture journal host unavailable: {}", record.hostDimension());
                    continue;
                }
                var id = record.targetStructure() == null ? null : new SubLevelId(record.targetStructure());
                if (record.stage().compareTo(StructureTransactionJournal.Stage.COMMIT_DECIDED) < 0) {
                    restore(host, record);
                    if (id != null && runtime.get(id).isPresent()) runtime.remove(id);
                    runtime.persist();
                    AcademyCraft.LOGGER.warn("Rolled back interrupted SubLevel capture {}", record.transactionId());
                } else if (id != null && runtime.get(id).isEmpty()) {
                    AcademyCraft.LOGGER.error("Committed SubLevel capture has no restored SubLevel {}; retaining journal",
                            record.transactionId());
                    continue;
                }
                journal.delete(record.transactionId());
            } catch (IOException | RuntimeException failure) {
                AcademyCraft.LOGGER.error("SubLevel capture recovery failed for {}", record.transactionId(), failure);
            }
        }
    }

    private static void restore(ServerLevel host, StructureTransactionJournal.Record record) {
        var payload = record.cells().getFirst().payload();
        if (!(payload.get("x") instanceof net.minecraft.nbt.IntArrayTag xs)
                || !(payload.get("y") instanceof net.minecraft.nbt.IntArrayTag ys)
                || !(payload.get("z") instanceof net.minecraft.nbt.IntArrayTag zs)
                || !(payload.get("states") instanceof net.minecraft.nbt.IntArrayTag states)
                || !(payload.get("be_index") instanceof net.minecraft.nbt.IntArrayTag beIndices)) {
            throw new IllegalStateException("Malformed capture recovery payload");
        }
        var tags = payload.getList("block_entities").orElseGet(ListTag::new);
        var x = xs.getAsIntArray();
        var y = ys.getAsIntArray();
        var z = zs.getAsIntArray();
        var stateIds = states.getAsIntArray();
        var beIndex = beIndices.getAsIntArray();
        if (x.length != y.length || x.length != z.length || x.length != stateIds.length
                || x.length != beIndex.length) throw new IllegalStateException("Malformed capture recovery arrays");
        for (int index = 0; index < x.length; index++) {
            var state = Block.BLOCK_STATE_REGISTRY.byId(stateIds[index]);
            if (state == null) throw new IllegalStateException("Unknown capture recovery block state");
            var position = new BlockPos(x[index], y[index], z[index]);
            if (!host.setBlock(position, state, SubLevelCaptureService.TRANSACTION_FLAGS)
                    && host.getBlockState(position) != state) {
                throw new IllegalStateException("Could not restore captured block " + position);
            }
            var tagIndex = beIndex[index];
            if (tagIndex >= 0 && tagIndex < tags.size() && tags.get(tagIndex) instanceof CompoundTag tag) {
                try {
                    var loaded = BlockEntity.loadStatic(position, state, tag, host.registryAccess());
                    if (loaded == null) throw new IllegalStateException("Could not reload captured block entity");
                    host.setBlockEntity(loaded);
                    loaded.setChanged();
                } catch (RuntimeException failure) {
                    throw new IllegalStateException("Capture journal could not restore BlockEntity at " + position, failure);
                }
            } else if (tagIndex != -1) {
                throw new IllegalStateException("Malformed captured block entity index");
            }
        }
    }
}
