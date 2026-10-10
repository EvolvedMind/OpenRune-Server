# Combat, shields and respawn policy

Origin: UPSTREAM + CUSTOM EXTENSIONS. `c46abc6af` adds explicit weapon/magic/shield
special dispatch; `9f38dbb1d` adds special reset. The accepted implementation is preserved.

The historical [special-attack audit](../special-attacks-audit-20261001/README.md)
records the starting weapon/shield investigation. Shield charging, inspection and
cooldowns are per-item/persistent. A special bar does not establish full mechanics.
Current coverage and parked work: [PROGRESS.md](../../PROGRESS.md).

Respawn policy: GWD 100 ticks = 60 seconds, other supported bosses 34 ticks = 20.4 seconds.
Encounter progression and non-respawning entities remain respected. Actual deadlines,
not guessed kill times, feed Nero. Changes to NPC death, instance cleanup or Boss DSL
need timer and duplicate-death tests as well as combat tests.

Upstream risks: target reach, autocast, shield dispatch, hit scheduling, NPC death hooks,
Boss DSL and stats/bonuses. Preserve current behaviour while evaluating any replacement.

## Ordinary NPC combat animations

Revision-240 import and lifecycle routes, measured **2026-10-10** against accepted
`main` **66f18993d**. Native IDs, framebases, models and timings remain authoritative;
the RuneMonk reference cache is never installed over the accepted cache.

The [parameter pack](../../content/other/npc-animations/pack/src/main/resources/pack/configs/npc-combat-animations.toml)
adds **10,671 missing parameters to 3,749 definitions**: 3,673 attacks, 3,298 blocks
and 3,700 deaths. Three additions belong to non-Attack active transform children.
Existing animation parameters are not replaced. The KBD attack script and the
three GWD attack scripts stay intact; only their missing ordinary block/death
roles are filled where explicitly allowed by the importer.

The [runtime module](../../content/other/npc-animations/src/main/kotlin/org/rsmod/content/other/npc/animations)
adds bounded, native visual routes:

- **56 spawn forms:** 54 Lumberjack emergence variants and two Temple Trekking
  tentacle variants. Native form/sequence changes precede the active body. Each
  part waits its own native duration and guards the real attack cooldown; NPC
  identity, HP and coordinates remain.
- **11 combat forms:** five ground Wyrms, the Killerwatt ball and five shadow
  shades enter their active body on a real hit. Death/despawn/foreign form changes
  cancel remaining work. The accepted Fiyr validation hook stays unchanged.
- **29 multipart deaths:** Xarpus, Jormungand, 24 Maiden variants and Maggot King.
  Maiden uses its two native corpse meshes as well as the corresponding sequence
  parts. The original visual type is restored before reward attribution.
- **40 non-retaliating props:** dummies, barricades, portals, webs, flowers and
  reviewed static targets suppress human attack fallback. Named portal/flower
  deaths are retained; truly unanimated targets use native removal/rewards.

A small optional extension in [NpcDeath](../../api/death/src/main/kotlin/org/rsmod/api/death/NpcDeath.kt)
plays multipart sequences/forms before the existing removal, drop and respawn
route. Existing public death entry points retain their signatures and default
behaviour. No body-customisation packet or replacement model is introduced.

### Measurement and remaining evidence

**3,993 Attack-option NPC types / 11,979 attack, block and death slots**:

| Evidence category | Slots |
|---|---:|
| Missing parameters now mapped | 10,668 |
| Existing explicit parameters | 304 |
| Named families without a native block | 385 |
| Accepted existing script routes | 95 |
| Separately reviewed existing script routes | 16 |
| Combat-form runtime routes | 32 |
| Non-retaliating / static no-block / static removal | 110 |
| Multipart runtime deaths without a parameter | 28 |
| **Still open** | **341** |

This is **97.15% technical action coverage**; **3,844 / 3,993 types** have their
three roles accounted for. A touched definition is not a completed encounter.
Source review and no-named-block evidence are distinct from visual acceptance.
The owner installed the lifecycle package on 2026-10-10; all 17 payloads and
979 prerequisites match its manifest. Login then failed on the new-account
varbit. The reference-table recovery below retains that exact runtime JAR and
all NPC animation data. Neither package has been merged.

