package org.academy.internal.common.music;

import java.util.ArrayDeque;

/**
 * 滑动窗口限流器（共享账号直链解析等外呼场景），时钟可注入便于测试喵。
 */
public final class SlidingWindowRateLimiter {
    private final int limit;
    private final long windowMillis;
    private final ArrayDeque<Long> stamps = new ArrayDeque<>();

    public SlidingWindowRateLimiter(int limit, long windowMillis) {
        this.limit = Math.max(1, limit);
        this.windowMillis = Math.max(1, windowMillis);
    }

    public boolean tryAcquire(long nowMillis) {
        evictExpired(nowMillis);
        if (stamps.size() >= limit) return false;
        stamps.addLast(nowMillis);
        return true;
    }

    public int currentCount(long nowMillis) {
        evictExpired(nowMillis);
        return stamps.size();
    }

    private void evictExpired(long nowMillis) {
        while (!stamps.isEmpty() && nowMillis - stamps.peekFirst() >= windowMillis) {
            stamps.pollFirst();
        }
    }
}
