"""Recheck E-stage numeric upgrade evidence without running or changing Minecraft."""
from pathlib import Path
import hashlib
import json

ROOT = Path(__file__).resolve().parents[2]
REPORTS = ROOT / "validation/precision_nodes"


def read(name):
    return json.loads((REPORTS / name).read_text(encoding="utf-8"))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest().upper()


def require(value, detail):
    if not value:
        raise AssertionError(detail)


def case_key(row):
    return row["nodes"], row["kind"], row["variant"]


def main():
    baseline = read("precision_e_numeric_baseline_2026_10_31.json")
    original_hashes = {x["path"]: x["sha256"] for x in read(
        "precision_e_numeric_baseline_source_hashes_2026_10_31.json")["files"]}
    for path, digest in baseline["source_sha256"].items():
        require(digest.upper() == original_hashes[path], "Baseline provenance differs: " + path)
    expected = {case_key(row): row["signature"] for row in baseline["cases"]}
    require(baseline["passed"] and len(expected) == 14, "Baseline matrix incomplete")
    for suffix in ("optimized", "optimized_repeat"):
        report = read(f"precision_e_numeric_{suffix}_2026_10_31.json")
        require(report["passed"] and len(report["cases"]) == 14, "Optimized matrix incomplete")
        require({case_key(row): row["signature"] for row in report["cases"]} == expected,
                "VM outcome or fuel signature changed")
        for row in report["cases"]:
            for metric in ("wall_nanos_per_session", "thread_allocated_bytes_per_session"):
                require(row[metric]["available"] and row[metric]["samples"] == 128,
                        "Measurement missing samples/allocation support")
        for path, digest in report["source_sha256"].items():
            require(sha(ROOT / path) == digest.upper(), "Numeric runtime changed after measurement: " + path)

    paid = read("numeric_upgrade_paid_final_2026_10_31.json")
    require(paid["passed"] and len(paid["cases"]) == 4, "Paid-action checks incomplete")
    rows = {row["name"]: row for row in paid["cases"]}
    for variant in ("legacy", "upgraded"):
        success, rejected = rows[variant + "_paid"], rows[variant + "_insufficient_cp"]
        require(success["accepted"] and success["block_became_air"] and success["cp_delta"] > 0,
                "Paid action did not actually execute")
        require(not rejected["accepted"] and not rejected["block_became_air"]
                and rejected["cp_delta"] == 0 and rejected["vm"]["nodeId"] == 5,
                "Insufficient CP rejection changed the world or wrong node")
    require(rows["legacy_paid"]["cp_delta"] == rows["upgraded_paid"]["cp_delta"], "CP cost changed")

    for language in ("zh_cn", "en_us"):
        client = read(f"numeric_upgrade_{language}_final_2026_10_31.json")
        require(client["passed"] and client["language"] == language and len(client["checks"]) == 6
                and len(client["screenshots"]) == 3, "Client upgrade/restore checks incomplete: " + language)
        require(len(client["evidence"]["change_nodes"]) == 2 and len(client["evidence"]["retained"]) == 1,
                "Client preview silently migrated the legacy integer divide")
        for row in client["evidence"]["preview_apply_network"]["operations"]:
            if row["phase"] == "send" and row["kind"] in ("save", "execute"):
                require(row["calls"] == (1 if row["kind"] == "execute" else 0),
                        "Preview/application automatically saved or executed")

    require(sha(REPORTS / "node_inventory_2026_10_30.json") ==
            "C78157DC66C0885CBFC8012EA1CCDC4D2230A730E9E7F9ADAE8FF92BCC3A1A24", "Old inventory changed")
    manifest = read("precision_batch_e_validation_2026_10_31.json")
    require(manifest["passed"], "Batch verification incomplete")
    for test in manifest["unit_tests"].values():
        require(test["tests"] > 0 and not any(test[k] for k in ("failures", "errors", "skipped")), "Test failure")
    for item in manifest["files"] + manifest["artifacts"] + manifest["screenshots"] + manifest["source_world_files"]:
        if item.get("sha256"):
            require(sha(ROOT / item["path"]) == item["sha256"], "Evidence changed: " + item["path"])
    require(sha(ROOT / "native/academy_physics/src/lib.rs") == manifest["preserved_unrelated_rust_sha256"],
            "Preexisting unrelated Rust content changed")
    print(json.dumps({"passed": True, "benchmark_cases_per_run": 14, "benchmark_runs": 3,
                      "paid_action_cases": 4, "client_checks": 12, "screenshots": len(manifest["screenshots"]),
                      "files": len(manifest["files"]), "source_world_files": len(manifest["source_world_files"])},
                     ensure_ascii=False))


if __name__ == "__main__":
    main()
