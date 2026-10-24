package org.academy.internal.client.validation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueOutput;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureSpaces;
import org.academy.api.common.structure.StructureWriteResult;
import org.academy.internal.common.structure.transaction.StructureTransactionJournal;
import org.academy.internal.common.structure.transaction.SubLevelCaptureTarget;
import org.academy.internal.common.sublevel.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Leaves durable transactions in distant source chunks for the next real server process. */
final class SubLevelCaptureRecoverySmoke {
    private SubLevelCaptureRecoverySmoke() {}

    static void prepare(MinecraftServer server) {
        var fixture = new ArrayList<String>();
        try {
            var journal = journal(server);
            var target = new SubLevelCaptureTarget(server);
            for (var host : List.of(server.overworld(), Objects.requireNonNull(server.getLevel(Level.NETHER)),
                    Objects.requireNonNull(server.getLevel(Level.END)))) {
                var runtime = SubLevelServer.runtime(host).orElseThrow();
                for (boolean committed : new boolean[]{false, true}) {
                    var source = new BlockPos(8192 + (committed ? 32 : 0), host.getMaxY() - 32, 8192);
                    var state = committed ? Blocks.DIAMOND_BLOCK.defaultBlockState() : Blocks.EMERALD_BLOCK.defaultBlockState();
                    host.getChunkAt(source);
                    host.setBlock(source, state, SubLevelCaptureService.TRANSACTION_FLAGS);
                    var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, host.registryAccess());
                    output.store("state", BlockState.CODEC, state);
                    var id = UUID.randomUUID();
                    var record = new StructureTransactionJournal.Record(id, SubLevelStructureRefs.BACKEND,
                            host.dimension().identifier(), source, StructureTransactionJournal.Stage.PREPARED, null,
                            List.of(new StructureTransactionJournal.Cell(source, output.buildResult())), null);
                    journal.persist(record);
                    var reference = target.allocate(host, source, BlockPos.ZERO, state, List.of(source), id).orElseThrow();
                    if (committed) {
                        require(target.writeCell(reference, BlockPos.ZERO, state) == StructureWriteResult.APPLIED,
                                "could not write committed recovery copy");
                        require(target.finalizeTarget(reference, source, List.of(source)), "could not finalize recovery copy");
                        journal.persist(record.withTarget(id).withStage(StructureTransactionJournal.Stage.COMMIT_DECIDED));
                        // Retained transactions use this exact durable staging state after an uncertain save.
                        target.releasePhysics(host, reference);
                    }
                    var body = runtime.get(new SubLevelId(id)).orElseThrow();
                    require(target.save(reference), "could not save recovery fixture");
                    fixture.add((committed ? "COMMIT_DECIDED" : "PREPARED") + " " + host.dimension().identifier()
                            + " " + id + " " + source.getX() + " " + source.getY() + " " + source.getZ()
                            + " " + body.plot().x() + " " + body.plot().z());
                }
            }
            Files.write(fixturePath(server), fixture);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot persist capture restart fixture", failure);
        }
        AcademyCraft.LOGGER.info("SUBLEVEL_CAPTURE_RECOVERY_PREPARED hosts=3 prepared=3 committed=3 distant_source_chunks=true");
    }

    static void verifyRestored(MinecraftServer server) {
        try {
            var rows = Files.readAllLines(fixturePath(server));
            require(rows.size() == 6, "incomplete recovery fixture");
            var journal = journal(server);
            for (var row : rows) {
                var fields = row.split(" ");
                require(fields.length == 8, "malformed recovery fixture");
                boolean committed = fields[0].equals("COMMIT_DECIDED");
                var host = Objects.requireNonNull(server.getLevel(ResourceKey.create(Registries.DIMENSION,
                        Identifier.parse(fields[1]))));
                var runtime = SubLevelServer.runtime(host).orElseThrow();
                var id = new SubLevelId(UUID.fromString(fields[2]));
                var source = new BlockPos(Integer.parseInt(fields[3]), Integer.parseInt(fields[4]), Integer.parseInt(fields[5]));
                require(!journal.exists(id.value()), "recovery journal was not completed: " + row);
                if (committed) {
                    // Completion is checked before this read: recovery had to load the distant
                    // source itself, but its temporary residency may already have expired.
                    host.getChunkAt(source);
                    require(host.getBlockState(source).isAir(), "committed recovery retained source");
                    var body = runtime.get(id).orElseThrow();
                    require(body.plot().equals(new PlotPos(Integer.parseInt(fields[6]), Integer.parseInt(fields[7]))),
                            "recovery changed plot ownership");
                    require(runtime.storage().blockState(body.plot(), BlockPos.ZERO).is(Blocks.DIAMOND_BLOCK),
                            "committed recovery lost target content");
                    var reference = new SubLevelStructureHandle(body, runtime.storage(), host.dimension().identifier()).reference();
                    require(StructureSpaces.resolve(server, reference).isPresent() && runtime.universe().motion(id).isPresent(),
                            "committed recovery did not reactivate its body");
                    require(runtime.remove(id), "could not clean recovered copy");
                } else {
                    require(runtime.get(id).isEmpty(), "prepared recovery retained its plot reservation");
                    host.getChunkAt(source);
                    require(host.getBlockState(source).is(Blocks.EMERALD_BLOCK), "prepared recovery changed its source");
                    host.setBlock(source, Blocks.AIR.defaultBlockState(), SubLevelCaptureService.TRANSACTION_FLAGS);
                    host.save(null, true, false);
                }
            }
            Files.delete(fixturePath(server));
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot verify capture restart fixture", failure);
        }
        AcademyCraft.LOGGER.info("SUBLEVEL_CAPTURE_RECOVERY_RESTART_PASSED hosts=3 prepared_rolled_back=3"
                + " committed_forward=3 source_preserved=true content_preserved=true bodies_active=true journals_recycled=true");
    }

    private static StructureTransactionJournal journal(MinecraftServer server) throws IOException {
        return new StructureTransactionJournal(server.getWorldPath(LevelResource.ROOT)
                .resolve("data/academy/" + StructureTransactionJournal.DIRECTORY_NAME));
    }

    private static Path fixturePath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data/academy/sublevel_capture_recovery_smoke.txt");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("SubLevel capture recovery: " + message);
    }
}
