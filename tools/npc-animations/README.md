# Revision-240 NPC animation import

The offline importer adds only missing animation parameters to the paired rev240
cache. Its runtime module also handles reviewed emergence/combat forms, static
targets and multipart deaths. Standard damage, rewards, NPC identity, stats and
accepted encounter controllers are preserved. Native assets and timings remain
unchanged; never import the reference cache over the installed cache.

The optional death API carries native corpse forms through all death parts and
restores the original visual type before native removal, respawn and rewards. Its
existing public entry points remain unchanged. Tests exercise actual create, hit,
retaliation and death-queue producers, rather than publishing expected events.

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
python tools/npc-animations/coverage.py --audit baseline.json --report overlays.json --lifecycle tools/npc-animations/lifecycle-profiles.json --output coverage.json
python tools/npc-animations/validate_profiles.py --audit baseline.json --lifecycle tools/npc-animations/lifecycle-profiles.json --output lifecycle-validation.json
python -m unittest discover -s tools/npc-animations -p test_overlays.py
```

Existing parameters are never overwritten. NPCs referenced by boss modules are
excluded by resolved numeric ID, including aliases. Explicit owner additions fill
only missing KBD block/death and the three GWD blocks; their attack scripts stay
unchanged. Owner candidates remain in the review output for source auditing. Full-model references, named variant actions and
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
`npc-animations-runtime-stage` or `npc-animations-lifecycle-runtime-stage`, and overlays JSON as its four arguments. It patches
only the selected files in **SERVER 2/58**, then compares every other client/server
archive and all other NPC files against the accepted runtime. It refuses unmarked
or identical source/target directories. Use this selective copy for a test
package; a full cache build is not a replacement for the installed client cache.

Coverage counts individual **attack/block/death action slots**. It separately
records existing actions, new mappings, named families without a native block,
runtime lifecycle handlers, accepted/reviewed existing script routes, and open
actions. Source review is distinct from owner visual acceptance. A touched NPC row is not a completed NPC. `no_named_native_block`
means the generic incoming-hit route correctly leaves the block absent; it is
listed for visual review. Coverage does not certify live visuals, new boss phases
or specials. The current acceptance state belongs in [PROGRESS.md](../../PROGRESS.md).

`lifecycle-profiles.json` records the native forms, multipart sequences and hashed
controller sources. `validate_profiles.py` checks native IDs, positive durations,
framebases, exact spawn bodies, source hashes and handler ownership. The exact
unresolved actions/candidates are in
[the evidence inventory](../../docs/custom/npc-animation-open-actions.json).

The runtime JAR must accompany lifecycle changes. The frozen 90.46% package was
data-only and cannot install this module. A source cache build alone does not
certify actor visuals, every combat style, new boss phases or special effects.
