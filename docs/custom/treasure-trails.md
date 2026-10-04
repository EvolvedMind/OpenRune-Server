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
- Guardian ownership and kill association, emote ordering/Uri and hot/cold devices.
- Music clues check the requested currently playing track at Cecilia. Charlie's
  eight hand-ins consume the requested item atomically. Three Sherlock gathering
  tasks use successful skilling events after assignment; the other tasks remain pending.
- 66 weighted reward tables with 743 named cache items. Persistent pending reward
  inventory is separate from Barrows' temporary display inventory.
- `::cluerewards` retrieves pending rewards; collection log integration.

## Required before release

- Finish and exercise Sherlock/Charlie tasks, Falo assignment, torn master parts,
  physical puzzle boxes/reopening, map-art associations and Watson exchange.
- Complete Mimic eligibility, private encounter, retries, mechanics and reward bonus.
- Verify guardian combat styles/animations, quest restrictions, step selection,
  completion counters, and full-inventory/reconnect/death paths end to end.
- Apply and audit cache patches, test persistent reward storage with real packed
  definitions, validate native interfaces and run isolated server startup.
- Regression test Barrows dig routing and contextual handler fallback.
- Build a reviewed test installer; user acceptance precedes merge.

## Validation so far

15 focused tests pass (boxes/state/catalog, puzzle invariants, reward data and
6,000 seeded casket rolls, temperature boundaries, pending reward reclaim and
contextual selector isolation/unregistration). The storage test caught and fixed
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
