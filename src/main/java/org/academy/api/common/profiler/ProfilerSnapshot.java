package org.academy.api.common.profiler;

import java.util.Map;
import org.jspecify.annotations.Nullable;

public class ProfilerSnapshot {
    private final Map<String, ZoneSnapshot> zones;
    private final @Nullable SamplerSnapshot sampler;
    private final FrameStatsSnapshot frame;
    public final boolean zonesEnabled;
    public final boolean sampling;
    public final boolean samplingPaused;

    public ProfilerSnapshot(Map<String, ZoneSnapshot> zones, @Nullable SamplerSnapshot sampler, FrameStatsSnapshot frame,
                            boolean zonesEnabled, boolean sampling, boolean samplingPaused) {
        this.zones = zones;
        this.sampler = sampler;
        this.frame = frame;
        this.zonesEnabled = zonesEnabled;
        this.sampling = sampling;
        this.samplingPaused = samplingPaused;
    }

    public Map<String, ZoneSnapshot> getZones() {
        return zones;
    }

    public @Nullable SamplerSnapshot getSampler() {
        return sampler;
    }

    public FrameStatsSnapshot getFrame() {
        return frame;
    }

    public boolean isZonesEnabled() {
        return zonesEnabled;
    }

    public boolean isSampling() {
        return sampling;
    }

    public boolean isSamplingPaused() {
        return samplingPaused;
    }
}
