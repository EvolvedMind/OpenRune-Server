# Prayer unlocks, free spellbook travel and gorilla access

Implementation reference for the owner's 2026-10-08 request. Current status and
acceptance remain in [PROGRESS.md](../../PROGRESS.md).

## Prayer policy and native integration

Chivalry and Piety no longer require King's Ransom/Knight Waves. Their normal
Prayer/Defence levels remain: 60/65 and 70/70 respectively. The native prayer
override script displays this policy without marking unrelated quests complete.

Reading a scroll asks for confirmation, consumes exactly the selected scroll in
an inventory transaction, and sets the permanent native unlock flag. Cancel,
stale inventory references and an already unlocked prayer consume nothing.

| Prayer | Native scroll symbol | Permanent unlock flag | Future loot source |
|---|---|---|---|
| Rigour | `obj.raids_prayerscroll` | `varbit.prayer_rigour_unlocked` | Chambers of Xeric |
| Augury | `obj.raids_prayerscroll_augury` | `varbit.prayer_augury_unlocked` | Chambers of Xeric |
| Preserve | `obj.raids_prayerscroll_preserve` | `varbit.prayer_preserve_unlocked` | Chambers of Xeric |
| Deadeye | `obj.deadeye_prayer_scroll` | `varbit.prayer_deadeye_unlocked` | Royal Titans |
| Mystic Vigour | `obj.mystic_vigour_prayer_scroll` | `varbit.prayer_mystic_vigour_unlocked` | Royal Titans |

CoX and Royal Titans are separate, unstarted content. This update implements the
scroll consumer; it does not create a simulated boss loot source.

Deadeye and Mystic Vigour upgrade the native Eagle Eye/Mystic Might slots. They
retain the same enabled flags, quick-prayer bit positions, exclusions and drain
handling. The server resolves upgraded names, required levels (62/63) and sounds
when selecting ordinary and quick prayers. Stored quick-prayer selections are
revalidated before enabling. Deadeye's additional defence multiplier applies
when Eagle Eye's upgraded slot is active; Hawk Eye does not grant it.

`::testprayers` supplies one of each scroll to an administrator. It requires five
free inventory slots and does not silently set or reset unlock flags.

## Free teleport policy

Fixed-destination travel on Standard, Ancient, Lunar and Arceuus spellbooks is
available without rune, Magic-level or quest requirements. Native home spells,
existing alternate destinations, Lunar group teleports and Standard Teleother
are handled. Travel grants no Magic XP.

The native teleport icon, lock filter, level filter and tooltip scripts reflect
this policy. Original spell rune/level/XP metadata is preserved because Barrows
tablet crafting and other consumers reuse `MagicSpellRegistry`. Tablet creation
continues to require its native level, runes and dark essence and awards its
native crafting XP. Only Watchtower's incorrect destination metadata changes:
`(2549,3112,2)`, with the Yanille alternate `(2544,3095,0)`.

Normal teleport restrictions remain checked when casting and again when landing.
Pending logout or death cancels the queued landing. Native animation families,
sound, action delay and queued teleport timing are retained.

Group/Teleother recipients must enable Accept Aid and explicitly accept a native
dialogue. Decline, expired offers, movement, death/logout or a newly active
teleport restriction prevent their travel. The group caster can travel without
forcing anyone else to follow.

Dynamic destinations need their owning systems: Sailing/boat location, purchased
POH, Bounty Hunter target and minigame selection are outside this update. Their
spellbook unlock policy is removed, but this does not fabricate missing world
systems. Respawn teleport currently uses the existing Lumbridge death default.

## Gorilla access and combat

The owner's screenshot correction uses the exact saved Nero placement tiles:

| Object | Native appearance | Position | Operation |
|---|---|---|---|
| Hole | `loc.yanilleholein` (2823) | `(2428,3522,0)` | Climb-down to `(2108,5654,0)` |
| Danger sign | `loc.dangersign` (1032), angle 2 | `(2429,3521,0)` | Read a short warning |
| Climbing rope | `loc.climbing_rope2` (18969) | `(2108,5651,0)` | Climb-up to `(2428,3521,0)` |

Both climbing routes use the native `human_reachforladder` sequence and one tick
of climbing delay, plus native arrival delay where applicable. Interrupted
coroutines and pending logout/death cannot land later. The outside return is
fixed and works after relog; the old temporary return varp is retained for cache
compatibility and cleared on exit. The original native cavern entrance/exit
also remain usable. Shutdown removes only the three module-owned objects.

The previously installed custom loc ID 62522 is retained and changes from the
cavern arch to the rope; the hole/sign use verified free IDs 62523/62524. The
fresh installer replaces the four task-specific Nero preview placements with
these three permanent definitions, preserving every other placement and all
deletions. The live world-edit file is not changed during development. Its exact
before/after hashes are included in the scoped rollback and boot evidence.

`::testgorillas` takes an administrator to the outside entrance. Native spawns,
models, stats, drops, kill counts and other accepted combat behavior are retained.

Only combat Tortured variants `npc.mm2_tortured_gorilla_1`/`_2` (7150/7151) receive
the Demonic melee/ranged/magic animation and projectile families, plus the native
death animation. Their native 210 HP and 13 maximum damage remain. Quest/lab
noncombat variants are excluded. Tortured gorillas have no Demonic prayer shield
or boulder ability. They change attack style after **four consecutive attacks
that deal zero actual damage**; a damaging hit resets the counter. Demonic
gorillas continue to switch after three. Miss accounting observes the standard
player hit processor's final impact, including prayer mitigation.

