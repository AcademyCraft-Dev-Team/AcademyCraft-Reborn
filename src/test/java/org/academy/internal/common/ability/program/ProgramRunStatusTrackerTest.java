package org.academy.internal.common.ability.program;

import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.ProgramRunStatus;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProgramRunStatusTrackerTest {
    private static final Identifier DIMENSION = Identifier.withDefaultNamespace("overworld");
    private static final Identifier CATEGORY = Identifier.fromNamespaceAndPath("academy", "accelerator");
    private static final ProgramRunStatus.Target TARGET = new ProgramRunStatus.Target(DIMENSION, CATEGORY, 0, new UUID(1, 2), 3);

    @Test void noSubscriptionDoesNotCreateAStatusAndIdentityChangesCannotReuseIt() {
        var tracker = new ProgramRunStatusTracker(); var owner = UUID.randomUUID(); var token = UUID.randomUUID();
        assertNull(tracker.begin(owner, TARGET, 0, 100)); assertNull(tracker.poll(owner, 0)); assertEquals(0, tracker.size());
        assertTrue(tracker.subscribe(owner, token, TARGET, 0, 100));
        var changed = new ProgramRunStatus.Target(DIMENSION, CATEGORY, 0, TARGET.programId(), 4);
        assertNull(tracker.begin(owner, changed, 0, 100));
        assertFalse(tracker.subscribe(owner, token, changed, 0, 100));
        assertFalse(tracker.unsubscribe(owner, UUID.randomUUID()));
        assertNotNull(tracker.poll(owner, 0));
    }

    @Test void oldRunTerminalCannotReplaceNewRunAndUpdateSerialIncreasesAcrossRuns() {
        var tracker = new ProgramRunStatusTracker(); var owner = UUID.randomUUID(); var token = UUID.randomUUID();
        tracker.subscribe(owner, token, TARGET, 0, 100);
        var first = tracker.begin(owner, TARGET, 0, 100);
        assertNotNull(first);
        tracker.finish(first, ProgramRunStatus.Phase.COMPLETED, 5, "NONE", "", "committed", false, 100, 0);
        var finished = tracker.poll(owner, 0); assertEquals(ProgramRunStatus.Phase.COMPLETED, finished.phase());
        var second = tracker.begin(owner, TARGET, 1, 101); assertNotNull(second);
        assertTrue(second.runSequence() > first.runSequence());
        tracker.finish(first, ProgramRunStatus.Phase.CANCELLED, 5, "NONE", "", "stale", false, 102, 2);
        assertNull(tracker.poll(owner, 4));
        var preparing = tracker.poll(owner, 5);
        assertEquals(second.runSequence(), preparing.runSequence());
        assertEquals(ProgramRunStatus.Phase.PREPARING, preparing.phase());
        assertTrue(preparing.updateSequence() > finished.updateSequence());
        tracker.finish(second, ProgramRunStatus.Phase.FAILED, 8, "ACTION_REJECTED", "value", "failed", true, 106, 6);
        tracker.update(second, waiting(10, 15, 106), 7);
        var failed = tracker.poll(owner, 10);
        assertEquals(ProgramRunStatus.Phase.FAILED, failed.phase()); assertTrue(failed.rollbackIncomplete());
    }

    @Test void tokenReplacementUnsubscribeAndInvalidationCannotBypassTheOwnerSendRate() {
        var tracker = new ProgramRunStatusTracker(); var owner = UUID.randomUUID(); var one = UUID.randomUUID();
        tracker.subscribe(owner, one, TARGET, 0, 0); assertNotNull(tracker.poll(owner, 0));
        var two = UUID.randomUUID(); tracker.subscribe(owner, two, TARGET, 1, 1);
        assertFalse(tracker.unsubscribe(owner, one)); assertNull(tracker.poll(owner, 1));
        assertTrue(tracker.unsubscribe(owner, two));
        var three = UUID.randomUUID(); tracker.subscribe(owner, three, TARGET, 2, 2); assertNull(tracker.poll(owner, 2));
        tracker.invalidate(owner); tracker.subscribe(owner, UUID.randomUUID(), TARGET, 3, 3);
        assertNull(tracker.poll(owner, 4)); assertNotNull(tracker.poll(owner, 5));
    }

    @Test void leaseUsesServerTicksWhileLogicalAndWorldClocksRemainSeparate() {
        var tracker = new ProgramRunStatusTracker(); var owner = UUID.randomUUID(); var token = UUID.randomUUID();
        tracker.subscribe(owner, token, TARGET, 0, 100000);
        var handle = tracker.begin(owner, TARGET, 0, 100000);
        tracker.update(handle, waiting(10, 110, 100000), 0);
        var status = tracker.poll(owner, 0);
        assertEquals(10, status.logicalTick()); assertEquals(110, status.wakeAtLogicalTick()); assertEquals(100000, status.worldGameTick());
        assertTrue(tracker.matches(owner, token, TARGET, 399));
        tracker.subscribe(owner, token, TARGET, 399, 999999);
        assertTrue(tracker.matches(owner, token, TARGET, 400));
        assertNull(tracker.poll(owner, 400), "heartbeat does not fabricate logical progress or send unchanged state");
        assertFalse(tracker.matches(owner, token, TARGET, 799)); assertEquals(0, tracker.size());
    }

    @Test void subscriptionAndRecentSenderTablesAreBoundedWithoutEvictingLiveRateLimits() {
        var tracker = new ProgramRunStatusTracker(); var owners = new java.util.ArrayList<UUID>();
        var tokens = new java.util.ArrayList<UUID>();
        for (int i = 0; i < 256; i++) {
            var owner = UUID.randomUUID(); var token = UUID.randomUUID(); owners.add(owner); tokens.add(token);
            assertTrue(tracker.subscribe(owner, token, TARGET, 0, 0)); assertNotNull(tracker.poll(owner, 0));
        }
        var next = UUID.randomUUID(); var token = UUID.randomUUID();
        assertFalse(tracker.subscribe(next, token, TARGET, 1, 1));
        tracker.unsubscribe(owners.getFirst(), tokens.getFirst());
        assertTrue(tracker.subscribe(next, token, TARGET, 1, 1));
        assertNull(tracker.poll(next, 1), "full cooldown table delays a new sender instead of dropping retained limits");
        assertNotNull(tracker.poll(next, 5));
        tracker.clear(); assertEquals(0, tracker.size());
    }

    @Test void failedTransactionIsNeverPublishedAsCompletedAndObservationDoesNotApplyItTwice() {
        var tracker = new ProgramRunStatusTracker(); var owner = UUID.randomUUID();
        tracker.subscribe(owner, UUID.randomUUID(), TARGET, 0, 0);
        var handle = tracker.begin(owner, TARGET, 0, 0);
        var transaction = new ProgramActionTransaction(); var calls = new java.util.concurrent.atomic.AtomicInteger();
        transaction.stage(7, () -> { calls.incrementAndGet(); throw new IllegalStateException("commit rejected"); });
        tracker.update(handle, new ProgramRunStatusTracker.Progress(ProgramRunStatus.Phase.RUNNING, ProgramRunStatus.WaitKind.NONE,
                7, -1, null, 10, -1, 100, "NONE", "", "settling_transaction", false), 0);
        assertEquals(ProgramRunStatus.Phase.RUNNING, tracker.poll(owner, 0).phase());
        assertEquals(0, calls.get());
        var committed = transaction.commit(); assertFalse(committed.successful());
        tracker.finish(handle, ProgramRunStatus.Phase.FAILED, committed.nodeId(), "ACTION_REJECTED", "", "commit_failed",
                committed.rollbackFailures() > 0, 100, 1);
        assertEquals(ProgramRunStatus.Phase.FAILED, tracker.poll(owner, 5).phase());
        assertEquals(1, calls.get());
    }

    @Test void dtoBoundsUtf8AndDoesNotExposeOverlongNodeTypeOrControlCharacters() {
        var dto = new ProgramRunStatus(UUID.randomUUID(), TARGET, 1, 1, ProgramRunStatus.Phase.WAITING,
                ProgramRunStatus.WaitKind.RETRY, 1, 1, Identifier.fromNamespaceAndPath("fixture", "a".repeat(129)),
                1, 2, 10000, 3, "字".repeat(100), "口".repeat(100), "\n😀".repeat(100), false);
        assertNull(dto.nodeType());
        assertTrue(dto.diagnosticCode().getBytes(StandardCharsets.UTF_8).length <= 64);
        assertTrue(dto.port().getBytes(StandardCharsets.UTF_8).length <= 64);
        assertTrue(dto.reasonCode().getBytes(StandardCharsets.UTF_8).length <= 96);
        assertFalse(dto.reasonCode().contains("\n"));
    }

    private static ProgramRunStatusTracker.Progress waiting(long logical, long wakeAt, long world) {
        return new ProgramRunStatusTracker.Progress(ProgramRunStatus.Phase.WAITING, ProgramRunStatus.WaitKind.WAIT,
                3, 2, null, logical, wakeAt, world, "NONE", "", "wait_logical_ticks", false);
    }
}
