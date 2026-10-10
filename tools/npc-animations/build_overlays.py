"""Build explicit revision-240 animation patches from a paired-cache audit.

Run with --audit <NativeAnimationAudit.json> --scope <scope.json> --output <file>.
The audit is an offline input, never loaded by the game server. Existing parameters
and scripted bosses are excluded; compatibility alone never chooses between attacks.
"""
import argparse
from collections import defaultdict, Counter
import json
from pathlib import Path
import re

ROLES = ("attack_anim", "defend_anim", "death_anim")
TOKENS = {
    "attack_anim": ("attack", "melee", "attack_melee", "bite", "punch", "kick", "slash", "stab", "crush", "shoot", "cast", "claw", "lunge", "pound"),
    "defend_anim": ("defend", "block", "parry"),
    "death_anim": ("death", "die"),
}
MOTION = re.compile(r"_(?:combat_)?(?:ready|idle|walk|wander|stand|run|flying|fly)[0-9]*(?:_|$)")
VARIANT_MOTION = re.compile(r"_(ready|idle|walk)(?=[0-9]*(_|$))")
EXCLUDED = re.compile(r"(^|_)(special|spec|loop|reverse|fake|hold|cutscene|quick|fast|merge|move|sound|quiet|detonate|enrage|transition|then)(_|$)")

