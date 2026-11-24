"""Check the documentation figures of every locale with the svg-diagrams skill's verify.py.

    python3 scripts/verify-figures.py <skill>/scripts/docfigures [--render DIR] [name-prefix ...]

The build writes each locale's figures under docs/<locale>/_images, and verify.py reads one folder
of figures, so this script hands it one locale at a time with the project's own settings.
"""
import argparse
import json
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def main(argv):
    parser = argparse.ArgumentParser(description='Verify the documentation figures of every locale.')
    parser.add_argument('library', help="the svg-diagrams skill's scripts/docfigures directory")
    parser.add_argument('--render', help='folder to render every figure into, one subfolder per locale')
    parser.add_argument('prefixes', nargs='*', help='figure name prefixes to check')
    args = parser.parse_args(argv)

    settings = json.loads((ROOT / '.claude' / 'document-figures.json').read_text(encoding='utf-8'))
    locales = json.loads((ROOT / 'scripts' / 'docs-config.json').read_text(encoding='utf-8'))['locales']
    verify = Path(args.library) / 'verify.py'
    failed = 0
    for locale in locales:
        per_locale = dict(settings)
        per_locale['out'] = str(ROOT / settings['out'] / locale / '_images')
        per_locale['modules'] = [str(ROOT / pattern) for pattern in settings['modules']]
        per_locale['helpers'] = [str(ROOT / pattern) for pattern in settings.get('helpers', [])]
        with tempfile.TemporaryDirectory() as folder:
            config = Path(folder) / 'document-figures.json'
            config.write_text(json.dumps(per_locale), encoding='utf-8')
            command = [sys.executable, str(verify), '--config', str(config)]
            if args.render:
                command += ['--render', str(Path(args.render) / locale)]
            command += args.prefixes
            print(f'=== {locale} ===', flush=True)
            failed += subprocess.run(command, cwd=ROOT, check=False).returncode != 0
    return 1 if failed else 0


if __name__ == '__main__':
    raise SystemExit(main(sys.argv[1:]))
