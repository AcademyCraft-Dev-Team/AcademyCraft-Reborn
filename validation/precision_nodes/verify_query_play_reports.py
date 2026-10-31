"""Recheck D-stage evidence without launching Minecraft or changing any world."""
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


def main():
    advanced = read("advanced_tasks_final_2026_10_31.json")
    require(advanced["passed"] and len(advanced["cases"]) == 12, "Twelve real advanced task cases required")
    require(all(case["passed"] for case in advanced["cases"]), "An advanced task case failed")
    names = {case["name"] for case in advanced["cases"]}
    require("sample_wait_crosshair_changed" in names, "Missing real sampled target across WAIT")
    require(any("mental" in name and "preserve" in name for name in names), "Missing same-slot mental control preservation")
    status = read("run_status_final_2026_10_31.json")
    require(status["passed"] and len(status["checks"]) >= 9 and all(status["checks"].values()), "Status lifecycle checks incomplete")
    require(any("dimension" in name for name in status["checks"]), "Missing actual dimension-change check")
    for language in ("zh_cn", "en_us"):
        client = read(f"query_play_{language}_final_2026_10_31.json")
        require(client["passed"] and client["language"] == language and len(client["checks"]) == 7,
                f"Real client checks incomplete: {language}")
        observation = client["observations"]
        require(observation["idle"]["phase"] == "IDLE", "Initial status must be honest IDLE")
        require(observation["waiting"]["waitKind"] == "WAIT" and observation["waiting"]["sourceNodeId"] == 2,
                "Wrong waiting node or wait kind")
        require(observation["completed"]["phase"] == "COMPLETED", "Real settled completion missing")
        for row in observation["closed_editor_network"]["operations"]:
            if row["kind"] == "run_status" and row["phase"] in ("send", "decode"):
                require(row["calls"] == 0, "Closed editor still receiving run status")
    require(sha(REPORTS / "node_inventory_2026_10_30.json") ==
            "C78157DC66C0885CBFC8012EA1CCDC4D2230A730E9E7F9ADAE8FF92BCC3A1A24", "Old 342-node inventory changed")
    manifest = read("precision_batch_d_validation_2026_10_31.json")
    require(manifest["passed"], "Batch manifest is not complete")
    for test in manifest["unit_tests"].values():
        require(test["tests"] > 0 and not any(test[k] for k in ("failures", "errors", "skipped")), "Unit test evidence failed")
    for item in manifest["files"]:
        if item.get("sha256"):
            require(sha(ROOT / item["path"]) == item["sha256"], "File changed since verification: " + item["path"])
    for item in manifest["artifacts"] + manifest["screenshots"]:
        require(sha(ROOT / item["path"]) == item["sha256"], "Artifact changed since verification: " + item["path"])
    for item in manifest["source_world_files"]:
        require(sha(ROOT / item["path"]) == item["sha256"], "Source test world changed: " + item["path"])
    require(sha(ROOT / "native/academy_physics/src/lib.rs") == manifest["preserved_unrelated_rust_sha256"],
            "Preexisting unrelated Rust change was modified")
    print(json.dumps({"passed": True, "advanced_cases": 12, "status_checks": len(status["checks"]),
                      "client_checks": 14, "screenshots": len(manifest["screenshots"]),
                      "files": len(manifest["files"]), "source_world_files": len(manifest["source_world_files"])}, ensure_ascii=False))


if __name__ == "__main__":
    main()
