"""Summarize CSV measurements from the opt-in dual-client VFX scene."""
import csv
import json
import statistics
import sys
from collections import defaultdict
from pathlib import Path


def percentile(values, fraction):
    values = sorted(values)
    return values[min(len(values) - 1, int((len(values) - 1) * fraction))]


def summarize(directory):
    result = {}
    for role in ("caster", "observer"):
        with (directory / f"{role}-frames.csv").open() as source:
            rows = list(csv.DictReader(source))
        groups = defaultdict(list)
        for row in rows:
            if float(row["phase_seconds"]) >= 1 and row["phase"] != "loading":
                groups[row["phase"]].append(row)
        phases = {}
        for phase, samples in groups.items():
            cpu = [float(row["render_cpu_ms"]) for row in samples]
            gpu = [float(row["gpu_rolling_ms"]) for row in samples]
            phases[phase] = dict(frames=len(samples), cpu_p50_ms=statistics.median(cpu),
                                 cpu_p95_ms=percentile(cpu, .95), gpu_p50_ms=statistics.median(gpu),
                                 graph_visible_mean=statistics.mean(float(row["visible"]) for row in samples),
                                 graph_culled_mean=statistics.mean(float(row["culled"]) for row in samples))
        result[role] = dict(received=int(rows[-1]["received_packets"]),
                            final_active_graphs=int(rows[-1]["active"]), phases=phases)
    (directory / "performance-summary.json").write_text(json.dumps(result, indent=2))
    return result


if __name__ == "__main__":
    print(json.dumps(summarize(Path(sys.argv[1])), indent=2))
