# Zulrah

Native Kotlin encounter using OpenRune's existing Zulrah map and assets. The module
creates a private solo instance of regions 9007 and 9008. It does not import the
recording's terrain, walls or scenery, and does not require Nero Capture Studio
at runtime.

## Enter and play

Board the sacrificial boat in Zul-Andra. The existing boat's transformed quest
variants and Quick-board option are supported. Administrators can use `::zulrah`
to enter directly and `::zulrah leave` to leave. Use the existing teleport scroll
in the arena for the ordinary exit.

Each entry creates one encounter. Zulrah has 500 hitpoints shared across all
three forms. Four rotations control the emergence locations, attacks, venom
clouds, snakelings and alternating ranged/magic phase. Player combat uses the
server's native accuracy, prayers, hit processing and venom rules. Damage, death
and rewards are calculated from the current fight, not replayed from a capture.

After a kill, collect the drops on the walkable arrival tile. Leave and board
again for a new encounter. Hazards and snakelings are removed when the fight
ends; the private map is released when the player leaves.

Items selected for loss by the server's normal PvM death rules are held in a
persistent recovery inventory. Talk to the priest in Zul-Andra to reclaim them.
Make inventory space and repeat to claim any remainder. Reentry is blocked while
items remain unclaimed. Recovery is free under this server's current balance;
the OSRS fee after 50 kills is not implemented. Another unsafe death destroys
unclaimed items. Existing keep-item, Ultimate Ironman and pet policies still
apply.

## Loot

The existing `content/drops` Zulrah table remains authoritative. One kill grants
one scales roll, two ordinary reward rolls, and the existing separate rare and
tertiary rolls. Collection log, loot tracker and pet hooks use the normal death
pipeline. The configured quantities and rare probabilities have been preserved;
they are not claimed to reproduce Alora's hidden drop rates.

`NpcDeathKillContext.dropCoords` forwards the requested ground tile to drop-table
hooks. Its default remains the NPC's tile for existing callers. This lets an
over-water boss drop accessible loot without moving the NPC or bypassing death
hooks.

## Recording and sources

Reference recording: `recording-20260927-234358-Zulrah` (Alora), 118 ticks and 508
events. It shows one 500-HP kill through ranged, melee and magic forms at the
north anchor, plus animations, projectiles and three reward stacks. Its runtime
coordinates map to source coordinates with `x - 7480`, `z - 11792`.

The recording does not contain every rotation, venom clouds, snakelings, entry,
player death, recovery or drop probabilities. Those parts are implemented
separately using native server APIs and the references in [NOTICE.md](NOTICE.md).
The four rotation action tables come from an independently implemented OSRS
simulator; timing and hazard placement are implementation choices, not a claim
of exact Alora server reconstruction.

## Build and verify

Use Java 21. Rebuild the cache after installing this module, before starting the
server, so the NPC overlays, instance row and recovery inventory exist:

```powershell
.\gradlew.bat :or-cache:mergePluginGamevals
if ($LASTEXITCODE) { throw 'Gameval merge failed.' }
.\gradlew.bat :or-cache:buildCache
if ($LASTEXITCODE) { throw 'Cache build failed.' }
.\gradlew.bat :content:bosses:zulrah:test :api:drop-table-plugin:test assemble
if ($LASTEXITCODE) { throw 'Build or tests failed.' }
```

With Nero OpenRune Studio, select the checkout containing this module and run
Setup to rebuild and deploy its cache and server. Then use PLAY OPENRUNE.
Importing or previewing the capture scene is unnecessary.

Before enabling this encounter for a public world, play through entry, all forms,
the alternating phase, dodging, victory, accessible loot, exit, death/reclaim and
logout in the target client. Automated cache and runtime tests do not establish
the final animation appearance or live-client timing.
