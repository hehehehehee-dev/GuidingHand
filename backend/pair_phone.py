"""Temporary USB development pairing only; never reads or transfers the Meta provider key."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import time
import urllib.request
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(); parser.add_argument('--serial', required=True)
    parser.add_argument('--candidate', action='store_true')
    parser.add_argument('--cloud', action='store_true', help='leave the temporary USB transport without changing the saved session token')
    parser.add_argument('--language', choices=('vi', 'en')); args = parser.parse_args()
    package = 'vn.guidinghand.candidate' if args.candidate else 'vn.guidinghand'
    component = package + '/vn.guidinghand.MainActivity'
    port = 8768 if args.candidate else 8766
    root = Path(__file__).resolve().parent
    adb = str(Path(os.environ.get('LOCALAPPDATA', '')) / 'Android/Sdk/platform-tools/adb.exe')
    token = None
    if not args.cloud:
        token = (root / '.state/client-token').read_text().strip()
        request = urllib.request.Request(f'http://127.0.0.1:{port}/core/capabilities', headers={'Authorization': 'Bearer ' + token})
        with urllib.request.urlopen(request, timeout=5) as response:
            capabilities = json.load(response).get('voice_input', {})
            if capabilities.get('model') != 'muse-voice-transcribe-1.0' or capabilities.get('status') != 'adapter_wired_not_live_verified':
                raise RuntimeError('speech_gateway_not_ready')
    def run(parts):
        result = subprocess.run([adb, '-s', args.serial] + parts, capture_output=True, timeout=15)
        if result.returncode or b'Error' in result.stdout:
            raise RuntimeError('phone_pairing_failed')
        return result.stdout
    if not args.cloud:
        run(['reverse', f'tcp:{port}', f'tcp:{port}'])
    # A credential-free challenge initializes the private nonce on a fresh install.
    # No session, sharing permission or model setting changes until the later authenticated intent.
    run(['shell', 'am', 'start', '-n', component, '--ez', 'configureCoreDev', 'true'])
    nonce = None
    for _ in range(8):
        try:
            xml = ET.fromstring(run(['exec-out', 'run-as', package, 'cat', 'shared_prefs/GuidingHand.xml']))
            nonce = next((item.text for item in xml if item.get('name') == 'devPairNonce'), None)
            if nonce: break
        except Exception: pass
        time.sleep(.3)
    if not nonce: raise RuntimeError('development_build_not_ready')
    # CLEAR_TOP + SINGLE_TOP delivers the authenticated configuration through onNewIntent
    # without force-stopping the app (which can disable Accessibility on some Xiaomi builds).
    settings = ['shell', 'am', 'start', '-f', '0x24000000', '-n', component,
                '--ez', 'configureCloudDev' if args.cloud else 'configureCoreDev', 'true', '--es', 'devPairNonce', nonce]
    if token: settings += ['--es', 'backendSession', token]
    if args.language: settings += ['--es', 'devLanguage', args.language]
    run(settings)
    time.sleep(.4)
    xml = ET.fromstring(run(['exec-out', 'run-as', package, 'cat', 'shared_prefs/GuidingHand.xml']))
    stored = {item.get('name'): item.text if item.tag == 'string' else item.get('value') for item in xml}
    if ((not args.cloud and stored.get('coreBackendSession') != token) or
            (args.cloud and stored.get('devUsbBridge') == 'true') or
            (args.language and stored.get('language') != args.language)):
        raise RuntimeError('development_configuration_not_applied')
    print('Cloud backend selected; saved session token and user setup preserved.' if args.cloud else
          'Temporary USB development connection paired. Microphone and sharing still require on-device consent.')


if __name__ == '__main__':
    try: main()
    except Exception as error:
        print(json.dumps({'pairing': 'failed', 'error_type': type(error).__name__})); raise SystemExit(1)
