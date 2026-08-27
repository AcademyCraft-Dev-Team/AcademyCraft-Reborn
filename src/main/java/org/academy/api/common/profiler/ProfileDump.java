package org.academy.api.common.profiler;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class ProfileDump {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private ProfileDump() {
    }

    public static String timestamp() {
        return LocalDateTime.now().format(TIME_FORMAT);
    }

    public static String dumpZones(ZoneSnapshot snapshot, int maxDepth) {
        var sb = new StringBuilder();
        sb.append("== AcademyCraft Zone Profile [thread=")
                .append(snapshot.getThreadName())
                .append("] ==\n");
        var rootTotal = snapshot.getRootTotalNs();
        sb.append("Total: ")
                .append(String.format("%.2f", rootTotal / 1e6))
                .append(" ms\n");
        appendZoneNode(sb, snapshot, ZoneProfiler.ROOT, 0, maxDepth);
        return sb.toString();
    }

    public static String dumpZones(ZoneSnapshot snapshot) {
        return dumpZones(snapshot, 8);
    }

    private static void appendZoneNode(StringBuilder sb, ZoneSnapshot snapshot, String path, int depth, int maxDepth) {
        if (depth > maxDepth) {
            return;
        }
        for (var child : snapshot.childrenOf(path)) {
            var pct = snapshot.parentPercent(child);
            var rootTotal = snapshot.getRootTotalNs();
            var gpct = rootTotal > 0 ? child.totalNs() * 100.0 / rootTotal : 0.0;
            sb.append("  ".repeat(depth))
                    .append(child.name())
                    .append(" - ")
                    .append(String.format("%.2f%% / %.2f%%", pct, gpct))
                    .append(" - ")
                    .append(String.format("%.2f ms", child.getTotalMs()))
                    .append(" (self ")
                    .append(String.format("%.2f ms", child.getSelfMs()))
                    .append(") - ")
                    .append(child.count())
                    .append(" calls - max ")
                    .append(String.format("%.2f ms", child.getMaxMs()))
                    .append('\n');
            if (depth < maxDepth) {
                appendZoneNode(sb, snapshot, child.path(), depth + 1, maxDepth);
            }
        }
    }

    public static String dumpSampler(SamplerSnapshot snapshot, int limit) {
        var sb = new StringBuilder();
        sb.append("== AcademyCraft Sampling Profile ==\n")
                .append("Total samples: ")
                .append(snapshot.totalSamples())
                .append("  Duration: ")
                .append(String.format("%.1f", snapshot.durationSeconds()))
                .append(" s\n");
        for (var view : snapshot.threads().values()) {
            sb.append("-- Thread: ").append(view.name())
                    .append(" (").append(view.samples()).append(" samples)\n");
            Map<String, Long> self = new HashMap<>();
            collectSelf(view.root(), self);
            List<Map.Entry<String, Long>> top = new ArrayList<>(self.entrySet());
            top.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
            var total = view.root().samples();
            for (var i = 0; i < Math.min(limit, top.size()); i++) {
                var entry = top.get(i);
                var pct = total > 0 ? entry.getValue() * 100.0 / total : 0.0;
                sb.append("  ")
                        .append(entry.getKey())
                        .append(" - ")
                        .append(String.format("%.2f%%", pct))
                        .append(" (")
                        .append(entry.getValue())
                        .append(" self samples)\n");
            }
        }
        return sb.toString();
    }

    public static String dumpSampler(SamplerSnapshot snapshot) {
        return dumpSampler(snapshot, 30);
    }

    private static void collectSelf(SampledNode node, Map<String, Long> map) {
        if (!"<root>".equals(node.label())) {
            map.merge(node.label(), node.selfSamples(), Long::sum);
        }
        for (var child : node.children()) {
            collectSelf(child, map);
        }
    }

    public static String status(ProfilerSnapshot snapshot) {
        var sb = new StringBuilder();
        sb.append("== AcademyCraft Profiler Status ==\n")
                .append("Zone capture: ").append(snapshot.isZonesEnabled() ? "ON" : "OFF").append('\n');
        var sampler = snapshot.getSampler();
        sb.append("Sampling: ").append(snapshot.isSampling() ? "ON" : "OFF");
        if (snapshot.isSampling()) {
            sb.append(" (paused: ").append(snapshot.isSamplingPaused() ? "yes" : "no").append(')');
            if (sampler != null) {
                sb.append("\n  samples: ").append(sampler.totalSamples())
                        .append("  duration: ").append(String.format("%.1f", sampler.durationSeconds()))
                        .append(" s");
            }
        }
        sb.append("\nThreads:\n");
        for (var ref : AcademyProfiler.samplerThreads()) {
            sb.append("  ").append(ref.getName()).append(" (id ").append(ref.getId()).append(") ")
                    .append(ref.isEnabled() ? "enabled" : "disabled").append('\n');
        }
        return sb.toString();
    }

    public static String zonesText(ProfilerSnapshot snapshot, String threadName, int maxDepth) {
        var zones = snapshot.getZones();
        if (zones.isEmpty()) {
            var state = snapshot.isZonesEnabled() ? "capture ON" : "capture OFF";
            return "== Zone Profile ==\nNo zone data (" + state + "). " +
                    "Start capture, then trigger the effect, then stop.\n";
        }
        Map<String, ZoneSnapshot> targets;
        if (threadName != null) {
            targets = new LinkedHashMap<>();
            for (var entry : zones.entrySet()) {
                if (threadName.equals(entry.getKey())) {
                    targets.put(entry.getKey(), entry.getValue());
                }
            }
            if (targets.isEmpty()) {
                return "== Zone Profile ==\nThread '" + threadName + "' not found. Available: " +
                        String.join(", ", zones.keySet()) + ".\n";
            }
        } else {
            targets = zones;
        }
        var sb = new StringBuilder();
        for (var entry : targets.entrySet()) {
            sb.append(dumpZones(entry.getValue(), maxDepth)).append('\n');
        }
        return sb.toString();
    }

    public static String samplerText(ProfilerSnapshot snapshot, int topN) {
        var sampler = snapshot.getSampler();
        if (sampler == null) {
            var state = snapshot.isSampling() ? "sampling ON" : "sampling OFF";
            return "== Sampling Profile ==\nNo sampler data (" + state + "). " +
                    "Start sampling, then trigger the effect, then stop.\n";
        }
        return dumpSampler(sampler, topN);
    }
}
