package org.academy.api.common.profiler;

import java.util.Arrays;

public final class FrameStats {
    private static final int CAPACITY = 720;
    private static final long[] frameTimesNs = new long[CAPACITY];
    private static final long[] heapBytes = new long[CAPACITY];
    private static int index = 0;
    private static int size = 0;

    private FrameStats() {
    }

    public static synchronized void recordFrame(long frameNs, long heapUsed) {
        frameTimesNs[index] = frameNs;
        heapBytes[index] = heapUsed;
        index = (index + 1) % CAPACITY;
        if (size < CAPACITY) {
            size++;
        }
    }

    public static synchronized FrameStatsSnapshot snapshot() {
        if (size == 0) {
            return new FrameStatsSnapshot(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, new double[0], new double[0]);
        }

        long min = Long.MAX_VALUE;
        long max = 0L;
        long sum = 0L;
        for (int i = 0; i < size; i++) {
            long v = frameTimesNs[i];
            if (v < min) {
                min = v;
            }
            if (v > max) {
                max = v;
            }
            sum += v;
        }

        int recentCount = Math.min(size, 120);
        long recentSum = 0L;
        for (int i = 0; i < recentCount; i++) {
            recentSum += frameTimesNs[(index - 1 - i + CAPACITY * 2) % CAPACITY];
        }

        int lastIndex = (index - 1 + CAPACITY) % CAPACITY;
        long p99 = percentile(0.99);
        double[] frameTimes = new double[size];
        double[] heap = new double[size];
        for (int i = 0; i < size; i++) {
            frameTimes[i] = frameTimesNs[i] / 1e6;
            heap[i] = heapBytes[i] / 1048576.0;
        }

        return new FrameStatsSnapshot(
                size,
                frameTimesNs[lastIndex] / 1e6,
                sum / (double) size / 1e6,
                min == Long.MAX_VALUE ? 0.0 : min / 1e6,
                max / 1e6,
                p99 / 1e6,
                recentSum > 0 ? recentCount * 1e9 / (double) recentSum : 0.0,
                frameTimes,
                heap
        );
    }

    private static long percentile(double q) {
        long[] arr = new long[size];
        for (int i = 0; i < size; i++) {
            arr[i] = frameTimesNs[i];
        }
        Arrays.sort(arr);
        int pos = (int) ((arr.length - 1) * q);
        return arr[pos];
    }
}
