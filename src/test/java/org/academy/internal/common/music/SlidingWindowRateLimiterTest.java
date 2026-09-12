package org.academy.internal.common.music;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlidingWindowRateLimiterTest {
    @Test
    void allowsUpToLimitInsideWindow() {
        var limiter = new SlidingWindowRateLimiter(3, 1000L);
        assertTrue(limiter.tryAcquire(0L));
        assertTrue(limiter.tryAcquire(100L));
        assertTrue(limiter.tryAcquire(200L));
        assertFalse(limiter.tryAcquire(300L));
        assertEquals(3, limiter.currentCount(300L));
    }

    @Test
    void oldStampsExpireWithWindow() {
        var limiter = new SlidingWindowRateLimiter(2, 1000L);
        assertTrue(limiter.tryAcquire(0L));
        assertTrue(limiter.tryAcquire(500L));
        assertFalse(limiter.tryAcquire(600L));
        // 第一票在 t=1000 时过期（>= 窗口）
        assertTrue(limiter.tryAcquire(1000L));
        assertFalse(limiter.tryAcquire(1100L));
        // 第二票（t=500）在 t=1500 过期
        assertTrue(limiter.tryAcquire(1500L));
    }

    @Test
    void currentCountEvictsExpiredOnly() {
        var limiter = new SlidingWindowRateLimiter(5, 100L);
        limiter.tryAcquire(0L);
        limiter.tryAcquire(50L);
        limiter.tryAcquire(99L);
        // t=150 时：t=0（150≥100）与 t=50（100≥100）均已过期，仅剩 t=99
        assertEquals(1, limiter.currentCount(150L));
    }
}
