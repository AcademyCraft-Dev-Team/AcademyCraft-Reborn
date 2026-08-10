package org.academy.api.common.profiler;

public class FrameStatsSnapshot {
    private final int size;
    private final double lastFrameMs;
    private final double avgMs;
    private final double minMs;
    private final double maxMs;
    private final double p99Ms;
    private final double fps;
    private final double[] frameTimesMs;
    private final double[] heapUsedMb;

    public FrameStatsSnapshot(int size, double lastFrameMs, double avgMs, double minMs, double maxMs,
                              double p99Ms, double fps, double[] frameTimesMs, double[] heapUsedMb) {
        this.size = size;
        this.lastFrameMs = lastFrameMs;
        this.avgMs = avgMs;
        this.minMs = minMs;
        this.maxMs = maxMs;
        this.p99Ms = p99Ms;
        this.fps = fps;
        this.frameTimesMs = frameTimesMs;
        this.heapUsedMb = heapUsedMb;
    }

    public int getSize() {
        return size;
    }

    public double getLastFrameMs() {
        return lastFrameMs;
    }

    public double getAvgMs() {
        return avgMs;
    }

    public double getMinMs() {
        return minMs;
    }

    public double getMaxMs() {
        return maxMs;
    }

    public double getP99Ms() {
        return p99Ms;
    }

    public double getFps() {
        return fps;
    }

    public double[] getFrameTimesMs() {
        return frameTimesMs;
    }

    public double[] getHeapUsedMb() {
        return heapUsedMb;
    }
}
