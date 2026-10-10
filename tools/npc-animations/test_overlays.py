"""Safety checks for ambiguous weapons, role names and existing script ownership."""
import unittest
from build_overlays import build, role


class OverlaySafetyTest(unittest.TestCase):
    def fixture(self):
        seq = {'seq.human_ready': 1, 'seq.human_walk_f': 2, 'seq.human_death': 3,
               'seq.human_sword_slash': 4, 'seq.human_blunt_pound': 5, 'seq.human_sword_block': 6}
        npc = dict(id=42, symbol='npc.guard_native', name='Guard', stand=1, walk=2,
                   attackable=True, transforms=None, params={}, models=[90001], definition={})
        audit = dict(revision=240, sequences=seq, npcSymbols={'npc.guard_alias': 42}, npcs=[npc],
                     sequenceDefinitions={str(id): {'framebases': [7]} for id in seq.values()},
                     serverSequences={'3': {'duration': 2}},
                     params={'attack_anim': 100, 'defend_anim': 101, 'death_anim': 102, 'attack_anim_stance1': 103})
        return audit, dict(bossSymbols=[]), npc

    def test_action_takes_precedence_over_family_word(self):
        self.assertEqual('death_anim', role('top_spider_melee_death'))
        self.assertEqual('defend_anim', role('melee_demon_block'))
        self.assertEqual('attack_anim', role('demon_spawn_attack'))
        self.assertIsNone(role('demon_spawn_fly'))

    def test_shared_human_skeleton_never_guesses_a_weapon(self):
        audit, scope, _ = self.fixture()
        patches, review = build(audit, scope)
        self.assertEqual({'death_anim': 'seq.human_death'}, patches[0]['params'])
        self.assertIn('attack_anim', review[0]['missing'])

    def test_conflicting_native_weapon_profiles_stay_unresolved(self):
        audit, scope, _ = self.fixture()
        audit['weapons'] = [dict(models=[90001], params={'103': attack, '101': 6}) for attack in (4, 5, 4)]
        patches, _ = build(audit, scope)
        self.assertNotIn('attack_anim', patches[0]['params'])
        self.assertEqual('seq.human_sword_block', patches[0]['params']['defend_anim'])

    def test_existing_animation_parameter_is_never_replaced(self):
        audit, scope, npc = self.fixture()
        npc['params'] = {'102': 99}
        patches, _ = build(audit, scope)
        self.assertFalse(patches)
        self.assertEqual({'102': 99}, npc['params'])

    def test_script_owner_alias_is_resolved_by_native_id(self):
        audit, scope, _ = self.fixture()
        scope['bossSymbols'] = ['npc.guard_alias']
        patches, review = build(audit, scope)
        self.assertEqual([], patches)
        # Ownership excludes mutation, but retains candidates for route auditing.
        self.assertEqual('npc.guard_native', review[0]['npc'])

    def test_zero_tick_death_is_not_added_to_suspending_death_route(self):
        audit, scope, _ = self.fixture()
        audit['serverSequences']['3']['duration'] = 0
        patches, review = build(audit, scope)
        self.assertFalse(patches)
        self.assertIn('death_anim', review[0]['missing'])

    def test_transform_parent_borrows_only_unanimous_visible_child_actions(self):
        audit, scope, parent = self.fixture()
        parent['models'] = None
        parent['transforms'] = [43, 44, -1]
        for id in (43, 44):
            audit['npcs'].append(dict(parent, id=id, symbol='npc.child_'+str(id),
                                     transforms=None, params={'100': 4, '101': 6, '102': 3}))
        patches, _ = build(audit, scope)
        result = next(p['params'] for p in patches if p['id'] == 42)
        self.assertEqual('seq.human_sword_slash', result['attack_anim'])
        audit['npcs'][-1]['params']['100'] = 5
        patches, _ = build(audit, scope)
        self.assertNotIn('attack_anim', next(p['params'] for p in patches if p['id'] == 42))

    def test_transform_refinement_does_not_patch_unrelated_noncombat_roots(self):
        audit, scope, npc = self.fixture()
        npc['params'] = {'100': 4, '101': 6, '102': 3}
        audit['npcs'].append(dict(npc, id=99, symbol='npc.unrelated_shopkeeper', attackable=False,
                                 params={}, models=None, transforms=[42]))
        patches, _ = build(audit, scope)
        self.assertFalse(patches)


if __name__ == '__main__':
    unittest.main()
