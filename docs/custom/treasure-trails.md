# Treasure Trails implementation

Origin: custom implementation over native clue cache rows, interfaces, inventory
transactions and skill events.

Current work and priorities: [PROGRESS.md](../../PROGRESS.md).
Task-by-task support: [coverage](clue-task-coverage.md).
The requested scope is full hunts, steps and puzzles: scroll boxes create clue scrolls.
Master-casket Mimic eligibility and the encounter come after the remaining clue gameplay.

## Foundation

- Native cache catalog: 997 non-tutorial clue/challenge records across six tiers.
  Catalog rows do not establish complete playable routes.
- Atomic box-to-scroll conversion and item-local persistent step progress. A held
  or banked clue of the same tier blocks a new scroll without consuming its box.
- Contextual location/NPC/dig dispatch, equipment/stat checks, numeric answers,
  key consumption and kill-based key collection. Unmatched interactions retain
  their original routing; selectors unregister only at plugin shutdown.
- Sliding/light puzzles use native interfaces and item-owned board state. Physical
  boxes serialize their clue ownership, reopen from inventory and are consumed
  only by exact matching slot at hand-in. Full inventories cannot partially issue
  a puzzle or advance its phase.
- Eight named cache maps are linked: easy 006, hard 006–007 and medium 008–012.
  The remaining native map associations still require completion/verification.
- Emote/Uri ordering, music-at-Cecilia checks and guardian kill association.
  [Guardian completion](clue-guardians.md) preserves same-cycle owner metadata and
  requires every credited kill before a new dig advances once or grants one casket.
- 66 weighted reward tables reference 743 named cache items. `::cluerewards`
  retrieves undelivered rewards through the Collection Log integration.

## Assignment and progression rules

