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
button packets drive the existing item-model cells; this requires no new cache
or interface assets. Administrator access to the ordinary test gallery is unchanged.

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
