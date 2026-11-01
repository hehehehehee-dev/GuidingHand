"""Render privacy-safe GuidingHand trace metadata as a standalone Meta pipeline viewer."""
from __future__ import annotations

import argparse
import hashlib
import html
import json
import re
from pathlib import Path

SAFE = re.compile(r"^[A-Za-z0-9_.:-]{1,100}$")
STAGES = {
    "model": "Model interface",
    "capture_encode": "Safety-checked snapshot",
    "snapshot_encoded": "Safety-checked snapshot",
    "transcribe": "Muse Voice Transcribe",
    "provider_spark": "Muse Spark",
    "provider_sam": "SAM 3.1",
    "provider_total": "Meta orchestration",
    "target_fusion": "Spark + SAM + Accessibility",
    "plan_cache_hit": "Exact-screen cache",
    "plan_cache_store": "Exact-screen cache",
    "state": "GuideEngine state machine",
    "transition": "GuideEngine state machine",
    "guide_stop": "Safety / recovery decision",
    "stale_result": "Screen-state verification",
}


def safe_token(value: object, fallback: str = "—") -> str:
    text = str(value)
    return text if SAFE.fullmatch(text) else fallback


def raw_events(path: Path) -> list[dict]:
    """Accept collector JSON arrays and service-export JSONL without exposing content fields."""
    text = path.read_text(encoding="utf-8", errors="replace")
    try:
        document = json.loads(text)
        if isinstance(document, list):
            return [item for item in document if isinstance(item, dict)]
        if isinstance(document, dict):
            return [document]
    except (ValueError, TypeError):
        pass
    rows = []
    for line in text.splitlines():
        try:
            item = json.loads(line)
        except (ValueError, TypeError):
            continue
        if isinstance(item, dict):
            rows.append(item)
    return rows


def read_trace(path: Path) -> list[dict]:
    events = []
    for raw in raw_events(path):
        event = safe_token(raw.get("event"), "")
        if not event:
            continue
        row = {
            "sequence": int(raw.get("sequence", len(events) + 1)),
            "uptime_ms": max(0, int(raw.get("uptime_ms", 0))),
            "event": event,
            "stage": STAGES.get(event, "App evidence"),
            "code": safe_token(raw.get("code")),
            "duration_ms": max(0, int(raw["duration_ms"])) if isinstance(raw.get("duration_ms"), (int, float)) else None,
            "value": int(raw["value"]) if isinstance(raw.get("value"), (int, float)) else None,
            "request_id": safe_token(raw.get("request_id")),
        }
        events.append(row)
    return events


def percentile(values: list[int], fraction: float) -> int | None:
    if not values:
        return None
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, max(0, round((len(ordered) - 1) * fraction)))]


def transition_edges(events: list[dict]) -> list[tuple[str, str, int]]:
    edges: dict[tuple[str, str], int] = {}
    explicit = [row for row in events if row["event"] == "transition" and "." in row["code"]]
    if explicit:
        for row in explicit:
            route = row["code"].split(":", 1)[0]
            before, after = route.split(".", 1)
            edges[(before, after)] = edges.get((before, after), 0) + 1
    else:
        states = [row["code"] for row in events if row["event"] == "state" and row["code"] != "—"]
        for before, after in zip(states, states[1:]):
            if before != after:
                edges[(before, after)] = edges.get((before, after), 0) + 1
    return [(before, after, count) for (before, after), count in sorted(edges.items(), key=lambda item: (-item[1], item[0]))]


