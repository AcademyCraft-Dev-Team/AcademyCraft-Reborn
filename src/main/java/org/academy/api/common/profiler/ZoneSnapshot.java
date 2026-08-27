package org.academy.api.common.profiler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class ZoneSnapshot {
    private final String threadName;
    private final Map<String, ZoneSlice> slices;
    private final long rootTotalNs;

    public ZoneSnapshot(String threadName, Map<String, ZoneSlice> slices, long rootTotalNs) {
        this.threadName = threadName;
        this.slices = slices;
        this.rootTotalNs = rootTotalNs;
    }

    public String getThreadName() {
        return threadName;
    }

    public long getRootTotalNs() {
        return rootTotalNs;
    }

    public ZoneSlice getRoot() {
        return slices.get(ZoneProfiler.ROOT);
    }

    public ZoneSlice sliceAt(String path) {
        return slices.get(path);
    }

    public List<ZoneSlice> childrenOf(String path) {
        List<ZoneSlice> children = new ArrayList<>();
        for (var slice : slices.values()) {
            if (isDirectChild(path, slice.path())) {
                children.add(slice);
            }
        }
        children.sort(Comparator.comparingLong(ZoneSlice::totalNs).reversed());
        return children;
    }

    public List<ZoneSlice> topSlices(int limit, boolean excludeRoot) {
        List<ZoneSlice> top = new ArrayList<>();
        for (var slice : slices.values()) {
            if (excludeRoot && slice.path().equals(ZoneProfiler.ROOT)) {
                continue;
            }
            top.add(slice);
        }
        top.sort(Comparator.comparingLong(ZoneSlice::totalNs).reversed());
        return top.subList(0, Math.min(limit, top.size()));
    }

    public double parentPercent(ZoneSlice slice) {
        var parentPath = parentPathOf(slice.path());
        var parent = slices.get(parentPath);
        var parentTotal = parent != null ? parent.totalNs() : rootTotalNs;
        return parentTotal > 0 ? slice.totalNs() * 100.0 / parentTotal : 0.0;
    }

    private String parentPathOf(String path) {
        if (path.equals(ZoneProfiler.ROOT)) {
            return ZoneProfiler.ROOT;
        }
        var idx = path.lastIndexOf(ZoneProfiler.PATH_SEPARATOR);
        return idx < 0 ? ZoneProfiler.ROOT : path.substring(0, idx);
    }

    private boolean isDirectChild(String parentPath, String path) {
        if (path.equals(parentPath)) {
            return false;
        }
        var prefix = parentPath + ZoneProfiler.PATH_SEPARATOR;
        if (!path.startsWith(prefix)) {
            return false;
        }
        var rest = path.substring(prefix.length());
        return rest.indexOf(ZoneProfiler.PATH_SEPARATOR) < 0;
    }
}
