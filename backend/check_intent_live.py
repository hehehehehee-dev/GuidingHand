"""Opt-in live Spark scope regression using only an owned synthetic UI fixture.

This measures intent decisions, not real-phone Zalo accuracy or task completion.
No audio, personal screen, credential or model-generated text is persisted.
"""
import argparse
import copy
import json
from pathlib import Path
import time

from core import CoreError, SparkPlanner
from gateway import Budget, config


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run-live', action='store_true', required=True)
    parser.add_argument('--case', help='Run just one named synthetic case.')
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    fixture = json.loads((root / 'build/outputs/meta-integration-fixture.json').read_text())
    settings = config()
    planner = SparkPlanner(settings['key'], Budget(root / 'backend/.state', settings['limit']))
    cases = [
        ('photo_without_details', 'Tôi muốn gửi một ảnh qua Zalo', [], 'send_photo_zalo'),
        ('app_confirmation', 'Tôi muốn gửi một ảnh qua Zalo',
         [('Do you want Zalo instead?', 'Đúng, dùng Zalo')], 'send_photo_zalo'),
        ('choose_recipient_in_app', 'Tôi muốn gửi một ảnh qua Zalo',
         [('Bác muốn gửi ảnh cho ai?', 'Tôi sẽ chọn người nhận trong Zalo')], 'send_photo_zalo'),
        ('correct_misunderstood_content', 'Tôi muốn chia sẻ với An qua Zalo',
         [('Bác muốn gửi tin nhắn chữ?', 'Không, tôi muốn gửi ảnh')], 'send_photo_zalo'),
        ('explicit_text_only', 'Tôi chỉ muốn gửi tin nhắn chữ qua Zalo, không gửi ảnh', [], 'other'),
        ('ambiguous_contact', 'Tôi muốn liên lạc với người thân', [], 'unclear'),
    ]
    results = []
    for name, goal, dialogue, expected in cases:
        if args.case and name != args.case:
            continue
        frame = copy.deepcopy(fixture)
        frame.pop('task', None)
        frame['language'] = 'vi'
        frame['goal'] = {'text': goal, 'answers': [a for _, a in dialogue],
                         'dialogue': [{'question': q, 'answer': a} for q, a in dialogue]}
        start = time.monotonic()
        try:
            decision = planner.plan(frame)
            intent, status = decision.get('intent'), decision.get('status')
            passed = intent == expected and (
                status in ('clarify', 'step', 'await') if expected == 'send_photo_zalo'
                else status == ('unsupported' if expected == 'other' else 'clarify'))
            if name in ('photo_without_details', 'app_confirmation', 'choose_recipient_in_app'):
                passed = passed and decision.get('step_id') not in ('attach', 'photo', 'send', 'delivery')
            result = {'case': name, 'intent': intent, 'status': status, 'passed': passed}
        except Exception as error:
            code = str(error) if isinstance(error, CoreError) else 'provider_or_validation_error'
            result = {'case': name, 'passed': False, 'error': code}
        result['elapsed_ms'] = round((time.monotonic() - start) * 1000)
        results.append(result)
        print(json.dumps(result), flush=True)
    report = {'source': 'live_spark_owned_synthetic_fixture_not_phone_test', 'cases': results,
              'passed': all(r['passed'] for r in results), 'phone_send_photo': 'TBD'}
    if not results:
        parser.error('Unknown case')
    suffix = '-' + args.case if args.case else ''
    (root / ('build/outputs/intent-live-regression' + suffix + '.json')).write_text(json.dumps(report, indent=2))
    raise SystemExit(0 if report['passed'] else 1)


if __name__ == '__main__':
    main()
