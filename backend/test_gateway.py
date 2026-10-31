import copy
from decimal import Decimal
import json
from pathlib import Path
import tempfile
import threading
import unittest
import urllib.error
import urllib.request

from gateway import Budget, Gateway, Rejected, RESERVE_USD, config, parse_decision, provider_body, serve, validate_frame


def frame():
    return {'request_id': 'test-request', 'fingerprint': 'a' * 64, 'goal': 'Send a test photo',
            'language': 'vi', 'package_name': 'synthetic.test', 'complete': True, 'unlocked': True,
            'consent': True, 'nodes': [{'text': 'Photos', 'password': False}],
            'choices': [{'id': 'c0', 'label': 'Photos', 'instruction': 'Bấm Hình ảnh.',
                         'step': 'attach', 'region': [.1, .2, .3, .4]}],
            'image': 'data:image/png;base64,iVBORw0KGgo='}


def answer(target='c0', status='guide', usage=True):
    data = {'choices': [{'finish_reason': 'stop', 'message': {
        'content': json.dumps({'status': status, 'choice_id': target})}}]}
    if usage:
        data['usage'] = {'prompt_tokens': 100, 'completion_tokens': 50}
    return data


class GatewayTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.directory = Path(self.temp.name)
        self.budget = Budget(self.directory, Decimal('5'))
        self.settings = {'key': 'fixture-key-not-real', 'limit': Decimal('5')}

    def tearDown(self):
        self.temp.cleanup()

    def test_sensitive_screens_stop_before_transport_or_credit(self):
        calls = []
        gateway = Gateway(self.settings, self.budget, lambda body: calls.append(body))
        for word in ['Password', 'Thanh toán', 'OTP', 'Mã xác thực', 'CVV']:
            value = frame(); value['nodes'][0]['text'] = word
            with self.assertRaises(Rejected):
                gateway.plan(value)
        value = frame(); value['nodes'][0]['password'] = True
        with self.assertRaises(Rejected):
            gateway.plan(value)
        self.assertEqual([], calls)
        self.assertFalse(self.budget.file.exists())

    def test_consent_lock_and_incomplete_tree_block_export(self):
        for flag in ['consent', 'unlocked', 'complete']:
            value = frame(); value[flag] = False
            with self.assertRaises(Rejected):
                validate_frame(value)

    def test_only_inline_images_and_valid_unique_regions_are_accepted(self):
        variants = []
        value = frame(); value['image'] = 'https://internal.example/image'; variants.append(value)
        value = frame(); value['choices'][0]['region'] = [.3, .2, .1, .4]; variants.append(value)
        value = frame(); value['choices'].append(copy.deepcopy(value['choices'][0])); variants.append(value)
        value = frame(); value['choices'][0]['region'][0] = float('nan'); variants.append(value)
        for value in variants:
            with self.assertRaises(Rejected):
                validate_frame(value)

    def test_model_cannot_fabricate_target_coordinates_or_completion(self):
        for data in [answer('c999'), answer(None, 'done'), answer('c0', 'stop')]:
            with self.assertRaises(Rejected):
                parse_decision(data, frame())
        data = answer(); data['choices'][0]['message']['tool_calls'] = [{'name': 'tap'}]
        with self.assertRaises(Rejected):
            parse_decision(data, frame())

    def test_valid_response_echoes_request_and_fingerprint_not_new_coordinates(self):
        result = parse_decision(answer(), frame())
        self.assertEqual({'request_id': 'test-request', 'fingerprint': 'a' * 64,
                          'status': 'guide', 'choice_id': 'c0'}, result)
        self.assertEqual('needs_input', parse_decision(answer(None, 'needs_input'), frame())['status'])

    def test_truncated_or_malformed_output_is_not_guidance(self):
        data = answer(); data['choices'][0]['finish_reason'] = 'length'
        with self.assertRaises(Rejected):
            parse_decision(data, frame())
        data = answer(); data['choices'][0]['message']['content'] = 'not JSON'
        with self.assertRaises(Rejected):
            parse_decision(data, frame())

    def test_provider_request_is_standard_stateless_and_has_no_execution_tools(self):
        body = provider_body(frame())
        self.assertEqual('muse-spark-1.3', body['model'])
        self.assertIs(False, body['store'])
        self.assertNotIn('tools', body)
        self.assertNotIn('previous_response_id', body)
        self.assertTrue(body['response_format']['json_schema']['strict'])

    def test_usage_is_settled_and_persists_across_restart(self):
        result = Gateway(self.settings, self.budget, lambda _: answer()).plan(frame())
        self.assertEqual('c0', result['choice_id'])
        data = json.loads(self.budget.file.read_text())
        self.assertEqual('0.0003375', data['spent_usd'])
        self.assertEqual({}, data['pending'])
        restarted = Budget(self.directory, Decimal('0.0004'))
        with self.assertRaises(Rejected):
            restarted.reserve()

    def test_network_failure_keeps_reservation_and_never_retries(self):
        calls = []
        def fail(body):
            calls.append(body)
            raise Rejected('provider_unavailable')
        with self.assertRaises(Rejected):
            Gateway(self.settings, self.budget, fail).plan(frame())
        self.assertEqual(1, len(calls))
        data = json.loads(self.budget.file.read_text())
        self.assertEqual(RESERVE_USD, sum(Decimal(v) for v in data['pending'].values()))

    def test_missing_or_impossible_usage_does_not_release_reservation(self):
        token = self.budget.reserve()
        for usage in [{}, {'prompt_tokens': 10, 'completion_tokens': 1000000}]:
            with self.assertRaises(Rejected):
                self.budget.settle(token, usage)
        self.assertIn(token, json.loads(self.budget.file.read_text())['pending'])

    def test_budget_rejects_before_sending_any_request(self):
        calls = []
        budget = Budget(self.directory, Decimal('0.01'))
        with self.assertRaises(Rejected):
            Gateway(self.settings, budget, lambda body: calls.append(body)).plan(frame())
        self.assertEqual([], calls)

    def test_configuration_pins_endpoint_model_and_authorized_ceiling(self):
        file = self.directory / '.env'
        valid = 'MODEL_API_KEY=fixture\nMODEL_API_BASE_URL=https://api.meta.ai/v1\nMODEL_API_MODEL=muse-spark-1.3\n'
        file.write_text(valid)
        self.assertEqual('fixture', config(file)['key'])
        for invalid in [valid.replace('api.meta.ai', 'other.example'),
                        valid.replace('muse-spark-1.3', 'muse-spark-1.3-contributor'),
                        valid + 'GUIDINGHAND_PROJECT_BUDGET_USD=151\n',
                        valid + 'GUIDINGHAND_TEST_BUDGET_USD=NaN\n']:
            file.write_text(invalid)
            with self.assertRaises(Rejected):
                config(file)

    def test_http_requires_client_token_and_sensitive_rejection_makes_no_provider_call(self):
        calls = []
        gateway = Gateway(self.settings, self.budget, lambda body: calls.append(body) or answer())
        server = serve(gateway, 'fixture-client-token', port=0)
        thread = threading.Thread(target=server.serve_forever, daemon=True); thread.start()
        url = f'http://127.0.0.1:{server.server_port}/plan'
        try:
            def request(value, token):
                return urllib.request.Request(url, data=json.dumps(value).encode(), headers={
                    'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token})
            with self.assertRaises(urllib.error.HTTPError) as rejected:
                urllib.request.urlopen(request(frame(), 'wrong'))
            self.assertEqual(401, rejected.exception.code)
            value = frame(); value['nodes'][0]['text'] = 'Payment'
            with self.assertRaises(urllib.error.HTTPError) as rejected:
                urllib.request.urlopen(request(value, 'fixture-client-token'))
            self.assertEqual(422, rejected.exception.code)
            self.assertEqual([], calls)
            with urllib.request.urlopen(request(frame(), 'fixture-client-token')) as response:
                self.assertEqual('c0', json.load(response)['choice_id'])
        finally:
            server.shutdown(); server.server_close(); thread.join()


    def test_launcher_navigation_is_tree_first_and_has_no_launch_tool(self):
        value = frame()
        value.update(mode='goal', tree_only=True, launcher_navigation=True)
        value.pop('image')
        validate_frame(value)
        body = provider_body(value)
        description = json.loads(body['messages'][1]['content'][0]['text'])
        self.assertTrue(description['launcher_navigation'])
        self.assertEqual(1, len(body['messages'][1]['content']))
        self.assertNotIn('tools', body)
        self.assertNotIn('open_app', body['response_format']['json_schema']['schema']['properties']['status']['enum'])
        self.assertIn('Folder previews are not tappable app icons', body['messages'][0]['content'])

    def test_launcher_cannot_bypass_mode_or_cross_app_validation(self):
        for fields in [dict(launcher_navigation='yes'), dict(launcher_navigation=True),
                       dict(mode='goal', launcher_navigation=True, cross_app=True)]:
            value = frame(); value.update(fields)
            with self.assertRaises(Rejected):
                validate_frame(value)

    def test_launcher_swipe_proposal_is_only_a_question_not_an_executed_action(self):
        value = frame()
        value.update(mode='goal', tree_only=True, launcher_navigation=True)
        value.pop('image')
        data = answer(None, 'needs_input')
        data['choices'][0]['message']['content'] = json.dumps(dict(status='needs_input', choice_id=None, question_code='scroll_down'))
        result = parse_decision(data, validate_frame(value))
        self.assertEqual('scroll_down', result['question_code'])
        self.assertIsNone(result['choice_id'])


if __name__ == '__main__':
    unittest.main()
