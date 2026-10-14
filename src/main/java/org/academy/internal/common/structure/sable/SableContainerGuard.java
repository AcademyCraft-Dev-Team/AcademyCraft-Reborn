package org.academy.internal.common.structure.sable;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Server-thread guard that keeps Academy-owned Sable container mutations out of
 * {@code SubLevelContainer.tick} iteration. Additions and removals requested from inside a
 * container callback are deferred to the end of the server tick; the one synchronous
 * operation (capture allocation) fails closed instead of mutating mid-iteration.
 *
 * <p>The compatibility mixin marks each container callback with {@link #enterContainerTick()}
 * and {@link #exitContainerTick()}; without the mixin the guard simply stays inactive and
 * operations run inline on a verified Sable version the mixin does not touch.</p>
 */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class SableContainerGuard {
    private static final ThreadLocal<State> STATE = ThreadLocal.withInitial(State::new);
    private static final AtomicLong ENTERED_CALLBACKS = new AtomicLong();

    private SableContainerGuard() {
    }

    /** Called by the version-gated Sable container tick mixin around one callback. */
    public static void enterContainerTick() {
        ENTERED_CALLBACKS.incrementAndGet();
        var state = STATE.get();
        state.depth = Math.incrementExact(state.depth);
    }

    /** Called by the version-gated Sable container tick mixin around one callback. */
    public static void exitContainerTick() {
        var state = STATE.get();
        if (state.depth <= 0) throw new IllegalStateException("Unbalanced Sable container tick guard");
        state.depth--;
    }

    public static boolean inContainerTick() {
        return STATE.get().depth > 0;
    }

    /** Validation counter: proves the compatibility mixin is marking real container callbacks. */
    public static long enteredCallbacks() {
        return ENTERED_CALLBACKS.get();
    }

    /**
     * Runs a container mutation now, or queues it for {@link #drainDeferred()} when a Sable
     * container tick is on the stack.
     *
     * @return {@code true} when the mutation ran inline
     */
    public static boolean containerMutation(String description, Runnable mutation) {
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(mutation, "mutation");
        var state = STATE.get();
        if (state.depth > 0) {
            state.deferred.addLast(new DeferredMutation(description, mutation));
            AcademyCraft.LOGGER.info("Deferred Sable container mutation to the end of the server tick: {}",
                    description);
            return false;
        }
        mutation.run();
        return true;
    }

    /**
     * Allocation is synchronous by contract and cannot be deferred; a request that arrives
     * during container tick iteration is refused so no caller observes a half-created body.
     */
    public static <T> Optional<T> containerAllocation(String description, Supplier<T> allocation) {
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(allocation, "allocation");
        if (inContainerTick()) {
            AcademyCraft.LOGGER.error(
                    "Refused a Sable container allocation during container tick iteration: {}", description);
            return Optional.empty();
        }
        return Optional.ofNullable(allocation.get());
    }

    /** Runs every deferred mutation in order; one failure never blocks the rest. */
    public static int drainDeferred() {
        var state = STATE.get();
        if (state.depth > 0) {
            throw new IllegalStateException("Cannot drain Sable container mutations inside a container tick");
        }
        var drained = 0;
        while (true) {
            var mutation = state.deferred.pollFirst();
            if (mutation == null) return drained;
            try {
                mutation.mutation.run();
                drained++;
            } catch (RuntimeException | LinkageError failure) {
                AcademyCraft.LOGGER.error("Deferred Sable container mutation failed: {}",
                        mutation.description, failure);
            }
        }
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        drainDeferred();
    }

    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) {
        STATE.get().deferred.clear();
    }

    private record DeferredMutation(String description, Runnable mutation) {
    }

    private static final class State {
        private final Deque<DeferredMutation> deferred = new ArrayDeque<>();
        private int depth;
    }
}
