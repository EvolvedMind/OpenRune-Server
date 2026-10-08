# Tormented Demons: encounter, Temple, rewards and crafting

## Sources and scope

The fork retains its BossDSL implementation from [OpenRune PR #213](https://github.com/OpenRune/OpenRune-Server/pull/213), merged as `c0690026c32925fcd87e0cee1b752b5018cae02a` (reviewed head `84084ea01fa198a393175cc44e3690aa4552bc87`). The current upstream encounter blob `7b4ac6893dc32866569e123e553ca948e59c8947` was reviewed; it does not supply complete lifecycle, grouped combat or Temple access. No wholesale branch import or revision upgrade.

Primary mechanic references, fetched from the Wiki revision API on 2026-10-08:

- [Tormented Demon](https://oldschool.runescape.wiki/w/Tormented_Demon), [strategies](https://oldschool.runescape.wiki/w/Tormented_Demon/Strategies), [Temple](https://oldschool.runescape.wiki/w/Ancient_Guthixian_Temple), [chamber indicators](https://oldschool.runescape.wiki/w/Map:Multicombat/AGT).
- [Emberlight](https://oldschool.runescape.wiki/w/Emberlight), [Arclight](https://oldschool.runescape.wiki/w/Arclight), [scorching bow](https://oldschool.runescape.wiki/w/Scorching_bow), [purging staff](https://oldschool.runescape.wiki/w/Purging_staff), [Duradel's notes](https://oldschool.runescape.wiki/w/Duradel%27s_notes).
- [Smouldering heart](https://oldschool.runescape.wiki/w/Smouldering_heart), [gland](https://oldschool.runescape.wiki/w/Smouldering_gland), [flesh](https://oldschool.runescape.wiki/w/Smouldering_pile_of_flesh).

The supplied Alora Nero captures are observations, not instructions or proof of every OSRS mechanic. `capture-tds-20261007-230037.npack` SHA-256 `36b7a89a572d75cfd631d72258cc2c839d554c7d6d9a62d5cdc48c8bcc59838a`; `recording-20261007-230331.npack` SHA-256 `79eecc4964e8e587a32a21dbbebb080260597a8defd0bfd838bc595dd2fa2597`. No foreign assets are imported.

This delivery implements the encounter, public Temple access/spawns, grouped fighting, reward pipeline, smouldering consumables and synapse/claw crafting. While Guthix Sleeps itself and the global Combat Achievement system are separate content; access respects the fork's existing quest requirement policy.

## Combat and appearance

- 600 HP, size 3, passive normal spawns, six-tick attacks, maximum normal hit 31, respawn 21 ticks, Greater Demon task 29 and 1,065 Slayer XP. Native stat-derived combat XP bonus is retained. Cannon immunity and the current 30% Water weakness are packed explicitly.
- Random melee/ranged/magic protection on spawn. Prayer changes after 150 actual HP lost, using the last damaging combat style, with a six-tick attack pause. Poison and NPC-source hits contribute to actual health loss; blocked rolls do not. Thralls must use an unprotected style.
- Non-demonbane/non-abyssal damage is reduced by 20% while shielded. The first player attack and periodic special open one unshielded hit. Crush style, heavy ranged and cast spells receive the slow-weapon flat bonus and the group-dependent recovery delay. Wrong-prayer punish damage is fixed at one third of that bonus. Powered staff attacks do not acquire spell punish damage.
- Cast-spell minimum and maximum both include the flat bonus. Manually casting while carrying a slow weapon uses its native attack speed/style. Inactive Arclight retains Darklight's demonbane properties. Healing Blade excludes TD punish damage from its sustain while preserving ordinary boss behavior.
- TD damage XP is deferred to native NPC impact and follows applied HP loss exactly, including shield reduction and overkill. No damage or Hitpoints XP is granted for a blocked hit. Ordinary spell casting XP remains separate. Other NPCs retain their existing launch XP route.
- At 30 ticks, the defenceless appearance and guaranteed accuracy become active. Native model variants use models 55475/55474 copied from the revision-240 client cache; they preserve options, size, animations and all other client definition fields. HP, UID, base identity and contributors stay on the original actor. No NPC body-customisation packet is used.
- Reset, departure, logout, unreachable targets, death, respawn and plugin shutdown restore appearance/state appropriately. Reset heals and clears contributions. Timers and old impacts cannot mutate a replacement encounter.

Grouped combat has one player owner per demon. Chamber limits are 1/2/3; another player cannot pile onto the same actor. The first two demons use complementary melee and ranged/magic roles, with staggered normal attacks. The third throws single bombs. Membership changes reassign roles. Binding specials occur simultaneously across the group after 60 ticks.

Bombs snapshot valid landing tiles, bind for two ticks and land after four, with 40–45 typeless damage. Movement avoids damage. Flight damage survives the demon dying; reset/unload cancels it even if it has already reached the player's hit queue. The player impact processor now honours explicit arrival guards; ordinary hit copies with distinct damage remain unchanged.

## Temple and test routes

All 26 canonical starting placements are in `.data/raw-cache/map/npcs/tormented_demons.toml`: ten single-room spawns, ten double-room spawns and six triple-room spawns. The compiled map test counts unique native placements, rather than only inspecting TOML.

The Guthixian Temple scroll consumes one scroll after validated travel to the user-approved cave arrival `(4061,4464,0)` (2026-10-08 screenshot `CoordGrid(0_63_69_29_48)`). The default admin teleport uses the same destination. Native climbing walls reach the skull entrance on level 2; the transformed open skull door enters at `(4062,4466,0)`. The entrance hole returns outside. A lit sapphire lantern used on a light creature offers Temple travel from the exterior arrival `(4063,4557,0)`. Native `td_multiway_indicator` updates on login/movement and clears outside the dungeon.

Admin test commands preserve existing items and add kits atomically:

- `::testtd`: approved cave arrival; `::testtd 2`: central pair.
- `::testtd 1`: single chamber; `::testtd 3`: triple chamber.
- `::testtd entrance`: full normal entry route.
- `::testtd kit`: combat weapons, ammunition, runes and supplies.
- `::testtd items`: notes, crafting components, charged/infused Arclight, scrolls and lantern.
- `::testloot td [1-1000]`: existing native reward hooks, Collection Log and ground delivery.

## Drops and smouldering items

The legacy TOML table is deliberately replaced by one registered Kotlin table. Guaranteed infernal ashes are always separate from the primary item. Primary selection is ordered: synapse 1/500, then claw 1/500 only after synapse failure, then the 11/125 smouldering pool, then one ordinary result. There is no synthetic `nothing` padding. Herb and tree-herb seed pools and the 29:1 noted-shortbow/unstrung-longbow split are retained. Two Temple scrolls roll independently at 1/12, and the native elite clue/scroll-box route at 1/128. Preview metadata reflects the conditional chances without executing rewards.

Smouldering drops are intercepted after normal reward/log processing, avoiding duplicate delivery. The items remain private on the ground and cannot be taken or telegrabbed. Two permanent server-only inventories store native item IDs, packed coordinates, remaining online ticks and uses. Logout removes live ground actors; login restores them with the saved remaining time. Ten-minute expiry pauses offline. Each player's pending ground list is bounded at 256 records.

Flesh has four uses, no attack delay and at level 99 heals 18 up to 117 HP. Heart maintains five combat boosts for 200 ticks and uses the existing native heart activation graphic. At level 99 boosts are +20 melee stats, +14 Ranged and +11 Magic; stats decay normally after the maintain period. It prevents ordinary decay, preserves explicit drains, survives death, and allows divine expiry to clear the corresponding boosts without changing other maintained-source rules. Gland restores 10–15 Prayer every four ticks for 80 ticks and ends on native teleport. Native buff structures 965/966 use timer units of 25 and four ticks; the corresponding native varbits are synchronised.

The Wiki gives the verified level-99 consumable amounts, not complete low-level formulas or the gland's server pulse implementation. Lower-level flesh scaling and the native percentage/constant heart boosts are fork formulas matching those published checkpoints; exact parity beyond those checkpoints requires independent source or gameplay verification. Gland duration is 48 seconds in game ticks against the Wiki's approximate 49 seconds.

## Crafting

Duradel's notes are actually delivered by the existing Kuradal dialogue, including resupply/full-inventory handling. Reading them persists the synapse unlock. The existing quest requirement policy is enforced; notes are required for all three synapse weapons.

- Emberlight: hammer/anvil, boostable Smithing 74, synapse plus active or inactive Arclight with 10,000 combined charges/infusion. Selected blade identity and skill/tools are rechecked after suspension.
- Purging staff: hammer/anvil, unboosted Smithing 55, boostable Crafting 74, synapse, iron bar and battlestaff; 13 Smithing XP plus Crafting creation XP.
- Scorching bow: boostable Fletching 74, synapse and unstrung magic longbow, in either item order.
- Each synapse weapon gives 730 creation XP first time, then 73. Permanent flags are distinct per recipe. Reverting returns only the synapse after confirmation.
- Two burning claws combine atomically without XP.
- Darklight plus three ancient shards at the native Catacombs altar creates Arclight with 1,000 charges. One/two/three shards add 333/666/1,000 charges. Normal successful Arclight hits consume charges and add persistent infusion; depletion becomes inactive without losing infusion. Specials retain the existing charge-free route. Check shows charges/infusion.

All consumption/output uses inventory transactions; no XP or unlock flag is committed before successful output. Tests cover normal native item/anvil interaction, full inventory, cancellation, first/subsequent XP, charged/infused blades, reversion and shard combinations.

Native inventory options are checked against the revision-240 cache: Wield is `HeldOp.Op2`, Arclight Check is `Op3`, and synapse weapon Revert is `Op4`. Check/Revert handlers do not intercept Wield. Acceptance regression tests call the real `HeldInteractions` producer and `HeldEquipOp` consumer, rather than publishing guessed menu events.

## Validation and final acceptance

The scoped regression run passes **412 tests**, including **49 TD tests**, 100 special-attack tests, 46 weapon tests, native boss/packet/registry tests and the accepted Araxxor, Kraken, Corporeal Beast, Zulrah, Barrows and Doom suites. The Slayer module compiles; it has no standalone test suite. Heart tests exercise native divine application/expiry, coexistence with other maintained sources, stat drains, death and logout/unload cleanup. Scoped formatting, cache/gamevals, runtime JAR and isolated revision-240/Nero boot are required for the packaged candidate; evidence is included in the installer.

The full `tormented-demons-complete-20261008` package remains immutable. Its follow-up `tormented-demons-options-fix-20261008` installer has a separate checkpoint/rollback to the verified installed full TD package. The menu/arrival patch passes **51 TD tests**, including native Wield, Check, Revert and scroll producers. No live files, player database, client revision or RSA are replaced by development validation.

The user tested the encounter and authorized merge after the menu/arrival corrections on 2026-10-08. The available computer tooling cannot operate the native revision-240 game client. Technical test success is not a claim of independently proved parity for undocumented low-level consumable formulas. Current status is maintained only in `PROGRESS.md`.
