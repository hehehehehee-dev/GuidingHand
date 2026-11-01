import tempfile
import unittest
from pathlib import Path

from generate_evaluation_report import fixture_rows, junit, report


class EvaluationReportTest(unittest.TestCase):
    def test_requires_both_clean_suites_and_never_claims_ring_accuracy(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name, tests in (("SafetyEvaluationTest", 3), ("TargetFusionEvaluationTest", 1)):
                (root / f"TEST-{name}.xml").write_text(
                    f'<testsuite name="vn.guidinghand.{name}" tests="{tests}" failures="0" errors="0" skipped="0"/>', encoding="utf-8")
            self.assertEqual(4, junit(root)["tests"])
            safety = root / "safety.tsv"; safety.write_text("a|SENSITIVE|password|\nb|SAFE|plain_label|News\n", encoding="utf-8")
            fusion = root / "fusion.tsv"; fusion.write_text("a|sam_visual|0,0,1,1|0,0,1,1|0,0,2,2|1\nb|REJECT|0,0,1,1|1,1,2,2|0,0,2,2|1\n", encoding="utf-8")
            value = report(root, safety, fusion)
            self.assertIn("Provider calls: **0**", value)
            self.assertIn("Ring accuracy: **TBD", value)
            self.assertNotIn("100%", value)

    def test_fixture_reader_ignores_comments(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "cases.tsv"
            path.write_text("# comment\ncase|SAFE|plain|text\n", encoding="utf-8")
            self.assertEqual([["case", "SAFE", "plain", "text"]], fixture_rows(path))


if __name__ == "__main__":
    unittest.main()
