package org.academy.api.common.profiler;

import java.util.List;
import java.util.function.Supplier;

public final class AcademyProfiler {
    private static final long SNAPSHOT_TTL_NANOS = 250_000_000L;

    private static volatile long cachedSnapshotAt = 0L;
    private static volatile ProfilerSnapshot cachedSnapshot = null;

    private AcademyProfiler() {
    }

    public static void push(String name) {
        ZoneProfiler.push(name);
    }

    public static void pop() {
        ZoneProfiler.pop();
    }

    public static void popPush(String name) {
        ZoneProfiler.popPush(name);
    }

    public static <T> T zone(String name, Supplier<T> block) {
        ZoneProfiler.push(name);
        try {
            return block.get();
        } finally {
            ZoneProfiler.pop();
        }
    }

    public static void runZone(String name, Runnable block) {
        ZoneProfiler.push(name);
        try {
            block.run();
        } finally {
            ZoneProfiler.pop();
        }
    }

    public static void incrementCounter(String name, int amount) {
        ZoneProfiler.incrementCounter(name, amount);
    }

    public static void incrementCounter(String name) {
        ZoneProfiler.incrementCounter(name, 1);
    }

    public static boolean isCapturingZones() {
        return ZoneProfiler.isEnabled();
    }

    public static void startZoneCapture() {
        ZoneProfiler.setEnabled(true);
        invalidateSnapshot();
    }

    public static void stopZoneCapture() {
        ZoneProfiler.setEnabled(false);
        invalidateSnapshot();
    }

    public static void resetZones() {
        ZoneProfiler.reset();
        invalidateSnapshot();
    }

    public static boolean isSampling() {
        return ProfilerSampler.isRunning();
    }

    public static boolean isSamplingPaused() {
        return ProfilerSampler.isPaused();
    }

    public static void startSampling() {
        ProfilerSampler.start(1000L);
        invalidateSnapshot();
    }

    public static void startSampling(long intervalMicros) {
        ProfilerSampler.start(intervalMicros);
        invalidateSnapshot();
    }

    public static void stopSampling() {
        ProfilerSampler.stop();
        invalidateSnapshot();
    }

    public static void pauseSampling() {
        ProfilerSampler.pause();
        invalidateSnapshot();
    }

    public static void resumeSampling() {
        ProfilerSampler.resume();
        invalidateSnapshot();
    }

    public static void resetSampling() {
        ProfilerSampler.reset();
        invalidateSnapshot();
    }

    public static ProfilerSampler.ThreadRef registerThread(Thread thread) {
        return ProfilerSampler.registerThread(thread);
    }

    public static void unregisterThread(Thread thread) {
        ProfilerSampler.unregisterThread(thread.threadId());
    }

    public static void setThreadEnabled(long threadId, boolean enabled) {
        ProfilerSampler.setThreadEnabled(threadId, enabled);
    }

    public static List<ProfilerSampler.ThreadRef> samplerThreads() {
        return ProfilerSampler.threadRefs();
    }

    private static void invalidateSnapshot() {
        cachedSnapshot = null;
        cachedSnapshotAt = 0L;
    }

    public static ProfilerSnapshot snapshot() {
        long now = System.nanoTime();
        ProfilerSnapshot snap = cachedSnapshot;
        if (snap != null && now - cachedSnapshotAt < SNAPSHOT_TTL_NANOS) {
            return snap;
        }
        ProfilerSnapshot fresh = buildSnapshot();
        cachedSnapshot = fresh;
        cachedSnapshotAt = System.nanoTime();
        return fresh;
    }

    private static ProfilerSnapshot buildSnapshot() {
        return new ProfilerSnapshot(
                ZoneProfiler.snapshot(),
                ProfilerSampler.hasData() ? ProfilerSampler.snapshot() : null,
                FrameStats.snapshot(),
                ZoneProfiler.isEnabled(),
                ProfilerSampler.isRunning(),
                ProfilerSampler.isPaused()
        );
    }
}
