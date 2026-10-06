# OpenRune fork progress

Updated **2026-10-06**. This is the human-reviewed progress entry point.
The automatic scanner writes [CONTENT_INVENTORY.md](CONTENT_INVENTORY.md), never this file.
A symbol, pet or drop table does not establish a playable or verified boss.

**Accepted runtime:** revision 240, server `f40f4d951`, Nero Studio `b29d93c`.
User accepted the Doom/HUD test package and approved merge on 2026-10-06.
Earlier recovery checkpoints remain preserved.
[Exact recovery baseline](docs/custom/baseline.md).

| Area | Actual status |
|---|---|
| Current playable build | User-accepted Doom/HUD package; 326 selected server tests + 89 Nero tests and isolated startup pass |
| Pets, commands, max cape, timers | Implemented; covered regression cases verified |
| Monster/pet/skill/quest interfaces | Implemented; remaining visual edge cases listed in the detailed progress |
| All weapon specials | USER-ACCEPTED CHECKPOINT / REMAINDER PAUSED; 191/285 registered; 94 still missing; registration does not certify full mechanics |
| Charged weapons | Tested charge/attack slices for tridents, scythes, blowpipe, Eye of Ayak and Sanguinesti; atomic Shadow/Venator loading/refunds; 83 selected weapon/special/impact tests pass; live effect qualification pending |
| Weapon checkpoint validation | 103 tests including NPC/pet/Zulrah regressions pass; server JAR build and isolated Nero-bridge boot pass; accepted installation not replaced |
| Installable weapon checkpoint | `weapons-update-20261003`, server `880c6a9fa`; 100 payload targets checked against installed guides/cape baseline; installer/rollback tests pass; user installation pending |
| Subsequent melee special effects | Native impact callbacks replace independent timers for drains/healing/freeze/run-energy effects; 42 special tests pass; not included in the frozen installer above |
| Further melee families | Dragon claws, Dragon scimitar, Darklight/Arclight/Emberlight, Dragon sword and Ancient mace: 17 additional item variants; 58 special tests and 122 selected tests total pass; full server build passes; client animation verification pending |
| Latest special validation | 99 special tests + 12 command/interface tests pass; build and isolated boot pass; user gave merge approval on 2026-10-04 |
| Zulrah | Active custom encounter; old alternative recovery code is review material, not installed |
| Araxxor | USER ACCEPTED; PR #16 merged, 186 encounter/shared tests plus 2 native Rancour recipe tests pass |
| Barrows | IMPLEMENTED / USER ACCEPTED; native chest test command `::testloot barrows [count]` |
| Treasure Trails | PARKED AT ACCEPTED TEST CHECKPOINT; 40/60 skill-task rows handled. Boxes, trail state, assignment and puzzle/reward foundations implemented; 20 skill tasks, other documented routes and live acceptance remain. Mimic last, not implemented. [Coverage](docs/custom/clue-task-coverage.md) / [test commands](docs/custom/clue-testing.md). |
| Doom | Full upstream encounter/delves integrated; ::testdoom and ::testloot doom; 326 server tests + isolated boot pass; USER ACCEPTED / MERGE APPROVED |
| NPC combat stat HUD | Real buffs/drains in native infoboxes; 89 Nero tests pass; USER ACCEPTED / MERGE APPROVED |
| Revision 241 | Upstream review pending; no automatic upgrade |
| Repository organization | Complete: 15 stale branches archived and removed; main and active integration branch retained |


**[Detailed status, origins, NOW / NEXT / LATER / BACKLOG](CUSTOM_PROGRESS.md)**

[Custom inventory](CUSTOM_CONTENT.md) ? [Operating manual](OpenRune_Fork_Development_Workflow.md)
? [Branch audit](docs/custom/branch-audit-20261003.md) ? [Custom documentation](docs/custom/README.md)

Future work: one focused topic per commit, relevant tests, update status and dependency
notes, then merge only a verified state. Compare overlapping upstream features before adoption.

Demonbane follow-up: parameter-only cache overlays add 96 verified demon flags and
two Duke resistance flags; all 16,577 NPC definitions retain other fields. Claws
reductions now apply per split hit. 122 selected tests, the full server JAR and isolated Nero-bridge boot pass;
these changes are outside the frozen `880c6a9fa` installer.

