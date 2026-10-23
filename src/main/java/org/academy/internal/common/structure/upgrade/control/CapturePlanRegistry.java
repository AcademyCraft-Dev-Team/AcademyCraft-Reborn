package org.academy.internal.common.structure.upgrade.control;

import org.academy.api.common.structure.upgrade.StructureExtraction;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-owned preview and binding registry for capture planning. A caller receives only the
 * display value of a {@link StructureExtraction.CapturePlan}; the authoritative request, actor,
 * source digest and expiry stay here. One preview binds to exactly one capture/control pair:
 * an identical retry returns the first binding, while reuse with another capture, actor or
 * control is a conflict. Entries are bounded and expire; live previews are never evicted to
 * make room, so capacity failure is reported instead of silently binding stale evidence.
 */
public final class CapturePlanRegistry {
    public static final int MAX_ENTRIES = 64;

    public enum Outcome { READY, MISSING, EXPIRED, DENIED, CONFLICT, REPEATED }

    public enum Registration { REGISTERED, CAPACITY }

    /** A resolved preview; {@code entry} is absent unless the outcome is READY. */
    public record Lookup(Outcome outcome, @Nullable Entry entry) {
        public Lookup {
            Objects.requireNonNull(outcome, "outcome");
            if ((outcome == Outcome.READY) != (entry != null)) {
                throw new IllegalArgumentException("Only a ready lookup carries an entry");
            }
        }

        static Lookup of(Outcome outcome, @Nullable Entry entry) {
            return new Lookup(outcome, entry);
        }
    }

    /** The first stored binding is returned for a repeated identical request. */
    public record Bind(Outcome outcome, StructureExtraction.@Nullable Plan plan) {
        public Bind {
            Objects.requireNonNull(outcome, "outcome");
            if ((outcome == Outcome.READY || outcome == Outcome.REPEATED) != (plan != null)) {
                throw new IllegalArgumentException("Only a bound or repeated outcome carries a plan");
            }
        }

        static Bind of(Outcome outcome, StructureExtraction.@Nullable Plan plan) {
            return new Bind(outcome, plan);
        }
    }

    /** Mutable server-side record; the bound plan is only visible through {@link #bind}. */
    public static final class Entry {
        private final StructureExtraction.CapturePlan preview;
        private final CaptureSourceSnapshot source;
        private long expiresAt;
        private StructureExtraction.@Nullable Plan bound;
        private @Nullable UUID captureOperationId;

        private Entry(StructureExtraction.CapturePlan preview, CaptureSourceSnapshot source) {
            this.preview = preview;
            this.source = source;
            this.expiresAt = preview.expiresAtGameTime();
        }

        public StructureExtraction.CapturePlan preview() {
            return preview;
        }

        /** Authoritative per-cell evidence; it is never part of the public preview value. */
        public CaptureSourceSnapshot source() {
            return source;
        }

        public Optional<UUID> captureOperationId() {
            return Optional.ofNullable(captureOperationId);
        }

        public long expiresAt() {
            return expiresAt;
        }

        public boolean bound() {
            return bound != null;
        }

        public Optional<StructureExtraction.Plan> boundPlan() {
            return Optional.ofNullable(bound);
        }
    }

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    public synchronized Registration register(StructureExtraction.CapturePlan plan, CaptureSourceSnapshot source,
                                              long now) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(source, "source");
        if (!plan.sourceContentDigest().equals(source.digest())) {
            throw new IllegalArgumentException("Preview digest does not match its source snapshot");
        }
        requireTick(now);
        sweep(now);
        if (entries.containsKey(plan.planId())) return Registration.REGISTERED;
        if (entries.size() >= MAX_ENTRIES) return Registration.CAPACITY;
        entries.put(plan.planId(), new Entry(plan, source));
        return Registration.REGISTERED;
    }

    /**
     * Resolves a preview for its owning actor only. Unknown and foreign previews are reported
     * separately so a caller can distinguish a stale retry from an attempt to read or bind
     * somebody else's plan without leaking its existence.
     */
    public synchronized Lookup resolve(UUID planId, UUID actorId, long now) {
        Objects.requireNonNull(planId, "planId");
        Objects.requireNonNull(actorId, "actorId");
        requireTick(now);
        var entry = entries.get(planId);
        if (entry == null) return Lookup.of(Outcome.MISSING, null);
        if (now > entry.expiresAt) {
            entries.remove(planId);
            return Lookup.of(Outcome.EXPIRED, null);
        }
        if (!entry.preview.actorId().equals(actorId)) return Lookup.of(Outcome.DENIED, null);
        return Lookup.of(Outcome.READY, entry);
    }

    /**
     * Stores the first binding for a preview. The plan must keep the preview's identity, actor
     * and control token; anything else is rejected before the binding becomes visible.
     */
    public synchronized Bind bind(UUID actorId, StructureExtraction.Plan plan, UUID captureOperationId,
                                  long now) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(captureOperationId, "captureOperationId");
        requireTick(now);
        var entry = entries.get(plan.planId());
        if (entry == null) return Bind.of(Outcome.MISSING, null);
        if (now > entry.expiresAt) {
            entries.remove(plan.planId());
            return Bind.of(Outcome.EXPIRED, null);
        }
        var preview = entry.preview;
        if (!preview.actorId().equals(actorId)
                || !preview.request().controlId().equals(plan.request().controlId())) {
            return Bind.of(Outcome.DENIED, null);
        }
        if (entry.bound != null) {
            boolean identical = captureOperationId.equals(entry.captureOperationId);
            return identical ? Bind.of(Outcome.REPEATED, entry.bound) : Bind.of(Outcome.CONFLICT, null);
        }
        entry.bound = plan;
        entry.captureOperationId = captureOperationId;
        entry.expiresAt = Math.max(entry.expiresAt, plan.expiresAtGameTime());
        return Bind.of(Outcome.READY, plan);
    }

    /** Drops only expired entries; live bindings keep their own deadline. */
    public synchronized void sweep(long now) {
        requireTick(now);
        entries.values().removeIf(entry -> now > entry.expiresAt);
    }

    public synchronized void clear() {
        entries.clear();
    }

    public synchronized int size() {
        return entries.size();
    }

    private static void requireTick(long tick) {
        if (tick < 0) throw new IllegalArgumentException("Game time cannot be negative");
    }
}
