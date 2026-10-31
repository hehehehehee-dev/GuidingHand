"""Local-only JPEG size/encode benchmark. It never calls a model or uploads an image."""
from __future__ import annotations

import argparse
from io import BytesIO
import json
from pathlib import Path
import time


def parse_profiles(value):
    profiles = []
    for item in value.split(','):
        edge, quality = item.split(':', 1)
        edge, quality = int(edge), int(quality)
        if not 640 <= edge <= 2000 or not 50 <= quality <= 95:
            raise ValueError('invalid_profile')
        profiles.append((edge, quality))
    if not profiles or len(profiles) > 8:
        raise ValueError('invalid_profiles')
    return profiles


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('images', nargs='+', help='consented local screenshots; never uploaded')
    parser.add_argument('--profiles', default='1600:80,1440:78,1280:75')
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    try:
        from PIL import Image
    except ImportError as error:
        raise SystemExit('Install Pillow in a separate local tooling environment to run this benchmark.') from error
    rows = []
    for source in args.images:
        with Image.open(source) as original:
            image = original.convert('RGB')
            for edge, quality in parse_profiles(args.profiles):
                scale = min(1.0, edge / max(image.size))
                resized = image.resize((max(1, round(image.width * scale)), max(1, round(image.height * scale)))) if scale < 1 else image.copy()
                began = time.perf_counter(); output = BytesIO(); resized.save(output, 'JPEG', quality=quality, optimize=True)
                rows.append({'source': Path(source).name, 'long_edge': edge, 'quality': quality,
                             'width': resized.width, 'height': resized.height, 'bytes': len(output.getvalue()),
                             'encode_ms': round((time.perf_counter() - began) * 1000, 2),
                             'sam_accuracy': 'TBD: run the real-phone ring protocol before changing production defaults'})
                resized.close()
    Path(args.output).write_text(json.dumps({'local_only': True, 'provider_calls': 0, 'results': rows}, indent=2), encoding='utf-8')
    print(json.dumps({'output': args.output, 'measurements': len(rows), 'provider_calls': 0}))


if __name__ == '__main__':
    main()
