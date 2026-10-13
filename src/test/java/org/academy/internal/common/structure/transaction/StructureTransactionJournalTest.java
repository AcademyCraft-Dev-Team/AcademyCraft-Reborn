package org.academy.internal.common.structure.transaction;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StructureTransactionJournalTest {
    private static final Identifier BACKEND = Identifier.fromNamespaceAndPath("academy", "native_level");
    private static final Identifier DIMENSION = Identifier.fromNamespaceAndPath("minecraft", "overworld");

    @TempDir
    Path directory;

    private static StructureTransactionJournal.Cell cell(int x, int y, int z, String tag) {
        var payload = new CompoundTag();
        payload.putString("state", tag);
        return new StructureTransactionJournal.Cell(new BlockPos(x, y, z), payload);
    }

    private static StructureTransactionJournal.Record prepared(UUID id, List<StructureTransactionJournal.Cell> cells) {
        return new StructureTransactionJournal.Record(id, BACKEND, DIMENSION, new BlockPos(1, 2, 3),
                StructureTransactionJournal.Stage.PREPARED, null, cells, null);
    }

    @Test
    void reopensEveryStageTargetAndEvidenceWithoutLosingCellPayloads() throws IOException {
        var journal = new StructureTransactionJournal(directory);
        var id = UUID.randomUUID();
        var record = prepared(id, List.of(cell(4, 5, 6, "minecraft:stone"), cell(4, 5, 7, "minecraft:oak_log")));
        journal.persist(record);
        record = record.withTarget(UUID.randomUUID());
        journal.persist(record);
        var evidence = new CompoundTag();
        evidence.putString("reason", "rollback_blocked");
        record = record.withStage(StructureTransactionJournal.Stage.COMMIT_DECIDED).withEvidence(evidence);
        journal.persist(record);
        journal.persist(record.withStage(StructureTransactionJournal.Stage.PUBLISHED));

        var reopened = new StructureTransactionJournal(directory);
        assertTrue(reopened.healthy());
        assertTrue(reopened.corruptFiles().isEmpty());
        var decoded = reopened.find(id).orElseThrow();
        assertEquals(StructureTransactionJournal.Stage.PUBLISHED, decoded.stage());
        assertEquals(record.targetStructure(), decoded.targetStructure());
        assertEquals(new BlockPos(1, 2, 3), decoded.origin());
        assertEquals(2, decoded.cells().size());
        assertEquals("minecraft:stone", decoded.cells().getFirst().payload().getString("state").orElseThrow());
        assertEquals("rollback_blocked", decoded.failureEvidence().getString("reason").orElseThrow());
    }

    @Test
    void recordsRescansTheDirectoryForRecordsPersistedByAnotherSession() throws IOException {
        var writer = new StructureTransactionJournal(directory);
        var reader = new StructureTransactionJournal(directory);
        var id = UUID.randomUUID();
        writer.persist(prepared(id, List.of(cell(0, 0, 0, "minecraft:dirt"))));
        assertEquals(1, reader.records().size());
        writer.delete(id);
        assertTrue(reader.records().isEmpty());
    }

    @Test
    void stagesOnlyMoveForwardAndAllocatedRecordsRequireATarget() throws IOException {
        var journal = new StructureTransactionJournal(directory);
        var id = UUID.randomUUID();
        var record = prepared(id, List.of(cell(0, 0, 0, "minecraft:dirt")));
        var allocated = record.withTarget(UUID.randomUUID());
        assertThrows(IllegalArgumentException.class,
                () -> allocated.withStage(StructureTransactionJournal.Stage.PREPARED));
        assertThrows(IllegalArgumentException.class,
                () -> new StructureTransactionJournal.Record(id, BACKEND, DIMENSION, new BlockPos(0, 0, 0),
                        StructureTransactionJournal.Stage.COPIED, null, List.of(cell(0, 0, 0, "minecraft:dirt")), null));
        assertThrows(IllegalArgumentException.class,
                () -> new StructureTransactionJournal.Record(id, BACKEND, DIMENSION, new BlockPos(0, 0, 0),
                        StructureTransactionJournal.Stage.PREPARED, UUID.randomUUID(),
                        List.of(cell(0, 0, 0, "minecraft:dirt")), null));
        journal.persist(record);
        journal.persist(allocated);
        assertThrows(IOException.class, () -> journal.persist(record));
    }

    @Test
    void rejectsEmptyOversizedAndDuplicateCells() {
        var id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> prepared(id, List.of()));
        assertThrows(IllegalArgumentException.class, () -> prepared(id, List.of(
                cell(1, 1, 1, "a"), cell(1, 1, 1, "b"))));
        var tooMany = new ArrayList<StructureTransactionJournal.Cell>();
        for (int index = 0; index <= StructureTransactionJournal.MAX_CELLS; index++) {
            tooMany.add(cell(index, 0, 0, "a"));
        }
        assertThrows(IllegalArgumentException.class, () -> prepared(id, tooMany));
    }

    @Test
    void cellPayloadsAreDefensiveCopies() {
        var payload = new CompoundTag();
        payload.putString("state", "minecraft:stone");
        var cell = new StructureTransactionJournal.Cell(new BlockPos(0, 0, 0), payload);
        payload.putString("state", "changed");
        assertEquals("minecraft:stone", cell.payload().getString("state").orElseThrow());
        cell.payload().putString("state", "mutated");
        assertEquals("minecraft:stone", cell.payload().getString("state").orElseThrow());
    }

    @Test
    void isolatesCorruptAndMismatchedRecordsWithoutDeletingThem() throws IOException {
        var journal = new StructureTransactionJournal(directory);
        var good = UUID.randomUUID();
        journal.persist(prepared(good, List.of(cell(0, 0, 0, "minecraft:dirt"))));
        Files.write(directory.resolve("garbage.dat"), new byte[]{1, 2, 3, 4});
        var misplaced = directory.resolve("00000000-0000-0000-0000-000000000001.dat");
        var foreignId = UUID.randomUUID();
        new StructureTransactionJournal(directory).persist(prepared(foreignId,
                List.of(cell(0, 0, 0, "minecraft:dirt"))));
        Files.move(directory.resolve(foreignId + ".dat"), misplaced);
        var reopened = new StructureTransactionJournal(directory);
        assertEquals(List.of(good), reopened.records().stream()
                .map(StructureTransactionJournal.Record::transactionId).toList());
        assertEquals(2, reopened.corruptFiles().size());
        assertTrue(Files.exists(directory.resolve("garbage.dat")));
        assertTrue(Files.exists(misplaced));
    }

    @Test
    void persistsAtomicallyWithoutTemporaryRemnants() throws IOException {
        var journal = new StructureTransactionJournal(directory);
        var id = UUID.randomUUID();
        journal.persist(prepared(id, List.of(cell(0, 0, 0, "minecraft:dirt"))));
        journal.persist(prepared(id, List.of(cell(0, 0, 0, "minecraft:dirt")))
                .withTarget(UUID.randomUUID()));
        try (var files = Files.list(directory)) {
            assertEquals(1, files.filter(file -> file.getFileName().toString().endsWith(".dat")).count());
        }
        try (var files = Files.list(directory)) {
            assertEquals(0, files.filter(file -> file.getFileName().toString().endsWith(".tmp")).count());
        }
    }

    @Test
    void refusesOversizedPayloadsAndRecyclesDeletedRecords() throws IOException {
        var journal = new StructureTransactionJournal(directory);
        var id = UUID.randomUUID();
        var payload = new CompoundTag();
        payload.putByteArray("blob", new byte[StructureTransactionJournal.MAX_PAYLOAD_BYTES + 1]);
        var oversized = prepared(id, List.of(new StructureTransactionJournal.Cell(new BlockPos(0, 0, 0), payload)));
        assertThrows(IOException.class, () -> journal.persist(oversized));
        journal.persist(prepared(id, List.of(cell(0, 0, 0, "minecraft:dirt"))));
        journal.delete(id);
        assertFalse(journal.exists(id));
        assertFalse(Files.exists(directory.resolve(id + ".dat")));
        journal.delete(id);
        var reopened = new StructureTransactionJournal(directory);
        assertTrue(reopened.records().isEmpty());
    }
}
