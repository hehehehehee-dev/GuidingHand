import json
from pathlib import Path
import tempfile
import unittest

from benchmark_snapshot_profiles import parse_profiles
from generate_submission_report import junit_summary, markdown, read_trace, source_revision, summarize


class SubmissionToolsTests(unittest.TestCase):
    def test_report_keeps_unmeasured_results_tbd_and_summarizes_safe_metrics(self):
        rows = [
            {'sequence': 1, 'uptime_ms': 1, 'event': 'provider_spark', 'duration_ms': 1200},
            {'sequence': 2, 'uptime_ms': 2, 'event': 'provider_spark', 'duration_ms': 800},
            {'sequence': 3, 'uptime_ms': 3, 'event': 'state', 'code': 'RECOVERING'},
            {'sequence': 4, 'uptime_ms': 4, 'event': 'stale_result', 'code': 'screen_changed'},
            {'sequence': 5, 'uptime_ms': 5, 'event': 'panel_layout', 'code': 'WAITING_TAP:visible:fixed', 'value': 0},
        ]
        result = summarize(rows)
        self.assertEqual(1000, result['latency']['provider_spark']['median_ms'])
        self.assertEqual(1, result['recovery_states_observed']); self.assertEqual(1, result['stale_results_discarded'])
        self.assertIn('TBD', result['real_phone_task_result']); self.assertIn('TBD', markdown(result))

    def test_trace_rejects_personal_or_unknown_fields(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'trace.json'
            path.write_text(json.dumps([{'event': 'state', 'screen_text': 'private'}]), encoding='utf-8')
            with self.assertRaises(ValueError): read_trace(path)

    def test_snapshot_profiles_are_bounded(self):
        self.assertEqual([(1600, 80), (1280, 75)], parse_profiles('1600:80,1280:75'))
        for value in ('200:80', '1600:10', ''):
            with self.assertRaises((ValueError, IndexError)): parse_profiles(value)

    def test_junit_summary_uses_reported_counts(self):
        with tempfile.TemporaryDirectory() as directory:
            Path(directory, 'TEST-one.xml').write_text(
                '<testsuite tests="4" failures="1" errors="0" skipped="2"/>', encoding='utf-8')
            result = junit_summary(directory)
            self.assertEqual({'files': 1, 'tests': 4, 'failures': 1, 'errors': 0, 'skipped': 2}, result)

    def test_source_revision_is_tbd_outside_git(self):
        with tempfile.TemporaryDirectory() as directory:
            self.assertEqual('TBD', source_revision(directory))


if __name__ == '__main__':
    unittest.main()
