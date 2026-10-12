package org.academy.api.common.structure;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/** Bounded, thread-local coordinate scopes that never move or mutate a real player. */
public final class StructureContexts {
    public static final int MAX_DEPTH = 16;
    private static final ThreadLocal<ArrayDeque<SpaceContext>> CONTEXTS = new ThreadLocal<>();

    private StructureContexts() {
    }

    public static Optional<SpaceContext> current() {
        var contexts = CONTEXTS.get();
        return contexts == null ? Optional.empty() : Optional.ofNullable(contexts.peek());
    }

    /**
     * Runs synchronously with a snapshot of an active server-thread handle. Scopes can nest,
     * including across structures; completion or failure always restores the caller's scope.
     * Ordinary world queries are not redirected and no actor coordinates are temporarily changed.
     */
    public static <T> T call(StructureHandle handle, Function<SpaceContext, T> operation) {
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(operation, "operation");
        // Real backend handles enforce their owning server thread at these accessors.
        if (handle.lifecycle() != StructureLifecycle.ACTIVE) {
            throw new IllegalStateException("Structure context requires an active handle");
        }
        var context = new SpaceContext(handle.reference(), handle.runtimeId(), handle.pose(), handle.world());
        var contexts = CONTEXTS.get();
        if (contexts == null) {
            contexts = new ArrayDeque<>();
            CONTEXTS.set(contexts);
        }
        if (contexts.size() >= MAX_DEPTH) throw new IllegalStateException("Structure context nesting limit exceeded");
        contexts.push(context);
        try {
            return operation.apply(context);
        } finally {
            contexts.pop();
            if (contexts.isEmpty()) CONTEXTS.remove();
        }
    }

    public static void run(StructureHandle handle, Consumer<SpaceContext> operation) {
        Objects.requireNonNull(operation, "operation");
        call(handle, context -> {
            operation.accept(context);
            return null;
        });
    }
}
