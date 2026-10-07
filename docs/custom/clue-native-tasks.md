# Native clue actions: eight additional tasks

Revision 240; branch `feature/clue-tasks-50`, based on accepted main `6c78dc5b0`.
This extends task coverage from 42 to 50 of the 60 native skill-challenge rows.
It does not declare Treasure Trails or the supporting activities complete.
Current status and remaining priorities belong in [PROGRESS.md](../../PROGRESS.md).

## Action routes

| Task | Native action and requirements |
|---|---|
| Elite 1 | Lvl-5 Enchant on dragonstone ring, necklace, bracelet or strung amulet. Magic 68; cosmic rune, 15 water and 15 earth runes. Native staff substitutions and rune pouch supplies are respected. |
| Elite 7 | Search for traps on the castle chest at 2588,3291,1 or 2588,3302,1. Thieving 72; 1,000 coins, shark, adamantite ore and sapphire; 500 XP, temporary empty chest and Witchaven teleport. Opening directly triggers the trap without clue credit. |
| Elite 17 | Repair a broken Dorgesh-Kaan lamp using a light orb. Firemaking 52; 1,000 XP. Native broken/repaired objects, animation and timed restoration. |
| Elite 23 | Use four lovakite bars on an anvil; select a tier 2–5 Shayzien platebody in the native production menu. Smithing 63/73/83/93; the cached clue also requires Mining 65. Tier 1 can be made but does not qualify. |
| Master 3 | Create a Barrows tablet at the existing Arceuus lectern at 1679,3765,0. Arceuus book, Magic 83, dark essence block, two law, two soul and one blood rune; 90 XP. Study, Create-last and essence-on-lectern routes. |
| Master 5 | Kill one spiritual mage while wearing an item belonging to that mage's god. Slayer 83; Saradomin, Zamorak, Armadyl and Bandos variants. Items in inventory do not qualify. |
| Master 17 | Cremate Fiyr remains on an existing Mort'ton funeral pyre, such as 3462,3282,0. Tinderbox and magic pyre logs, Firemaking 80; redwood logs require 90. Successful consumption, XP and reward precede clue completion. |
| Master 24 | Kill a Fiyr shade inside native shade catacombs at 3456–3519,9664–9727,0. The clue requires Firemaking 65. Native shadows become the active Fiyr model on attack; ordinary death credit and respawn remain in use. |

All actions require an assigned matching task. Completion retains the same clue
and step in permanent inventory vars until Sherlock advances it or awards the
final casket. Repeated actions and repeated Sherlock clicks cannot duplicate it.
Administrator supply fixtures are listed in [clue-testing.md](clue-testing.md).

## Integration and cache evidence

- Production uses native protected access, tick delays and inventory transactions.
  Inputs and output commit together; cancellation or stale targets grant no XP or
  clue credit. Chest contents roll back together when inventory space is missing.
- `LampRepairedEvent` and `ShadeCrematedEvent` describe actual world actions.
  Jewellery, chest and tablet products carry explicit sources. Kill consumers
  use `NpcDeath`'s credited player and the dead NPC's location.
- The top-level spellbook enum contains the jewellery group, not Lvl-5 Enchant.
  The additive `MagicSpellRegistry.getUtilitySpell` resolves its native definition
  without changing combat spell/autocast registration. Native spell object 3318
  has button 218:64, Magic 68 and 78 XP.
- Existing lectern, castle chest aliases, pyres, lamps, Fiyr spawns, item outputs
  and animation symbols were checked against the revision-240 cache. A complete
  mapsquare scan found the lectern and ten usable lamp tiles; no cache IDs were
  allocated and the accepted client remains unchanged.
- Lovakite item-on-anvil uses the existing `AnvilSmithingScript` category handler,
  which delegates platebody selection to the new production worker. There is no
  second category registration. Other bars and the ordinary anvil-click interface
  retain their previous routes. Native tests register that complete anvil script.

## Scope limits

The current cache contains no pre-broken static lamps. Ten existing Dorgesh-Kaan
lamps are therefore broken at startup and become repairable again 600 ticks after
repair. These are shared world lamps, not OSRS's personal random lamp distribution
or its hundred-repair bonus.

Shayzien support here is platebody production, not the complete armour activity.
Fiyr cremation automatically builds a complete pyre when all inputs are present;
it does not implement partially built pyres, all shade tiers, reward chests,
coffins or the full minigame. The current reward model uses coins/silver/gold
weights of 21%/63.4%/15.6%, with uniform colour selection within each key tier.
Exact key-colour parity is not established. Catacomb door/key access is outside
this task slice and still needs separate review.

