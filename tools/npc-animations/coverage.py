"""Count individual native combat-animation actions, never merely touched NPC rows."""
import argparse
from collections import Counter
import json
from pathlib import Path


def coverage(audit, report):
    patches = {p['id']: p['params'] for p in report['patches']}
    review = {r['npc']: r for r in report['review']}
    counts, rows = Counter(), []
    for npc in audit['npcs']:
        if not npc['attackable']:
            continue
        actions = {}
        remaining = review.get(npc['symbol'], {})
        for key in ('attack_anim', 'defend_anim', 'death_anim'):
            param = str(audit['params'][key])
            if param in (npc['params'] or {}):
                state = 'existing'
            elif key in patches.get(npc['id'], {}):
                state = 'mapped'
            elif (key == 'defend_anim' and remaining.get('missing') == [key]
                  and not remaining['candidates'].get(key)
                  and remaining['movement']
                  and not any(m.startswith('human') for m in remaining['movement'])):
                # Named native family has no block; generic incoming hits correctly
                # emit nothing. Keep this explicit in the catalogue for visual review.
                state = 'no_named_native_block'
            else:
                state = 'open'
            actions[key] = state
            counts[state] += 1
        rows.append({'id': npc['id'], 'npc': npc['symbol'], 'name': npc['name'], 'actions': actions})
    total = sum(counts.values())
    return {'revision': 240, 'attackableNpcTypes': len(rows), 'actionSlots': total,
            'counts': dict(counts), 'technicalActionCoveragePercent': round(100 * (total-counts['open']) / total, 2),
            'fullyMappedNpcTypes': sum(all(s != 'open' for s in r['actions'].values()) for r in rows),
            'measurement': 'attack, block, death; excludes visual acceptance and new boss special/phase mechanics',
            'npcs': rows}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    for name in ('audit', 'report', 'output'):
        p.add_argument('--'+name, type=Path, required=True)
    args = p.parse_args()
    result = coverage(json.loads(args.audit.read_text()), json.loads(args.report.read_text()))
    args.output.write_text(json.dumps(result, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({k: v for k, v in result.items() if k != 'npcs'}))


if __name__ == '__main__':
    main()
