# Clue test commands

Available on merged `main`; continued clue development is a separate branch. Requires an
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
| `::cluetest elf` | Assigned elf pickpocket challenge. Pickpocket in Lletya or Prifddinas, then return to Sherlock. Requires Thieving 85. |
| `::cluetest watermelon` | Assigned single-step watermelon planting clue, three seeds, rake, dibber and spade; requires Farming 47. Rake an allotment at Falador farm (3056,3309), plant seeds, then return to Sherlock. |
| `::cluetest enchant` | New on `feature/clue-tasks-50`: assigned dragonstone enchantment clue, ring and runes. Standard spellbook, Lvl-5 Enchant, Magic 68. |
| `::cluetest chest` | Assigned castle-chest clue. Search for traps at 2588,3291,1 or 2588,3302,1; Thieving 72 and four free inventory slots. |
| `::cluetest lamp` | Assigned lamp clue and light orb. Fix a broken lamp at 2699,5294,1; Firemaking 52. |
| `::cluetest shayzien` | Assigned clue, four lovakite bars and hammer. Bars on an anvil, select tier 2+ platebody; Smithing 63+ and Mining 65 for the clue. |
| `::cluetest tablet` | Assigned Barrows-tablet clue, dark essence block and runes. Lectern at 1679,3765,0; Arceuus spellbook, Magic 83. |
| `::cluetest cremate` | Assigned Fiyr-cremation clue, remains, magic pyre logs and tinderbox. Funeral pyre at 3462,3282,0; Firemaking 80. |
| `::cluetest mage` | Assigned spiritual-mage clue and Saradomin staff. Wear it and kill a Saradomin spiritual mage; Slayer 83. |
| `::cluetest shade` | Assigned Fiyr-shade clue. Kill one inside catacombs, e.g. 3460,9695,0; Firemaking 65. Door/key access remains outside this task slice. |
| `::cluetest aerial` | Cormorant glove, bait and knife. Wear glove, catch mottled eel at Lake Molch; Fishing 73/Hunter 68. |
| `::cluetest skullball` | Ring of Charos and assigned clue. Wear ring, enter trapdoor 3543,3462, talk to Skullball Boss and score a goal; Agility 25. |
| `::cluetest ape` | Ninja greegree and assigned clue. Wield it and complete six obstacles starting 2754,2742; Agility 48. |
| `::cluetest rellekka` | All six graceful pieces and assigned clue. Wear the whole set throughout seven obstacles starting 2625,3677; Agility 80. |
| `::cluetest warlock` | Butterfly net and empty jar; catch black warlock, e.g. 1233,3745; Hunter 45. |
| `::cluetest chin` | Three box traps; Lay near red chinchompa, wait, then Check. Hunter 63; e.g. 1316,3168. |
| `::cluetest tecu` | Rope/net and assigned native vm01 clue; Set-trap on young tree 1470,3087, then Check. Hunter 79. |
| `::cluetest reanimate` | Abyssal head and runes. Master Reanimation near Dark Altar, kill your creature; Arceuus book, Magic 90/Slayer 85. |
| `::cluetest spirit` | Seed, filled pot, trowel, charged can, rake and spade. Plant/water seed; five-minute sapling, then plant an outdoor patch. Farming 83+. After 58h40 growth and health check, travel TO it. |
| `::cluetest parts` | Parent clue with three cryptic visits. Read, visit matching NPCs with required equipment, collect and Combine all matching parts; no Sherlock hand-in. |
| `::cluetest info` | Current initialized inventory clues: row, kind, step, phase and clue text. |
| `::cluetest` | Usage help. |
| `::cluerewards` | Existing command to collect pending casket rewards after freeing inventory space. |

Tiers: `beginner`, `easy`, `medium`, `hard`, `elite`, `master`.
Box, casket and scroll default to beginner when the tier is omitted.
Box/casket quantity defaults to one and must be between 1 and 28.
The task index is the suffix of the native `cluehelper_skillchallenge_<tier>_<index>`
row; Tecu uses `master vm01`, not `master 26`. All 60 task rows now have handling. Consult [task coverage](clue-task-coverage.md).

No command equips, deletes, replaces or banks existing possessions, raises skills,
teleports the player, or marks a task completed. A failed addition rolls back the
whole set. A held or banked clue of the same tier prevents a new test scroll;
finish or manually drop it before creating another one. Random scrolls can still
select unimplemented routes; these commands are development tools, not a claim
that Treasure Trails is complete. Mimic is not implemented.

## Guardian dig regression

The focused guardian fix is on `fix/clue-guardian-completion`: [details and test flow](clue-guardians.md).
Use an existing coordinate clue, defeat every guardian, then dig again for the next step or
final reward casket. `::cluetest scroll hard coordinate` plus `::cluekit` supplies a fresh
coordinate test when no hard clue is held/banked. This does not resume the remaining clue
implementation or Mimic.

The guardian fix is now merged into main. Further clues resumed on user instruction;
remaining coverage is still tracked separately from the approved guardian fix.