def render(events: list[dict], source_sha: str, label: str, candidate: str) -> str:
    timings = {name: [row["duration_ms"] for row in events if row["event"] == name and row["duration_ms"] is not None]
               for name in ("transcribe", "provider_spark", "provider_sam", "provider_total", "backend_roundtrip", "capture_encode")}
    sensitive = sum(row["event"] in {"guide_stop", "state", "transition"} and "SAFETY" in row["code"] for row in events)
    stale = sum(row["event"] == "stale_result" for row in events)
    fusion = sum(row["event"] == "target_fusion" for row in events)
    cache_hits = sum(row["event"] == "plan_cache_hit" for row in events)
    timing_rows = "".join(
        f"<tr><td>{html.escape(STAGES.get(name, name))}</td><td>{len(values)}</td><td>{percentile(values, .5) if values else 'TBD'} ms</td><td>{percentile(values, .95) if values else 'TBD'} ms</td></tr>"
        for name, values in timings.items()
    )
    edge_rows = "".join(f"<tr><td>{html.escape(before)}</td><td>→</td><td>{html.escape(after)}</td><td>{count}</td></tr>"
                        for before, after, count in transition_edges(events)) or '<tr><td colspan="4">No transition evidence in this trace.</td></tr>'
    timeline = "".join(
        "<tr>" + "".join([
            f"<td>{row['sequence']}</td>", f"<td>{html.escape(row['stage'])}</td>",
            f"<td><code>{html.escape(row['event'])}</code></td>", f"<td>{html.escape(row['code'])}</td>",
            f"<td>{row['duration_ms'] if row['duration_ms'] is not None else '—'}</td>",
            f"<td>{row['value'] if row['value'] is not None else '—'}</td>",
        ]) + "</tr>" for row in events[-500:]
    ) or '<tr><td colspan="6">No valid privacy-safe events.</td></tr>'
    return f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>GuidingHand Meta trace evidence</title><style>
:root{{--ink:#12382b;--cream:#fffaf0;--yellow:#ffe600;--muted:#617169;--line:#d8dfda}}*{{box-sizing:border-box}}
body{{margin:0;background:#eef2ef;color:var(--ink);font:16px/1.45 system-ui,sans-serif}}main{{max-width:1120px;margin:auto;padding:32px 20px 64px}}
h1{{font-size:36px;margin:0 0 8px}}h2{{margin-top:34px}}.notice{{background:#111;color:white;border-left:10px solid var(--yellow);padding:16px 18px;border-radius:12px}}
.cards{{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:12px;margin:20px 0}}.card{{background:var(--cream);border:1px solid var(--line);border-radius:16px;padding:18px}}
.value{{display:block;font-size:30px;font-weight:800}}.label{{color:var(--muted)}}table{{width:100%;border-collapse:collapse;background:white;border-radius:14px;overflow:hidden}}
th,td{{padding:11px 12px;text-align:left;border-bottom:1px solid var(--line)}}th{{background:var(--ink);color:white}}code{{font-size:13px}}.meta{{color:var(--muted);word-break:break-all}}
</style></head><body><main><h1>GuidingHand · Meta trace evidence</h1>
<p class="meta">{html.escape(label)} · Candidate {html.escape(candidate)} · Trace SHA-256 {html.escape(source_sha)}</p>
<div class="notice"><strong>Privacy boundary:</strong> this viewer reads only event names, timings, reason codes, bounded geometry scores, request IDs and state transitions. Screenshots, audio, goals, messages, recipients, tokens and credentials are neither rendered nor copied.</div>
<div class="cards"><div class="card"><span class="value">{len(events)}</span><span class="label">safe events</span></div>
<div class="card"><span class="value">{fusion}</span><span class="label">target-fusion decisions</span></div>
<div class="card"><span class="value">{stale}</span><span class="label">stale results discarded</span></div>
<div class="card"><span class="value">{sensitive}</span><span class="label">safety pauses/stops</span></div>
<div class="card"><span class="value">{cache_hits}</span><span class="label">exact-screen cache hits</span></div></div>
<h2>Meta pipeline timing</h2><table><thead><tr><th>Stage</th><th>Samples</th><th>Median</th><th>P95</th></tr></thead><tbody>{timing_rows}</tbody></table>
<h2>Recovery state graph</h2><table><thead><tr><th>From</th><th></th><th>To</th><th>Observed</th></tr></thead><tbody>{edge_rows}</tbody></table>
<h2>Privacy-safe timeline</h2><table><thead><tr><th>#</th><th>Component</th><th>Event</th><th>Decision</th><th>ms</th><th>Value</th></tr></thead><tbody>{timeline}</tbody></table>
</main></body></html>"""


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("trace", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--label", default="Trace provenance not supplied")
    parser.add_argument("--candidate", default="TBD")
    args = parser.parse_args()
    source_sha = hashlib.sha256(args.trace.read_bytes()).hexdigest()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(render(read_trace(args.trace), source_sha, args.label, args.candidate), encoding="utf-8")
    print(args.output)


if __name__ == "__main__":
    main()
