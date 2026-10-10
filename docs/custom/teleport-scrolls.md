# Teleport animations and scrolls

Scope: the accepted revision-240 client/cache, free spellbook travel and all real
consumable teleport scrolls. Runtime handlers live in the existing
`content/skills/magic/spell-teleports` module; no cache upgrade or new core API.

## Animation correction

Spellbook landing now explicitly stops outgoing graphics and animation before
starting the Standard arrival animation. Lunar, Arceuus and Ancient casting
sequences otherwise survive a map rebuild, including their shrunken/invisible
frames. Late teleport denial and pending logout also clear the outgoing effect.
Death cancels travel without replacing the death animation.

The native assets were read from the accepted cache: Lunar actor/graphic
`4423/747`, Arceuus `6575/1296`, scroll actor/graphic `3864/1039`. The scroll
animation temporarily substitutes the held equipment; its finally block clears
animation and graphics after two ticks, both after arrival and cancellation.
The protocol consumer translates `EntitySeq.ZERO` into `setSequence(-1, 0)`;
resetting the server-side pending field to NULL would not tell the client to stop.

## Scroll routes

`TeleportScrollScript` owns all **20** real scroll options found in the native
cache. It excludes placeholder and scroll-book display items. Guthix's old held
handler was removed from `TormentedTempleScript`, leaving one active route; the
Temple's existing coordinate-change indicator continues to apply on arrival.

| Scroll | Destination (x, z, level) |
|---|---|
| Nardah | 3421, 2917, 0 |
| Digsite | 3324, 3412, 0 |
| Feldip Hills | 2542, 2925, 0 |
| Lunar Isle | 2093, 3912, 0 |
| Mort'ton | 3489, 3288, 0 |
| Pest Control | 2657, 2660, 0 |
| Piscatoris | 2339, 3648, 0 |
| Tai Bwo Wannai | 2788, 3066, 0 |
| Iorwerth Camp | 2193, 3257, 0 |
| Mos Le'Harmless | 3701, 2996, 0 |
| Lumberyard | 3303, 3487, 0 |
| Zul-Andra | 2197, 3056, 0 |
| Key Master | 1310, 1250, 0 |
| Revenant Cave | North: 3127, 3833, 0; middle: 3069, 3740, 0; south: 3075, 3653, 0 |
| Watson | 1645, 3579, 0 |
| Guthixian Temple | **4061, 4464, 0** — existing owner-approved cave arrival |
| Spider Cave | 3658, 3403, 0 |
| Colossal Wyrm | 1641, 2921, 0 |
| Chasm of Fire | 1439, 10076, 0 |
| Ardeaglais | 2543, 2216, 0 |

Each native Teleport option consumes exactly one owned scroll on arrival, with
normal teleport restrictions checked before and after the delay. Stale inventory
references, a changed origin, death/logout and unloaded destinations cancel
without consuming an item. There are no rune costs or travel XP. New routes
follow the fork's unlocked travel policy; the accepted Guthix quest requirement
is preserved.

Revenant's native Teleport is **held option 3**, not option 1. It requires explicit
Wilderness confirmation after selecting one of all three cave entrances. Config explains that this implementation retains a
warning before every teleport; it does not persist an OSRS warning toggle.
Master Scroll Book storage/default selection is a separate feature, not another
consumable scroll, and is outside this correction.

## Sources and upstream decision

- Upstream spell teleport [PR #139](https://github.com/OpenRune/OpenRune-Server/pull/139)
  is merged (`79258c96df33fa1237c80d8cf42a14bc89500aad`). The
  [current handler at `97502542`](https://github.com/OpenRune/OpenRune-Server/blob/97502542e388bc9c3b433361f3c63c3419738422/content/skills/magic/spell-teleports/src/main/kotlin/org/rsmod/content/skills/magic/spell/teleports/SpellTeleportScript.kt)
  already resets outgoing animation when no arrival sequence exists. Retain our
  accepted rune-free/group/Teleother policy and restore that cleanup, additionally
  clearing graphics and cancelled casts. No wholesale import.
- Upstream code search for `teleportscroll` found drop declarations but no scroll
  action implementation. The all-state PR search for `teleport` found no dedicated
  scroll route implementation; unrelated POH/Sailing/raid work was not imported.
- The [OSRS scroll overview](https://oldschool.runescape.wiki/w/Teleport_scrolls)
  documents consumable, single-use travel. It is not used as a revision-240 ID
  authority.
- Coordinate facts are taken from RuneLite's
  [TeleportLocationData at `0d427835`](https://github.com/runelite/runelite/blob/0d4278355dd845629fc61217e6c4e67a551c69b4/runelite-client/src/main/java/net/runelite/client/plugins/worldmap/TeleportLocationData.java),
  retaining its [BSD notice](teleport-scroll-coordinate-license.txt). Guthix's
  coordinate is intentionally the existing fork override. All 20 corresponding
  Wiki pages were also read via their raw text endpoints. The
  [Key Master destination](https://oldschool.runescape.wiki/w/Key_master_teleport)
  is `1310,1250` beside the Key Master, and the
  [Chasm scroll destination](https://oldschool.runescape.wiki/w/Chasm_teleport_scroll)
  is `1439,10076` in front of Yama's chamber; these replace RuneLite's map markers.
  Other fixed tiles lie inside the Wiki landing areas.

## Validation

**69 selected tests pass**: 10 spellbook routes/animation/restriction/group tests,
8 scroll tests (including every native scroll option and actual transaction
delivery) and 51 Tormented Demon/Temple regressions. The formatter checks and
runtime `shadowJar` build pass. An isolated rev240/Nero start with the accepted
cache passes, including all **22 walkable arrival tiles**, native plugin loading,
bridge snapshots and clean scoped shutdown. Initial test-import and reset-sentinel assertions
were corrected; collision fixtures now share one bounded native map instead of
allocating one per scroll.

Final paired-client visual acceptance remains an in-game check: use a Lunar and
Arceuus teleport, then a Guthix scroll; confirm the full-size player is visible
immediately on arrival and can move normally. Test a clue reward scroll and the
Revenant warning/decline route as well. These checks are not claimed as observed
by the isolated server tests.

## Merge and test package

[PR #35](https://github.com/EvolvedMind/OpenRune-Server/pull/35) merged as
`4b7a1ad840784cac8699ca1e47abbf82fbbd9a7e`. Its tree equals the tested head
`a113bdc44362b59c5e56b3a72452c957797e7511`. Local validation is documented above;
GitHub had no commit statuses or Actions runs for that head.

`outputs/teleport-fixes-20261010/INSTALLEREN.cmd` is a guarded candidate for the
accepted Shaman installation. It supplies the new server JAR and relevant
sources/docs; no cache payload is required. The package checks prerequisite
hashes and keeps a recoverable checkpoint. Close client/server/launcher before
installing, then use the existing launcher. No live installation was performed
while developing or merging this correction.
