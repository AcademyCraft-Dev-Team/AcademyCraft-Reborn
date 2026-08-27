package org.academy.api.common.profiler;

import java.util.Map;

public record ZoneSlice(String name, String path, long totalNs, long selfNs, long count, long maxNs, long rootTotalNs,
                        int color, Map<String, Long> counters) {

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
