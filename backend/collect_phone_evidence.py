"""Read-only candidate diagnostics. No screen capture, target-app input or provider calls."""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import subprocess


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--serial', required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    output = root / 'build' / 'phone-runs' / datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    output.mkdir(parents=True, exist_ok=True)
    adb = str(Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk/platform-tools/adb.exe')
    base = [adb, '-s', args.serial]
    def run(parts):
        return subprocess.run(base+parts, capture_output=True, timeout=20)
    package = 'vn.guidinghand.candidate'
    result = run(['exec-out', 'run-as', package, 'cat', 'files/guide-trace.jsonl'])
    events = []
    allowed = {'sequence', 'uptime_ms', 'event', 'duration_ms', 'code', 'request_id', 'value'}
    if result.returncode == 0:
        for line in result.stdout.decode('utf-8', errors='replace').splitlines():
            try:
                event = json.loads(line)
                if isinstance(event, dict) and not set(event)-allowed:
                    events.append(event)
            except ValueError:
                pass
    (output/'trace.json').write_text(json.dumps(events, indent=2), encoding='utf-8')
    logs = run(['logcat', '-d', '-v', 'brief', '-s', 'GuidingHandOverlay:I', '*:S'])
    geometry = []
    for line in logs.stdout.decode('utf-8', errors='replace').splitlines():
        if 'GuidingHandOverlay' in line and 'status=' in line:
            # The native logger emits enum/geometry only. Reject anything outside this format.
            value = line[line.index('status='):]
            if re.fullmatch(r'[A-Za-z0-9_=., ():-]+', value):
                geometry.append(value)
    (output/'panel-geometry.txt').write_text('\n'.join(geometry), encoding='utf-8')
    installed = run(['shell', 'dumpsys', 'package', 'vn.guidinghand']).stdout.decode('utf-8', errors='replace')
    version = re.search(r'versionName=(\S+)', installed)
    metrics = [{'stage': row['event'], 'duration_ms': row['duration_ms'], 'request_id': row.get('request_id')}
               for row in events if 'duration_ms' in row and row.get('event') in
               ('capture_encode', 'backend_roundtrip', 'provider_spark', 'provider_sam', 'provider_total', 'transcribe', 'screen_step')]
    recoveries = sum(1 for row in events if row.get('event') == 'state' and row.get('code') == 'RECOVERING')
    hard_stops = sum(1 for row in events if row.get('event') == 'state' and row.get('code') == 'SAFETY_PAUSED')
    stale = sum(1 for row in events if row.get('event') == 'stale_result')
    panel = [row.get('value') for row in events if row.get('event') == 'panel_layout' and isinstance(row.get('value'), int)]
    summary = {'overall_result': 'TBD: manual send-photo test not certified by log collection',
        'original_phone_version': version.group(1) if version else 'TBD',
        'candidate_trace_events': len(events), 'observed_native_done': any(row.get('code') == 'DONE' for row in events),
        'ring_accuracy_per_button': 'TBD: human review required', 'measured_durations': metrics or 'TBD',
        'panel_geometry_records': len(geometry), 'panel_target_coverage_ppm': panel or 'TBD',
        'recovery_state_count': recoveries, 'stale_result_discard_count': stale,
        'wrong_tap_recovery': 'observed_state_only' if recoveries else 'TBD',
        'hard_stop': 'observed_state_only' if hard_stops else 'TBD',
        'note': 'No photos, screen text, audio, recipient names or authentication values are collected.'}
    (output/'summary.json').write_text(json.dumps(summary, indent=2), encoding='utf-8')
    print(json.dumps({'evidence_directory': str(output), 'trace_events': len(events), 'phone_test': 'TBD'}))


if __name__ == '__main__':
    main()
