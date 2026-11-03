import base64
import copy
from decimal import Decimal
import hashlib
import io
import json
import os
from pathlib import Path
import struct
import tempfile
import threading
import unittest
import urllib.request
import wave
from unittest.mock import patch

from core import CoreError, CoreGateway, SamSegmenter, SparkPlanner, blocked_result, create_application, image_info, parse_sam, spark_body, validate
from gateway import Budget, Gateway, serve


def frame():
    image = png()
    return {'image': image, 'image_width': 100, 'image_height': 100,
            'image_sha256': hashlib.sha256(base64.b64decode(image.split(',',1)[1])).hexdigest(),
            'request_id': 'core-1', 'fingerprint': 'a'*64, 'consent': True, 'unlocked': True,
            'goal': {'text': 'Gửi ảnh bông hoa cho An qua Zalo', 'answers': []},
            'screen': {'package_name': 'com.zing.zalo', 'complete': True, 'nodes': [
                {'id': 'attach', 'text': 'Hình ảnh', 'aliases': [], 'clickable': True,
                 'selected': False, 'password': False, 'region': [.1, .1, .31, .31]}]}}


def decision(status='step'):
    return {'intent': 'send_photo_zalo', 'output_language': 'vi', 'status': status, 'instruction_vi': 'Bấm Hình ảnh.', 'target_node_id': 'attach', 'step_id': 'attach',
            'question_vi': '', 'options': [], 'speech_purpose': 'general', 'navigation_cue': 'none',
            'task_kind': 'send_photo_zalo', 'recipient': 'An', 'photo': 'Bông hoa', 'content': '', 'app_package': '', 'block_reason': None}


def png(width=100, height=100):
    # A generated PNG header is enough for image dimension validation; transport remains fake.
    return 'data:image/png;base64,'+base64.b64encode(b'\x89PNG\r\n\x1a\n'+b'\x00\x00\x00\rIHDR'+struct.pack('>II', width, height)).decode()


SAM_TEXT = "<0f>1<|box;x1=10;y1=10;x2=29;y2=29;w=100;h=100|><|mask;x=0;y=0;data=20,20,!!!!!'y:P=c*$|>"


def sam_response(wire=SAM_TEXT):
    return {'status': 'completed', 'output': [{'type': 'message', 'content': [{'type': 'output_text', 'text': wire}]}]}


class Planner:
    def __init__(self, result=None):
        self.result = result or decision(); self.calls = []

    def plan(self, value):
        self.calls.append(copy.deepcopy(value)); return copy.deepcopy(self.result)


class Sam:
    def __init__(self): self.calls = 0
    def segment(self, value, node):
        self.calls += 1
        return parse_sam(sam_response(), value['image_width'], value['image_height'], node['region'])