# Named canonical actions, reviewed against the movement variant and native frames.
# Missing roles stay absent: a model without a block action must not play a human block.
PROFILES = {
    "dragon_ready": ("dragon_attack", "dragon_block", "dragon_death"),
    "demon_ready": (None, "demon_block", None),
    "troll_ready": (None, "troll_block", "troll_death"),
    "troll_ready_shield": ("troll_attack", "troll_block", "troll_death"),
    "troll_ready_sword": ("troll_attack", "troll_block", "troll_death"),
    "troll_ready_sword+shield": ("troll_attack", "troll_block", "troll_death"),
    "giant_rat_update_ready": ("giant_rat_update_attack", "giant_rat_update_defend", "giant_rat_update_death"),
    "dog_update_wolf_ready": (None, "dog_update_wolf_defend", "dog_update_wolf_death"),
    "skeleton_update_ready": (None, "skeleton_update_defend", "skeleton_update_death"),
    "slice_surface_goblin_squat_ready": (None, "slice_surface_goblin_defend", "slice_surface_goblin_death"),
    "slice_surface_goblin_squat_ready_spear": ("slice_surface_goblin_squat_attack_spear", "slice_surface_goblin_defend_spear", "slice_surface_goblin_death_spear"),
    "slice_surface_goblin_squat_shield_spear_ready": ("slice_surface_goblin_squat_spear_attack_shield", "slice_surface_goblin_defend_spear", "slice_surface_goblin_death_spear"),
    "godwars_armadyl_ready": (None, "godwars_armadyl_defend", "godwars_armadyl_death"),
    "ork_update_weapon_ready": ("ork_update_double_grip_attack", "ork_update_defend", "ork_update_death"),
    "gnome_ready": (None, "gnome_block", "gnome_death"),
    "ogre_ready": ("ogre_attack", "ogre_block", "ogre_death"),
    "ogre_ready_new": ("ogre_attack", "ogre_block", "ogre_death"),
    "giant_update_fire_sword_ready": ("giant_update_fire_sword_attack", "giant_update_fire_sword_defend", "giant_update_fire_death"),
    "myq3_vampire_grounded_ready_male": ("myq3_vampire_grounded_attack", "myq3_vampire_grounded_defend_male", "myq3_vampire_grounded_death"),
    "myq3_vampire_grounded_ready_female": ("myq3_vampire_grounded_attack", "myq3_vampire_grounded_defend_female", "myq3_vampire_grounded_death"),
    "unicorn_rework_ready": ("unicorn_rework_attack", "unicorn_rework_defend", "unicorn_rework_death"),
    "bloodveld_ready": ("bloodveld_attack", "bloodveld_defend", "bloodveld_death"),
    "bat_rework_ready": ("bat_rework_attack", "bat_rework_defend", None),
    "horror_crab_ready": ("horror_crab_attack", "horror_crab_defend", "horror_crab_hide"),
    "small_spider_update_ready": ("small_spider_update_attack", "small_spider_update_defend", "small_spider_update_death"),
    "spider_update_ready": ("spider_update_attack", "spider_update_defend", "spider_update_death"),
    "spider_update_ready_large": ("spider_update_attack_large", "spider_update_defend_large", "spider_update_death_large"),
    "bear_rework_ready": ("bear_rework_attack", "bear_rework_defend", "bear_rework_death"),
    "pog_mutant_terror_bird_ready": ("pog_mutant_terror_bird_attack", "pog_mutant_terror_bird_defend", "pog_mutant_terror_bird_death"),
    "kalphite_ready": ("kalphite_attack_mandibles", "kalphite_block", "kalphite_death"),
    "kalphite_update_ready": ("kalphite_update_jaws_attack", "kalphite_update_defend", "kalphite_update_death"),
    "kalphite_update_lord_ready": ("kalphite_update_claw_lord_attack", "kalphite_update_defend", "kalphite_update_lord_death"),
    "sheep_update_ready": ("sheep_update_attack", "sheep_update_defend", "sheep_update_death"),
    "gargoyle_ready": ("gargoyle_attack", "gargoyle_parry", "gargoyle_death"),
    "lion_ready": ("lion_attack", "lion_block", "lion_death"),
    "ds2_stone_guardian_ready": ("ds2_stone_guardian_melee", "ds2_stone_guardian_defend", "ds2_stone_guardian_death"),
    "confready": ("confattack", "confparry", "confdeath"),
    "lessready": ("lessattack", "lessparry", "lessdeath"),
    "leechready": ("leechattack", "leechblock", "leechdeath"),
    "neckready": ("neckattack", "neckparry", "neckdeath"),
    "roosterready": ("roosterattack", "roosterparry", "roosterdeath"),
    "xbows_human_ready": ("xbows_human_fire_and_reload", "human_unarmedblock", "human_death"),
    "dorgesh_goblin_ready": ("dorgesh_goblin_unarmed_attack", "dorgesh_goblin_unarmed_defend", "dorgesh_goblin_death"),
    "dorgesh_goblin_ready_spear": ("dorgesh_goblin_club_attack", "dorgesh_goblin_spear_block", "dorgesh_goblin_death"),
    "dorgesh_goblin_ready_mining": ("dorgesh_goblin_club_attack", "dorgesh_goblin_spear_block", "dorgesh_goblin_death"),
    "olaf2_undead_ready": ("olaf2_undead_sword_lunge", "olaf2_undead_sword_def", "human_death"),
    "mummy_update_soldier_ready": ("mummy_update_sword_attack", "mummy_update_defend", "mummy_update_death"),
    "shade_ready": ("shade_attack", "shade_block", "shade_sink"),
    "myq3_vampire_flying_ready": ("myq3_vampire_flying_stab_attack", "myq3_vampire_flying_defend", "myq3_vampire_flying_death"),
    "demon_spawn_fly": ("demon_spawn_attack", "demon_spawn_parry", "demon_spawn_death"),
    "ds2_spawn_ready": ("ds2_spawn_attack", "ds2_spawn_defend", "ds2_spawn_death"),
    "lotr_terror_dog_look_ready": ("lotr_terror_dog_attack", "lotr_terror_dog_defend", "lotr_terror_dog_death"),
    "brain_barrelchest_ready": ("brain_barrelchest_normal_attack", None, None),
    "npc_djinn_01_idle": (None, None, "npc_djinn_01_ending"),
    "npc_venator_idle01": ("npc_venator_melee_attack01", None, None),
    "monalisk_cave_bug_ready": (None, None, "monalisk_mini_cave_bug_death"),
}

CASTERS = {"Dark wizard", "Wizard", "Chaos druid", "Chaos druid elder", "Salarin the twisted",
           "Invrigar the Necromancer", "Necromancer", "Witch", "Melzar the Mad", "Ancient Wizard",
           "Spiritual Mage", "Spiritual mage", "Elite Dark Mage", "Magic Mark", "Deathly mage", "Malevolent Mage",
           "Scarab Mage", "Brassican Mage", "Mercenary mage", "Emissary Conjurer", "Test Pirate Mage"}

