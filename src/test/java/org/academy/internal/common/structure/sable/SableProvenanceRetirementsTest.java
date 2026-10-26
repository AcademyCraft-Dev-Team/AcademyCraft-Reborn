package org.academy.internal.common.structure.sable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.upgrade.StructureProvenance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SableProvenanceRetirementsTest {
    @TempDir Path directory;

    private static SableProvenanceRetirements.Entry entry() {
        var reference = new StructureRef(UUID.randomUUID(), Identifier.parse("minecraft:overworld"),
                Identifier.parse("academy:sable"), 0);
        var metadata = StructureProvenance.SkillCreation.resting(UUID.randomUUID(), Identifier.parse("academy:kinetic_throw"))
                .capture(UUID.randomUUID(), 7);
        var evidence = new CompoundTag();
        evidence.putInt("plot_x", 0); evidence.putInt("plot_z", 1); evidence.putBoolean("saved", true);
        evidence.putInt("chunk_x", 5); evidence.putInt("chunk_z", -2);
        evidence.putInt("storage", 0); evidence.putInt("slot", 4);
        return new SableProvenanceRetirements.Entry(reference, metadata, evidence);
    }

    @Test void restartRetainsExactDeletionDecisionUntilDurabilityReceipt() throws IOException {
        var entry = entry();
        var journal = new SableProvenanceRetirements(directory);
        journal.prepare(entry); journal.prepare(entry);
        var reopened = new SableProvenanceRetirements(directory);
        assertTrue(reopened.healthy());
        assertEquals(java.util.List.of(entry), java.util.List.copyOf(reopened.entries()));
        reopened.complete(entry);
        assertTrue(new SableProvenanceRetirements(directory).entries().isEmpty());
    }

    @Test void pointerAndCreationCannotBeReplacedOnRetry() throws IOException {
        var entry = entry();
        var journal = new SableProvenanceRetirements(directory);
        journal.prepare(entry);
        var changed = entry.evidence(); changed.putInt("slot", 9);
        var other = new SableProvenanceRetirements.Entry(entry.reference(), entry.metadata(), changed);
        assertThrows(IOException.class, () -> journal.prepare(other));
        assertThrows(IOException.class, () -> journal.complete(other));
        assertEquals(entry, journal.entries().iterator().next());
    }

    @Test void mutableNbtCannotChangeTheRecordedOwnership() throws IOException {
        var entry = entry(); var evidence = entry.evidence();
        var copy = new SableProvenanceRetirements.Entry(entry.reference(), entry.metadata(), evidence);
        evidence.putInt("slot", 8); copy.evidence().putInt("slot", 10);
        assertEquals(entry, copy);
        var journal = new SableProvenanceRetirements(directory); journal.prepare(copy);
        assertThrows(UnsupportedOperationException.class, () -> journal.entries().clear());
    }

    @Test void failureBeforeAtomicPublishKeepsExistingMarkAndNoFalseIntent() throws IOException {
        var journal = new SableProvenanceRetirements(directory, (source, target, atomic) -> {
            throw new IOException("fault before intent publication");
        });
        assertThrows(IOException.class, () -> journal.prepare(entry()));
        assertTrue(journal.entries().isEmpty());
        assertTrue(new SableProvenanceRetirements(directory).entries().isEmpty());
    }

    @Test void corruptJournalBlocksCompletionAndPreservesEvidence() throws IOException {
        var entry = entry(); var journal = new SableProvenanceRetirements(directory); journal.prepare(entry);
        Files.write(directory.resolve("unknown.dat"), new byte[]{1,2,3});
        var reopened = new SableProvenanceRetirements(directory);
        assertFalse(reopened.healthy()); assertTrue(reopened.contains(entry.reference()));
        assertThrows(IOException.class, () -> reopened.complete(entry));
        assertTrue(Files.exists(directory.resolve("unknown.dat")));
    }

    @Test void unsavedBodyStillRequiresAnExplicitPlotAndDeletionDecision() {
        var entry = entry(); var evidence = new CompoundTag(); evidence.putBoolean("saved", false);
        assertThrows(RuntimeException.class, () -> new SableProvenanceRetirements.Entry(entry.reference(), entry.metadata(), evidence));
        evidence.putInt("plot_x", 1); evidence.putInt("plot_z", 2);
        assertNotNull(new SableProvenanceRetirements.Entry(entry.reference(), entry.metadata(), evidence));
    }

    @Test void failedCompletionPreservesReplayRecordAcrossRestart() throws IOException {
        var entry = entry();
        var journal = new SableProvenanceRetirements(directory,
                org.academy.internal.common.structure.DurableFile.FileOperations.DEFAULT,
                ignored -> { throw new IOException("simulated journal delete failure"); });
        journal.prepare(entry);
        assertThrows(IOException.class, () -> journal.complete(entry));
        assertEquals(entry, journal.entry(entry.reference()).orElseThrow());
        var reopened = new SableProvenanceRetirements(directory);
        assertTrue(reopened.healthy());
        assertEquals(entry, reopened.entry(entry.reference()).orElseThrow());
        reopened.complete(entry);
        assertEquals(0, new SableProvenanceRetirements(directory).size());
    }

    @Test void diagnosticSamplesAreBoundedAndNeverDiscardCorruptEvidence() throws IOException {
        var entry = entry(); new SableProvenanceRetirements(directory).prepare(entry);
        Path original;
        try(var paths=Files.list(directory)) { original=paths.filter(path -> path.toString().endsWith(".dat")).findFirst().orElseThrow(); }
        for(int i=0;i<12;i++) Files.copy(original,directory.resolve("wrong-identity-"+i+".dat"));
        var journal=new SableProvenanceRetirements(directory);
        assertFalse(journal.healthy()); assertEquals(1,journal.size());
        assertEquals(12,journal.problemCount()); assertEquals(8,journal.problems().size());
        assertTrue(journal.problems().stream().allMatch(problem -> problem.reason().equals("identity_mismatch")));
        assertThrows(UnsupportedOperationException.class, () -> journal.problems().clear());
        try(var paths=Files.list(directory)) { assertEquals(13,paths.count()); }
        assertThrows(IOException.class, () -> journal.complete(entry));
    }
}
