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

### Delve-specific drop chances — PR #30, 2026-10-09

PR #30 corrects the delve-sensitive
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

Source: [Jagex's 6 August 2025 announcement](https://secure.runescape.com/m=news/varlamore--summer-sweep-up-combat-tweaks?oldschool=1),
including both the rare-rate tables and elite-clue change. The owner authorized
updating, implementing and merging [PR #30](https://github.com/EvolvedMind/OpenRune-Server/pull/30)
on 2026-10-09. Validation now uses the real project APIs and production drop table,
including the native DoomRewards path and active-level restoration. See PROGRESS.md
for final validation/merge/installation status.

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
- `::maxmage`: supplies Confliction gauntlets instead of a tormented bracelet;
  the rest of the loadout, including Shadow's 20,000 charges, is unchanged.
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

## PR #30 validation — 2026-10-09

234 selected project tests pass: drops (15), Doom (36), native drop APIs (9),
commands (20), Collection Log (8), gorillas (14) and clues (132). The five new
rate/preview tests use the real APIs; an additional production-table test drives
the actual DoomRewards roller at shallow/deep levels with saturated boosts,
checks exclusive unlocked uniques/independent Dom and restores the active delve.
Scoped formatting, the runtime JAR and isolated revision-240/Nero startup pass.
The candidate uses the exact installed cache/mappings/world overlay; all three
gorilla access objects and removal of the native arch remain correct. Server
and private PostgreSQL stop cleanly. No new cache assets or client changes are
needed, and no live Doom install is claimed by these checks.

PR #30 merged as `4d9d974d10ab9fbff0570583d060d49c8b769c82`; its tree matches
tested head `a3bfe1c746b04c7cea4f80bd99ee95869055b2bd`. Full local `spotlessCheck`
and the native gameval conflict checker pass. GitHub Actions is disabled for the
repository; manual dispatch returned HTTP 422 with that explicit reason, so no
online CI result is claimed. The guarded `doom-delve-rates-20261009` installer
replaces only the changed source/docs and runtime JAR. Cache, mappings, paired
Nero plugins, world edits, RSA and playerdata are preserved. Its rollback restores
the exact matched gorilla package; live installation remains a separate action.

## Mokhaiotl waystone — 2026-10-09

The existing revision-240 item `obj.dom_teleport_item` (31099) has a native
first inventory option, **Channel**. `DoomWaystone` now handles that actual option:
a standard native teleport animation/effect runs for four game ticks, then
teleports to `DoomArena.LOBBY` `(1311,9551,0)`. This is the pre-lair; entry through
the gap still starts the existing private encounter. No quest requirement is
added to the fork's accepted Doom access policy.

Normal teleport validation applies, including late denial. A prepared native
inventory transaction consumes exactly one held waystone only after the player
reaches the destination. Cancellation, death, logout/disconnection, displacement,
a replaced inventory stack, unavailable destination or rejected final teleport
preserve the item. The existing instance leave/reward lifecycle remains native;
the waystone does not manually rewrite run state or reward inventories.

The native `obj.confliction_gauntlets` (31106) is the hands item in `::maxmage`.
No equipment stats, drops, delve rates or other loadout entries are changed.

Upstream comparison: the default-branch search and relevant closed/merged
[OpenRune PR #275](https://github.com/OpenRune/OpenRune-Server/pull/275), merged
as `fc10877fd39600637506de645f584467c11039c3`, reference the waystone in the
drop table without a held-item handler. The local native handler fills that
gap; no upstream branch or unrelated changes are imported. The OSRS Wiki item
page could not be read because access was blocked. The chosen ordinary teleport
animation is verified in this cache, not asserted as an exact OSRS waystone
animation reproduction.

Validation: **101 selected tests** pass (Doom 43, commands 21, drops 15, gorillas
14, Collection Log 8), including seven new actual HeldInteractions producer
tests and the native maxmage command test. Prepared consumption, initial/late
denial, stale references, death/logout/disconnect, cancellation and unavailable
destinations are covered. Full `spotlessCheck`, gameval conflict check and the
runtime JAR pass. Final runtime/package evidence is recorded in PROGRESS.md.
Isolated rev240/Nero startup uses the exact installed PR #30 cache/mappings/world
overlay, resolves Channel and wearable gauntlets, and confirms a walkable Doom
lobby. The accepted gorilla hole/sign/rope and removal of the old arch remain
correct. Server and private PostgreSQL stop cleanly. In-game visual acceptance
of the native standard teleport effect remains a manual check.

[PR #31](https://github.com/EvolvedMind/OpenRune-Server/pull/31) merged as
`59a71addd718db3d449511cb604fa64a1653785f`; its tree matches tested head
`b152effda75f55d815c9c33a0fe9e4139f0a5abc`. The guarded
`doom-waystone-maxmage-20261009` installer changes only the feature source/docs
and tested runtime JAR, with rollback to the exact installed PR #30 package.
No cache assets, mappings, Nero plugins, world edits, RSA or playerdata are
replaced. There is no online CI result: GitHub Actions is disabled for this repo.