The [measurement and checks](npc-animation-evidence.json) and
[149 unresolved native types/action candidates](npc-animation-open-actions.json)
make the remaining work explicit. These include quest-held/dormant actors,
ambiguous deaths/blocks, and boss/raid phases whose controller or exact visual
role remains unproven. Cached movement or a compatible skeleton alone cannot
choose a special attack, a death phase or its trigger. Secondary ranged/magic
styles, specials, projectile/effect placement and complete paired-client visual
acceptance remain outside the three-slot percentage and must still be verified.
Current status is recorded only in [PROGRESS.md](../../PROGRESS.md).

### Sources and preserved decisions

The supplied RuneMonk package has 14,513 sequence entries: 14,408 agree with the
paired cache's framebase; 12,156 names match, 2,314 are absent and 43 differ.
Native revision-240 mappings take precedence. The older dump and candidate list
provide movement/candidate evidence, not verified combat-role assignments.

[Official upstream NPC configuration](https://github.com/OpenRune/OpenRune-Server/blob/ed27808014f232cefd8087fc956fc08e6292c2b7/.data/raw-cache/server/npcs.toml)
and [PR #216](https://github.com/OpenRune/OpenRune-Server/pull/216), closed and
unmerged at **375581c32314973d7de6a04e5e7f3713ef429f22**, were reviewed. They
provide useful ordinary-family examples, not a verified 99% action catalogue.
Keep the fork's accepted controllers; do not import the mixed upstream drop.

The official [RuneLite ModelLoader](https://github.com/runelite/runelite/blob/master/cache/src/main/java/net/runelite/cache/definitions/loaders/ModelLoader.java),
published decoder 1.13.1, supports offline exact-geometry/limb comparisons. Native
worn-model matches and 81 separately rendered weapon meshes establish weapon
classes; a shared human skeleton never selects a weapon. In the RuneMonk model
preview, **Scarabs 729** appear through **1949 scarab_spiral** and disappear through
**5464 scarab_spiral_rev**. The reviewed reverse sequence is used for death rather
than the forward spawn sequence. This preview does not replace live acceptance.

### Validation and owner testing

**370 Kotlin tests** pass across native combat, the new lifecycle module, Treasure
Trails, TD, Doom, gorillas, specials, Araxxor, Kraken, Corp and Barrows. The 13 new
module tests use actual create/hit/retaliation/death-queue producers and exercise
interruption, corpse meshes, native timing, reward identity, one reward and world
respawn. Eight importer safety tests and 225 independently checked positive-duration
lifecycle/source bindings pass. Formatter and runtime JAR build pass.

All **16,579 NPC definitions** are compared against the accepted cache. Only the
reported missing parameters differ; stats, native models, other parameters,
sequence assets/timings and weapons are unchanged. The selective stage preserves
**117,585 LIVE archives** and **22,972 unrelated SERVER archives**, changing only
3,749 selected NPC files in **SERVER 2/58**. Client NPC archive 9, gamevals, models,
interfaces, CS2 and the paired revision stay intact.

The new JAR and selective cache pass isolated rev240/Nero boot with private ports
and an isolated database, pet/tabs checks, gorilla access geometry, real bridge
snapshots and clean shutdown. No live playerdata or world edit is altered. Native
apps are unavailable through this session's computer tools, so no paired-client
visual acceptance is claimed.

The frozen `npc-animations-20261010` and `npc-animations-lifecycle-20261010`
packages contain the reference-table regression described below. Their archive
content checks and successful boot did not establish login compatibility. The
guarded `outputs/npc-animations-login-fix-20261010/INSTALLEREN.cmd` recovery
requires the installed lifecycle snapshot and has its own rollback. Close the
local client/server before owner installation.
Test Wyrm/Killerwatt/shadow activation, lethal first hits, the two tentacle spawn
forms, complete Maiden/Xarpus/Jormungand/Maggot King death parts, static portal/
flower deaths and normal respawn. Check the clue guardians through actual clues,
weapon variants, jaguars and the accepted bosses/specials in fixed/resizable views.
This is a test candidate; the remaining 341 slots are not advertised as complete.

### Login regression and reference-table recovery — 2026-10-10

The supplied startup/login log records `CONNECT_FAIL` after
`Error getting varp from varbit: 65485`. RSProx forwards the request correctly;
its disconnected status follows the rejected server login. The actual
`AccountLoadResponseHook` account flag setter fails for both new and existing
players. `new_player_account` points to base varp 65516, which is absent under
that ID in the affected cache.

Displee rewrote shared config index metadata while patching the NPC archive.
The native `-1` first file sentinel in server varp archive **2/67** became
`32767`, shifting all **5,971 reference file IDs by 32,768**. The archive bytes
were unchanged, so the original preservation check missed the corruption.
`PreserveConfigReference.java` retains the original reference metadata and
copies only NPC CRC/checksum/digest, length and revision fields plus index version.
The patcher now compares file-ID arrays for every archive as well as its payload.

Recovery changes only **SERVER/main_file_cache.dat2**; the existing idx255
pointer/length remain valid and its file hash is unchanged. All **22,973 server archive payloads** and
**117,585 client archives** match the installed lifecycle package, including
NPC animation archive 2/58; the runtime JAR is unchanged. Every reference file-ID
table now matches the accepted pre-import cache. Eight metadata regression
fixtures pass. All **19,438 varbits** resolve their base varp, and the real
account flag setter/getter passes for new and existing players. Isolated rev240
boot, Nero bridge snapshots, pet tabs, gorilla access and clean database/server
shutdown pass with the recovered cache. This regression check does not require
an external market-price refresh. A paired-client login remains an owner check;
no live account records or running processes were changed by development tests.

Reproduction commands and native evidence rules: [offline tools](../../tools/npc-animations/README.md).

## Weapon implementation record (2026-10-03–04)

Original branch: feature/weapon-completeness, based on accepted runtime plus organization
commit 82d6c0203. User requests complete specials and normal weapon/charge behaviour.
Zulrah encounter files, rotations, reach policy and respawn policy remain unchanged.

The following mechanics and test results were recorded for successive slices.
They are not new validation or a statement of the currently installed software.
The accepted package and recovery references are in [baseline](baseline.md).

Shadow/Venator charge follow-up: charging and full refunds now use a single native
inventory transaction, revalidate the exact item after dialogue and reject negative or
over-capacity amounts. Resource shortages or insufficient refund space roll back both
the weapon and every material. Existing recipes, native charge layouts and capacities
are retained. Three regression cases exercise both weapon families, including full
capacity, swapped items and a refund where only one of two material stacks fits.
This does not certify their remaining normal-attack mechanics or live effects.

Melee impact slice: Warhammer, Elder Maul,
Bandos/Saradomin/Zamorak godswords, Whip and Anchor effects now attach to the native
hit instead of independent world timers. Drains use applied damage, zero damage
does not drain, and Healing Blade retains its pre-overkill heal basis. Callbacks are
once-only and reject replacement logins. Three new tests pass, including execution
through the registered Warhammer handler; all 42 special tests pass. No extra weapon
registrations are claimed and client FX placement remains unqualified.

Further melee families: 17 additional item variants.
Dragon claws has four conditional accuracy branches, bounded split damage, the
all-miss chip outcome and paired hit delays. Post-special reductions apply separately
to each split hit, including independent Elysian rolls and integer rounding.
Dragon scimitar locks protection
prayers for eight ticks on positive impact without disabling Protect Item.
Darklight, Arclight (including inactive) and Emberlight drain Attack, Strength and
Defence additively from base levels after an accurate hit impacts; demon flags select
10%/15% rather than 5%, with the additional one level. Their special does not consume
Arclight charges; its ordinary charge/infusion lifecycle remains unfinished.
Dragon sword and Ancient mace use native melee hit processing with PvP prayer
penetration, retaining retaliation, hit modifiers and damage attribution. Ancient
mace drains actual PvP damage and restores prayer up to base plus that hit; NPC
restoration retains the rolled amount even when weapon immunity blocks damage.
Tests cover cancellation/relogin, zero hits, source/target FX ownership, exact
animation symbols, prayer expiry, damage bounds and a real prayer-piercing hit queue.
The subsequent full JAR also passed the isolated revision-240 boot, Nero catalogue,
live respawn snapshot refresh and graceful database/server shutdown checks. This
validation did not replace files in the accepted installation.

Remaining qualification: client animation height/frame alignment, variant cosmetics,
NPC prayer/immunity interactions, boss stat-drain
floors and complete demon metadata coverage. The initial cache had no demon flags.
A parameter-only cache overlay now classifies 96 exact ID/name matches against
pinned Wiki DPS data and sets Duke resistance on two variants. A full comparison of
16,577 NPC definitions confirms zero unrelated field/parameter changes; an ordinary
NPC override was rejected because it reset existing fields. Tests exercise both
real packed Waterfiends and synthetic drain fixtures. This is not full coverage of
all transformed/NMZ demons or special vulnerabilities such as Yama. No local developer
client was listening on port 7780 during this validation. No "perfect FX" claim.
Formula references: [claw distributions](https://github.com/weirdgloop/osrs-dps-calc/blob/main/src/lib/dists/claws.ts),
[base-level demonbane drains](https://github.com/weirdgloop/osrs-dps-calc/blob/main/src/lib/scaling/DefenceReduction.ts),
[Ancient mace](https://oldschool.runescape.wiki/w/Ancient_mace).

Trident slice: eight normal/enhanced/ornament sea/swamp pairs plus full sea identities.
Item-local charge state uses the existing powered-staff varobj bit layout; inventory
transactions atomically pay/refund resources and preserve ornament and remaining charges.
Full tradable tridents derive 2500 charges from their identity. Coins are not refunded.
A cast spends one charge even on a splash; empty/PvP attempts do not create free hits.
Caster launch and target impact use distinct cache FX and projectile timing.

This is not yet full toxic-trident qualification: NPC venom and all encounter/PvP-area
exceptions remain to verify. Selected handler tests do not certify live client visuals.
Ordinary trident attacks add no special-energy entries.

Mechanics reference: https://oldschool.runescape.wiki/w/Trident_of_seas_full

Scythe charge slice: normal, Holy and Sanguine forms now have persistent per-item
charges, atomic 200-blood-rune/one-vial payments for 100 charges, check/uncharge menus,
and one charge consumed for a damaging swing rather than per hit. Empty forms retain
the existing size-based normal attack with their weaker stats. No uncharge refund is
implied outside a well; confirmation explicitly warns that resources are lost.
Vyre-well storage/refunds, secondary area targets, variant FX qualification and
impact-modified damage accounting remain open. Corrupted and quest forms are not
silently aliased. Fifteen module tests pass (nine trident, five scythe, one cache export).

Blowpipe slice: normal and Blazing blowpipes store nine dart types and scales in the
existing three native varobjs. Load/unload/uncharge transactions are atomic. Ranged
bonuses use the loaded dart, not unrelated quiver arrows. Normal attacks and Toxic
Siphon consume stored ammunition with Ava conservation and two-thirds scale usage.
PvM/PvP rapid delays are two/three ticks. Siphon uses doubled accuracy, 1.5x maximum
and schedules half the queued damage as healing, guarded against replacement logins.
Venom, exact live projectile trajectory and impact-time cancellation remain unqualified.

Tests: 21 special-weapons + 35 special-attacks passed.
Historical snapshot: [weapon registry](weapons-registry-20261003.tsv).
Reference: https://oldschool.runescape.wiki/w/Blowpibe

Eye of Ayak slice: server cache category corrected to PoweredStaff with range 6 and
3-tick ordinary casts. Native per-item varobjs preserve up to 50,000 charges and
whether the recipe used two death runes/one chaos rune or one demon tear. Recipes
cannot be mixed before uncharging; all remaining materials can be refunded atomically.
Soul Rend costs 50%, has 2x accuracy, scales the base maximum by 13/10 before gear,
and uses a 5-tick attack delay. Magic defence bonus drain is per NPC spawn, floors at
zero without altering Magic level or shared NPC definitions, and applies to subsequent
player and NPC magic accuracy. Respawn clears it. Ordinary and special PvP attempts
are rejected without spending resources. Doom passive recharge remains a separate
weapon integration check; the accepted encounter is not deferred by that check.

Validation: isolated revision-240 cache build passed; 27 ordinary-weapon and 39 special
attack tests passed. Tests cover refunds,
full inventories, final-charge splashes, cast/impact ownership, deferred drain, negative
base bonuses and respawn cleanup. The first cache build exposed stale shared CS2;
a fresh workspace-local LOCALAPPDATA resolved it. Live effect height/trajectory and
special impact timing still need client capture verification; tests confirm the intended
source/target wiring, not visual parity. No accepted installation files were replaced.
Mechanics/formula reference: https://github.com/weirdgloop/osrs-dps-calc/blob/main/src/lib/PlayerVsNPCCalc.ts

Impact completion follow-up: hits now share an opt-in, once-only effect list through
copies made by native processors. Callbacks run after damage is applied and receive
actual capped damage; discarded/cancelled hits never invoke them. Soul Rend drains
actual damage and Siphon heals its calculated pre-overkill amount only when that hit
impacts, retaining the source-login guard. Their previous independent world timers
are removed. Verified with two engine tests, a native NPC processor integration test,
and the full special suite (39 tests). No callback is registered by existing Zulrah code.

Sanguinesti slice: both ordinary and Holy staffs have native item-local 20,000-charge
storage, 2-blood-rune recharge/refund transactions and their cache check/charge/uncharge
menus. A four-tick cast consumes one charge even on splash. The recorded formula
uses floor(Magic/3), 1/5 leech chance and +8 damage on a leech proc; healing uses half
actual impact damage and cannot affect a replacement login. Cast, impact and heal use
the corresponding ordinary/Holy cache effects. Empty and PvP attempts do not cast.
Six Sanguinesti tests pass; combined selected suites now total 75 (34 weapons, 39
specials, 2 engine). PvP minigame exceptions and live effect trajectories remain open.
Reference: https://github.com/weirdgloop/osrs-dps-calc/blob/main/src/lib/PlayerVsNPCCalc.ts

Venom follow-up: blowpipe (normal and Toxic Siphon) and swamp-trident impacts now
roll their one-in-four venom chance only after positive damage. Charged serpent helm
identities guarantee NPC venom. NPC venom ticks every 30 ticks, increasing 6 through
20 damage, respects poison immunity and falls back to poison for venom-only immunity.
Ordinary poison cannot downgrade active venom; death/respawn clears the native state.
Helmet charge consumption itself remains outside this slice. Trident final-charge
consumption now follows hit construction so the equipped attack state is retained.
Five venom tests pass; selected suites total 80 (39 weapons, 39 specials, 2 engine).
The isolated cache build passed. Live client FX verification is still pending.
The accepted Zulrah source remains identical to the organization baseline.

Parameter-only NPC overlays use `[[npc_params]]`, `id` and `[npc_params.params]`.
They merge after existing server NPC overrides without changing client definitions,
combat stats, movement or other parameters. Unknown NPC IDs fail the pack.
Reference catalogue: [pinned Wiki DPS NPC data](https://github.com/weirdgloop/osrs-dps-calc/blob/89c3e25b344aea90d0189746e4b5f73dde0f0383/cdn/json/monsters.json).
Duke resistance: [Wiki strategy](https://oldschool.runescape.wiki/w/Duke_Sucellus/Strategies).

Silverlight/Darklight follow-up: both normal swords now receive their 60% demonbane
accuracy and damage bonuses (42% with Duke resistance). Dyed Silverlight retains
only its damage bonus; non-demons are unchanged. The actual equipped-item collector
and formula operations are regression-tested alongside Arclight/Emberlight controls.

Dragon hasta: all five poisoned/unpoisoned variants now implement Unleash. Damage
and accuracy scale by full 5%-energy steps, selected melee accuracy rolls against
stab defence, and PvP Protect from Melee is pierced via the native hit path. The
handler pays extra cost and leaves the minimum to the normal dispatcher; both
use native energy modifiers. Tests cover 5%, 25% and 100%, misses/hits, NPC/PvP,
all variants and reduced-cost mode. The caster uses the shared thrust animation
with the distinct post-2024 hasta spot effect at height zero. Live frame alignment
remains unverified. Reference: [Dragon hasta](https://oldschool.runescape.wiki/w/Dragon_hasta).
The recorded special suite passed 61 tests.

Saradomin sword slice: ordinary Saradomin sword has its shared-accuracy melee hit
and separate 1-16 magic hit, with separate melee/Magic experience. PvP Protect from
Magic blocks the secondary hit entirely. Both blessed identities use melee offence
against magic defence, 25% increased maximum damage and 65% energy; their damage
still follows melee prayers and immunities. Caster sword effects and target lightning
use separate cache symbols. Tests cover NPC/PvP, hit/miss, protection prayers,
energy, experience routing and actual hybrid accuracy calculation. Blessed sword
ordinary degradation remains a separate unfinished weapon task. Visual frame/height
parity has not been verified in a live client.
References: [Saradomin sword](https://oldschool.runescape.wiki/w/Saradomin_sword),
[blessed sword](https://oldschool.runescape.wiki/w/Saradomin%27s_blessed_sword),
[magical melee](https://oldschool.runescape.wiki/w/Magical_melee).
Validation: 65 special tests, 129 selected tests overall, full server JAR build.

Ancient warrior slice: four Vesta longsword identities use selected melee offence
against one-quarter of stab defence, with 20%-120% damage bounds. Three Statius
warhammers use 25%-125% damage bounds and drain current Defence only on positive
impact: 30% for ordinary/Last Man Standing, 75% for Bounty Hunter. Reductions apply
after rolling damage. Cancelled, blocked or stale-login hits do not drain. All 68
special tests pass, including exact accuracy boundaries and cumulative drains.
Bounty Hunter/Deadman usage restrictions, degradation,
boss-specific drain floors and live effect alignment remain separate open work.
References: [Vesta's longsword](https://oldschool.runescape.wiki/w/Vesta%27s_longsword),
[Statius's warhammer](https://oldschool.runescape.wiki/w/Statius%27s_warhammer),
revision-240 per-item special descriptions. The packed Statius effect uses the
same sequence/model as the granite hammer effect with its own recolours; retained
the Statius-specific symbol rather than substituting the granite recolour.

Abyssal tentacle slice: both variants implement Binding Tentacle. Impact freezes
for eight ticks even on a miss and independently rolls 50% poison starting at four.
Native freeze/poison immunity, existing freeze duration, cancelled hits, dead targets
and stale source logins are covered. Charge degradation remains unfinished.
The whip previously used graphic sequence 1669 as its player animation; the packed
spot 341 confirms 1669 belongs to that graphic. Whip/tentacle now animate the player
with the native whip attack and send the graphic to the target. A cache-backed test
rejects graphics accidentally used as player animations throughout the melee table.
All 73 special tests passed for this slice. Live timing/height
verification is still pending.
Reference: [Abyssal tentacle](https://oldschool.runescape.wiki/w/Abyssal_tentacle).

Staff protection slice: eight Dead/Light/Balance/toxic/Deadman identities now activate
their variant-specific player and cast effects for 100% special energy. A temporary
native varp expires at 100 ticks. Incoming melee damage halves at impact after
normal prayer reduction; magic/ranged/typeless damage is unchanged. Switching away
alone does not cancel protection, but receiving positive damage without a supported
staff does. Zero damage does not cancel; activation refreshes rather than stacks;
login/logout clears state. Native impact tests cover health, damage callbacks and
prayer-rounding order. Cache rebuild, 79 special / 143 selected tests, full JAR,
isolated startup/catalogue/clean shutdown passed.
Legacy duel restrictions, passive rune-saving/charges and live visual qualification
remain open. There is no claim that cast effects alone reproduce every lingering
visual stage. Reference: [Staff of the dead](https://oldschool.runescape.wiki/w/Staff_of_the_dead).


Dark bow / Duality slice: nine Dark bow identities now dispatch, including LMS and
both Deadman identities. Ordinary Descent retains 5/8 minimum damage on accuracy
misses; its 0-to-maximum roll clamps low rolls, while BH uses a uniform 7/10-to-maximum
roll. Dragon damage caps at 48 before target reductions; native Corp and Elysian
reductions run after the roll. Each arrow uses its own projectile delay and processed
hit for XP. Missing double-launch metadata rejects before resource use, including
Seeking dragon arrows in the current server cache: this ammunition still needs its
native projectile metadata completed. No extra Seeking minimum is added.
Dragon knife Duality now uses the native two-knife player animation; poisoned knives
have their own player/projectile effects. Its explicit special projectile no longer
fails validation merely because normal projectile metadata is absent. Tests execute
all five knife variants and reject single-knife attacks before animation/consumption.
Live frame/height qualification, event-world usage
restrictions and unimplemented target-specific reductions remain open.
References: [Dark bow](https://oldschool.runescape.wiki/w/Dark_bow),
[Dark bow (bh)](https://oldschool.runescape.wiki/w/Dark_bow_(bh)),
[Dragon knife](https://oldschool.runescape.wiki/w/Dragon_knife), revision-240 symbols
and packed item/projectile definitions. This is not a claim of 285/285 completion.

Validation: 87 special / 151 selected tests pass; server JAR and isolated
Nero-bridge startup, catalogue and clean shutdown pass. Zulrah source is unchanged.

BH Dragon mace: Shatter uses 15% native energy, 125% selected melee offence against
60% of crush defence and 150% melee maximum. Target levels remain unchanged.
Damage reductions follow the rolled damage; misses do not roll or apply modifiers.
Two new tests cover NPC/PvP accuracy boundaries, handler routing, FX, damage stages
and energy. All 89 special tests passed for this slice. Live visuals
and BH world restrictions remain open. Primary reference:
[Dragon mace (bh)](https://oldschool.runescape.wiki/w/Dragon_mace_(bh)).

Dorgeshuun slice: four Bone daggers and the Dorgeshuun crossbow use the last positive
damage contributor to choose guaranteed versus normal accuracy. This is independent
of top damage and insertion order; misses and imported totals do not replace the
last attacker. Contributions clear resets the marker. Both specials respect
QuestRequirements for quest_deathtothedorgeshuun and cost 75% native energy.
Defence drains by actual damage only when Defence is not already lowered; cancelled
hits and stale source/target identities have no effect. Bone dagger uses its native
player stab plus a distinct graphic; Snipe uses the bone special projectile and
one native ammo-consumption attempt. Native target/prayer modifiers remain in the
hit queue. Boss-specific drain floors and Kephri's special team-case are not yet
qualified. Eight new tests pass; 97 special / 161 selected tests and full JAR pass.
References:
[Bone dagger](https://oldschool.runescape.wiki/w/Bone_dagger),
[Dorgeshuun crossbow](https://oldschool.runescape.wiki/w/Dorgeshuun_crossbow).

Dorgeshuun isolated startup/catalogue and clean shutdown passed; recorded proof:
`dorgeshuun-validation-20261003.json`.

## Acceptance FX correction (2026-10-04)

Bludgeon Penance now places its miasma graphic on the NPC/player target, height 0,
with the existing 30-client-cycle impact delay. The wielder retains the attack
animation and no longer receives the target graphic.

Both Nightmare staff specials now animate the player with
`seq.nightmare_staff_special` (8532). Revision-240 cache decoding shows that
Volatile cast spot 1760 uses sequence 8546 and Eldritch cast spot 1762 uses
sequence 8548. The previous implementation incorrectly applied these effect-model
sequences to the player's body as well, producing disappearance/scale glitches.
The cast graphics retain their own sequences; target graphics, damage, energy,
attack speed and hit timing are unchanged. No cache/client patch is required.

Regression tests execute Bludgeon against NPC and player targets and distinguish
both Nightmare player animations from the decoded graphic sequences. Live visual
acceptance was not claimed by these tests. The user subsequently accepted the final
`6168204ee` checkpoint and authorized PR #15 on 2026-10-04; see [baseline](baseline.md).
No new weapon family was added by this FX correction.

Validation: 99 special-attack tests pass (0 failures/errors/skips); full server
JAR builds successfully. Visual acceptance is not claimed by these tests.
