import base64
from decimal import Decimal
import io
from pathlib import Path
import json
import tempfile
import unittest
import wave
from unittest.mock import patch

from gateway import Budget
from speech_input import SpeechInput, decode_audio, MODEL, upload, speech_settings


def clip(seconds=1, rate=16000, channels=1):
    out = io.BytesIO()
    with wave.open(out, 'wb') as wav:
        wav.setnchannels(channels); wav.setsampwidth(2); wav.setframerate(rate)
        wav.writeframes(b'\x01\x00' * int(seconds * rate * channels))
    return base64.b64encode(out.getvalue()).decode()


def frame(language='vi', keywords=None):
    return {'request_id': 'speech-test', 'fingerprint': 'a'*64, 'consent': True, 'unlocked': True,
            'language': language, 'speech_keywords': keywords or [],
            'goal': {'text': 'Nhận lời nói', 'answers': []},
            'screen': {'package_name': 'test.launcher', 'complete': True,
                       'nodes': [{'id': 'n0', 'text': 'Apps', 'password': False, 'clickable': True, 'selected': False, 'region': None}]},
            'audio_base64': clip()}


class SpeechInputTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.calls = []
        self.budget = Budget(Path(self.temp.name), Decimal('5'))
        def transport(key, audio, language, keywords):
            self.calls.append(audio)
            return {'transcript': 'Gửi ảnh cho An', 'audioDurationMs': 1000}
        self.speech = SpeechInput('fixture-not-a-key', self.budget, transport)
    def tearDown(self): self.temp.cleanup()
    def test_returns_only_transcript_from_input_model_and_settles_budget(self):
        result = self.speech.transcribe(frame())
        self.assertEqual('transcribed', result['status']); self.assertEqual(MODEL, result['model'])
        self.assertEqual('Gửi ảnh cho An', result['transcript']); self.assertEqual(1, len(self.calls))
        self.assertNotIn('audio', result); self.assertNotIn('fixture-not-a-key', str(result))
    def test_sensitive_locked_incomplete_or_unconsented_screen_stops_before_upload(self):
        for change in ['sensitive', 'locked', 'incomplete', 'consent']:
            value = frame()
            if change == 'sensitive': value['screen']['nodes'][0]['password'] = True
            elif change == 'locked': value['unlocked'] = False
            elif change == 'consent': value['consent'] = False
            else: value['screen']['complete'] = False
            self.assertEqual('blocked', self.speech.transcribe(value)['status'])
        self.assertEqual([], self.calls); self.assertFalse(self.budget.file.exists())
    def test_invalid_format_and_long_recordings_are_not_uploaded(self):
        for audio in ['invalid', clip(rate=8000), clip(channels=2), clip(seconds=21)]:
            value = frame(); value['audio_base64'] = audio
            self.assertEqual('blocked', self.speech.transcribe(value)['status'])
        self.assertEqual([], self.calls)
    def test_budget_limit_prevents_any_upload(self):
        self.speech.budget = Budget(Path(self.temp.name), Decimal('.001'))
        self.assertEqual('blocked', self.speech.transcribe(frame())['status'])
        self.assertEqual([], self.calls)

    def test_official_asr_transport_biases_language_and_has_no_synthesis(self):
        requests=[]
        class Opener:
            def open(self, request, timeout):
                requests.append(request)
                return io.BytesIO(json.dumps({'transcript':'Gửi ảnh cho An'}).encode())
        with patch('speech_input.urllib.request.build_opener',return_value=Opener()):
            result=upload('fixture-key',b'fixture-wave','vi',['Zalo','Minh'])
        self.assertEqual('Gửi ảnh cho An',result['transcript'])
        self.assertEqual('https://api.meta.ai/v1/asr/transcribe',requests[0].full_url)
        multipart=requests[0].data
        settings=json.loads(multipart.split(b'\r\n\r\n',1)[1].split(b'\r\n--',1)[0])
        self.assertEqual(MODEL,settings['model']);self.assertEqual(['Vietnamese'],settings['languageBias'])
        self.assertEqual(['Zalo','Minh'],settings['keywords'])
        self.assertNotIn('voice',settings);self.assertNotIn('tts',requests[0].full_url)

    def test_language_is_validated_and_biases_the_expected_language(self):
        self.assertEqual(['Vietnamese'], speech_settings('vi', [])['languageBias'])
        self.assertEqual(['English'], speech_settings('en', ['Instagram'])['languageBias'])
        self.assertEqual(['Zalo'], speech_settings('vi', [' Zalo ', 'zalo'])['keywords'])
        for language, keywords in [('fr', []), ('vi', ['x'] * 33), ('vi', ['bad\nword'])]:
            with self.assertRaises(Exception): speech_settings(language, keywords)

    def test_cannot_read_and_sensitive_are_distinct_without_transcription(self):
        value=frame();value['screen']['complete']=False
        self.assertEqual('cannot_read',self.speech.transcribe(value)['reason_category'])
        value=frame();value['screen']['nodes'][0]['text']='OTP'
        self.assertEqual('sensitive',self.speech.transcribe(value)['reason_category'])
        self.assertEqual([],self.calls)


if __name__ == '__main__': unittest.main()
