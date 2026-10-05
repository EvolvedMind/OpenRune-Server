# Treasure Trails - implementation in progress

User scope: full trails, including steps and puzzles; scroll boxes must produce
clue scrolls, not rewards. Master caskets can produce a Mimic encounter.
The accepted boss/item-crafting runtime is unchanged. This branch is not a release.

## Implemented foundation

- `::testloot kraken`, default 100 rolls, bounded 1-1000; native Kraken drop table.
- Cache-backed catalog: 997 non-tutorial clue/challenge records, all six tiers.
  Catalog coverage is not gameplay completion coverage.
- Atomic box-to-scroll conversion and item-local persistent step progress.
- Location/NPC/dig dispatch, equipment/stat checks, numeric challenge answers,
  key consumption and kill-based key collection.
- Sliding and light puzzle logic, native interfaces and item-owned board state.
- Physical puzzle boxes are issued atomically, reopen from inventory and remain
  associated with their clue after item serialization. Hand-in consumes only the
  matching box; a full inventory cannot partially issue a puzzle or change its phase.
- Eight explicitly named cache map interfaces are linked to their matching clue
  items (easy 006, hard 006-007, medium 008-012). Remaining map associations are
  unresolved; map clue coverage is not complete.
- Guardian ownership and kill association, emote ordering/Uri and hot/cold devices.
- Music clues check the requested currently playing track at Cecilia. Charlie's
  eight hand-ins consume the requested item atomically. Six Sherlock gathering
  tasks and two crafting tasks use successful skilling events after assignment.
  Elite Sherlock introductions assign a persistent task without advancing the trail;
  revisiting or reloading cannot reroll that assignment. Remaining tasks are tracked in
  [clue-task-coverage.md](clue-task-coverage.md).
- Master runite, anglerfish and redwood tasks require all outfit pieces in their
  correct worn slots at production time, supporting mixed accepted variants.
  Bonus products do not complete tasks; removing gear afterwards preserves success.
- Sherlock combat tasks recognize ordinary dust devils, Slayer Tower nechryaels
  and overworld lizardman shamans through the existing death kill-credit hook.
  Only the credited player's assigned task is marked complete. The Nechryael task
  checks the NPC's location through the shared area checker. Superior and raid
  variants remain unvalidated.
- 66 weighted reward tables with 743 named cache items. Persistent pending reward
  inventory is separate from Barrows' temporary display inventory.
- `::cluerewards` retrieves pending rewards; collection log integration.

## Required before release

- Finish and exercise Sherlock/Charlie tasks, Falo assignment, torn master parts,
  map-art associations and Watson exchange. Exercise physical puzzle UI in-game.
- Complete Mimic eligibility, private encounter, retries, mechanics and reward bonus
  last, after clue gameplay, as requested by the user.
- Verify guardian combat styles/animations, quest restrictions, step selection,
  completion counters, and full-inventory/reconnect/death paths end to end.
- Apply and audit cache patches, test persistent reward storage with real packed
  definitions, validate native interfaces and run isolated server startup.
- Regression test Barrows dig routing and contextual handler fallback.
- Build a reviewed test installer; user acceptance precedes merge.

## Validation so far

27 clue tests pass; the previous crafting regression run passed 16 tests. Coverage:
boxes/state/catalog, puzzle invariants and physical box
ownership/capacity/reopening, reward data and
6,000 seeded casket rolls, temperature boundaries, pending reward reclaim and
contextual selector isolation/unregistration, native map content and Close script.
Sherlock tests cover persistent elite assignment without advancing or rerolling,
correct post-assignment products, bonus/wrong/zero products and skill requirements.
Outfit cases cover every missing slot, inventory-only equipment, accepted mixed
variants, Varrock armour substitution and persisted completion after unequipping.
Combat task tests cover assignment, credited-player isolation, ordinary NPC variants,
wrong NPCs, Slayer requirements, repeated kills and NPC-based tower area checks.
Crafting tests verify success events occur after actual output insertion, never on
missing ingredients or failed insertion, alongside the existing boss-item recipes.
The storage test caught and fixed
automatic unnoting caused by an Always-stack inventory: escrow uses Normal stacking.
These tests do not establish
complete gameplay support or prove runtime dependency injection/startup.
Kraken command suite: 17 tests passed separately.