class CoreTests(unittest.TestCase):
    def test_structured_safety_does_not_block_articles_or_email_fields(self):
        for label, class_name in [('How to create a password', 'android.widget.TextView'),
                                  ('Email address', 'android.widget.EditText')]:
            value = frame(); value['screen']['nodes'] = [{
                'id': 'field', 'text': label, 'aliases': [], 'clickable': False,
                'selected': False, 'password': False, 'focused': class_name.endswith('EditText'),
                'heading': False, 'class_name': class_name, 'region': [.1, .1, .9, .2]}]
            self.assertIs(value, validate(value))

    def test_browser_article_does_not_pair_sensitive_words_with_address_bar(self):
        value = frame(); value['screen']['package_name'] = 'com.android.chrome'
        value['screen']['nodes'] = [
            {'id': 'com.android.chrome:id/url_bar', 'text': 'How to create a password', 'aliases': [],
             'clickable': True, 'selected': False, 'password': False, 'focused': True, 'heading': False,
             'class_name': 'android.widget.EditText', 'region': [.02, .02, .98, .09]},
            {'id': 'article', 'text': 'Payment details explained', 'aliases': [], 'clickable': False,
             'selected': False, 'password': False, 'focused': False, 'heading': True,
             'class_name': 'android.widget.TextView', 'region': [.05, .25, .95, .55]}]
        self.assertIs(value, validate(value))

    def test_browser_real_otp_form_and_payment_action_remain_blocked(self):
        variants = []
        otp = frame(); otp['screen']['package_name'] = 'com.android.chrome'; otp['screen']['nodes'] = [
            {'id': 'prompt', 'text': 'Enter your OTP', 'aliases': [], 'clickable': False,
             'selected': False, 'password': False, 'focused': False, 'heading': False,
             'class_name': 'android.widget.TextView', 'region': [.1, .3, .9, .4]},
            {'id': 'otp_field', 'text': '', 'aliases': [], 'clickable': True, 'selected': False,
             'password': False, 'focused': True, 'heading': False, 'class_name': 'android.widget.EditText',
             'region': [.1, .42, .9, .52]}]
        variants.append(otp)
        payment = frame(); payment['screen']['package_name'] = 'com.android.chrome'; payment['screen']['nodes'] = [
            {'id': 'pay', 'text': 'Pay now', 'aliases': [], 'clickable': True, 'selected': False,
             'password': False, 'focused': False, 'heading': False, 'class_name': 'android.widget.Button',
             'region': [.1, .7, .9, .82]}]
        variants.append(payment)
        for value in variants:
            with self.assertRaisesRegex(CoreError, 'sensitive_screen'):
                validate(value)

    def test_structured_safety_blocks_password_otp_and_payment_before_models(self):
        for label, password in [('Password', True), ('Enter your OTP', False), ('Payment details', False)]:
            value = frame(); value['screen']['nodes'][0].update(
                text=label, password=password, focused=True, heading=False,
                class_name='android.widget.EditText')
            result = self.core.step(value)
            self.assertEqual(('blocked', 'sensitive'), (result['status'], result['reason_category']))
        self.assertEqual([], self.planner.calls); self.assertEqual(0, self.sam.calls)

    def test_wrong_language_echo_is_rejected_before_sam(self):
        value = frame(); value['language'] = 'en'
        self.planner.result = {**decision(), 'output_language': 'vi'}
        result = self.core.step(value)
        self.assertEqual(('blocked', 'invalid_spark_response'), (result['status'], result['reason']))
        self.assertEqual(0, self.sam.calls)

    def test_mixed_language_visible_copy_is_rejected_before_sam(self):
        value = frame(); value['language'] = 'en'
        self.planner.result = {**decision(), 'output_language': 'en',
                               'instruction_vi': 'Bác bấm nút Hình ảnh.'}
        result = self.core.step(value)
        self.assertEqual(('blocked', 'invalid_output_language'), (result['status'], result['reason']))
        self.assertEqual(0, self.sam.calls)

    def test_default_browser_is_grounded_context_and_obstacles_keep_the_goal(self):
        value = frame()
        value['installed_apps'] = [{'label': 'Chrome', 'package': 'com.android.chrome'}]
        value['default_browser'] = value['installed_apps'][0]
        self.assertIs(value, validate(value))
        body = spark_body(value)
        facts = json.loads(body['messages'][1]['content'][0]['text'])
        prompt = body['messages'][0]['content']
        self.assertEqual(value['default_browser'], facts['default_browser'])
        self.assertIn('do not ask the same app question again', prompt)
        self.assertIn('temporary obstacles', prompt)
        self.assertIn('continue the bound goal after dismissal', prompt)

        invalid = copy.deepcopy(value)
        invalid['default_browser'] = {'label': 'Invented', 'package': 'example.missing'}
        with self.assertRaises(CoreError): validate(invalid)

    def test_actionable_error_copy_keeps_transient_failures_distinct(self):
        changed = blocked_result({'language': 'en'}, 'snapshot_changed')
        offline = blocked_result({'language': 'en'}, 'provider_unavailable')
        ambiguous = blocked_result({'language': 'en'}, 'sam_no_unique_region')
        self.assertEqual(('unavailable', 'snapshot_changed'), (changed['reason_category'], changed['reason']))
        self.assertIn('settle', changed['message_vi'])
        self.assertEqual('unavailable', offline['reason_category']); self.assertIn('offline', offline['message_vi'])
        self.assertEqual('cannot_read', ambiguous['reason_category']); self.assertIn('one clear button', ambiguous['message_vi'])

    def test_supported_or_unclear_intent_cannot_be_rejected_or_autorouted(self):
        for intent in ('send_photo_zalo', 'unclear'):
            self.planner.result = {**decision('unsupported'), 'intent': intent, 'question_vi': 'Not supported'}
            result = self.core.step(frame())
            self.assertEqual('clarify', result['status'])
            self.assertEqual('scope_conflict', result['reason_category'])
            self.assertNotIn('task', result)
        self.assertEqual(0, self.sam.calls)
        self.assertEqual(2, len(self.planner.calls))

    def test_other_or_unclear_intent_cannot_authorize_a_target(self):
        for intent in ('other', 'unclear'):
            self.planner.result = {**decision(), 'intent': intent}
            self.assertEqual('invalid_intent', self.core.step(frame())['reason'])
        self.assertEqual(0, self.sam.calls)

    def test_rejection_requires_a_spoken_explanation_instead_of_generic_fallback(self):
        self.planner.result = {**decision('unsupported'), 'intent': 'other'}
        self.assertEqual('invalid_text', self.core.step(frame())['reason'])
        self.assertEqual(0, self.sam.calls)

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.budget = Budget(Path(self.temp.name), Decimal('5'))
        self.planner, self.sam = Planner(), Sam()
        self.now = 100
        self.core = CoreGateway({'key': 'fixture-not-a-real-key'}, self.budget, self.planner, self.sam, lambda: self.now)

    def tearDown(self): self.temp.cleanup()

    def test_clarification_pairs_reach_spark_without_replacing_original_goal(self):
        value = frame()
        value['goal']['dialogue'] = [{'question': 'Who should receive the photo?', 'answer': 'Choose someone in Zalo'}]
        value['goal']['answers'] = ['Choose someone in Zalo']
        self.core.step(value)
        actual = self.planner.calls[0]['goal']
        self.assertEqual(value['goal'], actual)

    def test_recipient_can_be_chosen_in_the_app_after_navigation(self):
        self.planner.result = {**decision('await'), 'target_node_id': None, 'step_id': 'recover',
            'recipient': '', 'photo': '', 'instruction_vi': 'Về màn hình chính rồi tìm Zalo.'}
        result = self.core.step(frame())
        self.assertEqual('await', result['status'])
        self.assertEqual('', result['task']['recipient'])
        self.assertEqual('screen_change', result['handoff'])
        self.assertEqual(0, self.sam.calls)

    def test_home_swipe_visual_is_structured_and_only_allowed_for_navigation(self):
        self.planner.result = {**decision('await'), 'intent': 'guide_app', 'task_kind': 'guide_app',
            'app_package': 'com.zing.zalo', 'target_node_id': None, 'step_id': 'recover',
            'navigation_cue': 'home_swipe_up', 'instruction_vi': 'Bác vuốt từ dưới lên để về màn hình chính.'}
        result = self.core.step(frame())
        self.assertEqual(('await', 'home_swipe_up'), (result['status'], result['navigation_cue']))
        self.assertEqual(0, self.sam.calls)
        self.planner.result = {**decision(), 'navigation_cue': 'home_swipe_up'}
        self.assertEqual('invalid_navigation_cue', self.core.step(frame())['reason'])

    def test_only_missing_slots_can_be_refined_and_send_still_requires_both(self):
        value = frame(); value['task'] = {'kind': 'send_photo_zalo', 'recipient': '', 'photo': '', 'verified_steps': []}
        self.assertEqual('step', self.core.step(value)['status'])
        self.planner.result = {**decision(), 'step_id': 'send', 'photo': ''}
        self.assertEqual('missing_task_details', self.core.step(value)['reason'])

    def test_sensitive_clarification_answer_is_gated_before_models(self):
        value = frame(); value['goal']['dialogue'] = [{'question': 'What do you need?', 'answer': 'OTP'}]
        self.assertEqual('sensitive', self.core.step(value)['reason_category'])
        self.assertEqual([], self.planner.calls)

    def test_safety_before_any_model_and_before_credit(self):
        variants = []
        for flag in ('consent', 'unlocked'):
            value = frame(); value[flag] = False; variants.append(value)
        value = frame(); value['screen']['complete'] = False; variants.append(value)
        for word in ('Password', 'OTP', 'Thanh toán', 'Mã xác thực', 'CVV'):
            value = frame(); value['screen']['nodes'][0]['aliases'] = [word]; variants.append(value)
        value = frame(); value['screen']['nodes'][0]['password'] = True; variants.append(value)
        for value in variants:
            self.assertEqual('blocked', self.core.step(value)['status'])
        self.assertEqual([], self.planner.calls); self.assertEqual(0, self.sam.calls)
        self.assertFalse(self.budget.file.exists())

    def test_cannot_read_and_sensitive_have_distinct_reasons(self):
        value = frame(); value['screen']['complete'] = False
        self.assertEqual('cannot_read', self.core.step(value)['reason_category'])
        value = frame(); value['screen']['nodes'][0]['text'] = ''
        self.assertEqual('unlabeled_screen', self.core.step(value)['reason'])
        value = frame(); value['screen']['nodes'][0]['password'] = True
        self.assertEqual('sensitive', self.core.step(value)['reason_category'])
        self.assertEqual([], self.planner.calls)

    def test_spark_clarifies_generic_ambiguous_intent_without_task_shortcut(self):
        self.planner.result = {**decision('clarify'), 'question_vi': 'Muốn liên lạc bằng cách nào?',
                               'options': ['Trò chuyện bằng tin nhắn', 'Gọi điện'], 'task_kind': None}
        value = frame(); value['goal']['text'] = 'Tôi muốn liên lạc với người thân'
        value['family'] = {'recipient': 'An', 'photo': 'Bông hoa'}
        result = self.core.step(value)
        self.assertEqual('clarify', result['status']); self.assertEqual(2, len(result['options']))
        self.assertEqual(1, len(self.planner.calls)); self.assertNotIn('task', result)
        self.assertEqual(0, self.sam.calls)
        self.assertIn('never authorization', spark_body(value)['messages'][0]['content'])
        self.assertNotIn('tools', spark_body(value))

    def test_message_content_purpose_is_structured_and_returned_verbatim(self):
        self.planner.result = {**decision('clarify'), 'question_vi': 'Bác muốn viết nội dung gì?',
                               'speech_purpose': 'message_content', 'task_kind': None}
        result = self.core.step(frame())
        self.assertEqual('clarify', result['status'])
        self.assertEqual('message_content', result['speech_purpose'])

    def test_missing_or_unknown_speech_purpose_fails_closed(self):
        for result in ({k:v for k,v in decision('clarify').items() if k != 'speech_purpose'},
                       {**decision('clarify'), 'speech_purpose': 'message'}):
            self.planner.result = result
            self.assertEqual('invalid_spark_response', self.core.step(frame())['reason'])

    def test_other_tasks_stay_unsupported_even_with_family_photo_defaults(self):
        self.planner.result = {**decision('unsupported'), 'intent': 'other', 'question_vi': 'Hiện chỉ hỗ trợ gửi ảnh qua Zalo.', 'task_kind': None}
        value = frame(); value['goal']['text'] = 'Tôi muốn gọi điện'; value['family'] = {'recipient': 'An', 'photo': 'Bông hoa'}
        self.assertEqual('unsupported', self.core.step(value)['status']); self.assertEqual(0, self.sam.calls)

    def test_android_speech_output_never_requires_server_synthesis(self):
        result = self.core.step(frame())
        self.assertEqual('step', result['status']); self.assertEqual(1, self.sam.calls)
        self.assertNotIn('audio', result)
        self.assertEqual(0.85, self.core.capabilities()['speech_output']['rate'])

    def test_single_snapshot_is_seen_by_spark_and_sam_without_second_upload(self):
        value = frame(); sam_images = []
        class Segmenter:
            def segment(_self, received, node):
                sam_images.append(received['image'])
                return [.1, .1, .3, .3]
        self.core.sam = Segmenter()
        result = self.core.step(value)
        self.assertEqual('step', result['status'])
        self.assertEqual(value['image'], self.planner.calls[0]['image'])
        self.assertEqual([value['image']], sam_images)
        self.assertEqual(value['image_sha256'], result['image_sha256'])
        self.assertEqual('sam-3.1', result['region_source']); self.assertNotIn('plan_token', result)
        self.assertEqual({'spark','sam','total'}, set(result['timings_ms']))
        self.assertEqual(1, len(self.planner.calls))

    def test_missing_mismatched_or_replaced_snapshot_never_reaches_spark(self):
        for change in ('missing', 'hash', 'dimensions', 'continuation'):
            value = frame()
            if change == 'missing': del value['image']
            elif change == 'hash': value['image_sha256'] = '0'*64
            elif change == 'dimensions': value['image_width'] = 200
            else: value['plan_token'] = 'a'*64
            self.assertEqual('blocked', self.core.step(value)['status'])
        self.assertEqual([], self.planner.calls); self.assertEqual(0, self.sam.calls)

    def test_safety_rechecked_after_spark_and_before_sam(self):
        def altered(value):
            value['screen']['nodes'][0]['password'] = True
            return decision()
        self.core.planner.plan = altered
        result = self.core.step(frame())
        self.assertEqual('sensitive', result['reason_category']); self.assertEqual(0, self.sam.calls)

    def test_model_cannot_swap_the_screenshot_or_goal_before_sam(self):
        for changed in ('image','goal'):
            def altered(value):
                if changed=='image':
                    value['image'] = png(99,100); value['image_width'] = 99
                    value['image_sha256'] = hashlib.sha256(base64.b64decode(value['image'].split(',',1)[1])).hexdigest()
                else: value['goal']['text'] = 'A different request'
                return decision()
            self.core.planner.plan = altered
            self.assertEqual('snapshot_changed', self.core.step(frame())['reason'])
        self.assertEqual(0, self.sam.calls)

    def test_manual_home_handoff_keeps_task_but_does_not_invent_a_ring(self):
        self.planner.result = {**decision('await'), 'target_node_id': None, 'step_id': 'recover',
                               'instruction_vi': 'Bác về màn hình chính để tìm Zalo nhé.'}
        value = frame(); value['screen']['package_name'] = 'test.browser'
        result = self.core.step(value)
        self.assertEqual('await', result['status']); self.assertEqual('screen_change', result['handoff'])
        self.assertEqual('An', result['task']['recipient']); self.assertNotIn('region', result)
        self.assertEqual(0, self.sam.calls)

    def test_task_change_is_not_silently_accepted(self):
        value = frame(); value['task'] = {'kind':'send_photo_zalo','recipient':'Bình','photo':'Bông hoa','verified_steps':['launcher']}
        self.assertEqual('task_changed', self.core.step(value)['reason'])

    def test_new_spoken_request_may_extend_only_an_existing_general_task(self):
        value = frame()
        value['screen']['package_name'] = 'com.instagram.android'
        value['installed_apps'] = [{'label': 'Instagram', 'package': 'com.instagram.android'}]
        value['task'] = {'kind': 'guide_app', 'recipient': '', 'photo': '',
                         'content': '', 'app_package': 'com.instagram.android', 'verified_steps': ['launcher']}
        value['goal'] = {'text': 'Mở Instagram', 'answers': ['Gửi tin nhắn cho Minh'],
                         'updates': ['Gửi tin nhắn cho Minh']}
        value['allow_task_update'] = True
        self.planner.result = {**decision(), 'intent': 'guide_app', 'task_kind': 'guide_app',
            'app_package': 'com.instagram.android', 'recipient': 'Minh', 'photo': '',
            'content': 'Cháu khỏe không?',
            'target_node_id': 'attach', 'step_id': 'action', 'instruction_vi': 'Bấm Tin nhắn.'}

        result = self.core.step(value)

        self.assertEqual('step', result['status'])
        self.assertEqual('Minh', result['task']['recipient'])
        self.assertEqual('Cháu khỏe không?', result['task']['content'])
        self.assertEqual([], result['task']['verified_steps'])
        self.assertEqual(value['goal'], self.planner.calls[0]['goal'])
        self.assertTrue(self.planner.calls[0]['allow_task_update'])

        # Filling an empty slot is ordinary refinement. Replacing an already bound
        # recipient is the change that requires a fresh spoken-update revision.
        value['task']['recipient'] = 'An'
        value['allow_task_update'] = False
        self.assertEqual('task_changed', self.core.step(value)['reason'])

    def test_unlisted_target_or_invented_verification_cannot_create_step(self):
        self.planner.result['target_node_id'] = 'invented'
        self.assertEqual('ungrounded_target', self.core.step(frame())['reason'])
        self.planner.result = {**decision(), 'verified_steps': ['send']}
        self.assertEqual('invalid_spark_response', self.core.step(frame())['reason'])

    def test_ambiguous_malformed_empty_or_wrong_size_sam_stops(self):
        variants = ['', SAM_TEXT+','+SAM_TEXT.replace('1<|box', '2<|box'), SAM_TEXT.replace('!!!!!\'y:P=c*$', 'broken'),
                    SAM_TEXT.replace('w=100', 'w=999')]
        for wire in variants:
            with self.assertRaises(CoreError): parse_sam(sam_response(wire), 100, 100, [.1, .1, .31, .31])
        with self.assertRaises(CoreError): parse_sam({**sam_response(), 'status': 'incomplete'}, 100, 100, [.1,.1,.31,.31])

    def test_sam_uses_real_provider_endpoint_and_fixed_image_budget(self):
        calls = []
        adapter = SamSegmenter('fixture-key', self.budget, lambda key, endpoint, body: calls.append((endpoint,body)) or sam_response())
        value = frame()
        self.assertEqual([.1,.1,.3,.3], adapter.segment(value, value['screen']['nodes'][0]))
        self.assertEqual('responses', calls[0][0]); self.assertEqual('sam-3.1', calls[0][1]['model'])
        self.assertNotIn('tools', calls[0][1]); self.assertIn('<0f>1<|box;', calls[0][1]['input'][0]['content'][0]['text'])
        ledger = json.loads(self.budget.file.read_text()); self.assertEqual('0.0025', ledger['spent_usd']); self.assertEqual({}, ledger['pending'])

    def test_real_adapters_share_byte_identical_image_in_single_core_request(self):
        images = []
        def transport(key, endpoint, body):
            if endpoint == 'chat/completions':
                self.assertEqual('muse-spark-1.3', body['model'])
                images.append(body['messages'][1]['content'][1]['image_url']['url'])
                return {'choices':[{'finish_reason':'stop','message':{'content':json.dumps(decision())}}],
                        'usage':{'prompt_tokens':100,'completion_tokens':100}}
            self.assertEqual('responses', endpoint); self.assertEqual('sam-3.1', body['model'])
            images.append(body['input'][0]['content'][1]['image_url'])
            return sam_response()
        core = CoreGateway({'key':'fixture-key'}, self.budget,
                           SparkPlanner('fixture-key', self.budget, transport), SamSegmenter('fixture-key', self.budget, transport))
        value = frame(); result = core.step(value)
        self.assertEqual('step', result['status']); self.assertEqual([value['image'],value['image']], images)
        self.assertEqual(value['image_sha256'],result['image_sha256'])
        self.assertEqual([.1,.1,.3,.3],result['region'])
        self.assertEqual(2,json.loads(self.budget.file.read_text())['requests'])

    def test_diagnostic_log_contains_only_codes_and_durations(self):
        value = frame()
        with self.assertLogs('guidinghand.core', level='INFO') as log:
            self.core.step(value)
        combined = ' '.join(log.output)
        for private in [value['image'],value['goal']['text'],'Bông hoa','fixture-not-a-real-key']:
            self.assertNotIn(private,combined)
        self.assertIn('spark_ms=',combined);self.assertIn('sam_ms=',combined)

    def test_sam_uncertain_failure_preserves_reservation_and_never_speaks(self):
        def unavailable(*_): raise CoreError('provider_unavailable')
        self.core.sam = SamSegmenter('fixture-key', self.budget, unavailable)
        self.assertEqual('blocked', self.core.step(frame())['status'])
        ledger = json.loads(self.budget.file.read_text()); self.assertEqual(['0.01'], list(ledger['pending'].values()))

    def test_image_dimensions_must_match_encoded_source(self):
        with self.assertRaises(CoreError): image_info(png(100,100), 200, 100)


    def test_spark_transport_is_required_and_stateless_without_execution_tools(self):
        body = spark_body(frame()); self.assertEqual('muse-spark-1.3', body['model']); self.assertIs(False, body['store'])
        calls = []
        response = {'choices':[{'finish_reason':'stop','message':{'content':json.dumps(decision())}}], 'usage':{'prompt_tokens':100,'completion_tokens':100}}
        planner = SparkPlanner('fixture-key', self.budget, lambda key,endpoint,payload: calls.append(endpoint) or response)
        self.assertEqual('step', planner.plan(frame())['status']); self.assertEqual(['chat/completions'], calls)

    def test_observations_and_language_are_context_not_fabricated_progress(self):
        value = frame(); value['language'] = 'en'
        value['observations'] = [{'target': 'actual.control@0.1', 'matched_tap': False, 'screen_changed': True}]
        body = spark_body(value)
        facts = json.loads(body['messages'][1]['content'][0]['text'])
        self.assertEqual(value['observations'], facts['observations']); self.assertEqual('en', facts['language'])
        self.assertEqual(value['image'], body['messages'][1]['content'][1]['image_url']['url'])
        self.assertIn('with no mixed-language UI', body['messages'][0]['content'])
        self.assertNotIn('task', facts)

    def test_invalid_or_sensitive_observations_block_before_planning(self):
        for observations in [[{'target':'real','matched_tap':'yes','screen_changed':True}],
                             [{'target':'OTP','matched_tap':False,'screen_changed':True}],
                             [{'target':'real','matched_tap':False,'screen_changed':True}]*6]:
            value = frame(); value['observations'] = observations
            self.assertEqual('blocked', self.core.step(value)['status'])
        self.assertEqual([], self.planner.calls)

    def test_capabilities_do_not_claim_voice_or_production_ready(self):
        value = self.core.capabilities(); self.assertIs(False, value['production_ready'])
        self.assertEqual('muse-voice-transcribe-1.0', value['voice_input']['model']); self.assertEqual('speech_to_text', value['voice_input']['role']); self.assertFalse(value['automatic_actions'])

    def test_production_wsgi_requires_https_and_backend_session_auth(self):
        environment = {'MODEL_API_KEY':'fixture-key', 'GUIDINGHAND_APP_SESSION_TOKEN':'session-token-'*4,
                       'GUIDINGHAND_STATE_DIR':self.temp.name, 'GUIDINGHAND_PROJECT_BUDGET_USD':'5'}
        with patch.dict(os.environ, environment, clear=True):
            app = create_application()
        def request(scheme, auth, path='/core/capabilities', forwarded=''):
            status = []
            result = app({'wsgi.url_scheme':scheme,'HTTP_X_FORWARDED_PROTO':forwarded,'HTTP_AUTHORIZATION':auth,'REQUEST_METHOD':'GET','PATH_INFO':path},
                         lambda s,h:status.append(s))
            return status[0], json.loads(b''.join(result))
        self.assertEqual(('200 OK', {'status':'ok'}), request('http','', '/health'))
        self.assertEqual('400 Bad Request',request('http','Bearer '+'session-token-'*4)[0])
        self.assertEqual('400 Bad Request',request('http','Bearer '+'session-token-'*4, forwarded='https')[0])
        self.assertEqual('401 Unauthorized',request('https','wrong')[0])
        status,result = request('https','Bearer '+'session-token-'*4)
        self.assertEqual('200 OK',status); self.assertFalse(result['production_ready'])
        with patch.dict(os.environ, {**environment, 'GUIDINGHAND_TRUST_PROXY_HEADERS':'true'}, clear=True):
            proxy_app = create_application()
        app = proxy_app
        self.assertEqual('200 OK',request('http','Bearer '+'session-token-'*4, forwarded='https')[0])

    def test_temporary_dev_transport_routes_core_and_blocks_sensitive_before_planning(self):
        gateway = Gateway({'key': 'fixture-key', 'limit': Decimal('5')}, self.budget)
        server = serve(gateway, 'fixture-client-token', 0)
        worker = threading.Thread(target=server.serve_forever, daemon=True); worker.start()
        origin = 'http://127.0.0.1:'+str(server.server_port)
        headers = {'Authorization': 'Bearer fixture-client-token', 'Content-Type': 'application/json'}
        try:
            with urllib.request.urlopen(urllib.request.Request(origin+'/core/capabilities', headers=headers)) as response:
                self.assertFalse(json.load(response)['production_ready'])
            value = frame(); value['screen']['nodes'][0]['text'] = 'OTP'
            with patch.object(SparkPlanner, 'plan') as planner:
                request = urllib.request.Request(origin+'/core/step', data=json.dumps(value).encode(), headers=headers)
                with urllib.request.urlopen(request) as response:
                    self.assertEqual('blocked', json.load(response)['status'])
                planner.assert_not_called()
        finally:
            server.shutdown(); server.server_close(); worker.join()


if __name__ == '__main__': unittest.main()