def role(name):
    if re.search(r"(^|_)(ready|idle|walk|walking|run|stand)[0-9]*$", name):
        return None
    # A family may contain words such as 'melee' or 'spawn'. The actual death
    # or block action takes precedence: top_spider_melee_death is a death.
    for key in ("death_anim", "defend_anim", "attack_anim"):
        tokens = TOKENS[key]
        if re.search(r"(^|_)(" + "|".join(tokens) + r")[0-9]*(_|$)", name):
            return key
    return None

def build(audit, scope, model_audit=None):
    if audit["revision"] != 240:
        raise ValueError("Expected the accepted revision-240 cache")
    seq = audit["sequences"]
    names = {v: k.removeprefix("seq.") for k, v in seq.items()}
    definitions = {int(k): v for k, v in audit["sequenceDefinitions"].items()}
    def bases(id):
        return set(definitions.get(id, {}).get("framebases", []))
    base_sequences = defaultdict(set)
    for id in definitions:
        for base in bases(id):
            base_sequences[base].add(id)
    weapon_models = defaultdict(list)
    geometry_weapons = defaultdict(list)
    models = (model_audit or {}).get("models", {})
    model_profiles = json.loads(Path(__file__).with_name("weapon-model-profiles.json").read_text())["profiles"]
    defend_by_attack = defaultdict(set)
    for weapon in audit.get("weapons", []):
        p = weapon.get("params") or {}
        attack = p.get(str(audit["params"]["attack_anim_stance1"]))
        defend = p.get(str(audit["params"]["defend_anim"]))
        if attack in names and defend in names:
            defend_by_attack[attack].add(defend)
        for model in weapon["models"]:
            weapon_models[model].append(weapon)
            if str(model) in models:
                geometry_weapons[models[str(model)]["signature"]].append(weapon)
    used = {n["symbol"] for n in audit["npcs"] if n["attackable"]}
    owned = set(scope["bossSymbols"])
    owned_ids = {audit.get("npcSymbols", {}).get(symbol) for symbol in owned}
    body_references = defaultdict(lambda: defaultdict(set))
    body_motion = defaultdict(set)
    for n in audit["npcs"]:
        if not n.get("models"):
            continue
        for id in (n["stand"], n["walk"]):
            if id in names:
                body_motion[tuple(sorted(n["models"]))].add(id)
        body = (tuple(sorted(n["models"])), n["stand"], n["walk"])
        for key in ROLES:
            id = (n["params"] or {}).get(str(audit["params"][key]))
            if id in names:
                body_references[body][key].add(id)
    npcs = {n["id"]: n for n in audit["npcs"]}
    # Transformed visible definitions use their own parameters in native combat.
    frontier = [n for n in npcs.values() if n["symbol"] in used]
    while frontier:
        n = frontier.pop()
        for id in n.get("transforms") or []:
            child = npcs.get(id)
            if child and child["symbol"] not in used:
                used.add(child["symbol"])
                frontier.append(child)
            if child and (n["symbol"] in owned or n["id"] in owned_ids):
                owned.add(child["symbol"])
                owned_ids.add(child["id"])
    patches, review = [], []
    family_candidates = {}
    for n in sorted(npcs.values(), key=lambda n: n["id"]):
        if n["symbol"] not in used or n["symbol"] in owned or n["id"] in owned_ids:
            continue
        motion_ids = [id for id in (n["stand"], n["walk"]) if id in names]
        if not motion_ids:
            motion_ids = sorted(body_motion[tuple(sorted(n.get("models") or []))])
        movement = [names[id] for id in motion_ids]
        npc_bases = set().union(*(bases(id) for id in motion_ids)) if motion_ids else set()
        prefixes = {MOTION.split(name, maxsplit=1)[0] for name in movement}
        family_key = (tuple(movement), tuple(sorted(npc_bases)))
        if family_key not in family_candidates:
            pool = set().union(*(base_sequences[b] for b in npc_bases)) if npc_bases else set()
            candidates = defaultdict(list)
            for id in sorted(pool):
                name = names.get(id, "")
                key = role(name)
                other_variant = any(token in name and not any(token in m for m in movement) for token in ("champion", "thrall"))
                if key and not EXCLUDED.search(name) and not other_variant and any(name.startswith(p + "_") for p in prefixes):
                    candidates[key].append(name)
            family_candidates[family_key] = candidates
        candidates = family_candidates[family_key]
        # Exact native worn-model matches establish the held weapon, including recolours.
        # All configured items sharing that model must agree; no majority vote or ID guess.
        held = [w for m in (n.get("models") or []) for w in weapon_models[m]]
        for m in n.get("models") or []:
            if str(m) in models:
                held.extend(geometry_weapons[models[str(m)]["signature"]])
        # A weapon mesh is rigidly bound to the weapon hand. Body/arm meshes span
        # several limbs; absence is evidence only when every mesh was decoded.
        meshes = [models.get(str(m)) for m in n.get("models") or []]
        unarmed_body = bool(meshes) and all(mesh and "groups" in mesh for mesh in meshes) and not any(
            set(mesh["groups"]) in ({50}, {27}, {50, 70}, {27, 28}) for mesh in meshes)
        weapon_choices = defaultdict(set)
        for weapon in held:
            p = weapon.get("params") or {}
            for key, param in (("attack_anim", "attack_anim_stance1"), ("defend_anim", "defend_anim")):
                id = p.get(str(audit["params"][param]))
                if id in names:
                    weapon_choices[key].add(id)
        for model in n.get("models") or []:
            for key, symbol in model_profiles.get(str(model), {}).items():
                if key in ROLES:
                    weapon_choices[key].add(seq[symbol])
        if len(weapon_choices["attack_anim"]) == 1 and not weapon_choices["defend_anim"]:
            weapon_choices["defend_anim"] = defend_by_attack[next(iter(weapon_choices["attack_anim"]))]
        assignments = {}
        body = (tuple(sorted(n.get("models") or [])), n["stand"], n["walk"])
        for index, key in enumerate(ROLES):
            if str(audit["params"][key]) in (n["params"] or {}):
                continue
            selected = PROFILES.get(names.get(n["stand"]), (None, None, None))[index]
            if len(body_references[body][key]) == 1:
                selected = names[next(iter(body_references[body][key]))]
            if n["symbol"] == "npc.elite_npc_1":
                selected = ("godwars_armadyl_cannon_attack", "godwars_armadyl_defend", "godwars_armadyl_death")[index]
            if names.get(n["stand"]) == "roosterready" and "Evil Chicken" in n["name"] and key == "attack_anim":
                selected = "roostermagic"
            # Never infer a human's weapon from their shared standing skeleton.
            human = any(m.startswith("human_") for m in movement)
            if human:
                if not selected:
                    selected = "human_death" if key == "death_anim" else None
                if n["name"] in CASTERS and key == "attack_anim":
                    selected = "human_caststrike"
                if key == "defend_anim" and n["name"] in {"Ancient Wizard", "Spiritual Mage", "Magic Mark"}:
                    selected = "human_staff_block"
                if not selected and len(weapon_choices[key]) == 1:
                    selected = names[next(iter(weapon_choices[key]))]
                elif not selected and not held and (unarmed_body or n["name"] in {"Man", "Woman", "Mugger", "Monk"}):
                    selected = {"attack_anim": "human_unarmedpunch", "defend_anim": "human_unarmedblock", "death_anim": "human_death"}[key]
            else:
                stand = names.get(n["stand"], "")
                if stand == "skeleton_update_ready" and key == "attack_anim":
                    if "Mage" in n["name"] or "Mystic" in n["name"]:
                        selected = "skeleton_update_mage_casting"
                    elif "unarmed" in n["symbol"] or "miner" in n["symbol"]:
                        selected = "skeleton_update_attack_weapon"
                    elif "armed" in n["symbol"] or "shield" in names.get(n["walk"], ""):
                        selected = "skeleton_update_attack_sword"
                    elif n["name"] == "Skeleton":
                        selected = "skeleton_update_attack_weapon"
                if stand == "slice_surface_goblin_squat_ready" and key == "attack_anim":
                    if "unarmed" in n["symbol"]:
                        selected = "slice_surface_goblin_squat_unarmed_attack"
                    elif "armed" in n["symbol"] or "soldier" in n["symbol"]:
                        selected = "slice_surface_goblin_armed_attack"
                    elif n["name"] in {"Goblin", "Wormbrain", "Angry goblin", "Revenant goblin", "Skoblin", "Snothead", "Snailfeet", "Mosschin", "Redeyes", "Strongbones", "Giant goblin"}:
                        selected = "slice_surface_goblin_squat_unarmed_attack"
                if stand == "gnome_ready" and key == "attack_anim":
                    if "mage" in n["name"].lower(): selected = "gnome_mage_attack"
                    elif "archer" in n["name"].lower(): selected = "gnome_attackbow"
                    elif "guard" in n["name"].lower() or "troop" in n["name"].lower(): selected = "gnome_attacksword"
                    elif n["name"] in {"Gnome", "Gnome child", "Gnome woman", "Gnome Driver"}: selected = "gnome_attackunarmed"
                if stand == "thzaar_ready" and key == "attack_anim":
                    if "Mej" in n["name"]: selected = "thzaar_magic_attack"
                    elif "Hur" in n["name"] or "Reanimated" in n["name"]: selected = "thzaar_unarmed_attack"
                    elif "Ket" in n["name"]: selected = "thzaar_armed_attack"
                    elif "Xil" in n["name"]: selected = "thzaar_ring_attack" if 9307 in (n.get("models") or []) else "thzaar_armed_attack"
                if stand == "dog_update_small_dog_ready" and names.get(n["walk"]) == "dog_update_walk" and key != "attack_anim":
                    breed = "fox" if n["name"] == "Fox" else "jackal" if n["name"] == "Jackal" else "medium_dog"
                    selected = (f"dog_update_{breed}_attack", f"dog_update_{breed}_defend", f"dog_update_{breed}_death")[index]
                if stand == "barrow_dharok_ready":
                    selected = ("barrow_dharok_crush", "human_unarmedblock", "human_death")[index]
                if stand == "vyrelord_ready" and n["name"] == "Vyrewatch Sentinel":
                    selected = ("vyrelord_new_ground_attack", "vyrelord_ground_defend_female" if "female" in n["symbol"] else "vyrelord_ground_defend_male", "vyrelord_grounded_death")[index]
                if stand == "m_monkey_ready":
                    kind = "bow" if "Archer" in n["name"] else "doublescimitar" if names.get(n["walk"]) == "m_monkey_dh_walk" else "scimitar"
                    if key == "attack_anim": selected = f"m_monkey_attack_{kind}"
                    if key == "defend_anim": selected = f"m_monkey_block_{kind}"
                if stand == "godwars_armadyl_ready" and key == "attack_anim":
                    mesh_ids = set(n.get("models") or [])
                    if n["name"] == "Spiritual mage": selected = "godwars_spiritual_armadyl_warrior_magic"
                    elif mesh_ids & {27975, 27979, 30996}: selected = "godwars_armadyl_sword_attack"
                    elif mesh_ids & {27964, 27967, 27970, 27971, 27972, 27973, 27976, 27980, 27981, 27982, 27983, 27986}: selected = "godwars_armadyl_spear_attack"
                    elif mesh_ids & {27963, 27965, 27978, 27984}: selected = "godwars_armadyl_cannon_attack"
                if stand == "npc_mandrill_idle" and key == "death_anim":
                    selected = "npc_mandrill_explode" if n["name"] == "Volatile Baboon" else "npc_mandrill_despawn01"
                if stand == "npc_djinn_01_idle" and key == "death_anim": selected = "npc_djinn_01_ending"
                if stand == "brain_zombie_pirate_limpers_ready" and key == "death_anim": selected = "brain_zombie_pirate_drag_feet_death"
                if stand in {"ogre_dance", "ogre_drum"} and n["name"] in {"Zogre", "Skogre"}:
                    selected = ("ogre_attack", "ogre_block", "ogre_death")[index]
                if stand == "monalisk_cave_bug_ready" and key == "death_anim":
                    selected = "monalisk_giant_cave_bug_death" if n["definition"]["size"] > 1 else "monalisk_mini_cave_bug_death"
                exact = set()
                for m in movement:
                    for token in TOKENS[key]:
                        candidate = VARIANT_MOTION.sub("_" + token, m, count=1)
                        if candidate in candidates[key]:
                            exact.add(candidate)
                    if len(exact) == 1:
                        break  # The combat stance takes precedence over a shared walk variant.
                if not selected and len(exact) == 1:
                    selected = next(iter(exact))
                if not selected and len(candidates[key]) == 1:
                    selected = candidates[key][0]
            if selected:
                symbol = "seq." + selected
                if symbol not in seq:
                    raise ValueError(f"Unknown native sequence: {n['symbol']} {symbol}")
                if not (bases(seq[symbol]) & npc_bases):
                    raise ValueError(f"Incompatible native framebase: {n['symbol']} {symbol}")
                if key == "death_anim" and audit.get("serverSequences", {}).get(str(seq[symbol]), {}).get("duration", 1) <= 0:
                    # Native death() requires a positive delay; never introduce a death crash.
                    continue
                assignments[key] = symbol
        if assignments:
            patches.append({"id": n["id"], "npc": n["symbol"], "name": n["name"], "params": assignments,
                            "framebases": sorted(npc_bases), "weaponModels": sorted(m for m in (n.get("models") or []) if m in weapon_models)})
        missing = [key for key in ROLES if str(audit["params"][key]) not in (n["params"] or {}) and key not in assignments]
        if missing:
            review.append({"npc": n["symbol"], "name": n["name"], "movement": movement,
                           "missing": missing, "candidates": {key: candidates[key] for key in missing}})
    return patches, review

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--audit", type=Path, required=True)
    parser.add_argument("--scope", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--runemonk", type=Path, help="Optional user-supplied RuneMonk-Data directory")
    parser.add_argument("--models", type=Path, help="Optional exact native model geometry/limb audit")
    args = parser.parse_args()
    patches, review = build(json.loads(args.audit.read_text()), json.loads(args.scope.read_text()),
                            json.loads(args.models.read_text()) if args.models else None)
    lines = ["# Revision 240. Generated by tools/npc-animations/build_overlays.py.",
             "# Parameter-only additions: preserve stats, drops, native models and scripted bosses.", ""]
    for patch in patches:
        lines.extend(["# " + patch["name"], "[[npc_params]]", "id = " + json.dumps(patch["npc"]), "[npc_params.params]"])
        lines.extend(json.dumps("param." + key) + " = " + json.dumps(value) for key, value in patch["params"].items())
        lines.append("")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text("\n".join(lines), encoding="utf-8")
    report = {"revision": 240, "patchedNpcs": len(patches), "parameters": dict(Counter(k for p in patches for k in p["params"])),
              "patches": patches, "review": review}
    if args.runemonk:
        audit = json.loads(args.audit.read_text())
        reference = {}
        for base, groups in json.loads((args.runemonk / "Animations/CommonAnims.json").read_text()).items():
            for rows in groups.values():
                for id, name in rows:
                    reference[id] = (int(base), name)
        for base, rows in json.loads((args.runemonk / "Animations/AnimayaCommonAnims.json").read_text()).items():
            for id, name in rows:
                reference[id] = (int(base), name)
        confirmed = Counter()
        for patch in patches:
            for key, symbol in patch["params"].items():
                id = audit["sequences"][symbol]
                base, name = reference.get(id, (None, None))
                if name == symbol.removeprefix("seq.") and base in patch["framebases"]:
                    confirmed[key] += 1
        report["runemonk"] = {"sequences": len(reference), "confirmedParameters": dict(confirmed)}
    args.report.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps({key: report[key] for key in ("revision", "patchedNpcs", "parameters")}))

if __name__ == "__main__":
    main()
