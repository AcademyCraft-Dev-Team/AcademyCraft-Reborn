package org.academy.internal.common.structure.upgrade.launch;

import net.minecraft.resources.Identifier;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.upgrade.StructureLaunches;
import org.academy.internal.common.structure.DurableFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class StructureLaunchLedgerTest {
    @TempDir Path directory;
    private static StructureLaunches.Grant grant() {
        return new StructureLaunches.Grant(UUID.randomUUID(), Identifier.parse("minecraft:overworld"), UUID.randomUUID(),
                Identifier.parse("minecraft:armor_stand"), Optional.of(UUID.randomUUID()), Identifier.parse("test:explicit_actor"),
                1, 10, 210, Optional.of(new StructureLaunches.Source(new StructureRef(UUID.randomUUID(),
                Identifier.parse("minecraft:overworld"), Identifier.parse("academy:sable"), 7), UUID.randomUUID())));
    }
    @Test void persistedClaimCannotBeReplayedWithSameOrDifferentEffect() throws IOException {
        var grant = grant(); var ledger = new StructureLaunchLedger(directory); ledger.issue(grant);
        var effect = UUID.randomUUID(); assertTrue(ledger.claim(grant, effect, 20));
        var reopened = new StructureLaunchLedger(directory);
        assertEquals(Optional.of(effect), reopened.find(grant.id()).orElseThrow().effect());
        assertEquals(grant, reopened.find(grant.id()).orElseThrow().grant());
        assertEquals(0, grant.source().orElseThrow().reference().generation());
        assertFalse(reopened.claim(grant, effect, 21)); assertFalse(reopened.claim(grant, UUID.randomUUID(), 21));
    }
    @Test void revocationSurvivesRestartAndCannotBeUndoneByReissuing() throws IOException {
        var grant = grant(); var ledger = new StructureLaunchLedger(directory); ledger.issue(grant);
        assertTrue(ledger.revoke(grant.id())); assertTrue(ledger.revoke(grant.id()));
        var reopened = new StructureLaunchLedger(directory);
        assertTrue(reopened.find(grant.id()).orElseThrow().revoked());
        assertFalse(reopened.claim(grant, UUID.randomUUID(), 20));
        assertThrows(IOException.class, () -> reopened.issue(grant));
    }
    @Test void validIntervalIsInclusiveAtCreationAndExclusiveAtExpiry() throws IOException {
        var grant = grant(); var ledger = new StructureLaunchLedger(directory); ledger.issue(grant);
        assertFalse(ledger.claim(grant, UUID.randomUUID(), 9));
        assertFalse(ledger.claim(grant, UUID.randomUUID(), 210));
        assertTrue(ledger.claim(grant, UUID.randomUUID(), 10));
    }
    @Test void forgedOwnerCannotAmendOrClaimExistingGrant() throws IOException {
        var grant = grant(); var ledger = new StructureLaunchLedger(directory); ledger.issue(grant);
        var forged = new StructureLaunches.Grant(grant.id(), grant.dimension(), grant.actorId(), grant.actorType(),
                Optional.of(UUID.randomUUID()), grant.policyId(), 1, 10, 210, grant.source());
        assertThrows(IOException.class, () -> ledger.issue(forged));
        assertFalse(ledger.claim(forged, UUID.randomUUID(), 20));
        assertEquals(grant, ledger.find(grant.id()).orElseThrow().grant());
    }
    @Test void failedClaimPublishDeniesAllUseInProcessAndAcknowledgesNoEffect() throws IOException {
        var fail = new AtomicBoolean();
        var ledger = new StructureLaunchLedger(directory, (source, target, atomic) -> {
            if (fail.get()) throw new IOException("injected publication failure");
            DurableFile.FileOperations.DEFAULT.move(source, target, atomic);
        });
        var grant = grant(); ledger.issue(grant); fail.set(true);
        assertThrows(IOException.class, () -> ledger.claim(grant, UUID.randomUUID(), 20));
        assertFalse(ledger.healthy()); assertTrue(ledger.find(grant.id()).isEmpty());
        assertThrows(IOException.class, () -> ledger.claim(grant, UUID.randomUUID(), 21));
        // No effect may execute without the missing acknowledgment. The original record remains unclaimed.
        assertTrue(new StructureLaunchLedger(directory).find(grant.id()).orElseThrow().effect().isEmpty());
    }
    @Test void failedRevocationIsNotReportedSuccessfulAndBlocksCurrentProcess() throws IOException {
        var grant = grant(); new StructureLaunchLedger(directory).issue(grant);
        var ledger = new StructureLaunchLedger(directory, (source, target, atomic) -> { throw new IOException("denied revoke"); });
        assertThrows(IOException.class, () -> ledger.revoke(grant.id()));
        assertFalse(ledger.healthy()); assertTrue(ledger.find(grant.id()).isEmpty());
    }
    @Test void corruptOrMisnamedRecordsPreserveEvidenceAndBlockAuthorization() throws IOException {
        var grant = grant(); new StructureLaunchLedger(directory).issue(grant);
        Files.copy(directory.resolve(grant.id()+".dat"), directory.resolve(UUID.randomUUID()+".dat"));
        Files.writeString(directory.resolve("broken.dat"), "not a journal");
        var ledger = new StructureLaunchLedger(directory);
        assertFalse(ledger.healthy()); assertTrue(ledger.find(grant.id()).isEmpty());
        assertThrows(IOException.class, () -> ledger.revoke(grant.id()));
        try (var paths = Files.list(directory)) { assertEquals(3, paths.count()); }
    }
    @Test void invalidLifetimeAndCrossDimensionSourceAreRejected() {
        var grant = grant();
        assertThrows(IllegalArgumentException.class, () -> new StructureLaunches.Grant(grant.id(), grant.dimension(), grant.actorId(),
                grant.actorType(), grant.ownerId(), grant.policyId(), 1, 10, 10 + StructureLaunches.MAX_LIFETIME_TICKS + 1L, grant.source()));
        assertThrows(IllegalArgumentException.class, () -> new StructureLaunches.Grant(grant.id(), Identifier.parse("minecraft:the_nether"),
                grant.actorId(), grant.actorType(), grant.ownerId(), grant.policyId(), 1, 10, 100, grant.source()));
    }
}
