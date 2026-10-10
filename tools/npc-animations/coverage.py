"""Count individual native combat-animation actions, never merely touched NPC rows."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path


def coverage(audit, report, lifecycle=None):
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
            elif key == 'death_anim' and npc['symbol'] in (lifecycle or {}).get('deaths', {}):
                # Explicit runtime death-queue profiles, not unused cache metadata.
                state = 'runtime_multipart_death'
            elif npc['symbol'] in (lifecycle or {}).get('combatForms', {}):
                state = 'runtime_combat_form'
            elif npc['symbol'] in (lifecycle or {}).get('staticTargets', {}):
                state = {'attack_anim': 'runtime_non_retaliating_target',
                         'defend_anim': 'static_no_block',
                         'death_anim': 'runtime_static_removal'}[key]
            elif key in (lifecycle or {}).get('acceptedRoutes', {}).get(npc['symbol'], {}).get('roles', []):
                state = 'accepted_existing_script_route'
            elif key in (lifecycle or {}).get('reviewedRoutes', {}).get(npc['symbol'], {}).get('roles', []):
                state = 'reviewed_existing_script_route'
            else:
                state = 'open'
            actions[key] = state
        if (actions['defend_anim'] == 'open'
            and actions['attack_anim'] != 'open' and actions['death_anim'] != 'open'
            and 'defend_anim' in remaining.get('missing', [])
            and not remaining['candidates'].get('defend_anim') and remaining['movement']
            and 0 not in remaining.get('framebases', [])
            and not any(m.startswith('human') for m in remaining['movement'])):
            # A runtime multipart death completes the death role as well. Human
            # base 0 never becomes "no block" just because its stance is aliased.
            actions['defend_anim'] = 'no_named_native_block'
        counts.update(actions.values())
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
    p.add_argument('--lifecycle', type=Path, help='Profiles consumed by NpcAnimationDeathScript')
    args = p.parse_args()
    lifecycle = json.loads(args.lifecycle.read_text(encoding="utf-8")) if args.lifecycle else None
    if lifecycle:
        repo = Path(__file__).resolve().parents[2]
        for route in list(lifecycle.get('acceptedRoutes', {}).values()) + list(lifecycle.get('reviewedRoutes', {}).values()):
            for source in route['sources']:
                assert hashlib.sha256((repo/source['path']).read_bytes()).hexdigest() == source['sha256'], source['path']
    result = coverage(json.loads(args.audit.read_text(encoding="utf-8")), json.loads(args.report.read_text(encoding="utf-8")),
                      lifecycle)
    args.output.write_text(json.dumps(result, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({k: v for k, v in result.items() if k != 'npcs'}))


if __name__ == '__main__':
    main()
