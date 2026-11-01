import json
import tempfile
import unittest
from pathlib import Path

from generate_meta_trace_viewer import read_trace, render, transition_edges


class MetaTraceViewerTest(unittest.TestCase):
    def test_only_whitelisted_metadata_reaches_html(self):
        with tempfile.TemporaryDirectory() as directory:
            trace = Path(directory) / "trace.jsonl"
            trace.write_text('\n'.join([
                '{"sequence":1,"uptime_ms":10,"event":"state","code":"PLANNING","screen_text":"SECRET MESSAGE"}',
                '{"sequence":2,"uptime_ms":20,"event":"provider_spark","duration_ms":1200,"audio":"SECRET AUDIO"}',
                '{"sequence":3,"uptime_ms":30,"event":"target_fusion","code":"sam_ui_edge_bounded","value":910,"token":"SECRET TOKEN"}',
                '{"sequence":4,"uptime_ms":40,"event":"state","code":"WAITING_TAP","goal":"SECRET GOAL"}',
            ]), encoding="utf-8")
            events = read_trace(trace)
            output = render(events, "abc", "fixture", "TBD")
            self.assertIn("Muse Spark", output)
            self.assertIn("sam_ui_edge_bounded", output)
            self.assertNotIn("SECRET", output)
            self.assertEqual([("PLANNING", "WAITING_TAP", 1)], transition_edges(events))

    def test_invalid_codes_are_replaced_not_rendered(self):
        with tempfile.TemporaryDirectory() as directory:
            trace = Path(directory) / "trace.jsonl"
            trace.write_text('{"event":"state","code":"<script>alert(1)</script>"}', encoding="utf-8")
            output = render(read_trace(trace), "abc", "fixture", "TBD")
            self.assertNotIn("script", output)

    def test_collector_json_array_is_supported(self):
        with tempfile.TemporaryDirectory() as directory:
            trace = Path(directory) / "trace.json"
            trace.write_text(json.dumps([
                {"sequence": 1, "uptime_ms": 10, "event": "provider_spark", "duration_ms": 42, "code": "OK"},
                {"sequence": 2, "uptime_ms": 52, "event": "provider_sam", "duration_ms": 18, "code": "OK"},
            ]), encoding="utf-8")
            events = read_trace(trace)
            self.assertEqual([event["event"] for event in events], ["provider_spark", "provider_sam"])
            self.assertEqual(events[0]["duration_ms"], 42)


if __name__ == "__main__":
    unittest.main()
