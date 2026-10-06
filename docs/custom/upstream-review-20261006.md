# Upstream review - 2026-10-06

Reviewed OpenRune main through `8ec198fc3` against our accepted fork and focused
Doom branch. No blanket upstream merge or protocol/client revision upgrade.

| Change | Decision and reason |
|---|---|
| `fc10877fd` Doom #275 | Adopt encounter, native reward flow, drop gates/counters and necessary hooks; adapt into a dedicated module/pack and preserve fork inventory/instance contracts. |
| `625fe96ab` Boss DSL #268; `3c11747d2` #269 | Adopt required timers, hit conditions, bound tiles, multiple specs and lifecycle safety. Retain old stats/selector arguments and EachTile name so existing fork bosses remain compatible. Retain custom dragonfire absorption. |
| `6a194f665` Arceuus spellbook #272 | Defer full spellbook import; only the demonbane classifier needed by Doom is included. No replacement of accepted spells/specials. |
| `76a782234` saver/database shutdown #281 | Useful follow-up. Reconcile separately with embedded PostgreSQL, accepted shutdown ordering and Nero lifecycle; do not mix persistence changes into this boss release. |
| `4c8a516d3` unsigned shop-stock codec #276 | Useful bounded follow-up for large shop stock. Not required for Doom; retain as a focused codec fix with boundary tests. |
| `c072f701e` roll gates, XP and bones #280 | Review separately against custom clue skilling completion events and charge transactions; no wholesale import. |
| `d91c4216a` Slayer Trade #277 | Useful small content follow-up; unrelated to Doom encounter entry and deferred. |
| `b5def806c` packaged gamevals #283 | Useful packaging follow-up. Our installer/smoke currently supplies content/API mappings explicitly; reconcile with existing plugin merger without removing fork mappings. |
| `8ec198fc3` test harness #282, OpenWiki/docs/workflows | Defer broad tooling migration; current JUnit and isolated runtime validation stay in use. |

Fork extensions: safe ::testloot/::testdoom commands, atomic persistent reward
storage and 40-slot native scrolling rewards, administrator-only delve selection,
instance access guard and late holy-water cancellation. Core edits are documented
in core-modifications.md; follow-up items above are reviewed, not implemented.

The isolated Doom boot reproduces the existing concurrent shutdown warning
`Game database service failed to shut down` after PostgreSQL stops. Startup,
endpoint refresh, zero process exit and isolated database cleanup pass; this is
not a certification of save/drain ordering. The #281 persistence follow-up remains
separate from Doom and must address the close/saver ordering before being called
clean shutdown validation.
