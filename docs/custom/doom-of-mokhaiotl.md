# Doom of Mokhaiotl

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

`DoomRewards` is the single native drop-table roll path for kills and samples:
delve conditions, bonuses, transforms, quantity scaling and guaranteed demon
tears all apply. Uniques use the upstream table gates and rates. Real kills update
Doom counters; test rolls do not change counters, collection log or run state.

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
- `::doomsim 100 8`: the same safe ground sampler.

Use melee/demonbane equipment for the shield mechanics. Samples alone cannot
validate rotations or timings; test shallow, shield, burrowing and deep delves,
then claiming, chest collection, death, leaving and starting another run.

## Validation and status

326 selected server tests and 89 Nero tests pass. The isolated candidate starts,
publishes live bridge snapshots, exposes the game listener and exits with its
isolated database cleaned up. Automated suites cover delve/spec/rotation rules, real-cache symbol resolution,
reward scaling and run-state restoration, atomic storage, administrator dispatch
and boss DSL behaviour. The paired Nero update displays actual current/base NPC
combat-stat changes and fixes the three bare clue-item commands. Clues remain
parked at 40/60; Mimic is not implemented.

The release evidence records the exact JAR/cache/plugin hashes and isolated
startup results. The existing concurrent database-close warning remains a
separate persistence follow-up (#281); process cleanup is verified, save/drain
ordering is not certified by this encounter test. This is a candidate awaiting live encounter and visual acceptance;
source integration is not a claim that every animation has been visually certified.
