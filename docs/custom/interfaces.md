# Native interfaces

Origin: monster search/drop previews/stats are custom over upstream data;
skill/quest guide styling extends existing upstream interfaces. Current status:
[PROGRESS.md](../../PROGRESS.md).

Commands, monster drops, pets, skill guides and quest guides share the native steel
frame / orange labels / shadowed text design of Spawn and Collection Log.

- Commands: `content/other/commands` and its pack module.
- Monster guide: `content/interfaces/monster-info`, full-size NPC and reverse item
  search, inventory models, base drop probabilities, stats and registered locations.
- Pet gallery: `content/other/pets`, native item models and existing delivery service.
- Skills: `content/interfaces/skill-guides`, native `skill_guide_v2`, four initializer arguments.
- Quests: `content/quest`, steel frame over native requirements and scrollable real quest logs.

Commits: `921b43b95` for searchable guides/gallery and `444ead71b` for skill/quest
styling plus corrected cape submenu indices. [Guide regression notes](../guides-cape-regression.md).

Upstream risk: interface/CS2 signatures, gameval mapping, native model widgets and
packet op/subop semantics. Build the cache with matching server code. Preserve the
zero-based Max cape submenu index. Check fixed/resized layouts, long names, scrolling,
close buttons, search cancellation and rights before claiming visual completion.

## Items-button disconnect fix — 2026-10-09

The supplied screenshot is the Monster Guide (`::drops`), which has NPCs and
Items buttons. `::spawn` is a separate native grid (`interface.spawn_menu`,
1099); its mappings and archives are preserved. A production-cache/real-registry
probe reproduced the Items failure in `MonsterCatalogue.byItem`: an old table
contains `obj.trail_clue_elite_combat001`, absent from the accepted cache, and
the throwing RSCM lookup aborts the entire reverse index. This server exception
occurs before item results can be sent; the existing one-item mock did not cover it.

The catalogue now uses the native null-safe item mapping and checks for an
actual item definition. Unavailable references are omitted from reverse search;
forward rows retain their odds/quantities with an `Unavailable drop` label and
no clickable item icon. The original drop tables, rolls and loot are not changed.
Valid items, notes, source selection, locations and stats keep their existing routes.

The real catalogue now indexes 818 monster tables and 1,425 valid item types.
The regression inserts the exact reported legacy reference, then exercises
native `If3ButtonHandler` packets for Items, Clear, item icon/source selection,
drop paging, unavailable-icon refusal and repeated NPC/Items switching. The
complete Monster Guide module passes 13 tests. No client source/plugin changes
are needed for this server-side failure; confirm the screenshot button in-game
when installing the matching candidate.
