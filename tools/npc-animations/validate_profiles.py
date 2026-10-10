"""Validate native lifecycle inputs independently of the Kotlin event-route tests."""
import argparse
import hashlib
import json
from pathlib import Path


def validate(audit, lifecycle):
    npcs = {n['symbol']: n for n in audit['npcs']}
    seqs = audit['sequences']
    checked = 0

    def sequence(symbol, body=None):
        nonlocal checked
        id = seqs[symbol]
        assert audit['serverSequences'][str(id)]['duration'] > 0, symbol
        if body:
            n = npcs[body]
            bases = set().union(*(audit['sequenceDefinitions'].get(str(i), {}).get('framebases', [])
                                  for i in (n['stand'], n['walk'])))
            assert bases.intersection(audit['sequenceDefinitions'][str(id)]['framebases']), (body, symbol)
        checked += 1

    for npc, animations in lifecycle['deaths'].items():
        assert npc in npcs
        forms = lifecycle.get('deathForms', {}).get(npc)
        assert forms is None or len(forms) == len(animations), npc
        for i, symbol in enumerate(animations):
            sequence(symbol, forms[i] if forms else npc)
    for family in ('spawns', 'combatForms'):
        for npc, profile in lifecycle[family].items():
            assert npc in npcs and profile['active'] in npcs
            for part in profile['parts']:
                sequence(part['sequence'], part['npc'])
                if family == 'spawns':
                    assert npcs[npc]['models'] == npcs[part['npc']]['models'] == npcs[profile['active']]['models']
            for symbol in profile.get('death', []):
                sequence(symbol, profile['active'])
    for npc in lifecycle['staticTargets']:
        assert npc in npcs and npcs[npc]['attackable'], npc
    for family in ('acceptedRoutes', 'reviewedRoutes'):
        for npc, route in lifecycle[family].items():
            assert npc in npcs
            for symbol in route.get('animations', []):
                sequence(symbol)
            for source in route['sources']:
                assert hashlib.sha256(Path(source['path']).read_bytes()).hexdigest() == source['sha256']
    # Avoid overriding an owner/controller's existing native death or AI handlers.
    owned = set(lifecycle['acceptedRoutes']) | set(lifecycle['reviewedRoutes'])
    new_deaths = set(lifecycle['deaths']) | set(lifecycle['staticTargets']) | set(lifecycle['combatForms'])
    assert not owned.intersection(new_deaths), owned.intersection(new_deaths)
    assert not set(lifecycle['staticTargets']).intersection(lifecycle['deaths'])
    return {'revision': 240, 'nativePositiveDurationBindings': checked,
            'multipartDeathTypes': len(lifecycle['deaths']),
            'spawnTransitionTypes': len(lifecycle['spawns']),
            'combatTransitionTypes': len(lifecycle['combatForms']),
            'staticTargetTypes': len(lifecycle['staticTargets']),
            'sourceHashesVerified': True, 'visualAcceptance': False}


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--audit', type=Path, required=True)
    p.add_argument('--lifecycle', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    a = p.parse_args()
    result = validate(json.loads(a.audit.read_text(encoding="utf-8")), json.loads(a.lifecycle.read_text(encoding="utf-8")))
    a.output.write_text(json.dumps(result, indent=2)+'\n')
    print(json.dumps(result))