## References

- Reward data: oldschooljs commit `c930f5c41da9407600541ee76020661c09e67501`;
  redistribution notice in the module's `THIRD_PARTY_NOTICES.md`.
- Cache database cluehelper tables supply targets, outfits and questions.
- Temperature thresholds cross-checked against RuneLite `HotColdTemperature.java`:
  https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/cluescrolls/clues/hotcold/HotColdTemperature.java
- Device behavior: https://oldschool.runescape.wiki/w/Strange_device

## Mimic asset investigation (encounter not implemented yet)

Read-only scan of the existing server map cache confirmed the strange casket
(`loc.trail_mimic_enabler`, 34733) at (1645,3569,1), with Search as its first option.
The arena keyhole object (34727) is at (2719,4311,1), with Use and Exit options.
Arena wall/corner objects 34720-34732 occupy the same region. Use these actual
map objects when implementing entry and instance placement; do not infer a new
location or replace the map. Boss/minion assets are already present, but their
encounter logic and animation configuration still need implementation/validation.

## Compatibility

`inv.trail_pending_rewards` is permanent and owns undelivered rewards.
`inv.trail_rewardinv` remains temporary and is only a display projection for clues;
Barrows retains its existing use of that inventory. Closing a clue interface never
clears Barrows' display inventory. The clue puzzle inventory becomes permanent.
Contextual selectors unregister on plugin shutdown; unmatched interactions follow
their original route. No live server installation has been performed.

### Herblore clue production hooks (2026-10-05)

Finished potion and barbarian mix production now publish completion after successful inventory output. Sherlock accepts super defence (normal or chemistry extra dose), anti-venom doses 1-4, and a freshly made ranging mix. Buying, spawning, decanting, or merely possessing a potion does not publish this event. Assignment, wrong-product, bonus-product and return-to-Sherlock state checks are covered by clue tests. Actual queued brewing and live UI acceptance remain to be validated. Mimic remains last.

### Firemaking clue hooks (2026-10-05)

Yew, magic and redwood Sherlock tasks listen to successful ground-log ignition. Both existing tinderbox and bow paths reach this completion point; unsuccessful attempts do not. Assignment, wrong log, skill requirements and idempotent completion are tested through the event bus. Actual queued ignition, campfire compatibility and live acceptance still need validation.

### Runecrafting clue hooks (2026-10-05)

Nature and multiple-cosmic tasks observe successful standard/daeyalt altar output. Cosmic requires a base multiplier of at least two, not merely a batch containing multiple runes. Assignment, wrong rune, zero essence, Ourania exclusion and single-multiplier batch rejection are tested through the event bus. Actual altar interaction and live acceptance remain pending. The master blood-altar task is covered by the later altar-identity extension below.

### Assignment and skill expansion (2026-10-05)

Charlie now assigns one of eight requests without advancing the trail. Hand-in accepts unnoted items from any source, following Jagex's 30 November 2022 change; historical notes requesting self-production are superseded. Falo assigns a riddle and checks its equipment group without consuming the shown item. Watson stores partial tier deposits and only clears them after successfully delivering a master clue. Banked masters and full inventories preserve the deposit.

Cooking/smithing success events connect swordfish, mithril 2h swords and rune med helms. The initial Fletching module implements yew-longbow stringing and rune-dart feathering with atomic inventory transactions, level requirements and production events. This does not implement the entire Fletching skill. Chivalry activation and nickel mining have task completion hooks.

