"""Compare real-client inventories without treating localization edits as wire-format changes.

Usage: python verify_catalog_contract.py <new-client-inventory.json> [--output report.json]
The frozen baseline remains historical evidence; never regenerate it to make a check pass.
"""
import argparse
import hashlib
import json
from pathlib import Path

BASELINE = Path(__file__).with_name("node_inventory_2026_10_30.json")
TEXT_SLOTS = {"name", "description", "label"}
UNRELEASED = {f"academy:program/{category}/action/structure/staticize"
              for category in ("accelerator", "electromaster", "aeromanip")}


def localized_slot(key, item):
    return key in TEXT_SLOTS and isinstance(item, dict) and any(
        marker in item for marker in ("key", "zh_cn_present", "en_us_present"))


def contract(value):
    if isinstance(value, dict):
        return {key: {"key": item.get("key")} if localized_slot(key, item)
                else contract(item) for key, item in value.items() if key != "presentation_status"}
    if isinstance(value, list):
        return [contract(item) for item in value]
    return value


def missing_labels(value, path=""):
    result = []
    if isinstance(value, dict):
        for key, item in value.items():
            if localized_slot(key, item):
                for language in ("zh_cn", "en_us"):
                    text = item.get(language)
                    if (item.get(language + "_present") is not True
                            or not isinstance(text, str) or not text.strip()):
                        result.append(f"{path}/{key}: {language} {item.get('key')}")
            result.extend(missing_labels(item, f"{path}/{key}"))
    elif isinstance(value, list):
        for i, item in enumerate(value):
            result.extend(missing_labels(item, f"{path}/{i}"))
    return result


def verify(current_path):
    baseline = json.loads(BASELINE.read_text(encoding="utf-8"))
    current = json.loads(current_path.read_text(encoding="utf-8"))
    errors = []
    if current.get("passed") is not True:
        errors.append("client export failed")
    for key in ("category_count", "registered_occurrences", "unique_registered_ids",
                "unique_visible_ids", "unique_hidden_ids", "common_registered_ids"):
        if baseline[key] != current.get(key):
            errors.append(f"inventory statistic changed: {key}")
    before = {row["id"]: row for row in baseline["nodes"]}
    after = {row["id"]: row for row in current.get("nodes", [])}
    if before.keys() != after.keys() or len(after) != len(current.get("nodes", [])):
        errors.append(f"node IDs changed: removed={sorted(before.keys() - after.keys())}, added={sorted(after.keys() - before.keys())}")
    if baseline["categories"] != current.get("categories"):
        errors.append("category membership, visibility, role/group counts or extensions changed")
    for node_id in sorted(before.keys() & after.keys()):
        if contract(before[node_id]) != contract(after[node_id]):
            errors.append(f"execution/editor contract changed: {node_id}")
        errors.extend(missing_labels(after[node_id], node_id))
    statuses = {}
    for row in after.values():
        status = row.get("presentation_status", "missing")
        statuses[status] = statuses.get(status, 0) + 1
        expected = "UNRELEASED" if row["id"] in UNRELEASED else "AVAILABLE" if row["visible"] else "COMPATIBILITY"
        if status != expected:
            errors.append(f"incorrect presentation state: {row['id']}: {status}, expected {expected}")
    if statuses != {"AVAILABLE": 274, "COMPATIBILITY": 65, "UNRELEASED": 3}:
        errors.append(f"unexpected presentation states: {statuses}")
    return {
        "passed": not errors,
        "baseline": str(BASELINE.resolve()),
        "baseline_sha256": hashlib.sha256(BASELINE.read_bytes()).hexdigest(),
        "current": str(current_path.resolve()),
        "current_sha256": hashlib.sha256(current_path.read_bytes()).hexdigest(),
        "unique_nodes": len(after),
        "categories": current.get("category_count"),
        "presentation_states": statuses,
        "scope": "default and finite one-field schemas; registration and current bilingual completeness; not runtime skill behavior",
        "errors": errors,
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("inventory", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    report = verify(args.inventory)
    encoded = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.write_text(encoded, encoding="utf-8")
    print(encoded, end="")
    raise SystemExit(0 if report["passed"] else 1)
