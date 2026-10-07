# Collection Log, Examine, notifications and OSRS prices

Current status: [PROGRESS.md](../../PROGRESS.md).
Approved source, paired package and installation evidence: [baseline](baseline.md).

## Collection Log and chat news

`CollectionLog` remains the central grant API. Cache enums determine Collection Log
membership and `collection_log_category_all_pets` determines pets; there is no separate
unique-item or pet whitelist.

- Every qualifying obtain, including repeats, sends ordinary game-message type 0
  to online players. Use native grey chat bubble frame 19, a red `News:` label,
  green quantity/item and the known reward source. Do not use world-banner type 14.
- Pets always qualify. Other log rewards require a fresh external GE estimate of
  **at least 1,000,000 gp per item**. Notes normalize to the underlying item.
  Stack totals, stale/missing quotes and cache/alchemy fallback do not qualify.
- Cheap items still update log counts. Personal unlock messages, latest-item history
  and popups remain first-unlock only and respect existing player settings.
  Counts saturate safely.
- Native drops, Barrows, Doom's committed reward stash, key chests and clue caskets
  pass source context. Standalone/skilling pets also use the grant API.
  Pet-table interception suppresses duplicate grants; reclaims do not announce obtains.
- `::testloot doom` and its aliases log samples only after ground registration succeeds.
  Rejected spawns never grant log entries and pickup does not grant them a second time.
  Repeats count. `::doomsim` remains unlogged; both routes preserve active run state,
  kill counters and both persistent loot piles. See [Doom](doom-of-mokhaiotl.md).

## Item Examine

All inventory, bank, ground, worn, shop and price-checker consumers share two messages:

1. Native blue info icon, item name/stack quantity and the original description.
2. GE in green, High Alch in blue and Low Alch in red.

Values are exact stack totals, notes use underlying values and non-alchable items show
N/A. Ordinary game messages suppress the duplicate RuneLite price output.
The icon is frame 15 of the unchanged revision-240 `mod_icons` group.
Long arithmetic preserves large totals and prices above `Int.MAX_VALUE`.

## Notification queue

`ClientScripts.notificationDisplay` enqueues script 3343 notifications. A late-cycle
consumer displays one entry every fourteen game ticks; script 3348 terminates the old
timer. Existing colours/interfaces and first-unlock deduplication remain intact.
The FIFO is a temporary player attribute, never shared or persisted. Invalid sessions,
logout and shutdown discard pending entries. The generic queue can serve other
notification producers without changing its lifecycle.

## Price provider

The existing `MarketPrices` abstraction binds to an OSRS provider. One daemon fetches
the whole Wiki `/api/v2/osrs/latest` snapshot at startup and every five minutes.
Startup/gameplay do not wait for HTTP; individual lookups never perform requests.

- Estimate: integer midpoint of available high/low quotes, or the sole usable side.
  Ignore trades older than thirty days and malformed/null/nonpositive values.
- Failed refreshes retain the last good snapshot. After a day without refresh,
  ordinary price lookups fall back to `DefaultMarketPrices`; unknown/untradeable
  variants use cache cost. Coins remain one gp; HA/LA remain cache-defined.
- `MarketPrices.gePrice` exposes only fresh external estimates for the news filter.
  It never substitutes fallback values. `price` returns long values for Examine;
  the compatible int `get` API caps safely at `Int.MAX_VALUE`.
- Keep the provider replaceable by a future server-owned GE. This change does not
  implement that exchange or change shop trading rules.

## Research and reuse

- Upstream main `779b81b60` and all-state PR searches were checked on 2026-10-06.
  [Collection Log #148](https://github.com/OpenRune/OpenRune-Server/pull/148) and
  [fixes #167](https://github.com/OpenRune/OpenRune-Server/pull/167) already supplied
  inventory/enums/settings and gameframe reset; those integrations were retained.
  #167 head: `4daf38957891475e60dd7cbca6a195ef188da57e`.
- [Unmerged #216](https://github.com/OpenRune/OpenRune-Server/pull/216), head
  `375581c32314973d7de6a04e5e7f3713ef429f22`, uses static `ge-items.tsv` and a
  local exchange book. It was inspected but not imported for the live price provider.
  Legacy Alter/Neversink NPC-examine #34 was not a compatible implementation.
- The provider contract was checked against the official
  [Wiki API documentation](https://oldschool.runescape.wiki/w/RuneScape:Real-time_Prices)
  and a live 4,539-record response. Requests use a descriptive project/contact User-Agent,
  connection/read timeouts and a bounded response body.
  Notification scripts 3343/3346/3347/3348 were inspected in the paired revision-240 cache.

## Recorded validation — 2026-10-06

The final candidate records **134 passing tests**: clues 61, market 6, Collection Log 8,
Doom 35, drops 4, pets 15 and player output 5. Relevant cases include the 999,999/1,000,000
boundary, cheap large stacks, full long prices, all cached log pets, notes, missing/stale
quotes, expensive fallback, failed ground spawns and real NPC/Doom/pet reward producers.
The earlier 150-test extension baseline also covered notification tick/logout/command
lifecycle, ordinary commands and Barrows.

The runtime build and isolated `gameplay-smoke-abca7326` startup passed, including the
game listener, paired Nero endpoints and live price refresh. The approved integration
preserves that tested tree. Advisory formatting violations remain open.
Process exit/database cleanup passed; the existing database-close ordering warning
does not certify a safe save/drain order. Exact commit, JAR hash, package and rollback
are recorded once in [baseline](baseline.md).

## Regression checklist

1. `::testnotify`: three popups appear in order; logout discards the remainder.
2. Examine ordinary/noted stacks in inventory, bank, ground, shop and price checker:
   exactly two messages, correct colours and totals, no duplicate client price row.
3. Obtain a qualifying item twice: both obtains send news with source context; only
   the first gives a personal unlock popup. Check another client's chat.
4. Obtain multiple new log items in one reward: FIFO popups must not overwrite.
   Repeated `::testloot doom 100 8` increases log counts without announcing cheap
   tears/waystones; `::doomsim` leaves counts unchanged.
5. Verify pet news without a quote and >=1m unit-price news, plus offline/failed-refresh
   price fallback. Cache/alchemy fallback must never trigger non-pet news.