Charlie assigns one of eight requests without advancing the trail. Hand-in consumes
one matching unnoted item from **any source**; self-production is not required.
This follows the [30 November 2022 Jagex change](https://secure.runescape.com/m=news/the-garden-of-death--more?oldschool=1).
Sherlock assigns a persistent task; repeat dialogue/reload cannot reroll it, and successful
actions retain the step until the player returns to Sherlock.

Falo assigns a persistent riddle and checks the equipment group without consuming the
shown item. Watson persists partial tier deposits; a full inventory or banked master
clue preserves deposits until a master can be delivered atomically.

Hot/cold introductions assign a search location and device atomically, clear the device's
previous reading and retain completed-step count. Failed insertion preserves the
introduction; repeat talk cannot reroll. Random selection enters through the appropriate
NPC introduction. Quest requirements follow the existing policy, including virtual
completions, Making History for master hot/cold and the cached Lletya stage threshold.

## Skill action contracts

The [coverage table](clue-task-coverage.md) owns the exact supported/pending task list.

| Action | Required event/guard |
|---|---|
| Gathering and crafting | Successful primary output after assignment; wrong, zero and bonus products do not count. |
| Outfit gathering | Required slots worn when output is produced; mixed supported variants and Varrock armour 4 mining-top substitution work. Removing gear later preserves completion. |
| Combat | Existing credited-player kill hook; ordinary dust devil, Slayer Tower nechryael and overworld shaman variants. Check NPC location where required; superior/raid variants remain unvalidated. |
| Herblore | Successful finished-potion/mix output: super defence including chemistry extra dose, anti-venom doses 1–4 and freshly made ranging mix. Buying, spawning, decanting or possession is insufficient. |
| Firemaking | Successful ground ignition through tinderbox/bow; yew, magic and redwood tasks. Campfire tending remains outside this hook. |
| Runecrafting | Successful standard/daeyalt output with consumed essence and base multiplier. Double cosmic requires multiplier >=2, not a large batch. Blood output requires either native Blood Altar; exclude Ourania, missing altar identity and zero essence. |
| Cooking/smithing | Successful swordfish, mithril 2h sword and rune med helm output; burnt/failed output does not count. |
| Fletching | Native yew-longbow stringing and rune-dart feathering with levels, atomic transactions and output events; this is not the full skill. |
| Equipment/prayer/mining | Successful Dragon scimitar equip, actual Chivalry activation (including quick prayers) and nickel output after assignment. |

### Location and outfit boundaries

The whip task checks actual worn state, Slayer Tower and an ordinary same-floor abyssal
demon within ten tiles. Supported whip/ornament/tentacle variants follow
[RuneLite SkillChallengeClue](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/cluescrolls/clues/SkillChallengeClue.java).
The ten-tile rule still needs live parity validation.

The light-orb task uses empty light orb + cave goblin wire inside the public
Dorgesh-Kaan bank at level 0, x 2701–2707, z 5345–5354. The room was checked against
revision-240 booths/walls (`work/clue-bank-map.log`); the exact clue-script boundary
remains an acceptance item. Empty-orb production, other floors and outside rooms fail.

Juna and the Mage of Zamorak require three appropriate worn items from the cache outfit
lists, not every listed slot. Inventory-only/two-item cases fail; exact outfits retain
their ordinary slot requirements.

### Gem theft and sacred eels

The native Ardougne `loc.gemthiefstall` at (2667,3303,0) uses level 75, 408 base XP,
100-tick restock and sapphire/emerald/ruby/diamond weights 105/17/5/1. Restock is checked
again after the animation to prevent concurrent double rewards. Successful insertion
reports the symbol and coordinates; only that native stall completes the assigned task.

Knife-on-sacred-eel uses the standard production menu and atomically replaces an eel
with scales while retaining the knife. Cooking 72 is required. Scale ranges are 3–5,
4–6, 5–7, 6–8 and 7–9 at levels 72, 80, 88, 96 and 104; base XP is 100 + 3 per scale.
A distinct source excludes spawned scales and Zulrah dismantling. Both tasks retain
the step until return to Sherlock. Native eel fishing access/spot mechanics remain separate.

References: [Jagex stall rebalance](https://secure.runescape.com/m=news/project-rebalance-skilling--poll-81-mta-changes?oldschool=1),
[recorded stall data](https://osrsindex.com/wiki/gem-stall?site=osrs_wiki),
[recorded eel data and level-band clarification](https://osrsindex.com/wiki/sacred-eel?site=osrs_wiki).

## Persistence and native compatibility

`inv.trail_pending_rewards` permanently owns undelivered rewards and uses Normal stacking
to avoid automatic unnoting. `inv.trail_rewardinv` is only the temporary display projection;
closing clues never clears Barrows' use of that inventory. Puzzle inventory is permanent.
Keep Barrows mound routing, contextual fallbacks and atomic full-inventory transactions.

## Remaining validation and implementation

Finish unsupported task actions, torn master parts, remaining map/puzzle/world routes
and full hunts. Exercise guardian styles/animations, quest/location rules, step selection,
counters, reward reclaim and inventory/reconnect/death paths end to end.
Confirm queued skilling actions, native interfaces, stall spotting and the noted location
boundaries. Add Mimic eligibility, private encounter, retries, mechanics and reward bonus
after those routes. Follow the project validation/acceptance workflow for each new slice.

The existing cache places the strange casket `loc.trail_mimic_enabler` (34733) at
(1645,3569,1), Search option; the arena keyhole (34727) at (2719,4311,1), Use/Exit options;
and walls/corners 34720–34732 in the same region. Preserve these native placements.
Boss/minion assets alone do not provide encounter logic or verified animation configuration.

## Recorded evidence

The 2026-10-05 candidate recorded 54 clue, 3 Cooking, 2 Thieving and 3 Fletching tests,
full cache/server build, isolated startup/Nero bridge and installer/rollback checks.
Bare-command fixes increased clue coverage to 55 tests; the later guardian fix recorded
61 clue tests within the approved 134-test package. See [guardian evidence](clue-guardians.md)
and [exact accepted integration/package](baseline.md). These are recorded checkpoints,
not new tests or certification of complete clues.

Coverage includes physical puzzle ownership, 6,000 seeded casket rolls, temperature
boundaries, pending rewards, fallback/unregistration, native map Close scripts, assignment
retention, credited kills, real inventory output and outfit requirements.
Administrator commands and their limits live in [clue-testing.md](clue-testing.md).

Reward source: oldschooljs `c930f5c41da9407600541ee76020661c09e67501`, with the module's
`THIRD_PARTY_NOTICES.md`. Cluehelper cache tables supply targets, outfits and questions.
Temperature boundaries were checked against
[RuneLite HotColdTemperature](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/cluescrolls/clues/hotcold/HotColdTemperature.java);
device reference: [Strange device](https://oldschool.runescape.wiki/w/Strange_device).
