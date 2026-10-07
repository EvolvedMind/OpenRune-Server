# Project overview

Reviewed **2026-10-07**. This is the source for current status and priorities.
Implementation details: [custom documentation](docs/custom/README.md).
Detected modules and references: [technical inventory](CONTENT_INVENTORY.md).

**Status:** 🟢 Complete · 🟡 Started · 🔴 Not started

## 🟡 Active: Treasure Trails

Continue toward full clue gameplay, as requested on 2026-10-06.

- **42/60 skill-task rows have completion handling.** This is task coverage, not overall clue completion.
- Finish the missing native skill actions, clue routes, maps and puzzles; then validate complete trails and their lifecycle.
- The guardian progression fix is accepted and merged. The user approved PR #26 for merge on 2026-10-07: Uri recovery, native elf pickpocketing, corrected altar consumers and watermelon planting/Sherlock completion. Merge reconciliation is in progress. **Mimic comes last and is not implemented.**
- Validation: 180 selected tests, scoped Kotlin checks, full server JAR and isolated revision-240/Nero-bridge startup pass. Test package `clue-farming-20261007` and its independent rollback remain unchanged; merge does not install it.
- [Implementation](docs/custom/treasure-trails.md) · [coverage and gaps](docs/custom/clue-task-coverage.md) · [test commands](docs/custom/clue-testing.md) · [native action evidence](docs/custom/clue-completion.md) · [Farming scope](docs/custom/clue-farming.md).

## Accepted baseline

**Accepted runtime package:** revision **240**, Doom/HUD: server `f40f4d951`, Nero `b29d93c`.
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
| 🔴 | Tormented Demons | L |
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
