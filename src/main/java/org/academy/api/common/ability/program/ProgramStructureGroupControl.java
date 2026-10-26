package org.academy.api.common.ability.program;

import org.jspecify.annotations.Nullable;

import java.util.*;

import static org.academy.api.common.ability.program.ProgramStructureOrientationRuntime.Operation;

/**
 * Bounded, reusable group orchestration. The backend owns actor checks, costs and real physics.
 * All members are checked and leased before any command starts. Later failure cancels remaining
 * commands; it never rewinds physical motion. Release must be idempotent and exact-token scoped.
 */
public final class ProgramStructureGroupControl implements AutoCloseable {
    public static final int MAX_MEMBERS = 8;
    private final UUID receipt = UUID.randomUUID();
    private final Backend backend;
    private final List<Member> members;
    private final List<Member> attempted = new ArrayList<>();
    private final Map<Member, UUID> pending = new LinkedHashMap<>();
    private boolean started;
    private boolean workStarted;
    private @Nullable Operation terminal;

    public ProgramStructureGroupControl(List<ProgramStructureReference> targets, Backend backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
        members = canonical(targets).stream().map(target -> new Member(target, UUID.randomUUID())).toList();
    }

    public static List<ProgramStructureReference> canonical(List<ProgramStructureReference> targets) {
        if (targets.size() > 128) throw new IllegalArgumentException("group_input_budget");
        var values = List.copyOf(new LinkedHashSet<>(targets));
        if (values.size() > MAX_MEMBERS) throw new IllegalArgumentException("group_member_budget");
        var identities = new HashMap<org.academy.api.common.structure.StructureRef, UUID>();
        for (var value : values) {
            var previous = identities.put(value.reference(), value.runtimeId());
            if (previous != null && !previous.equals(value.runtimeId())) throw new IllegalArgumentException("group_conflicting_epochs");
        }
        return values;
    }

    public UUID receipt() { return receipt; }
    public List<Member> members() { return members; }
    public boolean finished() { return terminal != null; }
    /** Accepted work, or a command callback that threw after it may have issued work, is not refundable. */
    public boolean workStarted() { return workStarted; }

    public Operation start() {
        if (started) return current();
        started = true;
        try {
            for (var member : members) {
                var rejection = backend.rejection(member, false);
                if (rejection != null) return finish(rejected(member, rejection));
            }
            for (var member : members) {
                // A throwing acquisition may already have acquired its token: include it in cleanup.
                attempted.add(member);
                if (!backend.acquire(member)) return finish(rejected(member, "control_occupied_or_unavailable"));
            }
            for (var member : members) {
                boolean previousWork = workStarted;
                workStarted = true;
                var result = Objects.requireNonNull(backend.start(member), "group member operation");
                if (!accepted(result)) { workStarted = previousWork; return finish(memberFailure(member, result)); }
                if (result.operationId() != null && result.result().status() == ProgramStructureOperationResult.Status.PENDING) {
                    pending.put(member, result.operationId());
                }
            }
            return pending.isEmpty() ? finish(Operation.completed(members.isEmpty() ? "group_empty" : "group_completed")) : current();
        } catch (RuntimeException | Error failure) {
            cleanupAfter(failure);
            throw failure;
        }
    }

    public Operation poll() {
        if (!started) throw new IllegalStateException("group_not_started");
        if (terminal != null) return terminal;
        try {
            // Completed members remain part of the group and must not disappear unnoticed.
            for (var member : members) {
                var rejection = backend.rejection(member, true);
                if (rejection != null) return finish(rejected(member, rejection));
            }
            for (var entry : List.copyOf(pending.entrySet())) {
                var result = Objects.requireNonNull(backend.status(entry.getKey(), entry.getValue()), "group member status");
                if (result.operationId() != null && !result.operationId().equals(entry.getValue())) {
                    return finish(rejected(entry.getKey(), "orientation_receipt_changed"));
                }
                if (!accepted(result)) return finish(memberFailure(entry.getKey(), result));
                if (result.result().status() == ProgramStructureOperationResult.Status.COMPLETED) pending.remove(entry.getKey());
            }
            return pending.isEmpty() ? finish(Operation.completed("group_completed")) : current();
        } catch (RuntimeException | Error failure) {
            cleanupAfter(failure);
            throw failure;
        }
    }

    private Operation current() { return terminal != null ? terminal : Operation.pending(receipt, "group_running"); }
    private Operation rejected(Member member, String reason) { return memberFailure(member, Operation.rejected(reason)); }
    private Operation memberFailure(Member member, Operation result) {
        var detail = "group_member_" + (members.indexOf(member) + 1) + ":" + result.result().detail();
        return new Operation(new ProgramStructureOperationResult(result.result().status(), detail, null), null);
    }
    private Operation finish(Operation result) { terminal = result; releaseAll(); return result; }
    private void cleanupAfter(Throwable failure) {
        terminal = Operation.rejected("group_exception");
        try { releaseAll(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
    }
    private void releaseAll() {
        RuntimeException failure = null;
        for (var member : attempted.reversed()) {
            try { backend.release(member); }
            catch (RuntimeException error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        }
        attempted.clear(); pending.clear();
        if (failure != null) throw failure;
    }
    @Override public void close() {
        started = true;
        if (terminal == null) terminal = Operation.rejected("group_cancelled");
        releaseAll();
    }
    public static boolean accepted(Operation operation) {
        return operation.result().status() == ProgramStructureOperationResult.Status.PENDING
                || operation.result().status() == ProgramStructureOperationResult.Status.COMPLETED;
    }

    public record Member(ProgramStructureReference target, UUID controlId) {
        public Member { Objects.requireNonNull(target, "target"); Objects.requireNonNull(controlId, "controlId"); }
    }
    public interface Backend {
        @Nullable String rejection(Member member, boolean acquired);
        boolean acquire(Member member);
        Operation start(Member member);
        Operation status(Member member, UUID receipt);
        void release(Member member);
    }
}
