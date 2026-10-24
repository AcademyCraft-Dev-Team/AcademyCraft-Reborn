package org.academy.internal.common.structure.upgrade.lifecycle;

import net.minecraft.resources.Identifier;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.upgrade.StructureProvenance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StructureProvenanceLedgerTest {
    @TempDir Path directory;

    private static StructureRef reference(long generation) {
        return new StructureRef(UUID.randomUUID(), Identifier.parse("minecraft:overworld"),
                Identifier.parse("academy:sublevel"), generation);
    }

    private static StructureProvenance.Metadata metadata() {
        return StructureProvenance.SkillCreation.resting(UUID.randomUUID(), Identifier.parse("academy:kinetic_throw"))
                .capture(UUID.randomUUID(), 52);
    }

    @Test
    void restartPreservesCreationOwnerPolicyAndSourceGridAcrossRuntimeGenerations() throws IOException {
        var ref = reference(7);
        var metadata = metadata();
        var ledger = new StructureProvenanceLedger(directory);
        assertTrue(ledger.metadata(ref).isEmpty());
        ledger.commit(ref, metadata);
        var reopened = new StructureProvenanceLedger(directory);
        assertTrue(reopened.healthy());
        assertEquals(metadata, reopened.metadata(new StructureRef(ref.id(), ref.dimension(), ref.backend(), 901)).orElseThrow());
        assertEquals(0, reopened.entries().iterator().next().reference().generation());
    }

    @Test
    void exactCommitIsIdempotentAndCannotReplaceAnotherCreatorsMark() throws IOException {
        var ledger = new StructureProvenanceLedger(directory);
        var ref = reference(1);
        var original = metadata();
        ledger.commit(ref, original);
        ledger.commit(ref, original);
        assertEquals(1, ledger.entries().size());
        assertThrows(IOException.class, () -> ledger.commit(ref, metadata()));
        assertFalse(ledger.remove(ref, UUID.randomUUID()));
        assertEquals(original, ledger.metadata(ref).orElseThrow());
        assertTrue(ledger.remove(ref, original.creationOperationId()));
        assertTrue(new StructureProvenanceLedger(directory).entries().isEmpty());
        assertTrue(ledger.remove(ref, original.creationOperationId()));
    }

    @Test
    void sameUuidAcrossBackendOrDimensionNeverAliases() throws IOException {
        var ledger = new StructureProvenanceLedger(directory);
        var ref = reference(1);
        var otherBackend = new StructureRef(ref.id(), ref.dimension(), Identifier.parse("sable:ship"), 0);
        var otherDimension = new StructureRef(ref.id(), Identifier.parse("minecraft:the_nether"), ref.backend(), 0);
        ledger.commit(ref, metadata());
        assertTrue(ledger.metadata(otherBackend).isEmpty());
        assertTrue(ledger.metadata(otherDimension).isEmpty());
        ledger.commit(otherBackend, metadata());
        ledger.commit(otherDimension, metadata());
        assertEquals(3, new StructureProvenanceLedger(directory).entries().size());
    }

    @Test
    void failedDurableWriteNeverPublishesAnInMemoryMark() throws IOException {
        var ledger = new StructureProvenanceLedger(directory, (source, target, atomic) -> {
            throw new IOException("simulated storage failure");
        });
        var ref = reference(1);
        assertThrows(IOException.class, () -> ledger.commit(ref, metadata()));
        assertTrue(ledger.entries().isEmpty());
        assertTrue(new StructureProvenanceLedger(directory).entries().isEmpty());
    }

    @Test
    void unknownOrCorruptRecordsRemainOnDiskAndBlockAutomationInsteadOfAppearingUnmarked() throws IOException {
        var file = directory.resolve("bad.dat");
        Files.write(file, new byte[]{1, 2, 3});
        var ledger = new StructureProvenanceLedger(directory);
        assertFalse(ledger.healthy());
        assertTrue(ledger.entries().isEmpty());
        assertThrows(IOException.class, () -> ledger.commit(reference(1), metadata()));
        assertTrue(Files.exists(file));
    }

    @Test
    void entryViewIsReadOnlyAndUpdatesWithoutCopyingOrWorldScans() throws IOException {
        var ledger = new StructureProvenanceLedger(directory);
        var view = ledger.entries();
        ledger.commit(reference(1), metadata());
        assertEquals(1, view.size());
        assertThrows(UnsupportedOperationException.class, view::clear);
    }

    @Test
    void boundedDiscoveryCanFinishWhileCapturesKeepAddingNewMarks() throws IOException {
        var ledger = new StructureProvenanceLedger(directory);
        var original = new java.util.HashSet<StructureRef>();
        for (int index = 0; index < 20; index++) {
            var ref = reference(0);
            original.add(ref);
            ledger.commit(ref, metadata());
        }
        var found = new java.util.HashSet<StructureRef>();
        var iterator = ledger.entries().iterator();
        while (iterator.hasNext()) {
            found.add(iterator.next().reference());
            if (found.size() <= 20) ledger.commit(reference(0), metadata());
            assertTrue(found.size() <= 40);
        }
        assertTrue(found.containsAll(original), "New captures must not repeatedly restart discovery at its first page");
    }

    @Test
    void unknownPolicyIsPersistedAsDataWithoutBeingUpgradedToKnownPolicy() throws IOException {
        var seed = metadata();
        var unknown = new StructureProvenance.Metadata(seed.source(), seed.creationOperationId(), seed.rootLineageId(),
                Optional.empty(), seed.ownerId(), seed.skillId(), seed.sourceGridRotation(),
                new StructureProvenance.RestorePolicy(Identifier.parse("thirdparty:future_rest"), 9, Map.of("delay", 300.0)), 52);
        var ref = reference(1);
        new StructureProvenanceLedger(directory).commit(ref, unknown);
        assertEquals(unknown, new StructureProvenanceLedger(directory).metadata(ref).orElseThrow());
    }

    @Test
    void malformedFieldOrUnknownMetadataVersionCannotDefaultToTrustedValues() {
        var encoded = StructureProvenanceCodec.encode(metadata());
        encoded.putInt("version", 2);
        assertThrows(IllegalArgumentException.class, () -> StructureProvenanceCodec.decode(encoded));
        var invalidOwner = StructureProvenanceCodec.encode(metadata());
        invalidOwner.putInt("owner", 3);
        assertThrows(IllegalArgumentException.class, () -> StructureProvenanceCodec.decode(invalidOwner));
    }
}