Current acceptance milestone: `araxxor-completion-20261004` / `f7c349c0e`, merged in PR #16 and preserved by tag `server-araxxor-complete-20261004`. Barrows is also user accepted. Prior pending notes describe older checkpoints; remaining specials are parked.


## TO-DO — Economy prices, Examine & Collection Log notifications

### OSRS market prices
- [ ] Replace the current `DefaultMarketPrices` runtime source with an OSRS market-price provider while keeping `DefaultMarketPrices` as fallback.
- [ ] Use real OSRS item IDs as the lookup key.
- [ ] Fetch and cache OSRS GE prices server-side; never perform an external request per player examine or price-check action.
- [ ] Refresh prices periodically and keep the last known good cache if the external source is temporarily unavailable.
- [ ] Prefer a stable short-window market value (for example a recent average/midpoint) rather than blindly treating a single latest trade as guide price.
- [ ] Fall back to `uncert(type).cost` when no external GE price exists.
- [ ] Keep High Alchemy and Low Alchemy values independent; do not replace `ItemServerType.highAlch` or `ItemServerType.lowAlch`.
- [ ] Add parsing, noted/unnoted lookup, fallback and unavailable-source tests.
- [ ] Keep the `MarketPrices` abstraction so the OSRS provider can later be replaced by this server's own Grand Exchange price source without changing consumers.

### Item Examine
- [ ] Centralize item examine output instead of duplicating value formatting across inventory, bank, shop, price-checker and ground-item paths.
- [ ] Right-click **Examine** should preserve the normal item description.
- [ ] Add a second value line containing:
  - GE value
  - High Alch value
  - Low Alch value
- [ ] Use the central `MarketPrices` provider for GE value and the existing item definitions for HA/LA.
- [ ] Format large coin values consistently.
- [ ] Ensure noted items resolve to the underlying unnoted item's market/alchemy values where appropriate.

Target output:

```text
A weapon from the abyss.
GE: 1,482,000 gp | HA: 72,000 gp | LA: 48,000 gp
```

### Collection Log reward broadcasts
- [ ] Treat Collection Log membership as the source of truth for important/unique reward notifications.
- [ ] Do **not** maintain a separate hard-coded list of boss uniques or pets.
- [ ] Every obtained item that is accepted by `CollectionLog.grant(...)` should be eligible for a server-wide reward broadcast.
- [ ] Broadcast on every qualifying obtain, not only the player's first Collection Log unlock.
- [ ] Preserve the existing personal `New item added to your collection log` message only for first-time unlocks.
- [ ] Preserve the player's existing Collection Log popup/chat settings for personal unlock notifications.
- [ ] Extend Collection Log reward context so broadcasts can include the source where known, e.g. NPC/boss, minigame, raid, chest or activity.
- [ ] Avoid tying broadcasts to GE value or arbitrary drop-rate thresholds.

Target examples:

```text
[Collection Log] Bram received an Araxyte fang from Araxxor!
[Collection Log] Bram received an Elysian sigil from Corporeal Beast!
[Collection Log] Bram received a Pet kraken from Kraken!
[Collection Log] Bram received a Dharok's greataxe from Barrows!
```

### Notification queue
- [ ] Add a reusable per-player FIFO notification queue.
- [ ] Replace direct consecutive Collection Log calls to clientscript `3343` with queued notifications.
- [ ] Ensure multiple new Collection Log items obtained in one reward/kill are displayed one after another instead of later popups being overwritten.
- [ ] Preserve the current rule that multiple copies of the same newly unlocked item generate only one first-unlock popup.
- [ ] Make the queue generic so it can later be reused by Combat Achievements, Achievement Diaries, quests and other game notifications.
- [ ] Clear or safely discard pending notifications when the player logs out or becomes invalid.
- [ ] Add regression coverage for two or more Collection Log unlocks occurring in the same reward cycle.

Expected flow:

```text
reward/drop
  -> CollectionLog.grant(player, item, source)
  -> Collection Log membership check
  -> server-wide reward broadcast
  -> first unlock?
       -> personal chat message
       -> NotificationQueue.enqueue(...)
  -> queued popups display sequentially
```
