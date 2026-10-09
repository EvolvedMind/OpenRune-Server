# Doom of Mokhaiotl

Current status: [PROGRESS.md](../../PROGRESS.md). Accepted source and paired recovery
package: [baseline](baseline.md).

Origin: UPSTREAM + CUSTOM EXTENSIONS. Integrated from
[OpenRune fc10877fd (Doom #275)](https://github.com/OpenRune/OpenRune-Server/commit/fc10877fd39600637506de645f584467c11039c3),
with the required expanded boss DSL from #268/#269. Revision remains 240.
Implementation: `content/bosses/doom-of-mokhaiotl`; cache overrides live in its
`pack` child. Existing accepted boss scripts and weapon rules remain in place.

## Encounter

The native gap enters a private instance. Delves 1-8 select distinct encounter
specifications; 9 and later reuse the deep-delve specification while the actual
level and counters continue increasing. The upstream rotations include normal
attacks and tongue, rock throws and shrapnel, volatile-earth shockwaves and their
path nodes, larvae and prayer immunities, acid/venom, demonic shield and beam
charging, demonbane interruption, burrowing rush/trample/slams and post-kill holy
water. Models, sequences, projectiles, headbars and sound symbols use the existing
revision-240 cache; NPC combat stats and vars are packed explicitly.

Only the player's initialized instance boss may be attacked. Spawn callbacks and
holy-water callbacks check instance ownership. The last occupant leaving destroys
the arena. Death discards the unclaimed run pile and respawns at the lobby; loot
already claimed into the chest remains. View distance is enlarged only in Doom
and restored on leaving. Admin delve selection is available only in dev mode.

## Rewards

### Delve-specific drop chances (candidate, 2026-10-09)

The accepted Doom package is unchanged. This feature branch corrects the delve-sensitive
reward table without modifying combat, saved loot, collection logs or reward quantity scaling.

- Delves 2/3/4 unlock cloth, Eye of Ayak and Avernic treads, respectively.
  Each available item rolls at 1/2,500 (delve 2), 1/2,000 (3), 1/1,350 (4),
  1/810 (5), 1/765 (6), 1/720 (7), 1/630 (8), then 1/540 at delve 9+.
- The three principal uniques share an exclusive selection roll; a kill cannot
  independently award multiple main uniques. The server/player drop-rate multiplier
  still affects the roll, capped at one main unique per kill.
- Dom rolls separately at 1/1,000 (6), 1/750 (7), 1/500 (8), 1/250 (9+).
  Elite clues roll separately at 1/75 (1-2) or 1/50 (3+), unboosted as before.
- Existing common drops, demon tears, delve quantity multipliers, native claim/escrow
  and ::testloot/::doomsim all retain their existing code paths. Preview reports
  the initial available rate, not a live per-player/per-delve rate.

Sources: [Jagex August 2025 drop-rate announcement](https://steamdb.info/patchnotes/19467992/)
and [the OSRS Wiki Doom of Mokhaiotl](https://oldschool.runescape.wiki/w/Doom_of_Mokhaiotl).
This is a source/test candidate, **not** a new installed runtime or completed
in-game acceptance. Run the new DoomDelveBonusRollsTest plus existing Doom reward
regressions, then full build/boot before user approval.

`DoomRewards` is the single native drop-table roll path for kills and samples:
delve conditions, bonuses, transforms, quantity scaling and guaranteed demon
tears all apply. The correction uses the level-specific OSRS bonus table above.
Real kills update Doom counters. Test rolls preserve counters and run state. On user request (2026-10-06),
`::testloot doom` registers successfully spawned Collection Log rewards, including repeats;
`::doomsim` remains an unlogged simulation. Both preserve earned/claimed reward piles.

Both reward inventories are persistent, stacked, server-only 40-slot piles. The
native steel/stone reward interface has a 40-slot scrolling item grid. Complete
roll insertion and earned-to-claimed transfers use strict atomic transactions;
if a transfer cannot fit, both piles are preserved and collection-log progress is
not granted. Repeating claim cannot duplicate rewards. Inventory/bank collection
uses normal partial-transfer behaviour. Claiming ends further descent for that
run. Prior claimed rewards remain in the chest for collection.

## Administrator tests

- `::testdoom`: teleport to the lobby, then use the native gap to enter.
- `::testloot doom`: 100 ground-sample kills at delve 8.
- `::testloot doom 100 3`: 100 rolls at delve 3; counts/delves 1-1000.
- `::doomsim 100 8`: ground samples without Collection Log grants; run state/counters stay intact.

Use melee/demonbane equipment for the shield mechanics. Samples alone cannot
validate rotations or timings; test shallow, shield, burrowing and deep delves,
then claiming, chest collection, death, leaving and starting another run.

## Recorded validation — 2026-10-06

326 selected server tests and 89 Nero tests pass. The isolated candidate starts,
publishes live bridge snapshots, exposes the game listener and exits with its
isolated database cleaned up. Automated suites cover delve/spec/rotation rules, real-cache symbol resolution,
reward scaling and run-state restoration, atomic storage, administrator dispatch
and boss DSL behaviour. The paired Nero update displays actual current/base NPC
combat-stat changes and includes the three bare clue-item command fixes.
That package includes a partial clue milestone, not complete Treasure Trails.

The release evidence records the exact JAR/cache/plugin hashes and isolated
startup results. The existing concurrent database-close warning remains a
separate persistence follow-up (#281); process cleanup is verified, save/drain
ordering is not certified by this encounter test. The user accepted the tested encounter/HUD package and approved merge on
2026-10-06;
this acceptance is not a claim of exhaustive OSRS parity.

Merged on 2026-10-06: server PR #23 / c2ccd5c762f98fa24568b66458edad814be1c1f0;
paired Nero PR #11 / 85af63cedb2067b3b82c63ed86b0e55048c0399d.
Those merges identify the Doom/HUD checkpoint. Later gameplay changes, including
the testloot logging correction, are tracked in [baseline](baseline.md).
