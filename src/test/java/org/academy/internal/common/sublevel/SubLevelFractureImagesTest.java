package org.academy.internal.common.sublevel;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import org.academy.internal.common.structure.transaction.StructureTransactionJournal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SubLevelFractureImagesTest {
    @TempDir Path directory;

    @Test void all4096CellsSurviveDurableEnvelopeWithoutRaisingCaptureAdmissionLimit() throws Exception {
        var cells = new ArrayList<StructureTransactionJournal.Cell>();
        var child = UUID.randomUUID().toString();
        for (int i = 0; i < 4096; i++) {
            var payload = new CompoundTag(); payload.putString("child", child);
            payload.putInt("inventory_count", i % 4);
            cells.add(new StructureTransactionJournal.Cell(new BlockPos(i % 16, i / 256, i / 16 % 16), payload));
        }
        var record = record(SubLevelFractures.envelope(cells));
        assertEquals(1, record.cells().size());
        var journal = StructureTransactionJournal.atomicOnly(directory);
        journal.persist(record);
        var restored = SubLevelFractures.cells(StructureTransactionJournal.atomicOnly(directory).find(record.transactionId()).orElseThrow());
        assertEquals(4096, restored.size());
        for (int i = 0; i < 4096; i++) {
            assertEquals(cells.get(i).position(), restored.get(i).position());
            assertEquals(i % 4, restored.get(i).payload().getInt("inventory_count").orElseThrow());
        }
        assertEquals(256, StructureTransactionJournal.MAX_CELLS);
    }

    @Test void rejectsDuplicateOwnershipAndEmptyOrOversizedImages() {
        var tag = new CompoundTag();
        var cell = new StructureTransactionJournal.Cell(BlockPos.ZERO, tag);
        assertThrows(IllegalStateException.class, () -> SubLevelFractures.cells(record(SubLevelFractures.envelope(List.of(cell, cell)))));
        var empty = new CompoundTag(); empty.put("images", new ListTag());
        assertThrows(IllegalStateException.class, () -> SubLevelFractures.cells(record(List.of(new StructureTransactionJournal.Cell(BlockPos.ZERO, empty)))));
        var many = new ArrayList<StructureTransactionJournal.Cell>();
        for (int i = 0; i < 4097; i++) many.add(new StructureTransactionJournal.Cell(new BlockPos(i, 0, 0), tag));
        assertThrows(IllegalStateException.class, () -> SubLevelFractures.cells(record(SubLevelFractures.envelope(many))));
    }

    @Test void oversizedDistinctImagesCannotCreateAnOwnershipDecision() throws Exception {
        var images = new ArrayList<StructureTransactionJournal.Cell>();
        for (int i = 0; i < 16; i++) {
            var payload = new CompoundTag();
            payload.putString("inventory_blob", String.valueOf((char) ('a' + i)).repeat(32768));
            images.add(new StructureTransactionJournal.Cell(new BlockPos(i, 0, 0), payload));
        }
        var journal = StructureTransactionJournal.atomicOnly(directory);
        var failure = assertThrows(java.io.IOException.class, () -> journal.persist(record(SubLevelFractures.envelope(images))));
        assertTrue(failure.getMessage().contains("too large"));
        assertTrue(journal.readSnapshot().records().isEmpty());
    }

    @Test void rejectsInvalidPaletteIndexAndRetainsLegacyEnvelopeReadability() {
        var image = new CompoundTag(); image.putInt("cellX", 2); image.putInt("cellY", 3); image.putInt("cellZ", 4);
        var legacy = new CompoundTag(); var images = new ListTag(); images.add(image); legacy.put("images", images);
        assertEquals(new BlockPos(2, 3, 4), SubLevelFractures.cells(record(List.of(new StructureTransactionJournal.Cell(BlockPos.ZERO, legacy)))).getFirst().position());
        var packed = SubLevelFractures.envelope(List.of(new StructureTransactionJournal.Cell(BlockPos.ZERO, new CompoundTag()))).getFirst().payload();
        packed.put("indices", new net.minecraft.nbt.IntArrayTag(new int[] {-1}));
        assertThrows(IllegalStateException.class, () -> SubLevelFractures.cells(record(List.of(new StructureTransactionJournal.Cell(BlockPos.ZERO, packed)))));
    }

    private static StructureTransactionJournal.Record record(List<StructureTransactionJournal.Cell> cells) {
        return new StructureTransactionJournal.Record(UUID.randomUUID(), Identifier.parse("academy:sublevel_fracture"),
                Identifier.parse("minecraft:overworld"), BlockPos.ZERO, StructureTransactionJournal.Stage.TARGET_ALLOCATED,
                UUID.randomUUID(), cells, null);
    }
}
