# Slayer menus and helmet crafting

Source review: **2026-10-07**, [server commit `5abcbc386`](https://github.com/EvolvedMind/OpenRune-Server/commit/5abcbc38681baf013fbd965ff80432da2a248c4c). Current status: [PROGRESS.md](../../PROGRESS.md).

Slayer has existing handlers to reuse, but the requested menu and helmet conversions are incomplete. This review follows source handlers and data; it does not establish behavior in the installed client/cache.

## Trade menu

Requested layout: **Tasks | Equipment | Rewards | Unlocks | Extend**, reached through a Slayer master's **Trade** option. Each tab name below links to the owner's reference image.

| Tab / reference | Existing server support | Gap or difference |
|---|---|---|
| [Tasks](assets/slayer-tasks-reference.jpg) | Cancel, block and unblock handlers; master-specific block slots, streaks and points. | No active-task Store/retrieve/swap handler found. Mortimer's stored offers are assignment choices, not a stored active task. The pictured More Tasks control is not implemented. |
| [Equipment](assets/slayer-equipment-reference.jpg) | A 26-item Slayer Equipment shop definition and the generic coin-shop API exist. | Trade and the shop dialogue currently open rewards instead. Stock is finite and restocking, unlike the image's infinity markers. No integrated Equipment tab found. |
| [Rewards](assets/slayer-rewards-reference.jpg) | Cache-driven item IDs, quantities and Slayer-point costs; Buy 1/5/10/50 and Examine handlers. | The pictured item catalogue is unverified. Purchases can charge for undelivered items; see below. |
| [Unlocks](assets/slayer-unlocks-reference.jpg) | Purchases write native unlock bits; task selection checks unlocks. Some rewards have a separate enabled/disabled state. | Buying an unlock does not implement its mechanic or crafting recipe. Toggle handling is inconsistent. |
| [Extend](assets/slayer-extend-reference.jpg) | Task amounts use native replacement/additive extensions. Bulk purchase of remaining extensions exists. | Verify each pictured card's row, cost and effect in the paired cache. Generic extension support does not prove the Barrows-specific Oh brother effect. |

The opener uses native `interface.slayer_rewards` and client scripts 405/328. The exact five-tab composition is not established by those calls. Preserve the existing master rules: ordinary cancellation costs **30**, Mortimer cancellation **100**; block costs vary **40–120** by master. The screenshot's blanket 100-point block text must not override those rules silently.

The pictured basilisk, vampyre, aquanite, gryphon, dark beast, ankou, black dragon and metal dragon targets have [server mappings](../../.data/raw-cache/server/slayer/slayer_target_monsters.toml). Exact unlock/extension cards, prices and connections to those targets still require the native cache rows. The separate [Barrows boss-task route](../../content/skills/slayer/src/main/kotlin/org/rsmod/content/slayer/core/SlayerBossTasks.kt) caps the chosen count at 36 without checking an extension; do not count that as verified Oh brother support.

Sources: [NPC routes](../../content/skills/slayer/src/main/kotlin/org/rsmod/content/slayer/SlayerEvents.kt), [interface setup](../../content/skills/slayer/src/main/kotlin/org/rsmod/content/slayer/SlayerInterfaces.kt), [task actions](../../content/skills/slayer/src/main/kotlin/org/rsmod/content/slayer/rewards/SlayerRewardTasks.kt), [master definitions](../../or-cache/src/main/kotlin/dev/openrune/tables/skills/Slayer.kt), [equipment stock](../../.data/raw-cache/server/shops/slayershop.toml), [shop API](../../api/shops/src/main/kotlin/org/rsmod/api/shops/Shops.kt), [reward dispatch](../../content/skills/slayer/src/main/kotlin/org/rsmod/content/slayer/rewards/SlayerRewardsHandler.kt).

## Correctness gaps

1. **Opening rewards changes the assigned master.** `SlayerInterfaces.openInterface` writes both `slayer_master` and `slayer_master_in_focus`. Opening another master's menu can therefore change completion points, block/cancel rules and Konar/Wilderness restrictions for the existing task. Menu navigation must update focus only; retain the assignment's master, target, count and area. Sources: [interface setup](../../content/skills/slayer/src/main/kotlin/org/rsmod/content/slayer/SlayerInterfaces.kt), [task engine](../../content/skills/slayer/src/main/kotlin/org/rsmod/content/slayer/core/SlayerTaskManager.kt), [Konar checks](../../content/skills/slayer/src/main/kotlin/org/rsmod/content/slayer/core/KonarSlayerAreas.kt).
2. **Reward purchases can lose points.** The shop pays the full cost before non-strict insertion and refunds only on an error. The transaction API can return success with partial or zero delivery: Buy 5 with one free slot can deliver one non-stackable reward while charging five. Commit delivery and its matching cost together; enforce unique-item limits across the whole batch. Sources: [reward shop](../../content/skills/slayer/src/main/kotlin/org/rsmod/content/slayer/rewards/SlayerRewardShop.kt), [non-strict insertion](../../engine/objtx/src/main/kotlin/org/rsmod/objtx/Transaction.kt), [result counts](../../engine/objtx/src/main/kotlin/org/rsmod/objtx/TransactionResultList.kt).
3. **Block slots need real gates and bounds.** The seventh-slot diary check always returns true. Unblock confirmations can index beyond Mortimer's two slots. Validate the current master's slot count, unlock requirements and selected slot server-side. Sources: [slot gates](../../content/skills/slayer/src/main/kotlin/org/rsmod/content/slayer/rewards/SlayerBlockSlots.kt), [unblock handling](../../content/skills/slayer/src/main/kotlin/org/rsmod/content/slayer/rewards/SlayerRewardTasks.kt).
4. **Disabled rewards can still affect task logic.** Toggle handling retains the purchase bit, but task blocking and extensions read only that bit. The fossil-wyvern block and longer-revenants toggle-off values are not read by those consumers; the superior route does check its disabled state. Use a shared enabled-state check where appropriate. Sources: [unlock handler](../../content/skills/slayer/src/main/kotlin/org/rsmod/content/slayer/rewards/SlayerRewardUnlocks.kt), [toggle mappings](../../.data/raw-cache/server/slayer/slayer_toggleable_rewards.toml), [task engine](../../content/skills/slayer/src/main/kotlin/org/rsmod/content/slayer/core/SlayerTaskManager.kt).
5. **Suqah target data is wrong.** Target 83 is labelled Suqah but maps to `npc.prif_citizen_eldalote`; that NPC also receives the Suqah tip. Correct and verify the target mapping before accepting Suq-another one or Suqah task completion. Sources: [target mapping](../../.data/raw-cache/server/slayer/slayer_target_monsters.toml), [task tips](../../.data/raw-cache/server/slayer/slayer_task_tips.toml), [NPC tips](../../.data/raw-cache/server/slayer/slayer_npc_tips.toml).

### Upstream reuse

- [PR #277](https://github.com/OpenRune/OpenRune-Server/pull/277), merged as `d91c4216ac276f5ad705c0ad0c9664f2bd321b6c`, routes Trade to the existing equipment shop and adds the shop dependency. Its changes are absent from the reviewed fork. Reuse its shop integration when implementing the requested tab flow; it does not supply a five-tab menu or fix the other findings above.
- Its prerequisite, [PR #276](https://github.com/OpenRune/OpenRune-Server/pull/276), merged as `4c8a516d341a434601bfecb2091d49176be9860e`, fixes unsigned stock decoding. The fork's [codec](../../or-cache/src/main/kotlin/dev/openrune/codec/osrs/impl/InventoryServerCodec.kt) still reads signed shorts, while Slayer Equipment stocks **50,000 broad arrows**. Port and verify this bounded fix before exposing that stock.

Both changes were inspected, not imported. Keep revision 240 and the accepted client/cache pair.

## Existing helmet recipes

| Route | What the source defines |
|---|---|
| Ordinary helmet before Porcine of Interest | **55 Crafting** and `slayer_helm_unlocked` / Malevolent masquerade. Consume one each of black mask (`obj.harmless_black_mask`), earmuffs, face mask, nose peg, spiny helmet and enchanted gem; output `obj.slayer_helm`, 0 XP. Quest state follows the active policy. |
| Ordinary helmet after Porcine of Interest | Same recipe plus reinforced goggles. Quest policy selects the before/after recipe. |
| Imbued helmet creation or imbue transfer | No explicit recipe/handler found. The ordinary recipe accepts neither a charged-mask alias nor an imbued-mask input. |
| Coloured/boss variants, including Araxxor | No active head/ornament conversion or reversion recipe found. Existing item references do not prove creation works. |
| Hooded helmet and hooded variants | No active hood-combination or reversion handler found. |

Sources: [two helmet recipes](../../content/skills/crafting/pack/src/main/kotlin/org/rsmod/content/skills/crafting/pack/Crafting.kt), [recipe selection](../../content/skills/crafting/src/main/kotlin/org/rsmod/content/skills/crafting/CraftingApi.kt), [quest/unlock gates](../../content/skills/crafting/src/main/kotlin/org/rsmod/content/skills/crafting/util/CraftingGates.kt).

Inherited [item definitions](../../.data/raw-cache/server/items.toml) already include normal/imbued and several colour/boss families. Reuse verified definitions; their presence does not establish working conversions or combat parameters. Ingredient sources also exist: the [Slayer cape dialogue](../../content/skills/slayer/src/main/kotlin/org/rsmod/content/slayer/dialogue/GenericDialogue.kt) supplies a hood, and [Araxxor's drop table](../../content/drops/src/main/kotlin/org/rsmod/content/drops/tables/monsters/AraxxorDropTable.kt) contains `obj.poh_araxyte_head`.

The generic [crafting worker](../../content/skills/crafting/src/main/kotlin/org/rsmod/content/skills/crafting/CraftingWorker.kt) removes/adds items separately and compensates on failure. It is not one atomic inventory transaction; do not assume it already satisfies the conversion contract below.

## Variant creation contract

Visual direction: [hooded helmet brief](completion-rewards.md#hooded-slayer-helmets). The new unlock screenshot gives these recipe references:

| Reference unlock | Pictured cost | Pictured combination |
|---|---:|---|
| Eye see you | 500 Slayer points | Araxyte head + Slayer helmet → Araxxor-themed helmet. |
| Absolutely Slayin' | 500 Slayer points | Slayer skillcape hood + Slayer helmet → hooded helmet. The reference explicitly excludes existing cosmetic helmet variants. |

These are **reference prices and recipes**, not confirmed native cache rows. The requested hooded variants need their own explicit mappings; the base hood unlock cannot automatically accept every coloured helmet.

- Define each supported variant's input item IDs, component, output ID, unlock, point cost, Crafting/equip requirements and reversion result. Verify the models and obtainable ingredients before enabling it.
- Charge a permanent unlock once; consume the defined components for each craft. Unlocking or previewing a style must not grant the finished item or enable free switching without its recipe.
- Define normal and imbued mappings separately. Preserve the input's imbue source/state, item vars, Slayer bonuses and protection parameters; never create a free imbue or downgrade an existing one. Validate through the active [combat attribute collectors](../../api/combat/combat-formulas/src/main/kotlin/org/rsmod/api/combat/formulas/attributes/collector).
- Convert or revert with one `invTransaction`. Recheck unlock, levels, current item/slot and ingredients when committing. Insufficient space, stale selection or repeated clicks must leave inputs, points and output consistent.
- Define which components reversion returns or consumes and how imbues/death/reclaim behave. Reject unsupported combinations explicitly. Do not copy the screenshot's cosmetic-variant exclusion over the owner's requested variants without defining their separate routes.

## Acceptance

No Slayer-menu or helmet-conversion tests were found in the reviewed modules; generic crafting tests do not exercise these helmet recipes. This documentation update runs no server build or gameplay test.

Before completion, verify the five tabs in fixed/resizable mode; navigation without changing an active assignment; cancel/block/unblock and Store/retrieve; exact cache rows/prices and task effects before/after relog; extension toggles and bulk purchases; zero/partial/full inventory purchases; and each normal/imbued craft → revert → relog/death path. Check both player body types, clipping and retained combat/protection behavior.
