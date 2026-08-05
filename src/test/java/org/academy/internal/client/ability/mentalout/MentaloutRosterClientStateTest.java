package org.academy.internal.client.ability.mentalout;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MentaloutRosterClientStateTest {
    @AfterEach
    void resetState() {
        MentaloutRosterClientState.setResyncRequester(null);
        MentaloutRosterClientState.clearLocal();
    }

    @Test
    void deltaCannotPublishAgainstAnIncompleteFullTransfer() {
        var requestedRevision = new AtomicLong(-1L);
        MentaloutRosterClientState.setResyncRequester(requestedRevision::set);
        MentaloutRosterClientState.applyFullStart(5L, 1, 1, 30, 20);

        assertFalse(MentaloutRosterClientState.applyDelta(
                6L,
                MentaloutRosterClientState.DELTA_UPSERT,
                entry("delta"),
                null,
                30,
                20
        ));
        assertEquals(0L, requestedRevision.get());
        assertEquals(0L, MentaloutRosterClientState.snapshot().revision());
        assertTrue(MentaloutRosterClientState.snapshot().entries().isEmpty());
    }

    @Test
    void staleFullStartAndClearCannotReplaceANewerPendingTransfer() {
        MentaloutRosterClientState.applyFullStart(5L, 1, 1, 30, 20);
        MentaloutRosterClientState.applyFullStart(4L, 0, 0, 0, 0);
        MentaloutRosterClientState.clear(4L);

        assertTrue(MentaloutRosterClientState.applyFullChunk(5L, 0, List.of(entry("current"))));
        assertEquals(5L, MentaloutRosterClientState.snapshot().revision());
        assertEquals("current", MentaloutRosterClientState.snapshot().entries().getFirst().displayName());
    }

    @Test
    void localDisconnectResetAcceptsANewServersInitialRevision() {
        MentaloutRosterClientState.clear(42L);
        MentaloutRosterClientState.clearLocal();

        assertTrue(MentaloutRosterClientState.applyDelta(
                1L,
                MentaloutRosterClientState.DELTA_UPSERT,
                entry("new-server"),
                null,
                0,
                0
        ));
        assertEquals(1L, MentaloutRosterClientState.snapshot().revision());
        assertEquals("new-server", MentaloutRosterClientState.snapshot().entries().getFirst().displayName());
    }

    @Test
    void stalledFullTransferRequestsAndRetriesAResync() {
        var requests = new AtomicInteger();
        var requestedRevision = new AtomicLong(-1L);
        MentaloutRosterClientState.setResyncRequester(revision -> {
            requests.incrementAndGet();
            requestedRevision.set(revision);
        });
        MentaloutRosterClientState.applyFullStart(5L, 1, 1, 30, 20);

        tick(MentaloutRosterClientState.FULL_TRANSFER_TIMEOUT_TICKS - 1);
        assertEquals(0, requests.get());
        MentaloutRosterClientState.tick();
        assertEquals(1, requests.get());
        assertEquals(0L, requestedRevision.get());

        tick(MentaloutRosterClientState.RESYNC_RETRY_TICKS - 1);
        assertEquals(1, requests.get());
        MentaloutRosterClientState.tick();
        assertEquals(2, requests.get());
    }

    @Test
    void eachNewFullChunkExtendsTheTransferDeadline() {
        var requests = new AtomicInteger();
        MentaloutRosterClientState.setResyncRequester(_ -> requests.incrementAndGet());
        var firstChunk = java.util.Collections.nCopies(64, entry("first"));
        MentaloutRosterClientState.applyFullStart(5L, 2, 65, 30, 20);

        tick(MentaloutRosterClientState.FULL_TRANSFER_TIMEOUT_TICKS - 1);
        assertTrue(MentaloutRosterClientState.applyFullChunk(5L, 0, firstChunk));
        tick(MentaloutRosterClientState.FULL_TRANSFER_TIMEOUT_TICKS - 1);
        assertEquals(0, requests.get());
        MentaloutRosterClientState.tick();
        assertEquals(1, requests.get());
    }

    @Test
    void revisionGapResyncIsRetriedWhenTheResponseIsLost() {
        var requests = new AtomicInteger();
        MentaloutRosterClientState.setResyncRequester(_ -> requests.incrementAndGet());

        assertFalse(MentaloutRosterClientState.applyDelta(
                2L,
                MentaloutRosterClientState.DELTA_UPSERT,
                entry("gap"),
                null,
                0,
                0
        ));
        assertEquals(1, requests.get());

        tick(MentaloutRosterClientState.RESYNC_RETRY_TICKS);
        assertEquals(2, requests.get());
    }

    @Test
    void installingARequesterFlushesAnAlreadyPendingResync() {
        assertFalse(MentaloutRosterClientState.applyDelta(
                2L,
                MentaloutRosterClientState.DELTA_UPSERT,
                entry("gap"),
                null,
                0,
                0
        ));
        var requestedRevision = new AtomicLong(-1L);

        MentaloutRosterClientState.setResyncRequester(requestedRevision::set);

        assertEquals(0L, requestedRevision.get());
    }

    @Test
    void aLateIntermediateDeltaDoesNotCancelARevisionGapResync() {
        var requests = new AtomicInteger();
        var requestedRevision = new AtomicLong(-1L);
        MentaloutRosterClientState.setResyncRequester(revision -> {
            requests.incrementAndGet();
            requestedRevision.set(revision);
        });
        assertFalse(MentaloutRosterClientState.applyDelta(
                2L,
                MentaloutRosterClientState.DELTA_UPSERT,
                entry("revision-2"),
                null,
                0,
                0
        ));
        assertTrue(MentaloutRosterClientState.applyDelta(
                1L,
                MentaloutRosterClientState.DELTA_UPSERT,
                entry("revision-1"),
                null,
                0,
                0
        ));

        tick(MentaloutRosterClientState.RESYNC_RETRY_TICKS);

        assertEquals(2, requests.get());
        assertEquals(1L, requestedRevision.get());
        assertEquals(1L, MentaloutRosterClientState.snapshot().revision());
    }

    @Test
    void malformedFullShapeCannotLeaveATransferPending() {
        var requests = new AtomicInteger();
        MentaloutRosterClientState.setResyncRequester(_ -> requests.incrementAndGet());

        MentaloutRosterClientState.applyFullStart(5L, 2, 2, 30, 20);

        assertEquals(1, requests.get());
        assertFalse(MentaloutRosterClientState.applyFullChunk(5L, 0, List.of(entry("bad"))));
        assertTrue(MentaloutRosterClientState.snapshot().entries().isEmpty());
    }

    @Test
    void outOfOrderFullChunksPublishOnlyAfterAllChunksArrive() {
        var entries = new java.util.ArrayList<MentaloutRosterClientState.Entry>(65);
        for (var index = 0; index < 65; index++) entries.add(entry("entry-" + index));
        MentaloutRosterClientState.applyFullStart(5L, 2, 65, 30, 20);

        assertTrue(MentaloutRosterClientState.applyFullChunk(5L, 1, entries.subList(64, 65)));
        assertEquals(0L, MentaloutRosterClientState.snapshot().revision());
        assertTrue(MentaloutRosterClientState.applyFullChunk(5L, 0, entries.subList(0, 64)));
        assertEquals(5L, MentaloutRosterClientState.snapshot().revision());
        assertEquals(65, MentaloutRosterClientState.snapshot().entries().size());
    }

    private static void tick(int count) {
        for (var index = 0; index < count; index++) MentaloutRosterClientState.tick();
    }

    private static MentaloutRosterClientState.Entry entry(String name) {
        return new MentaloutRosterClientState.Entry(
                UUID.randomUUID(),
                1,
                "minecraft:zombie",
                name,
                20.0f,
                20.0f,
                3.0f,
                MentaloutRosterClientState.SUPPORT_FULL,
                (byte) 0,
                0
        );
    }
}
