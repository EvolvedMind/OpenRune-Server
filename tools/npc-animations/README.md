# Revision-240 NPC animation import

This is an offline, parameter-only content pack. Runtime NPC combat, damage,
cooldowns, stats, models, drops and encounter scripts remain unchanged. Native
sequence IDs and timing come from the accepted paired cache. Never import the
reference package's cache over the installed one.

`NativeAnimationAudit.java` exports decoded client sequences/framebases, server
NPC definitions, item weapon models and native mappings. Compile it against an
accepted runtime `server.jar`, then run in that runtime's **isolated copy**.
`NativeModelAudit.java` additionally uses the official RuneLite cache **1.13.1**
JAR (`https://repo.runelite.net/net/runelite/cache/1.13.1/cache-1.13.1.jar`) to
compare exact mesh geometry and rigid hand bindings. The RuneLite dependency is
an offline research tool; it is not added to the server. `NativeWeaponSheet.java`
renders selected native meshes for visual weapon classification. Supply an array
of model IDs as its first argument and an output PNG path as the second.

```text
python tools/npc-animations/export_scope.py --output scope.json
python tools/npc-animations/build_overlays.py --audit baseline.json --scope scope.json --models models.json --output content/other/npc-animations/pack/src/main/resources/pack/configs/npc-combat-animations.toml --report overlays.json --runemonk /path/to/RuneMonk-Data
python tools/npc-animations/coverage.py --audit baseline.json --report overlays.json --output coverage.json
python -m unittest discover -s tools/npc-animations -p test_overlays.py
```

Existing parameters and NPCs referenced by boss modules are excluded by resolved
numeric ID, including aliases. Full-model references, named variant actions and
unanimous exact weapon-model matches precede family selection. Shared human
skeletons alone never determine a weapon. Ambiguous actions remain in the review
report. A death animation with zero server ticks is rejected before it can reach
the suspending native death route. `weapon-model-profiles.json` records the
separately rendered weapon meshes and their canonical weapon action classes.

Build the cache **before** starting tests; parallel cache writes and reads are
unsafe. Export a new audit from the rebuilt copy and compare it:

```text
python tools/npc-animations/verify_overlays.py --before baseline.json --after candidate.json --report overlays.json --output preservation.json
```

`PatchNpcAnimations.java` takes compiled checkout, accepted runtime copy, marked
`npc-animations-runtime-stage` and overlays JSON as its four arguments. It patches
only the selected files in **SERVER 2/58**, then compares every other client/server
archive and all other NPC files against the accepted runtime. It refuses unmarked
or identical source/target directories. Use this selective copy for a test
package; a full cache build is not a replacement for the installed client cache.

Coverage counts individual **attack/block/death action slots**. It separately
records existing actions, new mappings, named families without a native block,
and open actions. A touched NPC row is not a completed NPC. `no_named_native_block`
means the generic incoming-hit route correctly leaves the block absent; it is
listed for visual review. Coverage does not certify live visuals, new boss phases
or specials. The current acceptance state belongs in [PROGRESS.md](../../PROGRESS.md).
