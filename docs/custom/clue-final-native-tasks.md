# Final ten native clue-task routes

Revision 240; continuation of PR #27 on `feature/clue-tasks-50`.
The task audit reaches 60/60. This count covers skill-challenge rows, not complete
Treasure Trails or full supporting skills. In-game acceptance remains separate.
Current status is maintained only in [PROGRESS.md](../../PROGRESS.md).

| Cached task | Native route |
|---|---|
| Elite 3 | Alry supplies a cormorant glove; wear it and Catch a Lake Molch spot using king worms/fish chunks. Mottled eel requires Fishing 73/Hunter 68; successful inventory output awards 65 Fishing/90 Hunter XP and clue credit. Knife returns chunks. |
| Elite 4 | Wear Ring of Charos, enter the native trapdoor, talk to Skullball Boss and Tap/Kick/Shoot an owned ball through a goal. Actual movement scores the goal; the 11-goal activity can finish and restart. |
| Elite 5 | Wield ninja/Kruk greegree and traverse all six Ape Atoll obstacles in order at Agility 48+. Temporary monkey model is restored even on interruption; an ordered lap earns 580 XP. |
| Elite 11 | Native black-warlock Catch, Hunter 45+, net and empty jar. Failed catches grant nothing; successful jar transaction grants 125 XP. Release returns the empty jar. |
| Elite 12 | Lay a box trap at Hunter 63+, wait for a nearby real red chinchompa and Check the catch. Reset retains the trap; failed/expired traps return materials. Catch grants 265 XP. |
| Master 2 | Seed + filled pot + trowel, water, wait five minutes, rake a native patch and plant the sapling. Five outdoor patches; Farming 83/91/99 limits one/two/five planted trees. After 3,520 minutes and a health check, use a spirit tree to travel TO the owned tree. |
| Master 9 | Complete all seven Rellekka rooftop obstacles at Agility 80+ with all six graceful pieces worn throughout. Removing any piece invalidates this lap's graceful credit. Ordered lap grants 1,000 XP. |
| Master 23 | Native Master Reanimation spell target near the Dark Altar: Arceuus book, Magic 90, Slayer 85, four nature/four soul/two blood runes and ensouled abyssal head. Owned creature uses native combat and credited death, granting 1,300 Prayer XP. |
| Master 25 | Read three cached cryptic NPC clues, satisfy their requirements and visit each real target. Each visit awards one part tied to the permanent parent trail. Combine all matching parts to advance the next step or final casket directly. |
| Master `vm01` | Set a rope/net trap on a native young tree in the Tecu area at Hunter 79+. Real nearby Tecu salamander triggers the trap. Check awards 344 XP and immature/adult item; both qualify. Native row is 3502, not `master_26`. |

## State, inventory and lifecycle

Agility lap progress, spirit-tree timestamps and trap material escrow use permanent
server-only native vars. No client upgrade is required. The new varp range
65423–65438 was checked for allocation collisions. Cache/gamevals are rebuilt.

Catch/recipe outputs commit before XP and completion events. Trap resources remain
in escrow while outside inventory: logout, death, expiry, departure and unload
remove owned world traps and return supplies when space permits. A full inventory
retains the debt rather than dropping or deleting resources. Foreign checks cannot
collect someone else's catch; stale targets do not commit.

Reanimated creatures and Skullballs have player ownership and bounded lifetime;
logout, departure, death and unload remove their actors without granting rewards.
Reanimation uses ordinary rune/staff/pouch resolution and native credited death.
Torn parts retain trail ownership through inventory vars and cannot be combined
from another trail or through stale click payloads.

All ordinary task completions retain the assigned clue until Sherlock. Torn-part
assembly advances directly. Supply fixtures do not auto-equip, teleport, grow,
raise levels or publish completion. See [commands](clue-testing.md).

## Sources and revision checks

