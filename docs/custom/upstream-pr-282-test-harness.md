# Upstream test harness review (2026-10-06)

Research only: no upstream files imported. Current guardian/news changes remain on
`fix/clue-guardian-completion`, stacked on the preserved PR #24 candidate.

## Source and findings

[OpenRune PR #282](https://github.com/OpenRune/OpenRune-Server/pull/282), "Test harness",
merged on 2026-10-05 as `8ec198fc374424582e722d140cb234cf342a1914`.
Reviewed head `638532e79971d180eb5b3b3ce84699e6aaea86ae`, base
`e428f7b22d0a91b5d3588ad8cd1a8a6f015cb78a`: 50 files, +2640/-197, nine commits.
No submitted reviews or discussion comments were returned. Build/boot, advisory Spotless
and gameval-conflict checks succeeded on that exact head.

This restores the RSMod harness removed during migration, adding `api/testing` and MockK
1.14.5. A module-scoped game state runs native game cycles; per-test scopes submit NPC,
location, interface and dialogue inputs and capture outgoing client packets. Synthetic
NPC/item/location definitions overlay the real cache through global MockK interception.
Factory tests exercise lookup, bulk retrieval, duplicate rejection and cross-thread access.
Inputs are cleared after ticks; there is no demonstrated repeated-input replay bug.

Nine combat-formula integration files are ported to the current cache types and the CI
command adds `integration`. The shared production API change is a typed `ParamMapBuilder`
setter overload. Some combat expectations change too, including Torva melee accuracy;
those numbers are not evidence that our accepted fork's combat calculations need changing.

## Fit with this fork

| Upstream part | Local state and required adaptation |
|---|---|
| `api/testing` and capture/factory helpers | Module absent locally; useful for actual tick/death/input lifecycle tests. Keep test-only and outside the runtime JAR. |
| Integration convention | Exists but references the missing testing module. The combat module does not apply it; nine old `src/integration` files are dormant. |
| Game bootstrap | `prepareGame` reads configuration, initializes cache/maps/scripts and also starts our external Nero plugin. Isolate config, RSA, database, bridge and ports before test startup. |
| Cache overlay | MockK's global object replacement needs a dedicated integration JVM and coordinated setup/cleanup. Preserve existing real revision-240 cache tests. |
| Custom hooks and DI | Adapt instances, Collection Log, market services, native drop/death hooks and custom plugins; do not copy upstream bindings blindly. |
| Existing runtime | `server/shared` already excludes testing projects from runtime aggregation. Preserve that exclusion, accepted gameplay and paired client/cache. |

Recommended adoption, if requested: (1) isolated helpers/factories, (2) scoped native game
state for our runtime, (3) wire and review combat integration tests, (4) add `integration`
to CI only after those tests pass. No blanket merge, revision upgrade or live RSA rewrite.
Packet assertions improve server validation but do not certify client animation rendering.

## Subsequent evidence

The user's later linked [level-up commit / PR #286](upstream-level-up-779b81b.md) builds on
this harness. Its exact PR head has a failed combat integration check: 42 cases, two failing
PvP matchups with `ProvisionException` / `MockKException`; boot was skipped.
[Primary job log](https://github.com/OpenRune/OpenRune-Server/actions/runs/37401641414/job/112069923774).
This does not invalidate #282's earlier green checks, but warrants checking harness
reliability before adoption. The log excerpt does not establish the full root cause.

Decision: useful future testing infrastructure, not a gameplay dependency of the guardian
fix. Research complete; implementation remains unstarted and separately scoped.
