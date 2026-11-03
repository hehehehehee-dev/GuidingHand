"""Muse Voice Transcribe input only. Audio stays in memory; provider key stays server-side."""
import base64
from decimal import Decimal
import io
import json
import logging
import secrets
import time
import urllib.request
import wave

from core import CoreError, NoRedirect, validate, message

MODEL = 'muse-voice-transcribe-1.0'
LOG = logging.getLogger('guidinghand.voice')


def decode_audio(value):
    try:
        raw = base64.b64decode(value, validate=True)
        if not 44 < len(raw) <= 960_044:
            raise ValueError()
        with wave.open(io.BytesIO(raw), 'rb') as wav:
            if wav.getnchannels() != 1 or wav.getsampwidth() != 2 or wav.getframerate() not in (16000, 24000) or wav.getcomptype() != 'NONE':
                raise ValueError()
            seconds = wav.getnframes() / wav.getframerate()
            pcm = wav.readframes(wav.getnframes())
            if not .2 <= seconds <= 20 or len(pcm) != wav.getnframes() * 2:
                raise ValueError()
        return raw, seconds
    except Exception:
        raise CoreError('invalid_voice_recording') from None


def speech_settings(language, keywords):
    if language not in ('vi', 'en'):
        raise CoreError('invalid_language')
    if not isinstance(keywords, list) or len(keywords) > 32:
        raise CoreError('invalid_speech_keywords')
    clean = []
    for keyword in keywords:
        if not isinstance(keyword, str) or not 1 <= len(keyword.strip()) <= 64 or '\n' in keyword or '\r' in keyword:
            raise CoreError('invalid_speech_keywords')
        value = keyword.strip()
        if value.casefold() not in {item.casefold() for item in clean}:
            clean.append(value)
    # Muse Voice detects the spoken language when languageBias is omitted. This lets an elder
    # switch between Vietnamese and English without first changing a Settings control.
    settings = {'model': MODEL, 'mode': 'PUSH_TO_TALK', 'audioEncoding': 'WAV'}
    if clean:
        settings['keywords'] = clean
    return settings


def upload(key, audio, language, keywords):
    boundary = 'guidinghand-' + secrets.token_hex(16)
    settings = json.dumps(speech_settings(language, keywords), ensure_ascii=False).encode()
    body = (('--' + boundary + '\r\nContent-Disposition: form-data; name="request"\r\nContent-Type: application/json\r\n\r\n').encode()
            + settings + ('\r\n--' + boundary + '\r\nContent-Disposition: form-data; name="audio"; filename="voice.wav"\r\nContent-Type: audio/wav\r\n\r\n').encode()
            + audio + ('\r\n--' + boundary + '--\r\n').encode())
    request = urllib.request.Request('https://api.meta.ai/v1/asr/transcribe', data=body,
        headers={'Authorization': 'Bearer ' + key, 'Content-Type': 'multipart/form-data; boundary=' + boundary,
                 'Accept': 'application/json'}, method='POST')
    with urllib.request.build_opener(NoRedirect()).open(request, timeout=35) as response:
        data = response.read(32_001)
    if len(data) > 32_000:
        raise CoreError('speech_response_too_large')
    return json.loads(data)


class SpeechInput:
    def __init__(self, key, budget, transport=upload):
        self.key, self.budget, self.transport = key, budget, transport

    def transcribe(self, frame):
        echo = {'request_id': frame.get('request_id', '')} if isinstance(frame, dict) else {}
        started = time.monotonic()
        try:
            validate(frame)  # Same consent, lock, complete-tree and sensitive-screen gate as planning.
            audio, seconds = decode_audio(frame.get('audio_base64'))
            keywords = frame.get('speech_keywords', [])
            # Validate language and vocabulary before reserving credit or sending audio.
            speech_settings(frame.get('language', 'vi'), keywords)
            def reserve(data, spent, pending):
                if spent + pending + Decimal('.01') > self.budget.limit:
                    raise CoreError('budget_limit')
                token = secrets.token_hex(16)
                data['pending'][token] = '.01'; data['requests'] += 1
                return token
            token = self.budget.transaction(reserve)
            validate(frame)  # Recheck immediately before the external speech-input call.
            result = self.transport(self.key, audio, frame.get('language', 'vi'), keywords)
            transcript = result.get('transcript') if isinstance(result, dict) else None
            if not isinstance(transcript, str) or not 1 <= len(transcript.strip()) <= 500:
                raise CoreError('no_transcript')
            def settle(data, spent, _pending):
                if token not in data['pending']:
                    raise CoreError('missing_reservation')
                del data['pending'][token]
                # Documented $0.18/hour, conservatively use full submitted duration.
                data['spent_usd'] = str(spent + Decimal(str(seconds)) * Decimal('.18') / Decimal(3600))
                data['updated_unix'] = int(time.time())
            self.budget.transaction(settle)
            elapsed = max(0, round((time.monotonic()-started)*1000))
            LOG.info('voice_input status=transcribed total_ms=%d', elapsed)
            return {**echo, 'status': 'transcribed', 'model': MODEL, 'transcript': transcript.strip(), 'timings_ms': {'transcribe': elapsed}}
        except Exception as error:
            code = str(error) if isinstance(error, CoreError) else 'speech_unavailable'
            category = 'sensitive' if code=='sensitive_screen' else 'cannot_read' if code in ('incomplete_screen', 'invalid_nodes', 'unlabeled_screen') else 'unavailable'
            elapsed = max(0, round((time.monotonic()-started)*1000))
            LOG.info('voice_input status=blocked category=%s total_ms=%d', category, elapsed)
            description = message(frame, 'Chưa đọc rõ màn hình. Chưa gửi âm thanh.', 'I cannot read the screen clearly. No audio was uploaded.') if category=='cannot_read' else message(frame, 'Màn hình có thông tin riêng tư. Đã dừng nhận giọng nói.', 'This screen contains private information. Speech input is paused.') if category=='sensitive' else message(frame, 'Chưa nghe rõ lời nói. Bấm mic để nói lại nhé.', 'I could not hear that clearly. Tap the microphone to try again.')
            return {**echo, 'status': 'blocked', 'reason': code, 'reason_category': category,
                    'message_vi': description,
                    'timings_ms': {'transcribe': elapsed}}
