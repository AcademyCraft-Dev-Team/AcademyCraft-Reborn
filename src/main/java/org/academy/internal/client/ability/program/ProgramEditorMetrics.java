package org.academy.internal.client.ability.program;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Opt-in screen-local work counters and elapsed times; no graph, world or drawing resource is retained. */
public final class ProgramEditorMetrics {
    private static final int FRAME_CAPACITY = 4096;
    private final long[] frames = new long[FRAME_CAPACITY];
    private final long[] counters = new long[Counter.values().length];
    private boolean enabled;
    private long frameCount;
    private long frameNanos;

    public enum Counter {
        NODES_REQUESTS, NODE_VIEWS_REBUILT, SCHEMA_DECODES, PALETTE_REBUILT,
        PALETTE_REQUESTS, VALIDATION_REQUESTS, VALIDATION_REBUILT,
        NODES_DRAWN, EDGES_DRAWN, NODE_CACHE_HITS, PALETTE_CACHE_HITS,
        VALIDATION_CACHE_HITS, NODES_CULLED, EDGES_CULLED;

        private final String key = name().toLowerCase(Locale.ROOT);

        public String key() { return key; }
    }

    public void reset(boolean enabled) {
        this.enabled = enabled;
        frameCount = 0;
        frameNanos = 0;
        Arrays.fill(counters, 0);
    }

    public void increment(Counter counter) {
        if (enabled) counters[counter.ordinal()]++;
    }

    public long beginFrame() {
        return enabled ? System.nanoTime() : 0;
    }

    public void endFrame(long started) {
        if (!enabled || started == 0) return;
        long elapsed = System.nanoTime() - started;
        frames[(int) (frameCount % FRAME_CAPACITY)] = elapsed;
        frameCount++;
        frameNanos += elapsed;
    }

    /** Percentiles cover the most recent bounded window; totals cover the complete capture. */
    public Map<String, Long> snapshot() {
        var result = new LinkedHashMap<String, Long>();
        result.put("frames", frameCount);
        result.put("frame_nanos_total", frameNanos);
        int count = (int) Math.min(frameCount, FRAME_CAPACITY);
        var sorted = Arrays.copyOf(frames, count);
        Arrays.sort(sorted);
        result.put("frame_samples", (long) count);
        result.put("frame_sample_capacity", (long) FRAME_CAPACITY);
        result.put("frame_p50_nanos", percentile(sorted, .50));
        result.put("frame_p95_nanos", percentile(sorted, .95));
        result.put("frame_p99_nanos", percentile(sorted, .99));
        for (var counter : Counter.values()) result.put(counter.key(), counters[counter.ordinal()]);
        return Map.copyOf(result);
    }

    private static long percentile(long[] sorted, double quantile) {
        return sorted.length == 0 ? 0 : sorted[(int) Math.ceil(sorted.length * quantile) - 1];
    }
}
