# Lizardman Shamans

Revision 240; implementation in `content/bosses/lizardman-shaman`, with its
native config/varp definitions in a separate `pack` module.
Current acceptance and remaining work belong in [PROGRESS.md](../../PROGRESS.md).

## Source decision

The fork and [upstream OpenRune](https://github.com/OpenRune/OpenRune-Server/tree/97502542e388bc9c3b433361f3c63c3419738422)
were checked for Shaman/lizardman aliases. Upstream has spawn, config, Slayer and
loot references but no discovered ordinary Shaman encounter controller. Searches
of open/draft and closed PRs for `shaman` and `lizardman` returned no matches.
Keep the existing native definitions/spawns/loot integrations and add the missing
controller using this fork's Boss DSL; no upstream branch import.

The owner's [Shaman reference](https://oldschool.runescape.wiki/w/Lizardman_shaman)
was robots-blocked. Indexed wiki content, including [tier-5 Shayzien protection](https://oldschool.runescape.wiki/w/Shayzien_%285%29),
was available: protection stacks per worn piece and does not prevent poison.
The current Dragon warhammer **1/3000** rate comes directly from
[Jagex's 29 May 2024 Project Rebalance](https://secure.runescape.com/m=news/project-rebalance-combat-changes?oldschool=1).
Old 1/5000 references are superseded. Exact probabilities and visual/timing
parity for specials remain a paired-client acceptance check; cache-native IDs
and automated technical coverage are separate evidence.

## Native bindings and mechanics

Only `npc.zeah_lizardshaman_1/2` (6766/6767),
`npc.lizardman_cave_shaman_1/2` (7744/7745) and
`npc.molch_lizardshaman_1` (8565) use this controller.
Raids variants and their loot table remain separate. All ordinary definitions
keep their models, size, stats, native block/death params and Slayer identity.
The 8-tile attack approach now reaches the registered AI handler; melee is only
eligible within native melee reach. Attack rate is six ticks.

| Action | Native revision-240 assets | Behaviour |
|---|---|---|
| Melee | `shay_lizard_warrior_attack_melee` 7192 | Native accuracy/prayer processing, max 31 |
| Ranged | `shay_lizard_warrior_attack_ranged` 7193; `lizardman_spit` 1291 | Native projectile/hit processing, max 21; Protect from Missiles blocks it |
| Acid spit | 7193; `lizardshaman_spit_acid` 1293 and `lizardshaman_acid_splash` 1294 | Snapshots target tile, flight/impact aligned at three ticks; moving outside its 3×3 splash avoids damage and poison; damage 25–30 |
| Jump | `shayzien_lizard_boss_jump` 7152; `shayzien_lizard_boss_land` 6946 | Snapshots landing tile; checks the full NPC footprint; lands after five ticks, damage 20–25; resumes native attack targeting and unlocks movement after recovery |
| Summon | `shayzien_lizard_boss_minion_summon` 7157; `zeah_lizardshaman_spawn` 6768; explosion 1295 | Three finite, unattackable actors follow the target; after eight ticks each explodes for 8–10 within radius two (Temple one); no respawn |

Temple jump damage uses its smaller 2×2 area. The random selector weights
melee/ranged/acid/jump/summon 4/4/2/1/1; melee requires reach and jump requires a
walkable footprint. Summon has an 18-tick cooldown. These are bounded gameplay
choices, not an asserted extraction of OSRS server probabilities.

Each worn tier-5 helm/body/legs/gloves/boots item contributes one fifth acid
protection; integer damage is `damage * (5 - pieces) / 5`. Items in inventory,
supply armour and lower-tier pieces do not count. Poison is applied through
`PlayerPoison`, retaining antipoison and worn immunity. This does not add diary
or Captain Clieve's Slayer-helm substitution.

Jobs capture the current encounter and player UID. Queues only exist for active,
finite specials; each tick checks parent death/deletion, logout, plane and
16-tile departure. Respawn/unload explicitly cancels jobs, unlocks movement and
removes minions. Native movement handles pathing; no global idle polling is added.

## Rewards and testing

The existing ordinary loot table receives an independent tertiary Dragon
warhammer roll at 1/3000. Its common loot and clue rolls are retained; existing
native remains handling is not duplicated. Reward delivery uses
`NpcDropTableKillHook`, native ground ownership, Collection Log and loot tracker.
Native `param.killcount_varp` on all five types points to
`varp.shaman_killcount` (65469, Perm/Never), shared across variants. Notifications
are suppressed for these regular mobs; the test command displays the total.

Administrator commands:

```text
::test shamans
::testloot shamans 100
::getvarp shaman_killcount
```

`testloot` defaults to 100 and accepts 1–1000; invalid counts or extra arguments
are refused before any reward/KC hook. Aliases are `shaman`, `shamans`,
`lizardman_shaman`, `lizardman_shamans`. The teleport uses the screenshot's exact
1451/3696/0 coordinate and does not replace existing world spawns.

Validation: 12 Shaman, 22 command, 17 drops, 14 gorilla, 51 TD, 13 NPC lifecycle
and 8 Collection Log tests (**137 total**, no failures/skips). Tests exercise the
native approach producer, registered handlers, real health/hitsplat consumer,
prayer, acid/poison/partial armour/dodge, jump retargeting, finite spawns and
cleanup, registered teleport and native KC hooks. Loot tests parse the production
TOML and verify 1/3000 preview, then force the configured unique's successful
branch for native ground delivery/log/loot-tracker checks across every variant.

The stock global CS2 workspace initially failed on unrelated missing symbols;
the existing isolated revision-240 workspace builds correctly. Only selected
server archives are applied to the candidate; client archives are unchanged.
`PatchShamans` preserves **22,971 unrelated SERVER and all 117,585 LIVE archives**,
plus **22,545 pre-existing config files**. All 220,157 prior gameval mappings
retain their IDs; only the permanent KC mapping is added. Fourteen metadata
fixtures cover the signed varp sentinel and deliberate native file insertion.
Isolated rev240/Nero boot, all five native Shaman bindings, the exact teleport
tile, seven pet tabs, accepted gorilla/Doom access, bridge and clean shutdown
pass. The actual login account flag passes for new/existing players, with zero missing
bases among 19,438 varbits.

Guarded package: `outputs/shamans-20261010/INSTALLEREN.cmd`, built against the
accepted installed NPC login-recovery package. The installer checks prior hashes,
refuses running live processes, backs up affected software and has a separate
rollback. It does not modify client/cache revision, RSA or playerdata.
Owner paired-client checks: normal ranged/prayer, acid with no/partial/full T5,
move out of spit/jump/spawns, repeated attacks after landing, leave/re-enter,
and loot/KC/log progression. No owner visual acceptance is claimed yet.