## Sources and port decisions

- [Upstream Demonic Gorillas PR #204](https://github.com/OpenRune/OpenRune-Server/pull/204),
  head `9ac5a127bf19bc2b2bc9811558d51fc57d72e74c`: inspected code/diff and available
  reviews (the reviews endpoint returned none). Its six Demonic variants and
  three-miss rule informed the comparison; it contains no equivalent Tortured
  integration. The fork's revision-240 boulder/event implementation is retained.
- Upstream baseline inspected: `7a6733cd1ca519b7afbc2b79f59baf91bc5496ef`, including
  [prayer repository](https://github.com/OpenRune/OpenRune-Server/blob/7a6733cd1ca519b7afbc2b79f59baf91bc5496ef/content/interfaces/prayer-tab/src/main/kotlin/org/rsmod/content/interfaces/prayer/tab/PrayerRepository.kt)
  and [spell teleports](https://github.com/OpenRune/OpenRune-Server/blob/7a6733cd1ca519b7afbc2b79f59baf91bc5496ef/content/skills/magic/spell-teleports/src/main/kotlin/org/rsmod/content/skills/magic/spell/teleports/SpellTeleportScript.kt).
  Native modules are extended locally; no branch import or revision upgrade.
- [Tortured gorilla reference](https://oldschool.runescape.wiki/w/Tortured_gorilla):
  three combat styles, maximum 13 damage and four missed attacks. Prayer quest
  removal and free spellbook travel are the owner's custom policies.
- Native revision-240 definitions and CS2 are used for item options, unlock varp
  scopes, prayer slot upgrades, components, sequence/projectile IDs and access
  objects. The new access loc and temporary varp use verified free mappings.

## Verification and manual acceptance

Native interface-button/player-target/held-item/loc producers are exercised with
their runtime consumers. Tests cover scroll confirmation and stale references,
permanent flags/reload, upgraded quick prayers, all fixed teleport destinations,
four spellbooks at level one with an empty inventory, aid/consent and late denial,
gorilla entry/exit, real hit processing and native animation protocol requests.
Resolved revision-240 NPC models/stand/walk/death and entrance collision are checked.

574 selected tests pass (including 132 clue regressions), along with scoped
formatting, cache and JAR builds. Isolated revision-240/Nero startup verifies the
actual production lobby/cavern coordinates against the loaded map's collision
flags: both are walkable. The process exits 0 and its private PostgreSQL PID file
is absent. The previously recorded database-close warning remains a separate
[persistence follow-up](upstream-review-20261006.md).

The screenshot access correction additionally passes 121 selected tests (11
gorilla tests plus prayer, teleport, boss/API/registry/network/game regressions),
formatting/cache/JAR and isolated startup with the migrated Nero overlay. The
runtime storage contains exactly the three expected custom objects on their
specified tiles; both production arrival constants resolve to walkable tiles.

Final test/build/boot evidence is recorded with the installer. Server tests do not
prove pixel-perfect client rendering. Manual acceptance: install the review
package, test prayer scroll Read/Cancel/already-unlocked and relog, cast with empty
rune inventory on each book, check prayer/spell icons in fixed/resizable mode,
then use `::testgorillas`, Climb-down/Climb-up/Read, and compare all three Tortured attack styles
with Demonic gorillas. Verify three versus four blocked hits before switching.

The first installer retains its independent TD options rollback (`8eafd7595`).
The entrance correction gets a separate installer and rollback to the verified
installed prayer/teleport/gorilla package (`f94663e17`), including only the
task-specific Nero preview migration. Neither changes playerdata or RSA or is
automatically installed.

## Underlying cavern arch follow-up

During diagnosis, live hashes matched `f94663e17`; the screenshot correction's
JAR/cache were not installed. The owner subsequently removed the
four Nero previews, so the replacement package uses the current overlay as its
guarded baseline and retains other placements/deletions.

An actual boot map scan exposed native `loc.mm2_cave_boss_waterfall_small`
(28719, Ruptured cavern) at `(2106,5652,0)`. Removing the custom object alone
reveals this original arch. The module now deletes that verified native type at
only this tile during startup, before creating its hole/sign/rope. Unload restores
the native arch only if the tile still has no visible object; subsequent editor
placements are preserved. Other occurrences of the native type are untouched.

The prior placement check counted only custom objects and did not prove this
native arch was absent. The follow-up checks effective static and spawned
objects together, including deletion masks, and explicitly rejects the native
arch at its original tile. Real LocRepository/LocRegistry tests cover removal,
unload restoration and preservation of other tiles/later editor placements.

The follow-up passes 124 selected tests (14 in the gorilla module), scoped
formatting and the runtime JAR. Isolated revision-240/Nero boot confirms exactly
the expected hole/sign/rope and walkable landing tiles. The effective map scan
no longer contains 28719 at `(2106,5652,0)`; the private server exits 0 and
PostgreSQL is stopped. Installer `gorilla-rope-complete-20261008` replaces the
matched live baseline as a complete JAR/cache/mapping/placement set, with its own
rollback. On 2026-10-09 all 21 live target hashes match this installed package.
PR #30 retains commit `0d2079013`, preserving these accepted access changes in
the merged main branch and subsequent Doom build. This hash check does not
replace visual client acceptance.
