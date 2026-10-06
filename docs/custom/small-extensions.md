# Small extensions: Collection Log, Examine, popups and OSRS prices

Branch: `feature/small-extensions`, based on accepted main `6856cffae`.
Code chunks: prices `4ef96445a`, notifications `2f9bbdf35`, Examine `eef9dc44d`,
reward broadcasts `e62742e50`, price-checker syntax repair `d91be61d2`.
Revision remains 240. Status: implemented candidate, awaiting in-game acceptance.
The accepted Doom/HUD checkpoint and parked clues/specials remain preserved.

## Behaviour

- `CollectionLog` remains the central grant API, now injected with the native player list.
  Cache Collection Log enums determine membership. Each qualifying reward broadcasts to
  everyone, including repeats. Counts saturate safely; personal new-item chat, latest-item
  history and popups remain first-unlock only and respect the existing personal setting.
- Native NPC drops, Barrows, Doom's committed reward stash, existing key chests and clue
  caskets pass their source. Standalone/skilling pets now also use the grant API. Pet table
  interception suppresses duplicate grants; reclaims do not announce new obtains.
  On user request, `::testloot doom` and its aliases now log successfully spawned samples.
  `::doomsim` remains an unlogged simulation; active Doom run, escrow and kill counts stay intact.
- All existing inventory, bank, ground, worn, shop and price-checker Examine consumers use
  the same two compact server messages: native blue info icon, item name and original
  description; then GE in green, High Alch in blue and Low Alch in red. Stack quantities
  appear next to the name, values are stack totals, notes use underlying values, and
  non-alchable items show N/A. Ordinary game messages prevent RuneLite Examine handlers
  from appending a duplicate price row. Large totals use exact arithmetic. The info icon
  is verified frame 15 of our unchanged revision-240 `mod_icons` sprite group.
- `ClientScripts.notificationDisplay` enqueues rather than immediately overwriting script
  3343. A native late-cycle consumer displays one FIFO entry every fourteen game ticks,
  with the existing notification colour and interface. Script 3348 terminates the old timer.
  State belongs to the player as a temporary attribute, is discarded for invalid sessions,
  and clears on logout/shutdown. It is not persisted or shared between players.
- The existing `MarketPrices` abstraction binds to an OSRS provider. One daemon service
  fetches the whole Wiki `/api/v2/osrs/latest` snapshot on startup and every five minutes,
  without blocking startup or the game loop. Lookups never do HTTP. The estimate is the
  integer midpoint of available high/low quotes, or the sole usable side. Trades older than
  thirty days and malformed/null/nonpositive values are ignored. Failed refreshes keep the
  last good snapshot; after a day without refresh it falls back to `DefaultMarketPrices`.
  Unknown/untradeable variants use cache cost; coins stay one gp. HA/LA stay cache-defined.
- `MarketPrices.price` exposes full long values for Examine while keeping the existing
  int `get` API compatible. Native int-only consumers safely cap at `Int.MAX_VALUE`;
  Examine preserves prices above that limit. This provider estimates the OSRS market;
  it does not create a local GE or change shop buy/sell rules.

## Research and reuse

