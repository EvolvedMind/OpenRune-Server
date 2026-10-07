# Native watermelon clue action

Branch: `feature/clue-completion`, draft PR #26. Revision 240 only.
This adds the watermelon planting action to the existing Sherlock clue flow.
It does not certify the complete Farming skill or Treasure Trails.

## Source and adaptation

Reviewed [OpenRune PR #231](https://github.com/OpenRune/OpenRune-Server/pull/231)
at `97aa0afa766469c0e2937a5aa0612b47b8c822e5` (open, no submitted reviews).
Adapted its crop/patch tables, packed state and native farming actions. Retained
the repository's RS Mod copyright and permission notice in LICENSE.md.
No upstream global raw-varp files, protocol changes or client upgrades imported.

The upstream dbrow range 62000–62049 overlaps our pet definitions. Allocated
62400–62449 instead, checked against content/API mappings in the correct namespace.
Permanent patch vars 65440–65459 and clock 65439 are defined in this module's pack,
with scope Perm and transmit Never. Client visuals use existing native varbits.
A saved crop uses its allocated row ID minus 62399, independent of row enumeration.
Watermelon remains index 7; snape grass is 27.

## Native action and safety

The server handles base patch locs through native multiloc interaction resolution:
rake, inspect, plant, compost, water, cure, harvest and clear. The five farm areas
have 20 allotment/flower/herb patch definitions and 27 crop rows.
Inputs and replacements use native inventory transactions. Protected tick delays
recheck patch state, player location, tools, levels and required inputs.
No XP or successful-plant event occurs if seed consumption fails.
Watering-can charge and compost-bucket replacements commit together.
Harvest inventory failure retains the remaining crop, and stale clear/plant
actions cannot overwrite changed state.

Growth uses saved wall-clock minutes. Backwards clock correction cannot grant
repeated growth, and zero disease chance never rolls. Patch visuals transmit the
current farm's state because the native cache shares varbits between farms.

`FarmingSeedPlantedEvent` is emitted after the real seed transaction, persistent
patch update and XP. Only a watermelon planting completes elite task 21, only in
assigned phase 9 and with its existing requirements. Completion moves to phase 10;
the clue remains until the player talks to Sherlock for the next step or final
reward casket. Spawning/holding seeds and planting another crop do not qualify.

## Validation

The tests use the actual revision-240 crop/patch cache, native LocInteractions and
LocUInteractions, inventory transactions, protected coroutines and contextual
Sherlock interaction. They cover rejection/cancellation, stale state/location/tool/
level changes, full inventory swaps/harvest, offline growth, farm transmission,
Inspect and dead-crop clear. Separate cache tests check all reachable crop art,
tools/animation definitions, stable crop indices and permanent varp scopes.

180 selected tests pass: clues 86, Farming 12, Thieving 9, market prices 6,
Collection Log 8, Doom 35, drops 4, pets 15 and player output 5. No failures,
errors or skips. Scoped Kotlin checks pass; the full runtime JAR builds.
Isolated revision-240 startup, Nero bridge health and asset lookup pass. The
private test server exits with code 0 and its PostgreSQL PID file is absent.
Existing database-close ordering warnings remain a separate follow-up; this
is not proof of a clean database drain.

Gameplay commit: `b1b04ee3558bd12352cc310951a8ed0a3eeba5d9`. JAR SHA-256: `260c446a7f995b02731e45685418e54abf665dd266c2e2103d0783da75a09551`.
In-game route/arrival and animation/rendering acceptance remain the user's test.

## Test flow

`::cluetest watermelon` atomically gives a single-step assigned elite clue, rake,
dibber, spade and three watermelon seeds. It preserves existing possessions,
levels, location and equipment; an existing held/banked elite clue prevents it.

Visit Falador farm (3056,3309,0), rake an allotment, then use the three seeds on it.
Farming 47 is required. Return to Sherlock after the completion message.
Planting is the challenge; waiting for growth or harvest is not required.

## Remaining scope

This is an upstream-based allotment/flower/herb foundation supporting a clue action.
Crop yields and growth scheduling retain the upstream simplified model. Flower
protection data is present but not applied; farmer protection, tool leprechauns,
watering-can refills, trees/fruit trees/spirit trees, hops/bush/special patches,
contracts and Tithe Farm remain outside this chunk. Full OSRS Farming qualification
and client acceptance remain open. Mimic is still scheduled last.

## Installable test checkpoint

New package: `outputs/clue-farming-20261007/INSTALLEREN.cmd`. Independent software
rollback: `outputs/checkpoint-20261007-voor-clue-farming`. The installed guardian
baseline matched all 826 checked targets. Previous frozen packages and the paired
Nero client remain preserved. The installer is prepared, not run; no live JAR,
cache or player database was changed by this work.
