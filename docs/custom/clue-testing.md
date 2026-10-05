# Clue test commands

Available on the unreleased `feature/treasure-trails` branch. Requires an
administrator account and a build containing this change. These commands do not
update the currently installed server by themselves.

| Command | Result |
|---|---|
| `::cluekit` | Spade, sextant, watch, chart, rope, knife, tinderbox, hammer and chisel in inventory. |
| `::cluetest box master 5` | Five master scroll boxes. Open one to test box-to-scroll assignment. |
| `::cluetest scroll easy` | Initialized easy clue with normal trail length. |
| `::cluetest scroll easy map` | Initialized easy map clue; optional kind must exist in that tier's starting catalog. |
| `::cluetest casket hard 3` | Three hard reward caskets for loot/UI tests. These can grant normal rewards and collection-log entries. |
| `::cluetest task elite 9` | Assigned yew-longbow challenge, configured as a single-step test. |
| `::cluetest task master 18` | Assigned sacred-eel challenge, single-step test, without supplies. |
| `::cluetest eel` | Assigned sacred-eel challenge, knife and three eels in one transaction. Requires normal skill levels. |
| `::cluetest gem` | Assigned Ardougne gem-stall challenge. Steal a gem at 2667,3303,0, then return to Sherlock. Requires Thieving 75. |
| `::cluetest info` | Current initialized inventory clues: row, kind, step, phase and clue text. |
| `::cluetest` | Usage help. |
| `::cluerewards` | Existing command to collect pending casket rewards after freeing inventory space. |

Tiers: `beginner`, `easy`, `medium`, `hard`, `elite`, `master`.
Box/casket quantity defaults to one and must be between 1 and 28.
The task index is the suffix of the native `cluehelper_skillchallenge_<tier>_<index>`
row; the indexed task may still be incomplete. Consult [task coverage](clue-task-coverage.md).

No command equips, deletes, replaces or banks existing possessions, raises skills,
teleports the player, or marks a task completed. A failed addition rolls back the
whole set. A held or banked clue of the same tier prevents a new test scroll;
finish or manually drop it before creating another one. Random scrolls can still
select unimplemented routes; these commands are development tools, not a claim
that Treasure Trails is complete. Mimic is not implemented.
