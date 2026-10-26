package org.academy.api.common.ability.program;

import net.minecraft.resources.Identifier;
import org.academy.api.common.structure.StructureRef;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.academy.api.common.ability.program.ProgramStructureOrientationRuntime.Operation;
import static org.junit.jupiter.api.Assertions.*;

class ProgramStructureGroupControlTest {
    private static final ProgramStructureReference A = target(1), B = target(2);

    @Test void deduplicatesSnapshotsAndRejectsBudgetsOrConflictingEpochsBeforeCallbacks() {
        var values = new ArrayList<>(List.of(A, A, B));
        var backend = new Backend();
        var group = new ProgramStructureGroupControl(values, backend);
        values.clear();
        assertEquals(List.of(A, B), group.members().stream().map(ProgramStructureGroupControl.Member::target).toList());
        assertThrows(UnsupportedOperationException.class, () -> group.members().clear());
        assertThrows(IllegalArgumentException.class, () -> new ProgramStructureGroupControl(java.util.stream.IntStream.rangeClosed(1, 9).mapToObj(ProgramStructureGroupControlTest::target).toList(), backend));
        assertThrows(IllegalArgumentException.class, () -> new ProgramStructureGroupControl(List.of(A, new ProgramStructureReference(A.reference(), UUID.randomUUID())), backend));
        assertThrows(IllegalArgumentException.class, () -> new ProgramStructureGroupControl(Collections.nCopies(129, A), backend));
        assertTrue(backend.events.isEmpty());
    }
    @Test void validatesEveryMemberBeforeAcquiringAnything() {
        var backend = new Backend(); backend.reject = B;
        var group = new ProgramStructureGroupControl(List.of(A, B), backend);
        assertEquals("group_member_2:unavailable", group.start().result().detail());
        assertTrue(backend.events.isEmpty()); assertTrue(group.finished());
        assertFalse(group.workStarted());
    }
    @Test void allLeasesPrecedeCommandsAndRepeatedStartDoesNotDuplicateWork() {
        var backend = new Backend(); var group = new ProgramStructureGroupControl(List.of(A, B), backend);
        var started = group.start();
        assertEquals(ProgramStructureOperationResult.Status.PENDING, started.result().status());
        assertEquals(group.receipt(), group.start().operationId());
        assertEquals(List.of("acquire:1", "acquire:2", "start:1", "start:2"), backend.events);
        backend.complete.add(A);
        assertEquals(ProgramStructureOperationResult.Status.PENDING, group.poll().result().status());
        assertEquals(2, backend.leases.size(), "finished members remain leased until the group finishes");
        backend.complete.add(B);
        assertEquals(ProgramStructureOperationResult.Status.COMPLETED, group.poll().result().status());
        assertTrue(backend.leases.isEmpty());
        int events = backend.events.size(); group.close(); group.close(); group.poll();
        assertEquals(events, backend.events.size());
    }
    @Test void emptyGroupsCompleteWithoutCallbacks() {
        var backend = new Backend(); var group = new ProgramStructureGroupControl(List.of(), backend);
        assertEquals("group_empty", group.start().result().detail()); assertTrue(backend.events.isEmpty());
    }
    @Test void partialAcquisitionFailureReleasesOnlyItsOwnTokensBeforeAnyCommand() {
        var backend = new Backend(); var successor = UUID.randomUUID(); backend.leases.put(B, successor);
        var group = new ProgramStructureGroupControl(List.of(A, B), backend);
        assertEquals(ProgramStructureOperationResult.Status.REJECTED, group.start().result().status());
        assertEquals(Map.of(B, successor), backend.leases);
        assertTrue(backend.events.stream().noneMatch(event -> event.startsWith("start:")));
    }
    @Test void throwingAcquisitionIsIncludedInCleanup() {
        var backend = new Backend(); backend.throwAcquire = B;
        var group = new ProgramStructureGroupControl(List.of(A, B), backend);
        assertThrows(IllegalStateException.class, group::start);
        assertTrue(backend.leases.isEmpty()); assertTrue(group.finished());
    }
    @Test void partialCommandFailureCancelsAlreadyStartedMembers() {
        var backend = new Backend(); backend.failStart = B;
        var group = new ProgramStructureGroupControl(List.of(A, B), backend);
        assertEquals("group_member_2:denied", group.start().result().detail());
        assertTrue(backend.leases.isEmpty()); assertTrue(backend.running.isEmpty());
        assertTrue(group.workStarted(), "partial accepted work remains payable after cancellation");
    }
    @Test void firstRejectedCommandDoesNotChargeButUncertainSubmissionDoes() {
        var rejected = new Backend(); rejected.failStart = A;
        var untouched = new ProgramStructureGroupControl(List.of(A, B), rejected);
        assertEquals("group_member_1:denied", untouched.start().result().detail());
        assertFalse(untouched.workStarted()); assertTrue(rejected.leases.isEmpty());

        var throwing = new Backend(); throwing.throwStart = true;
        var uncertain = new ProgramStructureGroupControl(List.of(A, B), throwing);
        assertThrows(IllegalStateException.class, uncertain::start);
        assertTrue(uncertain.workStarted(), "a throwing submission may already have affected the body");
        assertTrue(throwing.leases.isEmpty()); assertTrue(throwing.running.isEmpty());
    }
    @Test void unloadOfAnAlreadyCompletedMemberStillEndsTheWholeGroup() {
        var backend = new Backend(); var group = new ProgramStructureGroupControl(List.of(A, B), backend);
        group.start(); backend.complete.add(A); group.poll(); backend.reject = A;
        assertEquals("group_member_1:unavailable", group.poll().result().detail());
        assertTrue(backend.running.isEmpty()); assertTrue(backend.leases.isEmpty());
    }
    @Test void changedReceiptAndPollingFailureBothCleanEveryMember() {
        for (boolean throwing : List.of(false, true)) {
            var backend = new Backend(); var group = new ProgramStructureGroupControl(List.of(A, B), backend); group.start();
            backend.throwStatus = throwing; backend.changedReceipt = !throwing;
            if (throwing) assertThrows(IllegalStateException.class, group::poll);
            else assertEquals("group_member_1:orientation_receipt_changed", group.poll().result().detail());
            assertTrue(backend.running.isEmpty()); assertTrue(backend.leases.isEmpty());
        }
    }
    @Test void cancellationRetainsSuccessorAndAttemptsAllReleasesEvenWhenOneThrows() {
        var backend = new Backend(); var group = new ProgramStructureGroupControl(List.of(A, B), backend); group.start();
        var successor = UUID.randomUUID(); backend.leases.put(B, successor); backend.throwRelease = B;
        assertThrows(IllegalStateException.class, group::close);
        assertEquals(Map.of(B, successor), backend.leases);
        assertTrue(backend.events.contains("release:1"));
        group.close(); assertTrue(group.finished());
    }

