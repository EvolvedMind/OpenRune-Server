"""Export quoted NPC references, resolving aliases later through the paired-cache audit."""
import argparse
import json
from pathlib import Path
import re
import subprocess


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--repo', type=Path, default=Path('.'))
    p.add_argument('--output', type=Path, required=True)
    args = p.parse_args()
    files = subprocess.check_output(['rg', '--files', '.data/raw-cache/map', 'content'], cwd=args.repo, text=True).splitlines()
    buckets = {k: set() for k in ('mapSymbols', 'scriptSymbols', 'bossSymbols')}
    pattern = re.compile(r'["\'](npc\.[A-Za-z0-9_]+)["\']')
    for rel in files:
        path = Path(rel)
        if path.suffix not in ('.kt', '.toml') or any(x in path.parts for x in ('build', 'test', 'npc-animations')):
            continue
        symbols = pattern.findall((args.repo/path).read_text(encoding='utf-8'))
        if 'map' in path.parts:
            buckets['mapSymbols'].update(symbols)
        else:
            buckets['scriptSymbols'].update(symbols)
        if 'bosses' in path.parts:
            buckets['bossSymbols'].update(symbols)
    args.output.write_text(json.dumps({k: sorted(v) for k, v in buckets.items()}, indent=2)+'\n', encoding='utf-8')


if __name__ == '__main__':
    main()
