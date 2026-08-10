package org.academy.api.common.profiler;

import java.util.Map;

public class ZoneSlice {
    private final String name;
    private final String path;
    private final long totalNs;
    private final long selfNs;
    private final long count;
    private final long maxNs;
    private final long rootTotalNs;
    private final int color;
    private final Map<String, Long> counters;

    public ZoneSlice(String name, String path, long totalNs, long selfNs, long count, long maxNs,
                     long rootTotalNs, int color, Map<String, Long> counters) {
        this.name = name;
        this.path = path;
        this.totalNs = totalNs;
        this.selfNs = selfNs;
        this.count = count;
        this.maxNs = maxNs;
        this.rootTotalNs = rootTotalNs;
        this.color = color;
        this.counters = counters;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public long getTotalNs() {
        return totalNs;
    }

    public long getSelfNs() {
        return selfNs;
    }

    public long getCount() {
        return count;
    }

    public long getMaxNs() {
        return maxNs;
    }

    public long getRootTotalNs() {
        return rootTotalNs;
    }

    public int getColor() {
        return color;
    }

    public Map<String, Long> getCounters() {
        return counters;
    }

    public double getTotalMs() {
        return totalNs / 1e6;
    }

    public double getSelfMs() {
        return selfNs / 1e6;
    }

    public double getMaxMs() {
        return maxNs / 1e6;
    }

    public double getGlobalPercent() {
        return rootTotalNs > 0 ? totalNs * 100.0 / rootTotalNs : 0.0;
    }
}
