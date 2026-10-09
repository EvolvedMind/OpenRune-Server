# Pets

Origin: UPSTREAM + CUSTOM EXTENSIONS. Implementation: `content/other/pets`.
The accepted version extends the native registry, reward/insurance and follower APIs.
`a53731b25` carries the Lil' Zik metamorphosis fix; `83c47b4e3`/`c46abc6af`
restore follower ownership synchronization on relog; `921b43b95` adds the item-model gallery.

Regression coverage: `PetMetamorphosisTest`, `PetFollowerSyncTest`, `PetMenuTest`.
Future checks: summon, right-click after relog, call/teleport, pickup with full inventory,
metamorphosis, loss/insurance and no duplicate follower/reward.

Upstream risks: player map clock/login ordering, follower varp, NPC lifecycle,
teleports, inventory transactions and persistence. Old pet branches contain earlier
implementations and tests; archive references preserve them without replacing this version.

## Screenshot companion extension — 2026-10-09

The existing `::pet` gallery also lists the native Cats (35 forms), Dogs
(72 puppy/adult forms) and Hellcats. Their existing care, growth, dialogue,
death and follower scripts remain the sole handlers. The gallery now has
**194 item choices**: 71 existing registry pets, those 107 forms and 16 new
companion items/variants. Search matches both names and categories. Native IF3
button packets drive the existing item-model cells. The companion roster adds
no models; the later category tabs rebuild this existing interface as described
below. Administrator access to the ordinary test gallery is unchanged.

| Companion | Native item actions / integration |
|---|---|
| Archibald | The 2026 Easter egg, with seven native paint patterns; Interact, Wield and Paint. Paint opens seven native item icons and replaces exactly the owned egg in its original slot. Drop uses normal ground-item rules. |
| Broav | Drop summons `npc.wgs_broav`; native Pick-up returns the same companion item. Release retains the native confirmation/destruction route. |
| Humphrey Dumphrey | Native Egg (`obj.scrambled_egg`); Interact and native Wield. |
| Pet fish | Blue, green and spinefish fishbowls; Talk-At, Play-With, Feed and fish-food-on-bowl in both directions. |
| Mayor of Catherby | Consult and Feed; fish-food-on-mayor in both directions. |
| Pet rock | Interact and native Wield. |
| Spooky chair | Drop/Spin summons `npc.poh_hw_chair`; native Pick-up. Returns after 20 ticks; a full inventory retains the follower until room becomes available. |
| Toy cat | Native Release summons `npc.poh_toy_cat`; Pick-up returns it; owned Shoo offers confirmed release. |

`CompanionPets` is deliberately separate from the boss/skilling reward and
insurance registry. Inventory companions are not fabricated boss rewards or
Collection Log uniques. Fishbowls, rocks and eggs retain inventory/equipment
behaviour instead of borrowing unrelated moving NPC models. New actor forms
reuse `PetFollowers`, ownership, call/teleport and saved `followerObj`; their
cloned actor types do not block walking. Cached native NPC types remain intact.
Food consumption and empty-box output, and paint input/output, use inventory
transactions. Suspended item interactions recheck the original inventory object.

This bounded extension makes the screenshot roster obtainable through `::pet`;
it does not implement their originating quests, seasonal events or POH menagerie.
Short cosmetic interaction dialogue is fork-specific. The chair's safe retention
when inventory is full is also intentional: it avoids destroying a companion.
Logout/unload attempts an inventory return; if full, the existing saved follower
identity restores it on login, with a new temporary return window.

### Source decision and validation

Our accepted native pet module and the upstream default-branch/PR searches were
reviewed. [Upstream PR #249](https://github.com/OpenRune/OpenRune-Server/pull/249)
(`c092bf745c420f17f3bba3ce3c7e48e65bfa234a`, merge
`e224cb4c9494e9d7e5678c9fa726d828f03238b6`) provides the existing boss/skilling,
Cats/Dogs and reward/follower foundation. Its diff does not provide these new
companion handlers; the default branch search only found Broav map placement.
Keep the accepted implementation and extend its runtime APIs, without importing
another branch or registering Cats/Dogs twice. Rev240 native item options,
models, NPC actions and the existing pet-rock/chair sequence definitions were
resolved directly from the local accepted cache; no IDs were guessed.

`CompanionPetTest` exercises real `HeldInteractions`, `HeldUInteractions` and
`NpcInteractions` producers against native definitions and actual spawned actors.
`PetMenuTest` now uses the real `If3ButtonHandler` packet producer, including
normal search/grants, seven-pattern Paint for a non-admin, stale-item refusal,
privilege boundaries, native models, full inventories and existing boss rewards.
The pet module passes 27 tests, including existing morph/relogin regressions.
Native client rendering and the chair Spin effect still require owner in-game
acceptance; server assertions do not certify their visual appearance.

## Category tabs — 2026-10-09

The owner requested separated categories after testing the companion package.
The gallery has seven tabs: **All, Boss, Skilling, Minigames, Cats, Dogs, Other**.
Hellcats are under Cats and remain searchable by `Hellcat`. Each of the 194
choices appears in exactly one category, and All contains their union. The
Minigames tab includes Phoenix, Tiny tempor, Abyssal protector, Lil' Creator and
Pet Penance Queen; Herbi and Quetzin join Skilling. This is presentation grouping,
not a change to the native pet reward, insurance, care or acquisition registry.

Native stone buttons and shadowed labels occupy one category row. Search stays
within the active tab; changing tabs retains the query and resets pagination.
Eighteen item cards remain inside the fixed 512×334 layout. Paint hides the
category/search controls and ignores forged category clicks; it cannot become
a pet-grant operation. Native button and overlaid-label packets are both tested.

The candidate cache starts as the exact installed companion package. Only the
pet interface (`3/1103`), its init-script (`12/45542` in LIVE), and its own native
interface-metadata file (`24/14/1103`) are patched. The generated mapping binary
adds exactly 14 tab components and retains every existing mapping and ID.
All other interface-metadata files match byte-for-byte; 117,582 other LIVE
archives and 22,971 SERVER archives also match the installed cache. Models,
NPCs, objects, other interfaces, native spawn grid and existing world edits
are preserved. The isolated runtime verifies seven actual native button/label
pairs, all 194 choices, Doom/gorilla arrival tiles and the accepted object layout.
The Pets module now passes 30 tests; combined selected coverage is 108 tests.
