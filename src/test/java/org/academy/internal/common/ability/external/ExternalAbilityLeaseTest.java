package org.academy.internal.common.ability.external;

import org.academy.api.server.ability.AbilityExecutionHandle;
import org.academy.api.server.ability.external.ExternalAbilityExecution;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ExternalAbilityLeaseTest {
    @Test void missedTicksStillExpireAndRepeatedTicksDoNotRepeatNativeEffects() {
        var clock = new AtomicLong(10);
        var ticks = new AtomicInteger();
        var stops = new AtomicInteger();
        var lease = new ExternalAbilityLease(clock::get, () -> {}, () -> "", delegate(ticks, stops), 20);
        assertTrue(lease.tick());
        assertTrue(lease.tick());
        assertEquals(1, ticks.get());
        clock.set(30);
        assertFalse(lease.tick());
        assertEquals(1, ticks.get());
        lease.stop();
        assertEquals(1, stops.get());
        assertEquals(ExternalAbilityExecution.State.COMPLETED, lease.state());
    }

    @Test void sameFrameKnowledgeOrBindingLossStopsBeforeMoreEffects() {
        var reason = new AtomicReference<>("");
        var ticks = new AtomicInteger();
        var stops = new AtomicInteger();
        var lease = new ExternalAbilityLease(() -> 20, () -> {}, reason::get, delegate(ticks, stops), 20);
        assertTrue(lease.tick());
        reason.set("binding_stale");
        assertFalse(lease.tick());
        assertEquals(1, ticks.get());
        assertEquals(1, stops.get());
        assertEquals("binding_stale", lease.detail());
        assertEquals(ExternalAbilityExecution.State.FAILED, lease.state());
    }

    @Test void aFailedNativePhaseRemainsDiagnosableAndCleanupRunsOnce() {
        var stops = new AtomicInteger();
        var lease = new ExternalAbilityLease(() -> 1, () -> {}, () -> "", new AbilityExecutionHandle() {
            @Override public boolean tick() { throw new IllegalStateException("native phase failed"); }
            @Override public void stop() { stops.incrementAndGet(); throw new IllegalStateException("cleanup failed"); }
        }, 20);
        assertFalse(lease.tick());
        lease.stop();
        assertFalse(lease.tick());
        assertEquals(1, stops.get());
        assertTrue(lease.detail().contains("native phase failed"));
        assertTrue(lease.detail().contains("cleanup failed"));
        assertEquals(ExternalAbilityExecution.State.FAILED, lease.state());
    }

    @Test void wrongThreadCannotTickOrConsumeCancellation() {
        var stops = new AtomicInteger();
        var allowed = new AtomicReference<>(false);
        var lease = new ExternalAbilityLease(() -> 0, () -> { if (!allowed.get()) throw new IllegalStateException("wrong thread"); },
                () -> "", delegate(new AtomicInteger(), stops), 20);
        assertThrows(IllegalStateException.class, lease::tick);
        assertThrows(IllegalStateException.class, lease::stop);
        assertEquals(0, stops.get());
        allowed.set(true);
        lease.stop();
        lease.stop();
        assertEquals(1, stops.get());
        assertEquals(ExternalAbilityExecution.State.CANCELLED, lease.state());
    }

    private static AbilityExecutionHandle delegate(AtomicInteger ticks, AtomicInteger stops) {
        return new AbilityExecutionHandle() {
            @Override public boolean tick() { ticks.incrementAndGet(); return true; }
            @Override public void stop() { stops.incrementAndGet(); }
        };
    }
}
