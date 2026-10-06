# Clue guardian completion fix

Branch: `fix/clue-guardian-completion`, based on the preserved `feature/small-extensions`
head `aa09c7c77`. Code commit: `b46489dc0`. Revision 240. Awaiting user acceptance.

Coordinate dig -> guardian encounter -> all guardians defeated -> dig again -> next clue,
or exactly one matching reward casket when this was the last step.

## Cause and implementation

Native `NpcDeath.deathWithDrops` calls its death sequence first. For a temporary guardian,
`NpcRepository.del` publishes `NpcStateEvents.Delete` before the subsequent credited kill
hooks run. The old Delete handler removed the clue-owner link, so the hook could never
write phase 5 to the clue item. The next dig consequently started the same fight again.

`TrailGuards` now retains deleted dead guardians until the same-cycle kill hooks consume
their metadata. The normal late-cycle event clears uncredited deletion metadata, preventing
stale entity/player retention. An encounter counter requires every guardian to receive owner
kill credit; duplicate or wrong-owner hooks cannot satisfy it. Alive removals, logout, player
death and script shutdown do not grant completion. The native phase-5 marker persists in the
clue item's vars, and the existing dig/advance transaction produces the next step or casket.
No global NPC death order or existing boss mechanics changed.

## Validation and testing

61 Treasure Trails tests pass, including 6 new lifecycle regressions. The fixture uses the
real NPC repository/registry Delete producer and native `NpcDeath` kill dispatch, plus the
existing clue dig consumer and inventory transactions. Animation waits are shortened in the
fixture; native game-tick/render timing remains an in-game acceptance check. Coverage:
next-step dig, copied item state, final casket, multi-guardian credit, duplicate/wrong-owner
credit, stale clue state, alive removal, logout/death cleanup and expired uncredited deaths.
The other 55 clue regressions remain green; skill-task coverage stays 40/60 and Mimic parked.

Initial guardian-only build and isolated `gameplay-smoke-237e9bfc` startup pass with paired Nero endpoints.
Process exit is zero; the temporary database PID is gone. The known database-close ordering
warning stays a separate follow-up. Runtime JAR SHA-256: `51759ad32ca0b7d9b92d91a5de17dbe943a8fc9079429a24393c613197aaf252`.
Installed `collection-chat-news-20261006` baseline matched all 821 file hashes.

Latest package: `outputs/clue-guardian-fix-20261006/INSTALLEREN.cmd`, with a dedicated
software checkpoint and rollback. Installer preflight and payload hashes are checked before
handoff. No live install or merge performed. Earlier Examine/chat-news/Doom fixes are retained.

For an ordinary hard coordinate test, use `::cluekit` and `::cluetest scroll hard coordinate`
(only if you do not already hold/bank a hard clue). Follow the coordinates, dig, defeat the
guardian, then dig again. The next step must appear without a fresh guardian. Repeat on the
final step to receive one reward casket. An already broken pre-update phase-4 clue must win
its guardian once with this build; the old build did not save proof of the earlier defeat.

## Combined test package

The latest installer also includes the user's Collection Log news correction in
`ae719442c`: pets always qualify; other log rewards need a fresh GE unit price >= 1m.
Cheap items still log, regardless of stack size. All 134 selected tests pass (61 clue,
6 market, 8 Collection Log, 35 Doom, 4 drop, 15 pet and 5 player-output cases).
The final runtime build and isolated `gameplay-smoke-abca7326` startup pass. Runtime SHA-256:
`0de5d5bdfa79d8890d6b3775dfd4d2f3466b80938b7c2f67a6cd3eefb03bc84a`. Paired client/cache unchanged; user in-game acceptance remains pending.
Upstream [PR #282](upstream-pr-282-test-harness.md) and
[level-up commit](upstream-level-up-779b81b.md) were researched only, without imports.

## User-approved merge (2026-10-06)

User approved merging the tested candidate. PR #24 merged to main as `b289741bc`;
PR #25 merged to its parent branch as `e6b1702fa`, then its exact reviewed head
`4211eca06` was integrated into main as `474296f2b`. The final tree equals the
134-test / runtime / isolated-startup validated source tree. Advisory formatting
checks report violations and are not described as green. No live installation
was performed by merging; earlier installers/checkpoints remain available.
The user also resumed full clue development; the 40/60 task count has not increased yet.
