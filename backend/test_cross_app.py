import copy
import json
import unittest
from decimal import Decimal
from pathlib import Path
import tempfile

from gateway import Budget, Gateway, Rejected, parse_decision, parse_route, provider_body, route_body, validate_frame, validate_route
from test_gateway import frame, answer


APPS = [{'id': 'a0', 'label': 'Calculator', 'package': 'test.calculator'},
        {'id': 'a1', 'label': 'YouTube', 'package': 'test.youtube'},
        {'id': 'a2', 'label': 'Browser', 'package': 'test.browser'}]

def route_frame():
    return {'request_id': 'r1', 'goal': 'Read the news', 'language': 'en', 'package_name': 'test.calculator',
            'apps': copy.deepcopy(APPS), 'consent': True, 'unlocked': True}

def response(value):
    result = answer()
    result['choices'][0]['message']['content'] = json.dumps(value)
    return result


class CrossAppTests(unittest.TestCase):
    def test_router_cannot_invent_apps_urls_or_actions(self):
        valid = {'status': 'route', 'app_id': 'a2', 'action': 'search_web', 'query': 'news', 'question_code': 'none'}
        self.assertEqual('a2', parse_route(response(valid), route_frame())['app_id'])
        for changed in [{'app_id': 'a99'}, {'action': 'send'}, {'query': 'OTP'}, {'url': 'https://example.com'}]:
            value = {**valid, **changed}
            with self.assertRaises(Rejected): parse_route(response(value), route_frame())

    def test_route_validation_happens_before_credit_or_transport(self):
        with tempfile.TemporaryDirectory() as directory:
            calls = []; budget = Budget(Path(directory), Decimal('5'))
            gateway = Gateway({'key': 'fixture'}, budget, lambda body: calls.append(body))
            for changed in [{'consent': False}, {'unlocked': False}, {'goal': 'Enter password'}, {'apps': []}]:
                with self.assertRaises(Rejected): gateway.route({**route_frame(), **changed})
            self.assertEqual([], calls); self.assertFalse(budget.file.exists())

    def test_router_is_text_only_and_cache_rebinds_request_without_another_paid_call(self):
        valid = {'status': 'route', 'app_id': 'a2', 'action': 'launch', 'query': '', 'question_code': 'none'}
        body = route_body(validate_route(route_frame()))
        self.assertEqual('minimal', body['reasoning_effort'])
        self.assertTrue(all(isinstance(message['content'], str) for message in body['messages']))
        with tempfile.TemporaryDirectory() as directory:
            calls = []
            gateway = Gateway({'key': 'fixture'}, Budget(Path(directory), Decimal('5')), lambda body: calls.append(body) or response(valid))
            gateway.route(route_frame())
            result = gateway.route({**route_frame(), 'request_id': 'r2'})
            self.assertEqual('r2', result['request_id']); self.assertEqual(1, len(calls))
            self.assertEqual(1, gateway.metrics['route_cache_hits'])

    def tree(self):
        value = frame(); value.update(mode='goal', tree_only=True, cross_app=True, apps=copy.deepcopy(APPS))
        del value['image']; return value

    def test_tree_first_omits_pixels_and_only_accepts_a_real_other_app(self):
        value = validate_frame(self.tree()); body = provider_body(value)
        self.assertEqual(1, len(body['messages'][1]['content']))
        result = {'status': 'open_app', 'choice_id': None, 'question_code': 'none', 'app_id': 'a1'}
        self.assertEqual('a1', parse_decision(response(result), value)['app_id'])
        for app in ['a99', 'invented.package']:
            with self.assertRaises(Rejected): parse_decision(response({**result, 'app_id': app}), value)

    def test_image_escalation_and_specific_manual_handoffs_are_bounded(self):
        value = self.tree()
        parsed = parse_decision(response({'status': 'needs_image', 'choice_id': None, 'question_code': 'none', 'app_id': None}), value)
        self.assertEqual('needs_image', parsed['status'])
        for code in ['type', 'handoff', 'scroll_down', 'scroll_up', 'app']:
            self.assertEqual(code, parse_decision(response({'status': 'needs_input', 'choice_id': None, 'question_code': code, 'app_id': None}), value)['question_code'])
        with self.assertRaises(Rejected): validate_frame({**value, 'mode': 'choice'})


if __name__ == '__main__': unittest.main()
