# Project overview

Reviewed **2026-10-09**. This is the source for current status and priorities.
Implementation details: [custom documentation](docs/custom/README.md).
Detected modules and references: [technical inventory](CONTENT_INVENTORY.md).

**Status:** 🟢 Complete · 🟡 Started · 🔴 Not started

## 🟡 Started: Mokhaiotl waystone and Confliction maxmage — merge authorized

Native **Channel** now teleports to Doom's pre-lair and consumes one waystone
only on arrival. Normal teleport restrictions and native instance lifecycle
remain in place. `::maxmage` supplies Confliction gauntlets; other gear and Shadow
charges are preserved. **101 selected tests**, full formatter, gameval checks and
runtime JAR pass. Isolated rev240/Nero startup confirms the native Channel option,
wearable gauntlets, walkable Doom lobby and preserved gorilla objects. Server and
private database stop cleanly. Final merge and guarded installer are pending.
See [Doom mechanics](docs/custom/doom-of-mokhaiotl.md).

## 🟢 Complete: Doom delve loot-rate correction — PR #30 merged

The owner authorized update, implementation and merge of
[PR #30](https://github.com/EvolvedMind/OpenRune-Server/pull/30) on 2026-10-09.
Merged as `4d9d974d1`; its tree exactly matches tested head `a3bfe1c74`.
Mokhaiotl cloth, Eye of Ayak, Avernic treads, Dom and elite clues now use
delve-specific odds, capped at delve 9. The main uniques share an exclusive roll;
Dom and clues are independent. Existing reward/claim/logging paths are preserved.
All **234 selected project tests** pass, including five rare-rate tests, the
production DoomRewards pipeline, Collection Log/command coverage and 132 clue
regressions. Scoped formatting, JAR and isolated revision-240/Nero boot pass
against the exact installed cache/mappings/overlay. The gorilla hole/sign/rope
and absent old arch are preserved. Full local `spotlessCheck` and gameval conflict
checks pass. GitHub Actions is disabled for this repository (workflow dispatch
returned HTTP 422); no online CI is claimed. Installer:
`outputs/doom-delve-rates-20261009/INSTALLEREN.cmd`, with rollback to the matched
installed gorilla package. On 2026-10-09, all nine live target hashes match this
package's after-install manifest, confirming the installed PR #30 baseline.
See [Doom mechanics](docs/custom/doom-of-mokhaiotl.md).

## 🟢 Complete: gorilla hole/rope and underlying map arch repair — installed

The owner reported the old cave entrance and inactive hole/rope after PR #29.
The replacement installer preserves the current Nero edits and replaces the
four task-specific preview objects with the three functional access objects.
An isolated map scan additionally found the native Ruptured cavern
(`loc.mm2_cave_boss_waterfall_small`, 28719) at `(2106,5652,0)` underneath the old
custom entrance. The gorilla module now removes only this native object at that
tile, creates the outside hole/sign and inside rope, and restores its own map
change on unload without overwriting later editor changes. **124 selected tests**,
scoped formatting and the full JAR pass. Isolated startup confirms exactly the
three requested objects, both walkable arrival tiles and absence of the native
arch after applying the persisted overlay. The owner installed
`outputs/gorilla-rope-complete-20261008/INSTALLEREN.cmd`. All 21 live target hashes
match its after-install manifest on 2026-10-09. Its source commit `0d2079013` is
retained in PR #30 so the next Doom runtime cannot regress this installed repair.

## 🟢 Complete: prayers, spellbook travel and gorilla access — approved scope

The owner authorized merge after the screenshot placement correction on
2026-10-08. [PR #29](https://github.com/EvolvedMind/OpenRune-Server/pull/29) merged
as `b9baac5a8`; its tree matches tested head `66ca7608c`. Prayer, teleport and
gorilla changes remain in separate commits. Revision 240, accepted bosses,
specials, gear and the 60/60 clue-task slice are preserved. Merge performs no
automatic installation.

- Chivalry/Piety are quest-free with their normal Prayer/Defence levels. The five
  prayer scrolls permanently unlock Rigour, Augury, Preserve, Deadeye and Mystic
  Vigour after confirmed Read; upgraded native quick-prayer slots are handled.
- Fixed travel, homes, group and Teleother routes on all four spellbooks are
  unlocked and rune-free, with no travel XP. Existing teleport safety and
  recipient consent remain. Native spell metadata still supplies the accepted
  Barrows tablet's crafting level/runes/essence/XP.
- Screenshot correction: outside Hole at `(2428,3522,0)` and Danger sign at
  `(2429,3521,0)`; inside Climbing rope at `(2108,5651,0)`. Climb-down lands at
  `(2108,5654,0)`; Climb-up returns outside to `(2428,3521,0)`.
  Tortured gorillas share Demonic attack animations and switch after four
  consecutive zero-damage impacts; Demonic gorillas keep three.
- `::testprayers` supplies five scrolls; `::testgorillas` reaches the screenshot
  outside entrance. Future CoX/Royal Titans loot and dynamic POH/boat/Bounty Hunter/minigame
  destinations remain separate systems.
- **574 selected tests pass**, including 132 clue regressions; scoped formatter,
  revision-240 cache and full JAR pass. Isolated revision-240/Nero startup and
  real map-collision checks pass; private PostgreSQL stops and process exits 0.
  Client rendering is not certified by server tests; manual checks are documented
  for the owner when installing the requested placement correction.
- First test package `prayer-teleports-gorillas-20261008` remains unchanged.
  The entrance correction passes **121 selected tests**, formatter/cache/JAR and
  isolated startup with actual runtime placements and walkable landing tiles.
  New package: `outputs/gorilla-entrance-fix-20261008/INSTALLEREN.cmd`, with an
  independent rollback to the verified installed first package, including the
  four task-specific Nero preview replacements. Other world edits are preserved;
  no playerdata or RSA replacement or automatic installation.
- CI on the exact reviewed head `66ca7608c`: [build/tests/boot](https://github.com/EvolvedMind/OpenRune-Server/actions/runs/37844865414),
  [Formatting](https://github.com/EvolvedMind/OpenRune-Server/actions/runs/37844865443)
  and [Gameval Conflict Check](https://github.com/EvolvedMind/OpenRune-Server/actions/runs/37844865555)
  all passed. Original installer source `f94663e17` and corrected installer source
  `66ca7608c` remain immutable checkpoints; this merge record changes docs only.
- [Policy, sources, native interactions and manual tests](docs/custom/prayers-teleports-gorillas.md).

## 🟢 Complete: Tormented Demons — accepted encounter

The user tested the encounter and approved merge after the two menu/arrival
corrections on 2026-10-08. [PR #28](https://github.com/EvolvedMind/OpenRune-Server/pull/28)
merged into `main` as `8eac919a8`. Revision 240 and previously accepted
content are preserved.

- Impact-based XP, protection/prayer switches, defenceless appearance, slow-hit
  punish, bombs, single/dual/triple roles and lifecycle cleanup are implemented.
- Temple access/exit, 26 spawns, chamber limits/indicator, ordered rewards, ashes,
  private smouldering effects/items and full synapse/claw/Arclight crafting.
- Native Wield equips all affected weapons; Arclight Check uses `Op3` and synapse
  Revert uses `Op4`. Scroll and default `::testtd` arrive at `(4061,4464,0)`.
- The full delivery passed 412 selected regressions. The acceptance correction
  passes **51 TD tests**, scoped formatting, JAR and isolated revision-240/Nero
  startup. GitHub CI, Formatting and Gameval checks pass on the merged PR head.
- New installer: `outputs/tormented-demons-options-fix-20261008/INSTALLEREN.cmd`.
  Its independent rollback returns to the verified installed full TD package;
  previous installers/checkpoints remain preserved. No automatic live installation.
- Exact parity of unpublished lower-level consumable formulas is a reference
  verification limit, documented in the feature note. While Guthix Sleeps and
  global Combat Achievements remain separate systems.
- [Mechanics, sources, commands and evidence](docs/custom/tormented-demons.md).

## 🟡 Parked: Treasure Trails

The accepted 60/60 task slice is preserved. Full clue gameplay remains parked
until the owner's next instruction; Mimic remains last.

- **60/60 skill-task rows have completion handling.** The user approved the current scope on 2026-10-07; [PR #27](https://github.com/EvolvedMind/OpenRune-Server/pull/27) merged into `main` as `c535c713a`. This is task coverage, not overall clue completion.
- Finish the remaining clue routes, maps and puzzles; then validate complete trails and their lifecycle.
- The guardian progression fix is accepted and merged. User-approved [PR #26](https://github.com/EvolvedMind/OpenRune-Server/pull/26) merged into `main` on 2026-10-07 (`e6aa9e06a`): Uri recovery, native elf pickpocketing, corrected altar consumers and watermelon planting/Sherlock completion. **Mimic comes last and is not implemented.**
- Previous approved checkpoint: 180 selected tests, scoped Kotlin checks, full server JAR and isolated revision-240/Nero startup pass. Package `clue-farming-20261007` and its independent rollback remain unchanged; merge does not install it.
- CI for the approved PR #26 merge: [build/tests/boot](https://github.com/EvolvedMind/OpenRune-Server/actions/runs/37623645527), Formatting and Gameval checks all passed. The boot-port correction changes CI only. Documentation generator tests: 16/16 pass; generated output check passes.
- New eight-task slice: dragonstone enchantment, castle chest, Dorgesh lamp, Shayzien platebody, Barrows tablet, spiritual mage, Fiyr cremation and catacomb kill. Validation: 201 selected tests (107 clues), scoped formatting, full JAR and isolated revision-240/Nero startup pass; exit 0, PostgreSQL PID absent, no cleanup errors. Fresh package: `outputs/clue-tasks-50-20261007/INSTALLEREN.cmd`, with its own rollback. It is not installed automatically.
- Final ten added: aerial eel, Skullball, Ape Atoll/Rellekka laps, black warlock, red chinchompa, own spirit tree, reanimated abyssal, torn parts and Tecu salamander. Native producer/consumer and regression tests pass (226 total, 132 clues). Full JAR/cache, ten scoped formatting checks and isolated revision-240/Nero startup pass; exit 0, private PostgreSQL PID absent, no cleanup errors. The approved delivery `outputs/clue-tasks-60-20261007/INSTALLEREN.cmd` remains unchanged, with independent rollback to the verified installed 50-task baseline. Merge performs no installation. [Sources and activity limits](docs/custom/clue-final-native-tasks.md).
- CI for PR #27's exact reviewed head `84616d805`: [build/tests/boot](https://github.com/EvolvedMind/OpenRune-Server/actions/runs/37669314684), [Formatting](https://github.com/EvolvedMind/OpenRune-Server/actions/runs/37669314268) and [Gameval Conflict Check](https://github.com/EvolvedMind/OpenRune-Server/actions/runs/37669314243) all passed. The merged tree matches that tested head.
- [Implementation](docs/custom/treasure-trails.md) · [coverage and gaps](docs/custom/clue-task-coverage.md) · [test commands](docs/custom/clue-testing.md) · [native action evidence](docs/custom/clue-completion.md) · [Farming scope](docs/custom/clue-farming.md) · [eight new tasks and activity limits](docs/custom/clue-native-tasks.md).

## Accepted baseline

**Protected runtime recovery point:** revision **240**, Doom/HUD: server `f40f4d951`, Nero `b29d93c`.
Exact commits, package and recovery checkpoints: [baseline](docs/custom/baseline.md).

Acceptance below applies to the recorded scope. A merged change does not establish that it was installed in the live runtime.

| Status | Area | Accepted scope / reference |
|:---:|---|---|
| 🟢 | [Zulrah](docs/custom/zulrah.md) | Existing custom encounter; owner-confirmed acceptance. |
| 🟢 | [Araxxor](docs/custom/araxxor.md) | Encounter accepted; merged in PR #16. |
| 🟢 | [Kraken](docs/custom/kraken.md) | Public-cave encounter accepted; private instances are outside this scope. |
| 🟢 | [Corporeal Beast](docs/custom/corporeal-beast.md) | Encounter accepted; clan instances and individual Combat Achievement conditions remain separate work. |
| 🟢 | Barrows | Accepted native encounter/rewards; `::testloot barrows [count]` uses native chest rewards. |
| 🟢 | [Doom of Mokhaiotl and NPC combat-stat HUD](docs/custom/doom-of-mokhaiotl.md) | Accepted package; merged through server PR #23 and Nero PR #11. |
| 🟢 | [Small extensions](docs/custom/small-extensions.md) | OSRS GE Prices, Improved Item Examine, Notification Queue and Collection Log Reward Broadcasts approved and merged into `474296f2b`. Advisory formatting findings remain open. |
| 🟢 | [Clue skill-task handling](docs/custom/clue-task-coverage.md) | Current 60/60 scope approved and merged in PR #27; supporting activity limits and remaining full trails are tracked separately above. |
| 🟢 | [Prayer unlocks, free spellbook travel and gorilla access](docs/custom/prayers-teleports-gorillas.md) | Owner-authorized scope merged in PR #29, including the requested outside hole/sign and inside rope correction; future encounter loot/dynamic destinations remain separate. |

## Existing content and open follow-ups

| Status | Area | Remaining work / reference |
|:---:|---|---|
| 🟢 | [Pets](docs/custom/pets.md), [commands](docs/custom/commands.md), [max cape](docs/max-cape.md) and [timers](docs/custom/architecture.md) | Implemented; preserve covered behavior when shared systems change. |
| 🟡 | [Interfaces](docs/custom/interfaces.md) | Implemented; remaining fixed/resizable scrolling and visual edge cases need in-game verification. |
| 🟡 | [Boss-item crafting](docs/custom/boss-item-crafting.md) | Native atomic recipes implemented; user authorized merge. New recipes still need in-game acceptance. |
| 🟡 | [Slayer menus and helmet crafting](docs/custom/slayer.md) | Source audit: existing task/unlock/extension handlers and ordinary helmet recipes. Fix Trade routing, task state, purchases, block slots, toggles and Suqah mapping; complete the requested menu and conversion paths. |
| 🟡 | [Ordinary NPC combat animations](docs/custom/combat.md#ordinary-npc-combat-animations) | Initial source audit found missing explicit combat-animation configuration for the reported Armadylean/Bandosian clue guards and Tlati candidates. Verify resolved cache data, repair affected NPC families and test in-game; preserve accepted bosses and guardian progression. |
| 🔴 | [Persistence warning follow-up](docs/custom/upstream-review-20261006.md) | Recorded concurrent database-close/save-drain warning; investigate separately from accepted encounter scope. |
| 🟢 | Upstream research (review only) | [Test harness PR #282](docs/custom/upstream-pr-282-test-harness.md) and [level-up PR #286](docs/custom/upstream-level-up-779b81b.md) reviewed; not imported. Implementation has not started. Any adoption requires a scoped revision-240 port. |

## Backlog

Planning only; list order does not authorize starting work. Check existing code and upstream reuse before estimating remaining effort.

<!-- roadmap:start -->

Requirements and details: [roadmap.json](tools/progress/roadmap.json).

Scope: XS = tiny, S = small, M = medium, L = large, XL = very large, XXL = multiple systems. These describe the full target; inspect existing work and upstream reuse to estimate the remaining work.

### Bosses

| Status | Feature | Scope |
|:---:|---|---|
| 🔴 | Thermonuclear Smoke Devil | S |
| 🔴 | Deranged Archaeologist | S |
| 🔴 | Giant Mole | S–M |
| 🔴 | Chaos Fanatic | S–M |
| 🔴 | Obor | M |
| 🔴 | Brutus | M |
| 🔴 | Chaos Elemental | M |
| 🔴 | Kalphite Queen | M |
| 🔴 | Dagannoth Kings | M |
| 🔴 | Sarachnis | M–L |
| 🔴 | Shellbane Gryphon | M–L |
| 🔴 | Royal Titans | L |
| 🔴 | Vet'ion & Calvar'ion | L |
| 🔴 | Cerberus | L |
| 🔴 | Vorkath | L |
| 🔴 | Abyssal Sire | L |
| 🔴 | Alchemical Hydra | L |
| 🔴 | Grotesque Guardians | L–XL |
| 🔴 | The Hueycoatl | L–XL |
| 🔴 | Nex | XL |
| 🔴 | The Nightmare | XL |
| 🔴 | Yama | XL–XXL |

### Minigames and activities

| Status | Feature | Scope |
|:---:|---|---|
| 🔴 | Aerial Fishing | M |
| 🔴 | Tithe Farm | M–L |
| 🔴 | Vale Totems | M–L |
| 🔴 | Puro-Puro | M–L |
| 🔴 | Mahogany Homes | L |
| 🔴 | Mastering Mixology | L |
| 🔴 | Giants' Foundry | L |
| 🔴 | Fight Caves | L |
| 🔴 | Zalcano | L |
| 🔴 | Wintertodt | L–XL |
| 🔴 | Mage Training Arena | L–XL |
| 🔴 | Tempoross | XL |
| 🔴 | Pest Control | XL |
| 🔴 | Moons of Peril | XL |
| 🔴 | Guardians of the Rift | XL |
| 🔴 | The Inferno | XL–XXL |
| 🔴 | Fortis Colosseum | XL–XXL |
| 🔴 | The Gauntlet | XL–XXL |

### Other systems

| Status | Feature | Scope |
|:---:|---|---|
| 🔴 | Warriors' Guild Cyclopes & Defenders | M |
| 🔴 | Revenants | M–L |
| 🔴 | Hunter Guild | L |
| 🔴 | Random Events | XL |
| 🔴 | Forestry | XL |
| 🔴 | Own Grand Exchange | XL–XXL |
| 🔴 | Achievements | XL–XXL |
| 🔴 | Combat Tasks | XXL |
| 🔴 | Leagues | XXL |
| 🔴 | Sailing Skill | XXL |
| 🔴 | Completionist Cape + Particles | TBD |
| 🔴 | Hooded Slayer Helmets + Variants | TBD |

### Parked

| Status | Feature | Note |
|:---:|---|---|
| 🟡 | Weapon Special Attacks | Accepted checkpoint: 191/285 item registrations; 94 deferred. Registration is not full mechanics validation. Resume only when selected. |
| 🔴 | RSPS AgentCraft — In-Game Developer Observatory | Parked concept: real Claude/Codex agents through an in-game developer scene, with an external runner and isolated worktrees. Full-system scope XXL. |

### Runtime migration

| Status | Feature | Note |
|:---:|---|---|
| 🟡 | Revision 241 Upgrade | Deferred compatibility review. Keep revision 240; any client, protocol or cache upgrade needs a separate explicit task. |

<!-- roadmap:end -->
