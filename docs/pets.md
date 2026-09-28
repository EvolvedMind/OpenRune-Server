# Pets

This module integrates the pet system from OpenRune/OpenRune-Server PR #249
(`e224cb4`) into this fork. It replaces the earlier Nero follower implementation;
the two implementations must not be registered together.

## Catalog

The cache tables are authoritative. The module uses gameval symbols and generated
table rows, rather than matching item and NPC display names.

| Table | Families / stages | Item–NPC forms |
| --- | --- | ---: |
| `PetsTable` — boss | 54 families | 103 |
| `PetsTable` — skilling | 9 families | 70 |
| `PetsTable` — other | 8 families | 9 |
| `CatsTable` | Kitten, grown, overgrown, wily, lazy; seven colours | 35 |
| `DogsTable` | Twelve breeds; three colours; puppy and adult | 72 |
| Total | | 289 |

Each form supports its own inventory item, owned follower and Pick-up action.
The official `follower_npc` varp carries the NPC UID so the client displays the
owner's follower options. `follower_obj` persists the active form. Existing
`active_pet` saves migrate without overwriting a different current follower.

Followers trail the player, respect the complete NPC footprint and collision,
and never use the player's tile as a placement fallback. If no valid tile is
available, an active follower is retained in saved state and retried; an inventory
drop is refunded. Metamorphosis, growth and reclaim preserve their items, state
or credits when placement fails.

## Interactions

- The 71 collectible pet families have their upstream dialogue handlers.
- Metamorphosis uses each family's unlocks; item transformations include ores,
  seeds, logs, raid items and Phoenix firelighters. Chinchompa gold retains its
  chance and confirmation. Rift guardians retain their locking option.
- Cats have feeding, attention, growth, rat chasing, hell forms and the associated
  acquisition/trade NPCs. Dogs have feeding, puppy growth and their interactions.
- Supported pet rewards, insurance, Probita reclaim, emotes and Call follower use
  the shared follower service. Mutation and pickup actions enforce ownership.

Catalog coverage does not imply every OSRS acquisition activity exists in this
server. The upstream skilling reward table currently covers Fishing, Mining and
Woodcutting; NPC pet rolls depend on implemented encounters and drop tables.
Upstream's dog Dig action has no reward behavior. These are explicit remaining
content limits, rather than missing follower registrations.

## Build and verify

Rebuild the cache before Kotlin compilation: this module adds generated DB table
classes and gamevals. Run these as separate Gradle invocations:

```text
./gradlew :or-cache:buildCache :or-cache:mergePluginGamevals
./gradlew :content:other:pets:test
./gradlew assemble test
```

The real-cache tests cover every catalog form's item/follower round trip and cache
menu contract, plus ownership, persistence, migration, collision, blocked
transformation costs and care transitions. CI must also boot the server: successful
compilation alone does not prove every dynamic handler can register.

For an administrator, `::pet` opens the collectible pet menu and
`::pet skillpetwc` awards Beaver through the reward service. For explicit alternate
forms, cats or dogs, use the existing item-spawn tools, then drop that pet item.
Confirm Pick-up, walking behind the player, Call follower and logout/login before
testing that family's special options. Use the owner account for mutation actions.
