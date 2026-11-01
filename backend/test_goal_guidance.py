import copy
import json
import unittest

from gateway import Rejected, parse_decision, provider_body, validate_frame
from test_gateway import frame, answer


class GoalGuidanceTests(unittest.TestCase):
    def goal(self):
        value = frame()
        value.update(mode='goal', goal='Calculate 24 times 7', history=[
            {'target': '2', 'matched_tap': True, 'screen_changed': True}])
        return value

    def response(self, status, target=None, question='none'):
        value = answer(target, status)
        value['choices'][0]['message']['content'] = json.dumps({
            'status': status, 'choice_id': target, 'question_code': question})
        return value

    def test_free_goal_context_and_observations_reach_planner(self):
        value = self.goal()
        body = provider_body(validate_frame(value))
        content = json.loads(body['messages'][1]['content'][0]['text'])
        self.assertEqual('Calculate 24 times 7', content['goal'])
        self.assertEqual(value['history'], content['recent_observations'])
        self.assertIn('do NOT', body['messages'][0]['content'])
        self.assertIn('review', body['response_format']['json_schema']['schema']['properties']['status']['enum'])

    def test_review_and_clarification_are_not_completion_or_arbitrary_text(self):
        value = self.goal()
        self.assertEqual('review', parse_decision(self.response('review'), value)['status'])
        self.assertEqual('recipient', parse_decision(self.response('needs_input', question='recipient'), value)['question_code'])
        for response in [self.response('done'), self.response('review', 'c0'),
                         self.response('guide', 'c0', 'recipient'), self.response('needs_input', question='invented')]:
            with self.assertRaises(Rejected):
                parse_decision(response, value)

    def test_empty_action_set_allows_review_but_never_invented_control(self):
        value = self.goal(); value['choices'] = []
        validate_frame(value)
        parse_decision(self.response('review'), value)
        with self.assertRaises(Rejected):
            parse_decision(self.response('guide', 'c0'), value)

    def test_unbounded_sensitive_or_falsely_verified_history_is_rejected(self):
        value = self.goal()
        for history in [[value['history'][0]] * 6,
                        [{'target': 'Password', 'matched_tap': True, 'screen_changed': True}],
                        [{'target': '2', 'matched_tap': True, 'screen_changed': False}],
                        [{'target': '2', 'matched_tap': True, 'screen_changed': True, 'success': True}]]:
            invalid = copy.deepcopy(value); invalid['history'] = history
            with self.assertRaises(Rejected):
                validate_frame(invalid)
        value['goal'] = 'Enter my OTP'
        with self.assertRaises(Rejected):
            validate_frame(value)


if __name__ == '__main__':
    unittest.main()
