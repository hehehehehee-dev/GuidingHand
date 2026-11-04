import copy
import json
import unittest

from core import spark_body, validate
from test_core import frame


class PromptDelegationTests(unittest.TestCase):
    def test_explicit_delegation_picks_exactly_one_named_option(self):
        value = frame()
        value['installed_apps'] = [
            {'label': 'Chrome', 'package': 'com.android.chrome'},
            {'label': 'YouTube', 'package': 'com.google.android.youtube'},
        ]
        self.assertIs(value, validate(value))
        body = spark_body(value)
        prompt = body['messages'][0]['content']
        self.assertIn('choose exactly one suitable installed option', prompt)
        self.assertIn('do not ask again; the elder can still correct you', prompt)


if __name__ == '__main__':
    unittest.main()
