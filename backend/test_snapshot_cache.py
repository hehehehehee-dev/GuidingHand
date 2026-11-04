import base64
import copy
import struct
import tempfile
import unittest
from decimal import Decimal
from pathlib import Path
from unittest.mock import patch

import core
from core import CoreError, CoreGateway
from gateway import Budget
from test_core import Sam, decision, frame


class FakePlanner:
    def __init__(self, result):
        self.result = result
        self.calls = []

    def plan(self, value):
        self.calls.append(copy.deepcopy(value))
        return copy.deepcopy(self.result)


class SnapshotCacheTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.budget = Budget(Path(self.temp.name), Decimal('5'))

    def tearDown(self):
        self.temp.cleanup()

    def counting_gateway(self, calls):
        real_b64decode = base64.b64decode

        def counting(data, *args, **kwargs):
            calls.append(1)
            return real_b64decode(data, *args, **kwargs)

        return counting

    def test_full_step_decodes_image_once(self):
        value = frame()
        gateway = CoreGateway({'key': 'fixture-not-a-real-key'}, self.budget, FakePlanner(decision()), Sam(), lambda: 100)
        calls = []
        with patch.object(core.base64, 'b64decode', self.counting_gateway(calls)):
            result = gateway.step(value)
        self.assertEqual('step', result['status'])
        self.assertEqual(1, len(calls))

    def test_second_step_decodes_again_without_cross_step_state(self):
        first, second = frame(), frame()
        gateway = CoreGateway({'key': 'fixture-not-a-real-key'}, self.budget, FakePlanner(decision()), Sam(), lambda: 100)
        calls = []
        with patch.object(core.base64, 'b64decode', self.counting_gateway(calls)):
            self.assertEqual('step', gateway.step(first)['status'])
            self.assertEqual('step', gateway.step(second)['status'])
        self.assertEqual(2, len(calls))

    def test_mutated_frame_redecodes_and_fails(self):
        value = frame()
        core.validate_snapshot(value)
        tampered = dict(value)
        tampered['image'] = 'data:image/png;base64,' + base64.b64encode(
            b'\x89PNG\r\n\x1a\n' + b'\x00\x00\x00\rIHDR' + struct.pack('>II', 101, 100)).decode()
        with self.assertRaises(CoreError):
            core.validate_snapshot(tampered)


if __name__ == '__main__':
    unittest.main()
