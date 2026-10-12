package org.academy.internal.common.structure.space;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** One-way ownership receipts. A failed publication is evidence, never permission to replay it. */
final class NativeStructureDropLedger<T> {
    enum State { PENDING, ATTEMPTED, PUBLISHED, REJECTED, UNCERTAIN }

    static final class Entry<T> {
        private final T value;
        private State state = State.PENDING;

        private Entry(T value) { this.value = value; }
        T value() { return value; }
        State state() { return state; }
    }

    private final int limit;
    private final List<Entry<T>> entries = new ArrayList<>();
    private final IdentityHashMap<T, Entry<T>> identities = new IdentityHashMap<>();
    private boolean sealed;
    private boolean failed;

    NativeStructureDropLedger(int limit) {
        if (limit < 1) throw new IllegalArgumentException("Drop limit must be positive");
        this.limit = limit;
    }

    void capture(T value) {
        Objects.requireNonNull(value);
        if (identities.containsKey(value)) {
            failed = true;
            throw new IllegalStateException("The same drop was submitted twice");
        }
        var entry = new Entry<>(value);
        identities.put(value, entry);
        entries.add(entry);
        // Retain the triggering value: it may already have been split from a real inventory.
        if (sealed || entries.size() > limit) {
            failed = true;
            throw new IllegalStateException("Native drop output budget exceeded");
        }
    }

    List<Entry<T>> entries() { return List.copyOf(entries); }

    void publish(Predicate<T> publish) {
        if (sealed || failed) throw new IllegalStateException("Native drop output cannot be replayed or published after a failure");
        sealed = true;
        for (var entry : entries) {
            entry.state = State.ATTEMPTED;
            final boolean accepted;
            try {
                accepted = publish.test(entry.value);
            } catch (RuntimeException | Error failure) {
                entry.state = State.UNCERTAIN;
                throw failure;
            }
            entry.state = accepted ? State.PUBLISHED : State.REJECTED;
            if (!accepted) throw new IllegalStateException("Host refused a native structure drop");
        }
    }
}
