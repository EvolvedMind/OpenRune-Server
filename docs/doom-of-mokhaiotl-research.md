# Doom of Mokhaiotl: capture and implementation scope

Research date: 29 September 2026 (Europe/Brussels).
Official server baseline: `OpenRune/OpenRune-Server` main
`065ebc9eb2fe0e5070b925d5aac254edf20443ea` (28 September 2026).
This is a research record; no Doom encounter is implemented by this update.

## Supplied evidence

Capture: `recording-20260929-010949Doom.npack`, 165,424 bytes.
SHA-256: `6d6779c7e77386c75e79b74690ffa82c04447b66a874f898413355fbddbe15c8`.
The ZIP-format schema 0.1 capture contains 1,222 events over 208 ticks / 124.877
seconds and is not marked truncated. All 78 included binary blobs pass their hash
checks. Twenty-eight animation definitions explicitly have `portableBinary:false`;
the archive is a reference recording, not a complete portable asset bundle.

The supplied image shows **Level 9 Complete**, accumulated rewards and the
**Claim & Leave / Descend** choices. The recording itself contains no interface,
varbit, inventory, loot, item-spawn or health-bar events. Its delve number and
runtime maximum HP cannot be established from the capture. A 500-HP definition
does not establish the actual HP of the recorded fight.

## Observed fight

References below are one-based lines in `observations/events.ndjson` inside the
archive. Numeric IDs are evidence to map to current cache symbols, not IDs to
hardcode in gameplay scripts.

| Observation | Evidence |
| --- | --- |
| Arena mapping | All 1,385 static entity mappings agree: template X = runtime X - 12,624; template Z = runtime Y - 208 |
| Boss | NPC 14707, 5x5 footprint, template southwest `(1309, 9571, 0)`; no movement or form changes observed |
| Spawn | Sequence 12418 at tick 0; first attack at tick 7 |
| Ordinary actions | Sequence 12416 mostly at six-tick intervals; 12406 followed one tick later by projectiles 3378/3379/3380 |
| Boulder actions | Sequence 12407, projectiles 3384/3385 and subsequent eight-direction scatter 3389-3395 |
| Larvae | Six NPC 14710 spawns |
| Persistent obstacles | Object 57286 appears at ticks 28, 69, 92 and 133; removed at tick 185 |
| Death | Tick 176, line 1125; death sequence 12422 at tick 177, line 1130 |
| Exit hole | Object 57285 at tick 185, lines 1167-1191: one 5x5 object reported on 25 footprint tiles, not 25 separate objects |
| Boss despawn | Tick 186, line 1194; recording continues through tick 208 |

The static capture includes 928 terrain entries, 203 game objects, 156 ground
objects, 90 walls and eight decorations. Use the existing native arena and an
instance copy. Do not paste this scenery into the server map or create one object
per repeated footprint event.

Observed timings and asset identities are useful references. Hidden accuracy,
damage formulas, random selection, all failure branches and drop probabilities
must come from separately specified gameplay rules, not from replaying hits.

## Existing server support and gaps

- `content/drops/.../DoomOfMokhaiotlDropTable.kt` already registers `npc.dom_boss`.
- NPC dump symbols include `dom_boss`, `dom_boss_shielded`, `dom_boss_burrowed`,
  demonic-energy variants, `dom_shockwave_path_node` and `dom_shockwave_shield`.
- The Dom pet (`obj.dompet`, `npc.dom_pet`), its dialogue, collection-log category
  and Mokhaiotl cloth crafting already exist.
- There is no native Doom fight, instance row, progression manager or delve
  reward-interface handler in this baseline.
- Reuse `InstanceScript` / `InstanceManager`, native combat and NPC death hooks.
  The existing Muspah, Vardorvis and Zulrah modules provide examples of forms,
  hazards, cancellation, ownership and death/loot lifecycle handling.

The existing Doom drop table is **not ready for real delve progression**. Its
unique gates use placeholder quest names (`quest_delvelevel2anddeeper`,
`quest_delvelevel3anddeeper`, `quest_delvelevel4anddeeper`). Under the default
`AssumeCompleted` quest policy all these gates pass, including at delve 1; under
`RespectProgress` they fail because they are not registered quests. Replace these
conditions with actual run/delve state before enabling encounter rewards. Avoid
adding a second drop table or a second pet roll.

## First playable slice

Implement a native `content/bosses/doom-of-mokhaiotl` module with a pack sibling:

1. Private existing-map arena, entry, exit and owner/session lifecycle.
2. One playable fight using live player inputs and native damage, prayers,
   collision, projectiles, larvae and destructible/persistent obstacles.
3. Victory cleanup and one reward decision: claim and leave. Reserve the run
   state and screen structure for later delves; do not enable a nonfunctional
   Descend action or substitute an automatic boss respawn.
4. Real reward eligibility, collection-log and single pet-roll handling, with
   repeated-claim protection and defined death/logout behavior.
5. Tests covering attacks and hazards, duplicate death/loot, cancellation,
   instance isolation, claim flow and exclusion of later-delve rewards.

The recorded fight and a canonical delve-1 fight are not proven identical.
Keep mechanics keyed to an explicit delve configuration. Further levels and
scaling remain deferred until the initial fight is tested, as requested.

## Primary references

- [Alora Doom release and subsequent fixes](https://www.alora.io/forums/topic/98008-101025-varlamore-bosses-doom-of-mokhaiotl-delve-hueycoatl/):
  difficulty increases through delve 8; later delves repeat that difficulty.
  The player chooses between claiming accumulated loot and risking it by
  descending. The article documents projectile, boulder, larva, shield, charge,
  shockwave and acid mechanics, with later-level additions identified.
- [Jagex launch](https://secure.runescape.com/m=news/varlamore-the-final-dawn-out-now?oldschool=1):
  continuous solo runs and the claim/descend structure.
- [Jagex combat changes and drop progression](https://secure.runescape.com/m=news/varlamore--summer-sweep-up-combat-tweaks?oldschool=1):
  supplementary OSRS rules. Do not present OSRS drop rates as Alora's rates.

Look up the current official interface/component and clientscript definitions
before implementing the reward screen, as required by `AGENTS.md`.
