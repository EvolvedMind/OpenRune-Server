# Clue guardian completion

Implementation: `TrailGuards` in `content/other/treasure-trails`, code `b46489dc0`.
Current status: [PROGRESS.md](../../PROGRESS.md); approved source/package:
[baseline](baseline.md).

Expected flow: coordinate dig → guardian encounter → all guardians defeated → dig again
→ next clue, or exactly one matching casket for the final step.

## Cause and implementation

`NpcDeath.deathWithDrops` runs the death sequence before credited kill hooks.
For a temporary guardian, `NpcRepository.del` publishes `NpcStateEvents.Delete`
first. The old Delete handler removed the owner link before the kill hook could
write phase 5 to the clue item, so the next dig repeated the fight.

`TrailGuards` retains deleted dead guardians until same-cycle kill hooks consume
the metadata. A late-cycle event clears uncredited deletions. Every guardian must
receive owner kill credit; duplicate/wrong-owner hooks cannot satisfy the counter.
Alive removal, logout, player death and script shutdown do not grant completion.
The phase-5 marker persists in the clue item's vars; the existing dig/advance
transaction produces the next step or one casket. Global death order and existing
boss mechanics remain unchanged.

## Recorded validation — 2026-10-06

61 clue tests passed, including six lifecycle regressions. The fixtures use the real
NPC repository Delete producer, native credited-kill dispatch, clue dig consumer
and inventory transactions. They cover next-step/final-casket output, copied item
state, multiple guardians, duplicate/wrong-owner credit, stale clues, alive removal,
logout/death cleanup and expired metadata.

The combined package also includes the [unit GE news filter](small-extensions.md).
Its 134 selected tests, runtime build and isolated startup passed; the user approved
the integration into main. Exact references and remaining formatting/database notes
are in [baseline](baseline.md). This fixes guardian progression; it does not complete
the remaining clue tasks or implement Mimic.

## In-game regression

Use `::cluekit` and `::cluetest scroll hard coordinate` if no hard clue is held/banked.
Follow the coordinates, dig, defeat every guardian and dig again. The next step must
appear without a new guardian; the last step must issue one casket.
An old phase-4 clue must win its guardian once after updating, because the old build
did not save proof of the earlier defeat.

Test fixtures shorten animation waits; they do not establish native render timing.