- [Upstream PR #227](https://github.com/OpenRune/OpenRune-Server/pull/227), draft
  `98fe5d9a0554b202ccd63492c326dbe65793243a`: adapt only Ape Atoll/Rellekka obstacle
  data and native movement patterns. Do not import all courses. The draft's
  Rellekka XP sum was inconsistent; the scoped lap total is 1,000.
- [Upstream PR #229](https://github.com/OpenRune/OpenRune-Server/pull/229), closed
  unmerged `621c993f803a2e2fd81377633ade294f9e750038`: adapt red-chinchompa/Tecu trap
  definitions; implement local ownership, transactional materials, persistent
  escrow and cleanup rather than copying its whole Hunter implementation.
- [Upstream PR #272](https://github.com/OpenRune/OpenRune-Server/pull/272), merged
  `5c5536070ac12e671ffc46fd7b0348c03ffdb590`: adapt inventory abyssal reanimation;
  retain this fork's combat/death/rune APIs and avoid broader core/ground-head ports.
- Own/upstream code and PR inventory were inspected for Aerial Fishing, Spirit
  Trees and Skullball; reviewed sources supplied no ready implementation to port.
  These routes were built against native revision-240 definitions and map tiles.
- Native map/definition probes verified all obstacle roots, ten Skullball posts,
  the trapdoor at 3543,3462,0, Tecu trees and spirit-tree multiloc stages. Bird
  sequences belong to the bird model, so they are not applied to the human player.
- Reference mechanics: [Mottled eel](https://oldschool.runescape.wiki/w/Mottled_eel),
  [Tecu salamander](https://oldschool.runescape.wiki/w/Tecu_salamander),
  [Spirit tree](https://oldschool.runescape.wiki/w/Spirit_tree_protection).
  Existing [LICENSE.md](../../LICENSE.md) retains the upstream RS Mod notice.

## Validation and limits

`TrailNativeFinalTasksTest` exercises real native interaction producers and the
assigned-task consumers: all ten positive paths, Sherlock/casket, inventory
rollback, equipment/level/rune failures, foreign/stale targets, trap restoration,
interruption and owned actor cleanup. Native movement waypoints drive Skullball
checks; the fixture does not publish a synthetic goal-completion event. Tests
use an injected Farming clock to verify growth without an in-game grow cheat.

Supporting activities are scoped foundations. Aerial fish selection is uniform
among unlocked fish and bait consumption is a 1/5 roll; pearls, golden tench,
shops and spot relocation are outside this slice. Hunter catch rates are local
approximations; other butterflies/traps and lure AI are not implemented here.
Spirit trees grow healthy offline without disease/protection/compost, house
patches, quest gating or a complete ordinary public network. Only owned
outdoor destinations are offered by the new travel menu.

Agility lacks failures, marks of grace, pets and other courses. Skullball's final
hole landing, timing and full-course visuals require manual client acceptance;
the final approach tile was inferred from the mapped hole. Torn parts draw three
cached NPC cryptics, not all canonical dig/coordinate variants. The entire clue
route/map/puzzle inventory and Mimic remain separate work.

Selected module validation: 226 tests pass, including 132 clues (25 new native
action/lifecycle tests). Test classes use separate workers so each revision-240
cache graph is released before the next load.

Full JAR/cache and ten scoped Kotlin checks pass. Isolated revision-240/Nero boot,
game port, authenticated bridge health/catalog, live price refresh and continuing
NPC-effect/respawn snapshots pass. Exit 0, private PostgreSQL PID file absent and
no cleanup errors. The existing database-close/save-drain warning remains a
separate follow-up; this does not claim that warning is resolved.

Gameplay commits: `7a48a3137` (native actions) and `45deb0edf` (clue consumers/tests).
JAR SHA-256: `6a79a202d6e880e190c6640699e769c777f5344b455d476db211715c56577f8b`.
Cache fingerprint: `35a05b99093bc25821804ce4b29d0943e367dec3f6e40eac225b7c3baf820b1d`.
Nero server plugin remains `ac517bdbc6b2264720fe4654ef6f98b33de2151e71a3d69ce6d2a917188b9a4b`.
The installed 50-task baseline was verified against all 877 manifest file hashes;
the new installer carries only the difference from that known baseline.

Use the new installer to test native routes and visuals in the accepted client.
The 50-task package and prior checkpoints remain unchanged. No package installs
itself or changes player data automatically.
