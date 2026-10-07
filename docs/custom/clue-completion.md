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
client rendering or the complete network/UI input route. Full build/boot validation is recorded below;
live acceptance of this new chunk remains pending.

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

PR #230 had no submitted reviews or check runs on its reviewed head when queried.
Our tests qualify this adaptation independently; no upstream CI assurance is inferred.

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

## Next upstream adaptation: watermelon planting

Reviewed PR #231's patch persistence, native loc/item handlers and planting path.
It supplies allotment/flower/herb patches and offline growth, but no spirit trees.
Its proposed varp range 65439–65459 does not collide with the currently declared
content/API varps (checked by parsing gamevals namespaces, not comparing other ID
types). Cache definitions must still be allocated/validated in our own pack.
The patch has no successful-plant clue event. Seed/can/bucket replacements need our
atomic transaction rules and state revalidation after native tick delays. Adopt that
slice with producer/consumer tests before counting the watermelon task; do not import
upstream global varp files or claim the entire Farming skill complete.


## Validation and handoff — 2026-10-07

158 selected tests pass: clues 76, Thieving 9, market prices 6, Collection Log 8,
Doom 35, drops 4, pets 15 and player output 5. No failures, errors or skipped cases.
Scoped Spotless checks pass in the three changed modules. Full server JAR builds;
isolated revision-240 startup, Nero-bridge health and asset lookup pass. The private
test server exits with code 0 and its PostgreSQL PID file is absent. Existing
database-close ordering warnings remain a separate follow-up; this is not proof of
a clean database drain.

JAR SHA-256: `172993cc534a4e392eda3a87276eb7e816a84ffcd16350c4e0ad34664f0d59e8`.
Studio remains `85af63cedb2067b3b82c63ed86b0e55048c0399d`; no client changes.
The installed guardian checkpoint `4211eca06` matched all 826 checked targets.
New package: `outputs/clue-native-actions-20261007/INSTALLEREN.cmd`, with its own
software rollback checkpoint. Preparing a package does not install it or overwrite
live playerdata. The new branch still requires in-game acceptance.

Old approved branches were archived and removed locally/remotely. Recoverable tags:
`archive/20261007/feature/small-extensions` at `e6b1702fa` and
`archive/20261007/fix/clue-guardian-completion` at `4211eca06`.
Main retains all approved fixes; `feature/clue-completion` holds this new work.

## Native watermelon planting - 2026-10-07

The next Farming slice is now implemented and tested through the native loc/item
producer, persistent patch state and Sherlock consumer. Coverage is 42/60, with
18 task rows still missing. [Source, scope and tests](clue-farming.md). The frozen
clue-native-actions package is preserved; a new Farming package will have its own
rollback. 180 selected tests, scoped formatting, full JAR build and isolated
Nero-bridge boot pass; live acceptance pending. Mimic last.

## Next action research: dragonstone enchanting - 2026-10-07

The local Magic modules are alchemy, spell attacks, teleports and spellbook altars;
no jewellery-enchant handler was found in the targeted source inventory. Upstream
main's Magic directory additionally includes Arceuus spells. Searches across open,
draft and closed PRs for enchanting/jewellery found no reusable enchanting module.
The two broader search matches were checked: unmerged [PR #216](https://github.com/OpenRune/OpenRune-Server/pull/216)
at `375581c32314973d7de6a04e5e7f3713ef429f22` has no jewellery-enchant file among its
872 changed paths; merged [PR #280](https://github.com/OpenRune/OpenRune-Server/pull/280)
at `b7828ecb3cfd67cf73c2fee62c54f67fdcb52857` changes unrelated skill/drop fixes.
Do not import either broad content drop for this action.

Use the existing native `AlchemyScript` spell-target interaction, queued protected
access and `MagicSpellRegistry` rune/level definitions as integration references.
Verify revision-240 recipes/graphics, then test real spell casting, rune-pouch/staff
supplies, atomic input/output, cancellation and Sherlock credit. This research is
not an implemented task; coverage remains 42/60.