    private static ProgramStructureReference target(int number) {
        return new ProgramStructureReference(new StructureRef(new UUID(0, number), Identifier.parse("minecraft:overworld"), Identifier.parse("academy:test"), 1), new UUID(1, number));
    }
    private static final class Backend implements ProgramStructureGroupControl.Backend {
        final Map<ProgramStructureReference, UUID> leases = new HashMap<>(), running = new HashMap<>();
        final Set<ProgramStructureReference> complete = new HashSet<>();
        final List<String> events = new ArrayList<>();
        ProgramStructureReference reject, throwAcquire, failStart, throwRelease;
        boolean changedReceipt, throwStatus, throwStart;
        private String event(String action, ProgramStructureGroupControl.Member member) { return action + ":" + member.target().reference().id().getLeastSignificantBits(); }
        @Override public String rejection(ProgramStructureGroupControl.Member member, boolean acquired) {
            return member.target().equals(reject) ? "unavailable" : null;
        }
        @Override public boolean acquire(ProgramStructureGroupControl.Member member) {
            events.add(event("acquire", member));
            if (leases.containsKey(member.target())) return false;
            leases.put(member.target(), member.controlId());
            if (member.target().equals(throwAcquire)) throw new IllegalStateException("acquisition failure after taking the lease");
            return true;
        }
        @Override public Operation start(ProgramStructureGroupControl.Member member) {
            events.add(event("start", member));
            if (member.target().equals(failStart)) return Operation.rejected("denied");
            var id = UUID.randomUUID(); running.put(member.target(), id);
            if (throwStart) throw new IllegalStateException("submission failed after starting work");
            return Operation.pending(id, "started");
        }
        @Override public Operation status(ProgramStructureGroupControl.Member member, UUID receipt) {
            assertEquals(running.get(member.target()), receipt);
            if (throwStatus) throw new IllegalStateException("poll failed");
            return complete.contains(member.target()) ? Operation.completed("done") : Operation.pending(changedReceipt ? UUID.randomUUID() : receipt, "running");
        }
        @Override public void release(ProgramStructureGroupControl.Member member) {
            events.add(event("release", member));
            if (leases.remove(member.target(), member.controlId())) running.remove(member.target());
            if (member.target().equals(throwRelease)) throw new IllegalStateException("cleanup failed");
        }
    }
}