Checked upstream main `779b81b60` and all-state PR searches for collection, broadcast,
notification, popup, examine, market and prices. [Collection Log #148](https://github.com/OpenRune/OpenRune-Server/pull/148)
and [fixes #167](https://github.com/OpenRune/OpenRune-Server/pull/167) are already in the
accepted implementation; retain their inventory/enums/settings and gameframe reset.
[Content drop #216](https://github.com/OpenRune/OpenRune-Server/pull/216), closed unmerged
head `375581c32314973d7de6a04e5e7f3713ef429f22`, was also inspected: its GE reads static
`ge-items.tsv` guide prices and implements a local exchange book. That is not the requested
live replaceable MarketPrices provider, so none of its exchange implementation was imported.
#167's head is `4daf38957891475e60dd7cbca6a195ef188da57e`; no submitted reviews.
#148's discussion confirms inventory-backed counts and calls out the original missing
popups/latest items, already repaired in our baseline. #34 is legacy Alter/Neversink
NPC-examine work, not a compatible solution to these four requirements. No branch imported.

Provider contract checked against the official [Wiki API documentation](https://oldschool.runescape.wiki/w/RuneScape:Real-time_Prices)
and a live v2 bulk response on 2026-10-06 (4,539 records). Requests use a descriptive
project/contact User-Agent, connection/read timeouts and a bounded response body.
Rev-240 notification scripts 3343/3346/3347/3348 were inspected in our coupled cache.

## Acceptance checks

1. `::testnotify`: three sample popups in order. This administrator command changes no
   account progression. Log out while waiting; remaining samples must disappear.
2. Examine an Abyssal whip and a noted stack in inventory/bank and on the ground; also
   Examine shop stock and a price-checker entry. Exactly two messages should show the info
   icon/name/description and the green/blue/red GE/HA/LA values, without an extra client price row.
3. Obtain a real Collection Log drop twice. Both obtains broadcast their NPC name, while
   only the first unlock sends personal new-item chat/popup. Two clients can verify scope.
4. Open a real multi-unique reward or several fresh caskets; new popups must play in order.
   An already unlocked item sends no new popup. `::testloot doom 1000 8` now registers
   successfully spawned Collection Log rewards; repeating it increases existing counts.
   `::doomsim` still leaves the log unchanged.
5. Check prices after startup and again after a refresh. Offline startup remains playable
   with cache values. In-game timing/visual acceptance is the user's final check.

150 selected tests pass: market 5, player output 5, Collection Log 5, notification
lifecycle/command 2, pets 15, native drops 4, Doom 32, commands 19, Barrows 8,
and parked clue regression 55. New coverage includes real NPC roll consumption,
Doom commit/rollback, pet delivery/reclaim deduplication, popup command/tick/logout,
first/repeat unlocks, personal settings, saturated counts, noted/excessive stack totals,
invalid/stale/one-sided price data and failed refreshes. The test cache is locked across
fixtures; test modules declare their storage and RSProt dependencies explicitly.

`:server:app:shadowJar` passes. The isolated candidate `gameplay-smoke-96eca8ca`
initialized the game listener, authenticated Nero asset/respawn/NPC-effect endpoints and
refreshed the OSRS price cache through the actual Java provider. Process exit is zero;
the isolated database PID is gone. JAR SHA-256:
`adaab5be6bc6f1c515061f01c2ecfb3f31aea09d3ec2d2f39cd53aef32f6abed`.
Accepted Nero artifact hashes and runtime cache mappings remain paired in the smoke report.
The previously recorded database-close ordering warning also appears in this shutdown;
it remains a separate persistence follow-up, not evidence of a clean save/drain shutdown.

Test package: `outputs/small-extensions-20261006/INSTALLEREN.cmd`, with its own software
checkpoint and `CONTROLEREN.cmd` / `TERUGZETTEN.cmd`. The baseline hash check covered
781 accepted Doom/HUD files. No live installation or merge performed. Final installer
preflight and the packaged hashes are checked before handing off; in-game acceptance remains.

## User-requested fixes (2026-10-06)

The installed four-extension baseline matched all 821 prerequisite/target hashes before
preparing this update. Doom test samples now call the central Collection Log service only
after the Boolean ground-registration API succeeds; rejected spawns never create obtains.
All four actual Doom uniques are tested against real revision-240 Collection Log enums,
including repeat counts, source broadcasts and preservation of both persistent loot piles.
The native pickup handler performs no extra Collection Log grant. No encounter changes.

Fix commits: Examine `f53d4e760`, Doom testloot `84803569e`.

63 targeted tests pass: player output 5, Collection Log 5, Doom 34 and commands 19
(no failures/errors/skips). The earlier 150-test extension baseline is recorded above.
The full runtime JAR build passes. Fresh isolated startup `gameplay-smoke-ba06211e` verifies
all native plugin construction, the game listener, paired Nero endpoints and live price refresh.
Exit code is zero and the temporary database PID is gone. The previously documented
database-close ordering warning remains a separate follow-up. New JAR SHA-256: `92756d50b9fbf0194c462639080070020901420fd01868a7328925320ddccb54`.

Updated test package: `outputs/small-extensions-fixes-20261006/INSTALLEREN.cmd`,
with checkpoint `outputs/checkpoint-20261006-voor-small-extensions-fixes`.
Read-only installer preflight and artifact hashes are checked before handing off.
PR #24 remains a draft; in-game acceptance and merge are pending.
