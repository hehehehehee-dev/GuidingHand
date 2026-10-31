"""Generate privacy-safe submission evidence from GuidingHand trace events.

This tool never turns an unreviewed trace into a claimed phone-test pass.
"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
import math
from pathlib import Path
import statistics
import subprocess
import xml.etree.ElementTree as ET


ALLOWED = {'sequence', 'uptime_ms', 'event', 'duration_ms', 'code', 'request_id', 'value'}
LATENCY_EVENTS = ('capture_encode', 'backend_roundtrip', 'provider_spark', 'provider_sam', 'provider_total', 'transcribe', 'screen_step')


def junit_summary(directory):
    result = {'files': 0, 'tests': 0, 'failures': 0, 'errors': 0, 'skipped': 0}
    for path in Path(directory).glob('TEST-*.xml'):
        suite = ET.parse(path).getroot()
        result['files'] += 1
        for name in ('tests', 'failures', 'errors', 'skipped'):
            result[name] += int(suite.attrib.get(name, 0))
    return result if result['files'] else 'TBD'


def source_revision(repo):
    repo = Path(repo)
    try:
        branch = subprocess.run(['git', 'branch', '--show-current'], cwd=repo, check=True,
                                capture_output=True, text=True).stdout.strip()
        commit = subprocess.run(['git', 'rev-parse', '--short=12', 'HEAD'], cwd=repo, check=True,
                                capture_output=True, text=True).stdout.strip()
        dirty = bool(subprocess.run(['git', 'status', '--porcelain'], cwd=repo, check=True,
                                    capture_output=True, text=True).stdout.strip())
        return {'branch': branch, 'commit': commit, 'working_tree': 'dirty' if dirty else 'clean'}
    except (OSError, subprocess.SubprocessError):
        return 'TBD'


def trace_provenance(path, label='unlabeled historical trace', version=None):
    source = Path(path)
    return {
        'label': label,
        'file': source.name,
        'sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
        'candidate_version': version or 'TBD: trace is not tied to the current candidate build'
    }


def percentile(values, fraction):
    if not values:
        return 'TBD'
    ordered = sorted(values)
    if fraction == .5:
        return statistics.median(ordered)
    return ordered[min(len(ordered) - 1, max(0, math.ceil(len(ordered) * fraction) - 1))]


def read_trace(path):
    value = json.loads(Path(path).read_text(encoding='utf-8'))
    if not isinstance(value, list):
        raise ValueError('trace_must_be_a_list')
    rows = []
    for row in value:
        if not isinstance(row, dict) or set(row) - ALLOWED or not isinstance(row.get('event'), str):
            raise ValueError('unsafe_trace_row')
        rows.append(row)
    return rows


def summarize(events, verification=None, provenance=None):
    latency = {}
    for name in LATENCY_EVENTS:
        values = [row['duration_ms'] for row in events if row.get('event') == name and type(row.get('duration_ms')) is int]
        latency[name] = {'count': len(values), 'median_ms': percentile(values, .5), 'p95_ms': percentile(values, .95)} if values else 'TBD'
    states = Counter(row.get('code') for row in events if row.get('event') == 'state' and isinstance(row.get('code'), str))
    coverage = [row['value'] / 1_000_000 for row in events if row.get('event') == 'panel_layout' and type(row.get('value')) is int]
    snapshots = [row['value'] for row in events if row.get('event') == 'snapshot_encoded' and type(row.get('value')) is int]
    return {
        'trace_events': len(events),
        'latency': latency,
        'state_counts': dict(states),
        'stale_results_discarded': sum(1 for row in events if row.get('event') == 'stale_result'),
        'recovery_states_observed': states.get('RECOVERING', 0),
        'safety_pauses_observed': states.get('SAFETY_PAUSED', 0),
        'done_state_observed': states.get('DONE', 0) > 0,
        'panel_target_coverage': {'count': len(coverage), 'maximum': max(coverage), 'median': percentile(coverage, .5)} if coverage else 'TBD',
        'snapshot_bytes': {'count': len(snapshots), 'maximum': max(snapshots), 'median': percentile(snapshots, .5)} if snapshots else 'TBD',
        'ring_accuracy': 'TBD: requires human review of the pointed control',
        'real_phone_task_result': 'TBD: a DONE state or trace alone is not certification',
        'privacy': 'No screen text, image, audio, recipient, goal or credential is read by this report.',
        'verification': verification or {'source': 'TBD', 'android_unit_tests': 'TBD', 'backend_unit_tests': 'TBD'},
        'trace_provenance': provenance or {'label': 'TBD', 'file': 'TBD', 'sha256': 'TBD',
                                          'candidate_version': 'TBD: trace is not tied to the current candidate build'}
    }


def markdown(summary):
    source = summary['verification']['source']
    source_label = 'TBD' if source == 'TBD' else f"{source['branch']}@{source['commit']} ({source['working_tree']})"
    android = summary['verification']['android_unit_tests']
    android_label = 'TBD' if android == 'TBD' else f"{android['tests']} tests, {android['failures'] + android['errors']} failed, {android['skipped']} skipped"
    backend = summary['verification']['backend_unit_tests']
    backend_label = 'TBD' if backend == 'TBD' else f"{backend['tests']} tests, {backend['failures']} failed"
    provenance = summary['trace_provenance']
    lines = ['# GuidingHand submission evidence', '',
             '> Generated from privacy-safe diagnostic events. Missing evidence remains TBD.', '',
             f"- Trace events: **{summary['trace_events']}**",
             f"- Stale AI results discarded: **{summary['stale_results_discarded']}**",
             f"- Recovery states observed: **{summary['recovery_states_observed']}**",
             f"- Safety pauses observed: **{summary['safety_pauses_observed']}**",
             f"- Real-phone task result: **{summary['real_phone_task_result']}**", '',
             '## Trace provenance', '',
             f"- Label: `{provenance['label']}`",
             f"- Trace file: `{provenance['file']}`",
             f"- SHA-256: `{provenance['sha256']}`",
             f"- Candidate version: `{provenance['candidate_version']}`", '',
             '## Reproducible verification', '',
             f"- Source: `{source_label}`",
             f"- Android unit tests: `{android_label}`",
             f"- Backend unit tests: `{backend_label}`", '',
             '## Measured trace latency', '',
             '| Stage | Samples | Median | P95 |', '|---|---:|---:|---:|']
    for name, result in summary['latency'].items():
        if result == 'TBD':
            lines.append(f'| {name} | 0 | TBD | TBD |')
        else:
            lines.append(f"| {name} | {result['count']} | {result['median_ms']} ms | {result['p95_ms']} ms |")
    lines += ['', f"Ring accuracy: **{summary['ring_accuracy']}**", '', summary['privacy'], '']
    return '\n'.join(lines)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--trace', required=True, help='trace.json produced by collect_phone_evidence.py')
    parser.add_argument('--output', required=True, help='output directory')
    parser.add_argument('--repo', default=str(Path(__file__).resolve().parents[1]), help='repository root')
    parser.add_argument('--backend-tests', type=int, help='backend test count from a passing test run')
    parser.add_argument('--trace-label', default='unlabeled historical trace', help='human-reviewed trace description')
    parser.add_argument('--trace-version', help='installed candidate version reported when this trace was collected')
    args = parser.parse_args()
    output = Path(args.output); output.mkdir(parents=True, exist_ok=True)
    revision = source_revision(args.repo)
    android = junit_summary(Path(args.repo) / 'app' / 'build' / 'test-results' / 'testDebugUnitTest')
    verification = {
        'source': revision,
        'android_unit_tests': android,
        'backend_unit_tests': {'tests': args.backend_tests, 'failures': 0} if args.backend_tests is not None else 'TBD'
    }
    summary = summarize(read_trace(args.trace), verification,
                        trace_provenance(args.trace, args.trace_label, args.trace_version))
    (output / 'submission-evidence.json').write_text(json.dumps(summary, indent=2), encoding='utf-8')
    (output / 'submission-evidence.md').write_text(markdown(summary), encoding='utf-8')
    print(json.dumps({'output': str(output), 'trace_events': summary['trace_events']}))


if __name__ == '__main__':
    main()
