# EvolvedMind OpenRune progress

Updated: 2026-10-06. The user has accepted the current runtime and explicitly requires
it to be preserved. The completed organization pass changed documentation and Git references,
not gameplay, cache, plugins or the installed package.

**Current accepted server:** `f40f4d951fb7e08df715952879a15314a4f85639` (user-accepted Doom test package, revision 240).
Previous weapons baseline: `6168204ee3992a3e00f6906e34200d4cff3e95f4`.
**Paired Nero Studio:** `b29d93cabe7b6d064430bba688f3a9e7903267fb`.
[Baseline / test evidence](docs/custom/baseline.md) ? [Branch audit](docs/custom/branch-audit-20261003.md)

## Status meaning

VERIFIED means the stated checks passed, not exhaustive OSRS correctness.
IMPLEMENTED / NEEDS TESTING means code exists with remaining acceptance checks.
NEEDS REVIEW and UPSTREAM REVIEW forbid automatic adoption.
PLANNED / NOT STARTED describe future work, not functionality in the current build.

## Active work

| Work | State | Scope |
|---|---|---|
| Repository organization | VERIFIED | 15 stale branches archived and removed; 2 branches retained; accepted runtime preserved |
| Araxxor completion | VERIFIED / USER ACCEPTED | Merged PR #16; 186 tests, full JAR and isolated boot pass; completion f7c349c0e accepted; initial rollback preserved |
| Accepted gameplay bundle | VERIFIED at baseline | User acceptance + 121 selected server tests, 86 Nero tests, isolated boot; GitHub server CI/format/gameval checks passed |
| Weapon completeness | ACCEPTED CHECKPOINT / REMAINDER PAUSED (merged PR #15) | Requested after cleanup: 285/285 special target plus normal attacks, charges and FX; Zulrah encounter is frozen |
| Weapon test installer | IMPLEMENTED / NEEDS USER TESTING | `special-fx-update-20261004` at `6168204ee`; user gave explicit green light on 2026-10-04 |

## Bosses

| Feature | State | Origin | Evidence / limitation |
|---|---|---|---|
| Zulrah | IMPLEMENTED / NEEDS TESTING for exhaustive mechanics | CUSTOM | User played it; halberd regression covered; old death-recovery implementation is archived, not active |
| GWD four bosses | IMPLEMENTED / NEEDS TESTING for full encounters | UPSTREAM + CUSTOM EXTENSIONS | Current respawn policy 100 ticks / 60 s; Nero reads actual deadlines |
| Other existing boss modules | IMPLEMENTED / NEEDS TESTING | UPSTREAM; selected custom timer integration | Amoxliatl, Callisto, demonic gorilla, Duke, gemstone crab, KBD, Leviathan, Muspah, Scurrius, Spindel, tormented demon, Vardorvis, Whisperer |
| Araxxor | VERIFIED / USER ACCEPTED (PR #16) | CUSTOM | Completion f7c349c0e accepted by user; completion adds impact reflection, max-hit rules, native acid ball, Slayer gates and Harvest/Destroy rewards; see docs/custom/araxxor.md |
| Barrows | VERIFIED / USER ACCEPTED | UPSTREAM | User reports encounter works perfectly; ::testloot barrows exercises the native chest reward calculation |
| Doom of Mokhaiotl | VERIFIED / USER ACCEPTED; merge approved | UPSTREAM + CUSTOM EXTENSIONS | Complete integrated delve encounter and ::testloot; 326 selected server tests and isolated startup; accepted 2026-10-06 |

## Systems

| System | State | Origin | Verification / remaining work |
|---|---|---|---|
| Follower relog and Lil' Zik morph fixes | VERIFIED for covered regressions | UPSTREAM + CUSTOM EXTENSIONS | Pet synchronization/morph suites and user gameplay feedback |
| Native commands / inventory loadouts / ::spres | VERIFIED for covered regressions | UPSTREAM + CUSTOM EXTENSIONS | Inventory-only grants; no replacement of worn gear |
| Max cape submenu indexing | VERIFIED for covered regressions | CUSTOM | 15 tests, including actual packets and Farming Guild selection |
| Monster drops, pet gallery, skill/quest guides | IMPLEMENTED / NEEDS TESTING for visual edge cases | HYBRID | Search, navigation, log/map and all skill buttons tested; fixed/resized scrolling remains an explicit visual checklist |
| Combat specials and shields | ACCEPTED CHECKPOINT / REMAINDER PAUSED | UPSTREAM + CUSTOM EXTENSIONS | 191/285 special-energy items registered; 94 missing, six shield forms covered separately; see combat audit |
| Respawn countdowns | VERIFIED for covered regressions | HYBRID | Actual deadline snapshots; non-GWD policy is 34 ticks = 20.4 s |
| Nero object library and loot colours | VERIFIED for covered regressions | CUSTOM integration | Paired plugin tests; native Ground Items aggregation/value colour fallback |
| Plugin lifecycle / offline login / installer alternatives | NEEDS REVIEW | Archived custom alternatives | Not imported into the accepted runtime |

## Skills, quests, raids and minigames

- Existing skill modules: cooking, crafting, firemaking, fishing, herblore, magic,
  mining, prayer, runecrafting, slayer, smithing, thieving, woodcutting, plus shared
  combat/skill APIs. Presence does not certify complete skills.
- No sailing gameplay integration in this accepted build; cape boat actions remain disabled.
- Quest tests cover existing implemented quest flows; guide styling does not complete missing quests.
- Raids/minigames have no newly verified implementation in this pass. Pet names,
  drops, collection-log entries or cache symbols must not be counted as playable encounters.
- The automatic presence scanner is retained as supporting inventory in
  [CONTENT_INVENTORY.md](CONTENT_INVENTORY.md), not as the quality/status authority.

## Roadmap

### NOW

- User accepted PR #15 on 2026-10-04. Preserve this checkpoint; remaining specials are parked. Araxxor completion is accepted for merge (PR #16).
- Keep the accepted baseline recoverable and main organized.

### NEXT

- Select the next focused feature after the accepted Araxxor merge; no new encounter is started automatically.
- Compare archived improvements only when intentionally selected for new work.

### LATER

- Paired revision 241 upgrade: client/protocol/cache, Boss DSL, custom encounter and HUD review.
- Other weapon families remain in the active completeness audit; never promote registration counts to full mechanic verification.

### BACKLOG

- Doom is now user accepted; select the next feature only on user instruction.
- Reassess old Zulrah item recovery and personal pet helper commands.

## Upstream review queue

`upstream/main` observed at `71c5ec59f427463de34bd90f538e5bd68cb42d93`.
Revision 241 and Boss DSL changes overlap custom dependencies. Outcome:
**DEFERRED REVIEW**; no upstream merge or runtime upgrade in this organization pass.
See [upstream review](docs/custom/upstream-review.md).

Demonbane follow-up: parameter-only cache overlays add 96 verified demon flags and
two Duke resistance flags; all 16,577 NPC definitions retain other fields. Claws
reductions now apply per split hit. 122 selected tests, the full server JAR and isolated Nero-bridge boot pass;
these changes are outside the frozen `880c6a9fa` installer.

Dragon hasta slice: all five variants implemented; 61 special tests pass including
partial/full energy, misses, NPC/PvP and native cost modifiers. Coverage 162/285;
123 registrations remain missing, and live FX qualification is still pending.

Saradomin sword slice: all three identities registered with distinct ordinary and
blessed damage paths, caster/target effects and hybrid accuracy. 65 special tests
and 129 selected tests overall pass; full server JAR builds. Current registry is
165/285, with 120 missing. Blessed sword degradation and live FX qualification
remain open. The frozen installer and accepted installation are unchanged.

Ancient warrior slice: four Vesta longswords and three Statius warhammers registered;
variant-specific 30%/75% Defence drains, post-roll damage reductions and reduced
defence accuracy tested. 68 special tests pass; registry 172/285 with 113 missing.
Their mode restrictions, degradation and visual alignment are not fully qualified.

Abyssal tentacle: two variants added, including miss-triggered freeze and independent
poison with native immunities. Whip graphics/player animation mix-up repaired and
target ownership regression-tested. 73 special tests pass; 174/285 registrations,
111 missing. Tentacle charge degradation and live visual parity remain open.

Staff protection: eight variants added with temporary native state, 100-tick expiry,
prayer stacking and damage-time weapon checks. 79 special / 143 selected tests,
full JAR, cache rebuild and isolated Nero-bridge startup/clean shutdown pass.
Registry at this slice: 182/285; 103 missing. Live visual qualification and remaining weapon
mechanics are still open; the accepted installation and frozen installer are intact.


Latest ranged / BH / Dorgeshuun slices: nine Dark bow identities, corrected minimum
hits and BH distributions, projectile timing and Dragon knife Duality effects.
BH Dragon mace uses its own 60% defence roll. Four Bone daggers and Dorgeshuun
crossbow now guarantee hits according to the last positive damager, respecting the
quest policy; their non-stacking Defence drain waits for actual impact. Native
contribution totals are unchanged. 97 special / 161 selected tests and the full
server JAR pass. Current registry: 191/285; 94 missing. Seeking arrows need missing
double-launch metadata; mode restrictions, boss drain floors and live FX validation
remain open. The accepted installation and frozen installer remain unchanged.

Weapon acceptance command: `::weptest <set>` supplies registered combat weapons/shields in inventory-only atomic batches. New specials are paused; 94 missing registrations remain deferred. See [commands](docs/custom/commands.md).

Final acceptance FX correction: Bludgeon graphic moved below NPC/player targets;
Volatile/Eldritch player animation separated from their graphic-model sequences.
Damage and hit timing unchanged. New specials remain paused; final visual user
check and explicit merge approval are still pending.

## Acceptance milestone — 2026-10-04

User explicitly approved the final FX checkpoint (6168204ee) for merge. PR #15 is merged into the gameplay integration branch; PR #14 carries the combined accepted work to main. 99 special tests, 12 command/interface tests, full build and isolated boot passed; all three GitHub workflows on 6168204ee passed. This acceptance does not declare 285/285 completeness: 191 registered, 94 deferred. Earlier pending notes above describe historical checkpoints.

Araxxor runtime: private tunnel entry, native attacks, egg/arachnid cycles, acid, enrage/cleave, one-time harvest and 34-tick respawn are implemented. Full server build and isolated boot pass. Two egg positions, exact hazard patterns/FX, Mirrorback impact timing, minion max-hit rules, Destroy rewards and the upstream unconditional morph drop remain open. This is a development checkpoint, not completed boss acceptance. No live installation changes.

## Araxxor completion candidate - 2026-10-04

Initial Araxxor runtime `b87051471` was tested and accepted by the user.
`outputs/araxxor-test-20261004` is preserved as the installation baseline.
Completion work is in PR #16; the user accepted it and explicitly approved merge. No claim of exhaustive OSRS parity, full Vengeance spell support or Combat
Achievements completion is made. Specials remain paused; Zulrah source is unchanged.

## Barrows acceptance and loot testing - 2026-10-04

User confirmed Barrows works perfectly. Added ::testloot barrows [count], default
100, range 1..1000, for administrator-only chest loot with all six brothers and
maximum reward potential. Uses the native reward calculation, diary rune bonus,
drop-rate modifiers and clue conversion. Loot goes to the ground; active run,
quest progress, collection log and chest count are not modified by this test.
Araxxor PR #16 is merged as 2e3473d25; its branch was removed after ancestry
verification and tag server-araxxor-complete-20261004 preserves the milestone.

## Fang crafting fix - 2026-10-04

IMPLEMENTED / NEEDS USER TESTING: Etch and chisel handlers for Araxyte fang
(86 Crafting) and elder venator fang (84), plus etched fang with torture.
Earlier Rancour coverage only verified the unetched recipe; that limitation is
now covered by real interaction tests. Accepted rupture assembly is unchanged.
Validation: 8 Crafting tests and full server JAR build pass, including real
item dispatch, confirmation cancellation, inventory transactions and rupture.

## Kraken and ranged loadout candidate - 2026-10-04

IMPLEMENTED / NEEDS USER TESTING: ::maxrange now supplies necklace of rupture.
Cave krakens and Kraken use the existing whirlpools/models, native animations,
magic versus typeless boss attacks, four tentacles, fishing explosives, native
loot/Slayer hooks, owner isolation, cleanup and repeat-kill respawns/countdown.
::krakentest enables temporary admin access inside the cave without assigning a
new Slayer task. Paid instances and boss-specific Combat Achievements remain out
of scope. Details: [Kraken](docs/custom/kraken.md).
Validation: 11 Kraken tests, 9 command tests, formatting, full server JAR and
isolated startup/bridge/shutdown pass. In-game visual acceptance remains pending.
Installer: outputs/kraken-update-20261004, based on the installed fang-crafting fix.
Maxrange, Kraken runtime and documentation are separate commits. No merge yet;
Zulrah, Araxxor, Barrows and the parked special-attacks baseline are preserved.

## 2026-10-04 - Kraken attack/crevice follow-up (PR #19)

- Reproduced first-impact exception: native player hit queue rejected cycles=0.
- Fixed scheduling for cave kraken, tentacles and boss; added real incoming-hit regression.
- Registered public crevice Enter and inner Use exit; verified both destination tiles against cache collision.
- 12 Kraken tests, module formatting and full JAR build passed. Isolated startup and installer checks recorded in package evidence.
- New candidate: outputs/kraken-fix-20261004/INSTALLEREN.cmd. In-game retest pending; no merge approval.

## 2026-10-04 - Kraken accepted

User confirmed Kraken works perfectly and authorized merge. Accepted runtime: dab74feae, installer kraken-fix-20261004. Preserve ranged damage / 7. Merge PR #18 dependency then PR #19 into main. Private instances remain out of scope.

## 2026-10-04 - Corporeal Beast (IN PROGRESS)

Branch feature/corporeal-beast from accepted main 0dbd0110a. Native encounter, core, lair access, testcorp and testloot corp. See docs/custom/corporeal-beast.md. No merge or in-game acceptance yet.

## 2026-10-04 - Corporeal Beast test candidate

- Encounter, core, lair access and testcorp implemented; testloot corp adds native loot rolls with sigils.
- 13 Corp tests and 16 commands tests pass; cache and full server JAR build pass.
- Isolated startup, bridge request and shutdown pass. NPC audit: only Corp (319) changed across 16,577 definitions.
- Installer: outputs/corporeal-beast-update-20261004 with rollback to accepted Kraken baseline.
- In-game acceptance pending. Timing choices and remaining clan-instance/CA scope are documented in docs/custom/corporeal-beast.md. No merge authorized.

## 2026-10-04 - Corporeal Beast accepted (PR #20)

User confirmed perfect and authorized merge. Accepted runtime 1e4f7f7e4, installer corporeal-beast-update-20261004. Preserve encounter and earlier accepted bosses. Clan instances and individual CA conditions remain separate future scope.

## 2026-10-04 - Spirit shields and Zulrah item crafting

Implemented spirit shield blessing/sigils, blowpipe assembly, existing serpentine helm and toxic weapon recipes, enhanced/ornamented tridents, mutagen variants, scale dismantling and reversible component separation. Native atomic inventory transactions preserve ingredients on failure. 60 focused tests, formatting, full server build and isolated startup/bridge/shutdown pass. User authorized merge; new recipe in-game verification remains pending. Installer: outputs/boss-item-crafting-20261004. See docs/custom/boss-item-crafting.md.

## 2026-10-04 - Treasure Trails (IN PROGRESS)

Branch feature/treasure-trails. Kraken alias added to testloot with 1-1000 validation; commands tests pass. Clue assets and drop transforms exist, but no active clue completion/reward/Mimic implementation was found. Requested scope: scroll boxes, correct rewards and Mimic; full step-by-step trails versus direct reward opening awaits user clarification. No clue completion claim or merge yet.

## 2026-10-04 - Full Treasure Trails scope confirmed

User explicitly requests full hunts, steps and puzzles, with master Mimic chance. Scroll boxes produce clue scrolls. Foundation and 11 focused tests pass; catalog contains 997 records but this is not gameplay completion. Pending: remaining challenge flows, Mimic encounter, runtime/cache integration and installer. See docs/custom/treasure-trails.md. No merge or live install.

2026-10-04 - Treasure Trails chunk: physical sliding/light puzzle boxes now retain clue ownership, reopen from inventory and are consumed by exact slot at hand-in. Atomic full-inventory issuance and stale-reference protection verified; 18 clue tests pass. Maps, remaining tasks, Mimic and live/cache validation still pending; no release or merge.

2026-10-04 - Treasure Trails map chunk: linked eight cache-named map clues to their existing native interfaces. Verified drawable map models and clientscript Close actions. 18 existing tests passed; new map test passed separately after correcting its legacy close-button assumption. Remaining maps, task coverage and Mimic remain unfinished; accepted runtime untouched.

2026-10-04 - Treasure Trails Sherlock chunk: elite introductions now assign and persist a task without advancing or rerolling. Added successful-crafting completion events and green dragonhide body / unstrung dragonstone amulet task tracking. 22 clue tests and 16 crafting tests pass, including existing fang, spirit-shield and Zulrah crafting. Detailed task coverage: docs/custom/clue-task-coverage.md. Mimic is last per user instruction. No installation or merge.

2026-10-05 - Treasure Trails master gathering chunk: runite/prospector, anglerfish/angler and redwood/lumberjack task completion is checked on real primary production with required worn slots. Mixed supported variants and Varrock armour 4 substitution included. 24 clue tests pass, including each missing slot, inventory-only gear and bonus rejection. Task coverage updated. Mimic remains last; no installation or merge.

2026-10-05 - Treasure Trails Sherlock combat chunk: ordinary dust devil, Slayer Tower nechryael and overworld lizardman shaman task kills use the existing credited-player death hook. Assignment and requirements are enforced; completion awaits return to Sherlock. 27 clue tests pass offline with cached dependencies; no dependency updates. Superior/raid variants still require validation. Mimic remains last; no installation or merge.

### 2026-10-05 - Treasure Trails: Herblore challenges
- Added successful brewing hooks for assigned super defence, anti-venom and ranging mix tasks, including valid dose variants.
- Herblore compilation passed; 28 clue tests passed (clues-herblore-retest.log).
- Actual queued brewing and in-game validation still pending. No install or merge. Mimic remains last.

### 2026-10-05 - Treasure Trails: equipment challenge
- Dragon scimitar task completes after assigned, successful equip; removal and wrong weapons do not count.
- Native inventory equip transaction tested. All 29 clue tests pass (clues-equip-retest.log).
- Task coverage: 15/60 completion hooks; 45 incomplete. Live acceptance and remaining clue systems pending; Mimic last.

### 2026-10-05 - Treasure Trails: ground-fire tasks
- Added successful ground-log ignition event and assigned yew, magic and redwood task completion.
- Firemaking compilation passed; all 30 clue tests pass (clues-firemaking-test.log).
- 18/60 task rows have completion hooks, 42 incomplete. Queued ignition, campfire compatibility and live acceptance pending. Mimic last.

### 2026-10-05 - Treasure Trails: nature and cosmic altar tasks
- Successful altar output now reports essence and base multiplier. Nature and double-cosmic tasks complete after assignment.
- Runecrafting compilation passed; all 31 clue tests pass (clues-runecrafting-test.log).
- 20/60 task rows have completion hooks, 40 incomplete. Blood altar and live altar validation pending. Mimic last.

### 2026-10-05 - Clue assignments and skill expansion (local, not released)
- Skill task coverage increased from 20/60 to 36/60: Charlie's eight assigned hand-ins, cooking/smithing, yew-longbow/rune-dart Fletching, Chivalry, nickel and Blood Altar output.
- Charlie accepts items from any source after assignment (Jagex 2022 rule); the older self-production requirement in historical notes is superseded.
- Falo assigns a persistent riddle. Watson stores partial tier deposits and preserves them when a master cannot be delivered.
- Hot/cold introductions now assign a search and device atomically instead of completing the step. Full inventory and repeat-talk checks pass.
- Boxes preserve their contents when a same-tier clue is held or banked. Making History and cached Lletya requirements follow the existing quest policy.
- Validation: 43 Treasure Trails tests and 3 Fletching tests passed; Runecrafting and Prayer Tab compile. Log: work/clues-assignment-final.log (BUILD SUCCESSFUL). Git diff check clean for this scope.
- Skill changes committed separately as c412b8ace. Remaining: 24 skill tasks, outstanding map/puzzle/world validation and other coverage gaps documented in docs/custom/clue-task-coverage.md. Mimic has NOT started; it remains last.
- Accepted installation, main and remote branches unchanged. No installer, push or merge.
### 2026-10-05 - Location-sensitive clues and god outfits (local)
- Added the assigned whip-equip challenge with worn-state, Slayer Tower, same-plane and nearby abyssal-demon checks, including accepted whip/tentacle variants.
- Added the light-orb crafting task using the existing recipe and cache-verified public Dorgesh-Kaan bank room. Exact proximity/boundary parity remains a live acceptance item.
- Corrected Juna and Mage of Zamorak outfits to count three matching worn items rather than require every slot from the cache lists.
- Coverage now 38/60 skill tasks; 22 skill tasks remain, plus outstanding map/puzzle/special cryptic routes and live validation. Mimic remains last and is not implemented.
- Validation: 47 clue tests pass (work/clues-locations-outfits.log, BUILD SUCCESSFUL), plus the previously passing 3 unchanged Fletching tests. No accepted runtime installation, push or merge.
### 2026-10-05 - Clue 40/60 test candidate
- Added assigned Ardougne gem-stall theft and sacred-eel dissection. Coverage: 40/60 skill-task rows; 20 remain, plus outstanding world/map/puzzle routes and live acceptance. Mimic remains last and is not implemented.
- Added administrator ::cluekit and ::cluetest commands for tools, tier boxes, initialized scrolls, task fixtures and reward caskets. Inventory transactions preserve existing items; normal skill requirements apply.
- Validation: 54 Treasure Trails tests, 3 Cooking tests, 2 Thieving tests and 3 Fletching tests passed. Full cache/server build successful; isolated startup, game port, Nero bridge/catalog and graceful shutdown passed against the candidate JAR/cache. Installer/rollback regression tests passed.
- Test distribution: outputs/clues-test-20261005/INSTALLEREN.cmd, with read-only CONTROLEREN.cmd and TERUGZETTEN.cmd. The package verifies baseline and payload hashes and preserves a checkpoint of replaced software/cache files; player database is excluded.
- Updated CONTENT_INVENTORY.md and preserved the expanded main-branch roadmap. Active branch: feature/treasure-trails. This is a test candidate, not a complete clue release; no merge or accepted runtime installation performed.

### 2026-10-05 - Clue commands corrected; clue development parked
- Bare ::cluetest box/casket/scroll now supply beginner items. Help uses square brackets because the client interprets angle brackets as markup. Explicit tiers remain supported.
- Validation: 55 Treasure Trails tests pass, including all three bare commands. Clues remain 40/60 skill tasks; remaining clue work and Mimic are parked at the user request. Next work: NPC combat-effect HUD, then reviewed upstream Doom integration.

### 2026-10-06 - Doom integration and NPC stat HUD test candidate
- Integrated upstream fc10877fd into a dedicated Doom module/pack: delves 1-8/deep 9+, rotations, rocks/shockwaves, larvae, demonic shield/beam, burrow rush/slams, acid/venom and holy water; native instance entry and reward/chest interfaces. Existing accepted boss scripts, weapon rules and revision 240 remain in place.
- Added ::testdoom and ::testloot doom [count] [delve], with a shared native roller that preserves active run/escrow/counters/log during sampling. Persistent 40-slot stacked reward piles and scrolling item models use strict atomic roll/stash transactions. Death loses only the active run pile; prior claimed loot remains.
- Nero native infoboxes report real current/base NPC combat-stat levels, with authenticated immutable snapshots and stale/target/session guards. Stat icons show drains/boosts and exact hover values. Poison/freeze and bespoke phase markers remain outside this stat-level HUD.
- Clue item command fix is included; clues stay parked at 40/60 and Mimic remains last/unimplemented.
- Validation: 326 selected server tests and 89 Nero client/server-plugin tests pass; cache build and real revision-240 symbol/client-script contracts pass; server JAR and external-plugin contract pass. Isolated startup verifies real bridge/catalog, continuously refreshed NPC effects/respawn snapshots, game listener and process/database cleanup against exact JAR/cache/plugin hashes.
- Startup testing found and fixed duplicate injection constructors and a bridge endpoint-prefix mismatch; production-injector and real HTTP tests prevent recurrence. Live encounter timing/animations and HUD visuals still require user acceptance.
- Previous installed baseline verified read-only: 684 files, zero hash differences. Test distribution: outputs/doom-update-20261006/INSTALLEREN.cmd, hash preflight, checkpoint and TERUGZETTEN.cmd; player database excluded. No live installation or main merge performed.
- Work is split into API prerequisites, Doom encounter/storage, commands, injector fix and documentation commits. Upstream review and deferred optional changes are in docs/custom/upstream-review-20261006.md.
- The isolated startup passes with zero exit and scoped process/database cleanup; it also reproduces the pre-existing concurrent DB-close warning. Save/drain ordering is not certified here. Upstream #281 is documented as a separate persistence follow-up.

- Remote repository instructions were preserved through a documentation-only merge. Packaged server commit f40f4d951 and Nero b29d93c remain the exact validated runtime; subsequent changes affect documentation only.

### 2026-10-06 - Doom and NPC stat HUD user accepted
- User reports perfect and explicitly authorizes merge of the tested package: server f40f4d951, Nero b29d93c. Preserve this gameplay checkpoint.
- PR #23 carries Doom and the existing clue milestone to server main; Nero PR #11 carries the accepted Studio basis and the new stat HUD to Nero main.
- Clues remain parked at 40/60 and Mimic remains unimplemented; accepting this package does not declare full clue completion. Remaining specials and other backlog features stay parked.
- Latest main roadmap grouping and AgentCraft Observatory planning are preserved. Validation remains 326 selected server tests, 89 Nero tests and isolated startup, plus installer/checkpoint/hash evidence. Existing shutdown warning remains separate.

### 2026-10-06 - Merges completed
- Server PR #23 merged into main as c2ccd5c762f98fa24568b66458edad814be1c1f0. The clue dependency PR #22 is included; remaining clue work stays parked.
- Nero PR #11 merged into main as 85af63cedb2067b3b82c63ed86b0e55048c0399d, preserving accepted basis #8/#10; #10 is closed as superseded. Both CI server pins reference the accepted server merge.
- Runtime sources on both mains equal the validated package; post-acceptance edits are documentation and CI pins only. No live deployment or player-data replacement.
- Accepted package remains outputs/doom-update-20261006 with verified hashes and rollback checkpoint. Completed feature branches are removed only after ancestry and expected-head checks.


## 2026-10-06 - Small extensions (four requested items)

Branch: feature/small-extensions, baseline main 6856cffae. Dedicated price service, central Examine, transient notification FIFO and Collection Log repeat broadcasts with reward context. Candidate implemented: 150 selected tests and runtime build pass; isolated startup observes the live OSRS price refresh, accepts paired Nero endpoints, exits zero and removes its database PID. Existing database-close ordering warning remains a separate follow-up. User acceptance pending. See docs/custom/small-extensions.md. No live install or merge performed. Parked clues/specials and accepted checkpoints preserved.


## 2026-10-06 - Compact Examine and Doom sample log fixes

User-requested follow-up on feature/small-extensions / draft PR #24. Examine is exactly two game messages: native info icon + name + description; green GE, blue HA and red LA values. This suppresses duplicate client price output. Successfully spawned ::testloot doom samples now register Collection Log counts and source broadcasts, including repeats. ::doomsim stays unlogged; both native loot piles, run state and counters are preserved. Commits f53d4e760 / 84803569e. 63 targeted tests, full runtime build and isolated startup pass. Installed baseline matched 821 hashes. Fresh small-extensions-fixes-20261006 installer/checkpoint; user test and merge pending. See docs/custom/small-extensions.md.


## 2026-10-06 - Collection Log chat-wide news

User corrected the display requirement: chat-wide News lines, not world-broadcast banners. CollectionLog now sends ordinary game-message type 0 to all online players, with native grey chat bubble, red News label and green quantity/item. Counts, source context, personal first-unlock settings/popups and Doom testloot registration remain intact. Code 06b7bfd2b on feature/small-extensions / draft PR #24. 58 targeted tests, runtime JAR and isolated startup pass. Installed small-extensions-fixes baseline matched all 821 hashes. Latest collection-chat-news-20261006 installer has its own checkpoint/rollback; in-game acceptance and merge pending.


## 2026-10-06 - Clue guardian progression and quieter chat news

- Branch fix/clue-guardian-completion, based on preserved PR #24 head aa09c7c77.
- Guardian code b46489dc0: retain same-cycle dead-NPC ownership through native deletion
  before credited kill; require every guardian, then redig advances or grants one casket.
  Alive removal, wrong owner, logout/death and expired metadata do not grant completion.
- News code ae719442c: pets always qualify; other Collection Log rewards require fresh
  GE unit price >= 1,000,000 gp. Stack totals/cache fallback do not qualify; cheap items log.
- 134 selected tests pass (61 clues, 6 market, 8 log, 35 Doom, 4 drops, 15 pets, 5 output).
  Full runtime build and isolated gameplay-smoke-abca7326 startup pass with paired Nero.
  JAR SHA-256 0de5d5bdfa79d8890d6b3775dfd4d2f3466b80938b7c2f67a6cd3eefb03bc84a.
- Latest combined installer outputs/clue-guardian-fix-20261006/INSTALLEREN.cmd;
  separate checkpoint/rollback, installed baseline verified at 821 hashes. No live install
  or merge. In-game acceptance pending; clues stay parked at 40/60 and Mimic remains last.
- Research completed: upstream PR #282 native test harness and commit 779b81b / PR #286
  level-up dialogues/jingles/fireworks. No imports. Harness needs scoped custom bootstrap;
  #286 CI reports 2/42 combat integration failures with Guice/MockK exceptions.
- Details: docs/custom/clue-guardians.md, docs/custom/small-extensions.md,
  docs/custom/upstream-pr-282-test-harness.md, docs/custom/upstream-level-up-779b81b.md.
