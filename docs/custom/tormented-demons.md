# Tormented Demons: native appearance repair and first combat slice

## Sources and reuse

The fork already contains the BossDSL encounter and synapse/claw recipes from
[OpenRune PR #213](https://github.com/OpenRune/OpenRune-Server/pull/213), merged as
`c0690026c32925fcd87e0cee1b752b5018cae02a` (reviewed head
`84084ea01fa198a393175cc44e3690aa4552bc87`). The upstream search found no other
dedicated Tormented Demon PR. The current upstream encounter file was reviewed at
blob `7b4ac6893dc32866569e123e553ca948e59c8947`; its varn-based phase tracking does
not provide a complete replacement for this fork's encounter lifecycle. Retain the
native DSL and repair the specific missing behavior; no wholesale branch import.

Mechanic reference: [Tormented Demon](https://oldschool.runescape.wiki/w/Tormented_Demon).
The two supplied Nero captures are observations from Alora, not instructions or
proof of the OSRS rules. No foreign cache assets were imported.

| Capture | SHA-256 | Evidence used |
|---|---|---|
| `capture-tds-20261007-230037.npack` | `36b7a89a572d75cfd631d72258cc2c839d554c7d6d9a62d5cdc48c8bcc59838a` | Two NPC placements at (4075,4427) and (4080,4427), IDs 13599/13600. |
| `recording-20261007-230331.npack` | `79eecc4964e8e587a32a21dbbebb080260597a8defd0bfd838bc595dd2fa2597` | Native attack/death sequences, six-tick normal attacks and fire-bomb observations. |

## Model repair

Upstream disables defenceless model changes because of a reported revision-240
client/rsprox `decodeBodyCustomisationV3` crash. Removing that guard alone would
leave the reported route in use. This slice instead packs two native NPC appearance
variants and uses the existing transmog update to display them. Each client
definition copies its original demon's options, size, animations, colours and other
fields, changing only the model and definition ID. Models 55475/55474 are present
in the **client** cache; the stripped server cache intentionally omits models.

The actor remains the same entity, with its original type, UID, HP, stats and damage
contributors. Native Modify/Impact handlers cover both appearances. The normal
appearance returns after a bomb, reset, death and respawn. Tormented Demons do not
send a body-customisation update. The shared protocol implementation and revision
240 remain unchanged; this is a repair of the demon's appearance route, not a claim
that every use of RSProt body customisation has been fixed.

Packet tests use the pinned `1.0.0-ALPHA-20260912` encoder and independently read
revision-240 wire fields. They cover a body block, a combined graphic/body/sequence
update and the native transformation/reset block. Their field order follows the
[revision-240 RSProx decoder](https://github.com/blurite/rsprox/blob/d5493fb7714d057e7c97be4eda3d10b6078bb87c/protocol/osrs-240/src/main/kotlin/net/rsprox/protocol/v240/game/outgoing/model/info/npcinfo/NpcInfoClient.kt).
These tests do not reproduce the historical full client session crash or certify
rendered animation quality. The in-game phase transition remains an acceptance check.

## Combat and test access

- Passive demons; normal attack rate 6, normal max hit 31, respawn 21 ticks and no
  fabricated elemental weakness.
- Prayer switches after 150 actual HP loss, through the native queued-hit modifier
  and impact processor, rather than counting rolled/blocked damage.
- The defenceless clock handles fights starting at tick zero and checks active
  actors between attacks. Reset clears drained stats and previous damage ownership.
- Bombs snapshot distinct landing tiles, bind briefly, then queue 40–45 typeless
  damage only if the target remains on a landing tile. Aborted fights cannot apply
  old bomb or player-hit callbacks to a new fight.
- Synapse/claw recipes use atomic inventory transactions; XP follows successful output.
- Two observed Temple placements are packed as native world spawns. This is a test
  slice, not a claim that the complete Temple spawn layout has been reconstructed.
- Administrator `::testtd` teleports to (4072,4422), near those actors.
  `::testloot td 100` uses the native kill/reward hooks; aliases include `tds` and
  `tormented`. This does not complete or correct the existing loot table by itself.

## Validation and build environment

Focused tests exercise the real queued NPC-hit producer and StandardNpcHitProcessor,
blocked damage, prayer timing, appearance changes, lethal hits, departure/logout,
pending-hit cancellation and plugin shutdown. Native cache tests verify that both
appearance definitions differ only in ID/model and preserve the original animation
and option data. Bomb tests check hit production, dodging and cancellation.

Validation: 137 selected tests pass, including 13 Tormented Demon tests and the
accepted Araxxor/Kraken/Corporeal Beast/Zulrah suites. Full server JAR, full cache,
five scoped formatting checks and isolated revision-240/Nero startup pass. The
isolated process exits 0 and leaves no private PostgreSQL PID or cleanup errors.
The test installer has its own rollback to the verified installed 60-task clue
baseline. It does not install automatically or change the paired client software.

The default global CS2 build directory produced compilation errors in generated
native sources. The complete cache build succeeds with a complete isolated copy of
the previously used revision-240 build environment at `work/td-build-appdata`:

```powershell
$env:LOCALAPPDATA = '<workspace>/work/td-build-appdata'
.\gradlew.bat :or-cache:buildCache :or-cache:mergePluginGamevals
```

No failing native scripts were deleted or skipped. A build started while that copy
was incomplete failed; the subsequent complete-copy build compiled all native and
plugin scripts. No revision upgrade or live installation was performed.

## Remaining full encounter work

Current status is maintained only in [PROGRESS.md](../../PROGRESS.md). Full Temple
entry/escape and progression gates, the complete spawn/area layout, coordinated
two/three-demon combat, ordered drop-table odds and guaranteed ashes, smouldering
consumables, full synapse crafting requirements and final rendered gameplay remain
outside this first repair slice. Do not mark Tormented Demons 100% complete from the
module's existence or these component tests.
