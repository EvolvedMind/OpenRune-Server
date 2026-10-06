# Clue completion work

Active branch: `feature/clue-completion`, based on main `90f1e5fb9`.
Accepted gameplay and revision 240 are preserved. Mimic follows the remaining clue work.

## Emote recovery — 2026-10-06

Uri can be recalled after timeout, logout or player death without repeating an already
credited guardian fight. Medium clues retain the two-emote sequence; native hard/master
rows use one emote and a guardian. Owned Uri actors are removed on death and plugin unload.
Other players and stale clue references cannot claim a reward.

Validation: 66 Treasure Trails tests pass, including five new native-cache emote cases.
The tests dispatch native emote events and NPC death/delete events, use inventory
transactions, and check actor ownership and persisted clue vars. They do not certify
client rendering or the complete network/UI input route. Build/boot and live acceptance
of this new chunk remain pending.

## Selective upstream sources

Reviewed upstream main and all open/draft/closed PR pages. There is no complete upstream
Treasure Trails or Mimic module to import. Potential native-action sources:

| Source | Reviewed head | Decision |
|---|---|---|
| [Thieving #230](https://github.com/OpenRune/OpenRune-Server/pull/230) | `8769932dab36733b60eb2b0653dd65dd481c130e` | Adapt elf target/loot data; preserve our existing stalls and hooks. |
| [Farming #231](https://github.com/OpenRune/OpenRune-Server/pull/231) | `97aa0afa766469c0e2937a5aa0612b47b8c822e5` | Review for watermelon planting; no spirit-tree implementation supplied. |
| [Agility #227](https://github.com/OpenRune/OpenRune-Server/pull/227) | `98fe5d9a0554b202ccd63492c326dbe65793243a` | Draft reference for Ape Atoll/Rellekka; not yet imported. |
| [Hunter #229](https://github.com/OpenRune/OpenRune-Server/pull/229) | `621c993f803a2e2fd81377633ade294f9e750038` | Closed/unmerged reference for red chinchompas; not butterflies/salamanders. |
| [Arceuus #272](https://github.com/OpenRune/OpenRune-Server/pull/272) | `5c5536070ac12e671ffc46fd7b0348c03ffdb590` | Compare reanimation support before adoption. |
| [Teletablets #7](https://github.com/OpenRune/OpenRune-Server/pull/7) | `06ea07fca2c652e88dd54b5aef281288d7cb7fe0` | Review actual lectern creation, not just tablet consumption. |

Only reviewed adaptations will be committed. These sources are not completion claims.

## Elf pickpocketing — 2026-10-07

Adapted PR #230's elf level, XP, success chance, stun and weighted main loot. Bound
53 cache-verified NPC symbols: four Lletya elves and 49 Prifddinas citizens. Existing
citizen/master-farmer rewards and Ardougne gem-stall logic are preserved.
Added Prifddinas seed/shard rolls from the wiki; full rogue outfit doubles applicable
loot but not crystal shards. Pouches open atomically for 280–350 coins each.
The native success producer publishes only after committed inventory output and XP.
Sherlock recognizes an assigned elf task, retaining the clue until the player returns.

Tests: nine Thieving tests pass (seven new reward cases); six native NPC/Sherlock
integration tests pass, including failure/stun, cancellation, low level, pouch cap,
inventory filling during the attempt, wrong tasks and final-casket idempotency.
Native animation/sound calls execute in the fixtures; client visual acceptance remains
pending. Shadow Veil, diary/glove boosts and partial rogue-set chances are outside
this clue-focused adaptation. This does not declare the whole Thieving skill complete.

Admin fixture: `::cluetest elf` or `::cluetest task master 13` (single-step assigned task).
Pickpocket an actual elf, then talk to Sherlock. Neither command grants skill levels,
teleports or completes the task. The existing world spawns include Goreu at
2337,3159,0 and Miriel at 3238,6124,0.

## Native Runecrafting compatibility — 2026-10-07

Corrected three clue consumers to use the cache's native `obj.naturerune`,
`obj.cosmicrune` and `obj.bloodrune` symbols emitted by the existing altar producer.
No Runecrafting gameplay code was changed. Added four tests through the actual
`AltarEvents` loc handlers, cached altar/rune rows, real tick delay and inventory
output. Nature, multiple cosmics and both Blood Altars now complete assigned tasks.
Base level/multiplier governs cosmic multiplication; a visible boost alone does not
qualify. Cancellation consumes no essence and produces no XP or clue credit.
The test loc geometry is mocked; network route/arrival and client rendering remain
for live validation. All 76 Treasure Trails and nine Thieving tests pass (85 total).
Coverage remains 41/60; these three consumers were already in the existing count.
