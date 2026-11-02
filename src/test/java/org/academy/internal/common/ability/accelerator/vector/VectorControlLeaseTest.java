package org.academy.internal.common.ability.accelerator.vector;

import org.academy.api.server.ability.AbilityExecutionHandle;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class VectorControlLeaseTest {
    @Test
    void programAndRuntimeTicksDoNotApplyTwiceInOneFrame() {
        var clock = new AtomicLong(30);
        var ticks = new AtomicInteger();
        var stops = new AtomicInteger();
        var releases = new AtomicInteger();
        var lease = new VectorControlLease(30, 3, clock::get, () -> true,
                delegate(ticks, stops), releases::incrementAndGet);

        assertTrue(lease.tick());
        assertTrue(lease.tick());
        assertEquals(1, ticks.get());
        clock.set(31);
        assertTrue(lease.tick());
        assertEquals(2, ticks.get());
        clock.set(33);
        assertFalse(lease.tick());
        lease.stop();
        assertFalse(lease.tick());
        assertEquals(2, ticks.get());
        assertEquals(1, stops.get());
        assertEquals(1, releases.get());
        assertEquals("expired", lease.termination());
    }

    @Test
    void authorizationLossInSameFrameCancelsBeforeAnotherEffect() {
        var clock = new AtomicLong(10);
        var allowed = new AtomicBoolean(true);
        var ticks = new AtomicInteger();
        var stops = new AtomicInteger();
        var lease = new VectorControlLease(10, 20, clock::get, allowed::get,
                delegate(ticks, stops), () -> {});
        assertTrue(lease.tick());
        allowed.set(false);
        assertFalse(lease.tick());
        assertEquals(1, ticks.get());
        assertEquals(1, stops.get());
        assertEquals("admission_lost", lease.termination());
    }

    @Test
    void delegateFailureAndCleanupFailureStillReleaseExactlyOneClaim() {
        var releases = new AtomicInteger();
        var stops = new AtomicInteger();
        var lease = new VectorControlLease(10, 20, () -> 10, () -> true,
                new AbilityExecutionHandle() {
                    public boolean tick() { throw new IllegalStateException("foreign tick"); }
                    public void stop() {
                        stops.incrementAndGet();
                        throw new IllegalStateException("foreign cleanup");
                    }
                }, releases::incrementAndGet);

        assertFalse(lease.tick());
        lease.stop();
        assertEquals(1, releases.get());
        assertEquals(1, stops.get());
        assertTrue(lease.termination().contains("provider_failed"));
        assertTrue(lease.termination().contains("cleanup_failed"));
    }

    @Test
    void completedProviderAndExplicitCancellationCannotResume() {
        var ticks = new AtomicInteger();
        var lease = new VectorControlLease(0, 20, () -> 0, () -> true,
                new AbilityExecutionHandle() {
                    public boolean tick() { ticks.incrementAndGet(); return false; }
                    public void stop() {}
                }, () -> {});
        assertFalse(lease.tick());
        assertFalse(lease.tick());
        assertEquals(1, ticks.get());
        assertEquals("provider_completed", lease.termination());

        var canceled = new VectorControlLease(0, 20, () -> 0, () -> true,
                delegate(ticks, new AtomicInteger()), () -> {});
        canceled.stop();
        assertFalse(canceled.tick());
        assertEquals(1, ticks.get());
    }

    @Test
    void checkFailureClosesForeignHandleWithoutApplyingIt() {
        var ticks = new AtomicInteger();
        var stops = new AtomicInteger();
        var lease = new VectorControlLease(0, 20, () -> 0,
                () -> { throw new IllegalArgumentException("unloaded provider state"); },
                delegate(ticks, stops), () -> {});
        assertFalse(lease.tick());
        assertEquals(0, ticks.get());
        assertEquals(1, stops.get());
    }

    private static AbilityExecutionHandle delegate(AtomicInteger ticks, AtomicInteger stops) {
        return new AbilityExecutionHandle() {
            public boolean tick() { ticks.incrementAndGet(); return true; }
            public void stop() { stops.incrementAndGet(); }
        };
    }
}
