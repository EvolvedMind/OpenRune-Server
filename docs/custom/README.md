# Fork technical documentation

Current status, active work and roadmap: [PROGRESS.md](../../PROGRESS.md).
This index contains implementation notes and evidence, not a second status list.

| Subject | Module / main source | Details |
|---|---|---|
| Runtime and recovery | Server packages, paired Nero Studio | [Checkpoints](baseline.md), [architecture](architecture.md) |
| Zulrah | `content/bosses/zulrah` | [Encounter](zulrah.md) |
| Araxxor | `content/bosses/araxxor` | [Encounter and fang recipes](araxxor.md) |
| Kraken | `content/bosses/kraken` | [Public-cave encounter](kraken.md) |
| Corporeal Beast | `content/bosses/corporeal-beast` | [Encounter](corporeal-beast.md) |
| Doom of Mokhaiotl | `content/bosses/doom-of-mokhaiotl` and `pack` | [Encounter and rewards](doom-of-mokhaiotl.md) |
| Barrows reward testing | `content/bosses/barrows` | [Administrator commands](commands.md#barrows-loot-test) |
| Treasure Trails | `content/other/treasure-trails` | [Implementation](treasure-trails.md), [task coverage](clue-task-coverage.md), [test commands](clue-testing.md), [guardian fix](clue-guardians.md) |
| Pets and gallery | `content/other/pets` | [Followers, relog and morphs](pets.md) |
| Commands and loadouts | `content/other/commands`, `content/interfaces/worldmap` | [Commands](commands.md) |
| Monster, skill and quest interfaces | `content/interfaces/monster-info`, `content/interfaces/skill-guides`, `content/quest` | [Interfaces](interfaces.md) |
| Max cape | Custom native-menu handlers in `content/other/max-cape` | [Menus and perks](../max-cape.md) |
| Completionist cape and hooded Slayer helmets | Reward requirements, equipment variants and client/cache visuals | [Design and visual reference](completion-rewards.md) |
| Weapons, shields and charges | `content/other/special-attacks`, `content/other/special-weapons`, `api/specials`, `api/combat` | [Combat](combat.md) |
| Boss-item assembly | `content/skills/crafting`, `content/other/special-weapons` | [Recipes and dismantling](boss-item-crafting.md) |
| Prices, Examine and notifications | `api/market`, `api/player-output`, `content/interfaces/collection-log` | [Four extensions](small-extensions.md) |
| Timers, NPC stat HUD and object library | `api/npc`, `api/death`, `api/instances`; paired Nero repository | [Architecture](architecture.md) |
| Shared APIs, plugin loading and cache mappings | `engine/plugin`, `or-cache`, `server/app` and affected APIs | [Core changes](core-modifications.md) |

## Upstream and recovery references

- [General upstream comparison](upstream-review.md) and [Doom/DSL review](upstream-review-20261006.md).
- [Test harness PR #282](upstream-pr-282-test-harness.md) and [level-up PR #286](upstream-level-up-779b81b.md).
- [Historical branch audit](branch-audit-20261003.md) and [research decisions](research.md).
- Repositories: [upstream](https://github.com/OpenRune/OpenRune-Server), [fork](https://github.com/EvolvedMind/OpenRune-Server), [Nero Studio](https://github.com/EvolvedMind/Nero-OpenRune-Studio).

Archived implementations remain review material. Do not load them alongside the active handlers.
