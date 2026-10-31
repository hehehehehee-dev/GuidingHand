"""Pair a debug phone with the local gateway. Never reads the Meta provider key."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import urllib.request

from gateway import ROOT, client_token


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--serial', required=True)
    parser.add_argument('--language', choices=['en', 'vi'])
    args = parser.parse_args()
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or
               (str(Path(os.environ.get('LOCALAPPDATA', '')) / 'Android' / 'Sdk')))
    adb = shutil.which('adb') or str(sdk / 'platform-tools' / ('adb.exe' if os.name == 'nt' else 'adb'))
    token = client_token(ROOT / '.state')
    request = urllib.request.Request('http://127.0.0.1:8766/health',
                                     headers={'Authorization': 'Bearer ' + token})
    with urllib.request.urlopen(request, timeout=5) as response:
        health = json.load(response)
        if health.get('ready') is not True or not {'goal_guidance', 'cross_app', 'tree_first'}.issubset(health.get('features', [])):
            raise RuntimeError('gateway_not_ready')
    commands = [
        [adb, '-s', args.serial, 'reverse', 'tcp:8766', 'tcp:8766'],
        [adb, '-s', args.serial, 'shell', 'am', 'start', '-n', 'vn.guidinghand/.MainActivity',
         '--ez', 'configureLocalMeta', 'true', '--es', 'metaClientToken', token]]
    if args.language:
        commands[-1] += ['--es', 'guideLanguage', args.language]
    for command in commands:
        result = subprocess.run(command, capture_output=True, text=True, timeout=15)
        if result.returncode or 'Error' in result.stdout:
            raise RuntimeError('phone_pairing_failed')
    print('Local USB gateway paired. AI guidance is available in Preferences and remains opt-in.')


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print(json.dumps({'pairing': 'failed', 'error_type': type(error).__name__}))
        raise SystemExit(1)