Native action tests exercise the producer, successful inventory/XP changes,
clue consumer and contextual Sherlock/casket route. They include cancellation,
stale items/world objects, wrong spellbook, levels, missing materials, staff/pouch
supplies, full inventory, mismatched god equipment, kill ownership and saved
completion. Loc repositories and network routing are mocked in the action tests;
live arrival, visuals and the new gameplay slice require in-game acceptance.

## Reviewed sources and choices

The earlier [upstream audit](clue-completion.md#selective-upstream-sources) was
reused, with targeted inspection of the relevant main/PR implementations.

| Source | Reviewed commit | Choice |
|---|---|---|
| [Thieving #216](https://github.com/OpenRune/OpenRune-Server/pull/216), closed and unmerged | `375581c32314973d7de6a04e5e7f3713ef429f22` | Adapt the native trapped-chest handler pattern; restrict it to the two mapped castle chests and retain our atomic rewards/events. No branch import. |
| [Thieving #230](https://github.com/OpenRune/OpenRune-Server/pull/230) | `8769932dab36733b60eb2b0653dd65dd481c130e` | Previously adapted elf data retained; this PR supplies no castle-chest handler. |
| [Tablet #7](https://github.com/OpenRune/OpenRune-Server/pull/7), merged | `06ea07fca2c652e88dd54b5aef281288d7cb7fe0` | Consumption reference only; no lectern-production implementation to adopt. |
| [Agility #227](https://github.com/OpenRune/OpenRune-Server/pull/227), draft | `98fe5d9a0554b202ccd63492c326dbe65793243a` | Reviewed for the remaining courses; not imported into this eight-task slice. |
| [Hunter #229](https://github.com/OpenRune/OpenRune-Server/pull/229), closed/unmerged | `621c993f803a2e2fd81377633ade294f9e750038` | Reviewed for later chinchompa work; not imported. |
| [Arceuus #272](https://github.com/OpenRune/OpenRune-Server/pull/272), merged | `5c5536070ac12e671ffc46fd7b0348c03ffdb590` | Reanimation reference for the remaining task; not imported here. |

No reusable lamp/Shayzien/Fiyr-creation implementation was found in the reviewed
upstream paths and PRs. These routes use this fork's existing DSL and services.

Canonical task interpretation and materials:
[RuneLite skill challenges](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/cluescrolls/clues/SkillChallengeClue.java).
Activity references: [dragonstone](https://oldschool.runescape.wiki/w/Dragonstone),
[magic tablets](https://oldschool.runescape.wiki/w/Magic_tablet),
[light orb](https://oldschool.runescape.wiki/w/Light_orb), and
[Shades of Mort'ton](https://oldschool.runescape.wiki/w/Shades_of_Mort%27ton_(minigame)).
These are references, not a claim of complete OSRS parity.

## Validation

The selected suites pass 201 tests, including 107 Treasure Trails tests; all eight
changed modules pass scoped Spotless checks. The inventory generator passes its
16 tests and its read-only output check. The full server JAR builds.

The first isolated boot caught a duplicate lovakite/anvil category registration.
It was resolved by delegating from the existing anvil handler; the complete native
anvil script now participates in the action fixtures. All 107 clue tests and the
integration JAR build pass again. Isolated revision-240 startup, Nero health,
catalog lookup, game port, NPC effects and respawn snapshots pass. The server
exits with code 0; its PostgreSQL PID file is absent and cleanup reports no errors.
The previously recorded database-close ordering warning is still present and is
a separate follow-up; this is not proof of clean database-save draining.

Gameplay source: `cad716e29`. Server JAR SHA-256:
`7ea647fe9b8df6f00485bf50b80c9f373011d2445c7f654779959eb69a94671a`.
Paired Studio stays `85af63cedb2067b3b82c63ed86b0e55048c0399d`; no client edits.
[PR #27](https://github.com/EvolvedMind/OpenRune-Server/pull/27) requires in-game
acceptance before merge. Fresh test package:
`outputs/clue-tasks-50-20261007/INSTALLEREN.cmd`, with an independent checkpoint
at `outputs/checkpoint-20261007-voor-clue-tasks-50`. Earlier immutable packages
remain available. Preparing this package does not change live software/playerdata.
