package org.academy.internal.common.sublevel;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import org.academy.AcademyCraft;
import org.academy.internal.common.structure.transaction.StructureTransactionJournal;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

/** Durable write-ahead journal for host-block staticization of a SubLevel. */
public final class SubLevelStaticizeJournal {
    public static final Identifier BACKEND = Identifier.fromNamespaceAndPath("academy", "sublevel_staticize");
    private static final WeakHashMap<MinecraftServer, StructureTransactionJournal> JOURNALS = new WeakHashMap<>();

    private SubLevelStaticizeJournal() {
    }

    public static Optional<StructureTransactionJournal> journal(MinecraftServer server) {
        try {
            var existing = JOURNALS.get(server);
            if (existing != null && existing.healthy()) return Optional.of(existing);
            var created = new StructureTransactionJournal(directory(server));
            if (!created.healthy()) return Optional.empty();
            JOURNALS.put(server, created);
            return Optional.of(created);
        } catch (IOException | RuntimeException failure) {
            AcademyCraft.LOGGER.error("SubLevel staticize journal unavailable", failure);
            return Optional.empty();
        }
    }

    public static StructureTransactionJournal.Record begin(StructureTransactionJournal journal,
                                                            ServerLevel host, BlockPos origin,
                                                            SubLevelId id, List<BlockPos> targets)
            throws IOException {
        int[] xs = new int[targets.size()];
        int[] ys = new int[targets.size()];
        int[] zs = new int[targets.size()];
        for (int index = 0; index < targets.size(); index++) {
            var target = targets.get(index);
            xs[index] = target.getX();
            ys[index] = target.getY();
            zs[index] = target.getZ();
        }
        var payload = new CompoundTag();
        payload.putString("kind", "staticize_targets");
        payload.put("x", new net.minecraft.nbt.IntArrayTag(xs));
        payload.put("y", new net.minecraft.nbt.IntArrayTag(ys));
        payload.put("z", new net.minecraft.nbt.IntArrayTag(zs));
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

    public static void finish(StructureTransactionJournal journal, UUID transactionId) {
        try {
            journal.delete(transactionId);
        } catch (IOException failure) {
            AcademyCraft.LOGGER.error("SubLevel staticize journal cleanup failed for {}", transactionId, failure);
        }
    }

    /** Startup reconciliation: rollback uncommitted writes or finish a committed removal. */
    public static void recover(MinecraftServer server, SubLevelRuntime runtime) {
        var journal = journal(server).orElse(null);
        if (journal == null) return;
        for (var record : journal.records()) {
            if (!BACKEND.equals(record.backend())) continue;
            if (!record.hostDimension().equals(runtime.host().dimension().identifier())) continue;
            try {
                var host = server.getLevel(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION, record.hostDimension()));
                if (host == null) {
                    AcademyCraft.LOGGER.error("SubLevel staticize journal host is unavailable: {}", record.hostDimension());
                    continue;
                }
                if (record.stage().compareTo(StructureTransactionJournal.Stage.COMMIT_DECIDED) < 0) {
                    for (var position : targets(record)) {
                        if (!host.getBlockState(position).isAir()
                                && !host.setBlock(position, Blocks.AIR.defaultBlockState(), SubLevelCaptureService.TRANSACTION_FLAGS)) {
                            throw new IllegalStateException("Could not roll back staticized block " + position);
                        }
                    }
                    AcademyCraft.LOGGER.warn("Rolled back interrupted SubLevel staticize {}", record.transactionId());
                } else if (record.targetStructure() != null) {
                    var id = new SubLevelId(record.targetStructure());
                    if (runtime.get(id).isPresent()) runtime.remove(id);
                    runtime.persist();
                    AcademyCraft.LOGGER.warn("Completed committed SubLevel staticize {}", record.transactionId());
                }
                journal.delete(record.transactionId());
            } catch (IOException | RuntimeException failure) {
                AcademyCraft.LOGGER.error("SubLevel staticize recovery failed for {}", record.transactionId(), failure);
            }
        }
    }

    private static List<BlockPos> targets(StructureTransactionJournal.Record record) {
        var payload = record.cells().getFirst().payload();
        if (!(payload.get("x") instanceof net.minecraft.nbt.IntArrayTag xs)
                || !(payload.get("y") instanceof net.minecraft.nbt.IntArrayTag ys)
                || !(payload.get("z") instanceof net.minecraft.nbt.IntArrayTag zs)) {
            throw new IllegalStateException("Malformed staticize recovery payload");
        }
        var x = xs.getAsIntArray();
        var y = ys.getAsIntArray();
        var z = zs.getAsIntArray();
        if (x.length != y.length || x.length != z.length) throw new IllegalStateException("Malformed staticize recovery arrays");
        var positions = new ArrayList<BlockPos>(x.length);
        for (int index = 0; index < x.length; index++) {
            positions.add(new BlockPos(x[index], y[index], z[index]));
        }
        return positions;
    }

    private static Path directory(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data/academy/sublevel_transactions");
    }
}
