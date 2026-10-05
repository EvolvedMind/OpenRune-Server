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
