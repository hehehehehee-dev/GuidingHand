"""Create fixture/CI evidence without claiming human-reviewed ring accuracy."""
from __future__ import annotations

import argparse
import csv
import hashlib
from pathlib import Path
import xml.etree.ElementTree as ET


def fixture_rows(path: Path) -> list[list[str]]:
    with path.open(encoding="utf-8") as source:
        return [row for row in csv.reader((line for line in source if line.strip() and not line.startswith("#")), delimiter="|")]


def junit(results: Path) -> dict[str, int]:
    totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
    matched = 0
    for file in results.glob("TEST-*.xml"):
        root = ET.parse(file).getroot()
        if root.attrib.get("name", "").endswith(("SafetyEvaluationTest", "TargetFusionEvaluationTest")):
            matched += 1
            for key in totals:
                totals[key] += int(root.attrib.get(key, "0"))
    if matched != 2:
        raise ValueError("Both evaluation test suites must be present")
    if totals["failures"] or totals["errors"] or totals["skipped"]:
        raise ValueError("Evaluation test suites did not pass cleanly")
    return totals


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def report(android_results: Path, safety: Path, fusion: Path) -> str:
    tests = junit(android_results)
    safety_rows, fusion_rows = fixture_rows(safety), fixture_rows(fusion)
    sensitive = sum(row[1] in {"SENSITIVE", "LOCKED"} for row in safety_rows)
    unreadable = sum(row[1] == "CANNOT_READ" for row in safety_rows)
    safe = sum(row[1] == "SAFE" for row in safety_rows)
    accepted = sum(row[1] != "REJECT" for row in fusion_rows)
    rejected = len(fusion_rows) - accepted
    return "\n".join([
        "# GuidingHand deterministic evaluation evidence", "",
        "> Fixture tests only. This report does not claim human-reviewed ring accuracy or older-adult usability.", "",
        f"- Evaluation JUnit tests: **{tests['tests']} passed, 0 failed**",
        f"- Safety fixtures: **{len(safety_rows)}** ({sensitive} sensitive/locked, {unreadable} unreadable, {safe} safe controls)",
        f"- Fusion geometry fixtures: **{len(fusion_rows)}** ({accepted} accepted, {rejected} rejected)",
        "- Provider calls: **0**", "- Real-phone result: **TBD**", "- Ring accuracy: **TBD — requires human review**", "",
        "## Provenance", "",
        f"- Safety fixture SHA-256: `{sha(safety)}`", f"- Fusion fixture SHA-256: `{sha(fusion)}`", "",
        "The safety suite executes the native pre-model gate and asserts zero planner calls for every sensitive, locked, or unreadable fixture. The fusion suite executes the production edge-aware geometry used after Spark selects an Accessibility node and SAM returns a region.", ""
    ])


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--android-results", type=Path, default=Path("app/build/test-results/testDebugUnitTest"))
    parser.add_argument("--safety", type=Path, default=Path("app/src/test/resources/evaluation/safety_cases.tsv"))
    parser.add_argument("--fusion", type=Path, default=Path("app/src/test/resources/evaluation/fusion_cases.tsv"))
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(report(args.android_results, args.safety, args.fusion), encoding="utf-8")
    print(args.output)


if __name__ == "__main__":
    main()