Hot/cold introductions assign a search location and device atomically, retain the completed-step count, and cannot reroll by repeating the conversation. Existing devices have their previous reading cleared for a new assignment. Failed inventory insertion retains the introduction. Random clue selection enters these searches through their NPC introduction rather than bypassing it.

Live acceptance remains pending. Remaining skill actions, quest/location requirements, native map associations and full puzzle routes must still be completed before Mimic. No installer or accepted runtime was changed.

The blood-altar extension recognizes successful blood-rune output from the two native Blood Altars, excluding Ourania, missing altar identity and zero consumed essence. Boxes now reject a duplicate tier held in inventory or bank without consuming the box. Quest requirements use the existing server policy (including virtual completions), with Making History checked for master hot/cold searches and the cached stage threshold checked for Lletya.

Validation for this expansion: 43 Treasure Trails tests and 3 Fletching tests passed; Runecrafting and Prayer Tab compile. Log: work/clues-assignment-final.log. These checks do not replace live gameplay acceptance.

### Location and outfit checks (2026-10-05)

The whip challenge checks actual worn state on equip, the existing Slayer Tower area, and an ordinary abyssal demon on the same floor within ten tiles. Accepted weapon variants follow [RuneLite SkillChallengeClue](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/cluescrolls/clues/SkillChallengeClue.java). The ten-tile proximity rule still needs live parity validation; it is not an assertion of an extracted OSRS server boundary.

The light-orb challenge uses the existing native Crafting recipe (empty light orb plus cave goblin wire). The production event must occur at level 0, x 2701-2707, z 5345-5354, inside the public bank room. Bounds were checked against cache-240 booths and surrounding walls (work/clue-bank-map.log); the exact clue-script boundary still requires live acceptance. Making an empty orb, working outside the room or on another floor does not complete it.

Juna and the Mage of Zamorak count three appropriate worn items using the cache outfit lists. The prior generic slot loop incorrectly required every listed slot. Inventory-only items and two worn pieces are rejected; ordinary exact outfits retain their slot requirements.

Location/outfit validation: all 47 clue tests pass in work/clues-locations-outfits.log. The unchanged Fletching module's 3 tests passed in the preceding validation.

### Forty skill tasks: gem theft and sacred eels (2026-10-05)

The Ardougne gem stall now uses the existing theft/spotter flow with level 75,
408 base XP, a 100-tick restock, and sapphire/emerald/ruby/diamond weights
105/17/5/1. Successful insertion publishes the stall symbol and coordinates.
Sherlock's master task accepts only the native `loc.gemthiefstall` at
2667,3303,0 (verified against revision-240 map data in `work/clue-market-map.log`).
The restock timer is rechecked after the animation to prevent simultaneous
attempts awarding multiple gems from the same stock.

Knife-on-sacred-eel opens the standard skill production menu and atomically
replaces one eel with scales, retaining the knife. Cooking 72 is required.
Scale ranges are 3-5, 4-6, 5-7, 6-8 and 7-9 at levels 72, 80, 88, 96 and 104;
base XP is 100 plus 3 per scale. A distinct dissection source prevents unrelated
Zulrah dismantling or spawned scales from completing the task. Both tasks require
assignment and retain the current step until returning to Sherlock.

References: [Jagex's May 2024 stall rebalance](https://secure.runescape.com/m=news/project-rebalance-skilling--poll-81-mta-changes?oldschool=1),
[gem stall rates and Jagex attribution](https://osrsindex.com/wiki/gem-stall?site=osrs_wiki),
[eel production and Mod Ash's level-band clarification](https://osrsindex.com/wiki/sacred-eel?site=osrs_wiki).

Scope is task completion and these production actions, not the entire Thieving
or Fishing skill. Sacred-eel fishing access/spot mechanics, live stall spotting,
UI/timing acceptance and exact engine behaviour remain live validation items.
Twenty skill tasks and the other documented trail gaps remain before Mimic.
