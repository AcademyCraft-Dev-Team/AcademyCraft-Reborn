"""Compare C2 evidence without treating noisy timing values as correctness assertions."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent


def read(name):
    result = json.loads((ROOT / name).read_text(encoding="utf-8-sig"))
    assert result["passed"], f"Failed run: {name}"
    return result


def observed_network(case):
    operations = case["production_network_metrics_measured"]["operations"]
    send = [row for row in operations if row["phase"] == "send"]
    encoded = [row for row in operations if row["phase"] == "encode"]
    assert not any(row["phase"] == "decode" for row in operations), "Observer decode polluted actual receive counts"
    assert all(row["failed"] == 0 for row in operations)
    packets = case["measured_network"]["result.packets"]
    assert sum(row["calls"] for row in send) == sum(row["calls"] for row in encoded) == packets
    assert sum(row["payload_bytes"] for row in encoded) == case["measured_network"]["result.payload_bytes"]
    assert case["measured_network"]["result.misaka_envelope_bytes"] > case["measured_network"]["result.payload_bytes"]


def automatic(before, after):
    assert len(before["cases"]) == len(after["cases"]) == 13
    summaries = []
    for old, new in zip(before["cases"], after["cases"], strict=True):
        assert old["passed"] and new["passed"]
        for field in ("players", "automatic_graphs_per_player", "wait_ticks", "boundary_only",
                      "book_sha256", "book_bytes", "graph_nodes", "graph_edges"):
            assert old[field] == new[field], f"Workload changed: {field}"
        if old["boundary_only"]:
            assert old["actual_live_sessions"] == new["actual_live_sessions"] == 256
            for key, value in old["counters"].items():
                assert new["counters"][key] == value, f"Capacity behavior changed: {key}"
            continue
        for field in ("functional_result_signature", "committed_runs", "measured_network", "profile_network",
                      "running_session_peak_at_post", "all_admitted_runs_committed_exactly_once",
                      "query_result_and_loaded_players_verified"):
            assert old[field] == new[field], f"Observable behavior changed: {field}"
        for key, value in old["all_observed_counters"].items():
            assert new["all_observed_counters"][key] == value, f"Runtime counter changed: {key}"
        observed_network(old)
        observed_network(new)
        assert new["all_observed_counters"]["book_decode_hit"] > 0
        old_stages = old["timed_samples"]["automatic_stages_per_server_tick"]
        new_stages = new["timed_samples"]["automatic_stages_per_server_tick"]
        assert old_stages["book_codec"]["calls"]["total"] > 0
        assert new_stages["book_codec"]["calls"]["total"] == 0
        summaries.append({
            "players": old["players"], "graphs": old["automatic_graphs_per_player"], "wait": old["wait_ticks"],
            "dispatch_p95_ms_before": old_stages["total"]["wall_ns"]["p95"] / 1e6,
            "dispatch_p95_ms_after": new_stages["total"]["wall_ns"]["p95"] / 1e6,
            "dispatch_mean_allocated_bytes_before": old_stages["total"]["allocated_bytes"]["total"] / before["measured_ticks"],
            "dispatch_mean_allocated_bytes_after": new_stages["total"]["allocated_bytes"]["total"] / after["measured_ticks"],
        })
    return summaries


def network(before, after):
    assert before["corpus"] == after["corpus"]
    assert len(before["cases"]) == len(after["cases"]) == 18
    for old, new in zip(before["cases"], after["cases"], strict=True):
        for field in ("name", "layer", "payload_bytes", "payload_sha256", "misaka_envelope_bytes"):
            assert old[field] == new[field], f"Wire bytes changed: {field}"


def main():
    before = read("precision_c2_automatic_baseline_2026_10_30.json")
    compared = {}
    for filename in ("precision_c2_automatic_optimized_2026_10_30.json",
                     "precision_c2_automatic_optimized_r2_2026_10_30.json"):
        if filename.endswith("_r2_2026_10_30.json") and not (ROOT / filename).exists():
            continue
        compared[filename] = automatic(before, read(filename))
    network(read("precision_c2_network_baseline_2026_10_30.json"),
            read("precision_c2_network_optimized_2026_10_30.json"))
    client_before = read("precision_c2_client_baseline_2026_10_30.json")
    client_after = read("precision_c2_client_optimized_2026_10_30.json")
    assert client_before["checks"] == client_after["checks"] and len(client_after["checks"]) == 9
    assert client_before["revision"] == client_after["revision"] == 5
    print(json.dumps({"passed": True, "automatic": compared,
                      "network_wire_contracts_equal": True, "client_checks_equal": True}, indent=2))


if __name__ == "__main__":
    main()
