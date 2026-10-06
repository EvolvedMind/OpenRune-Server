# Startkaart voor EvolvedMind/OpenRune-Server

Persoonlijke modulaire OpenRune/RSMod/Alter-fork; behoud custom gameplay en de geaccepteerde runtime.

## Sessiegrenzen en snel starten

- Eén taak per sessie; maak bij grotere taken eerst een kort plan. Houd antwoorden kort.
- Begin met `git status --short` en `git branch --show-current`; behoud bestaand werk.
- Lees daarna alleen relevante bestanden/fragmenten. Verken de repo niet opnieuw en lees lange workflowdocs alleen als de taak ze vereist.
- Status: `PROGRESS.md`. Roadmap/ideeën: `tools/progress/roadmap.json`. Details per systeem: `docs/custom/`.
- `CONTENT_INVENTORY.md` is gegenereerd en groot (~64 KB): nooit volledig lezen of handmatig bewerken; alleen gericht `rg`/grep gebruiken.
- Runtime: revisie **240**. Geen protocol-, cache- of revisie-upgrades; behoud bestaande revisieconfiguratie en de gekoppelde client.
- Wijzig geen bestaande modules buiten de opdracht. Signaleer noodzakelijke scope-uitbreiding eerst; herstel geen geparkeerde features automatisch.
- Behoud geaccepteerde checkpoints. Geen automatische upstream-merge, oude branchimport, installatie of vervanging van live JAR/cache/playerdata.
- Vergelijk bij upstream-overlap de bestaande en nieuwe implementatie; voorkom twee actieve handlers voor dezelfde feature.

## Architectuurkaart

- `server/app`: bootstrap en game-loop; `server/shared`: plugin discovery; `server/services`: achtergrondservices.
- `engine/`: entities, maps/routing, coroutines, eventbus, inventorytransacties en pluginbasis.
- `api/`: gedeelde gameplay-API's: combat/bosses, players/NPCs, instances, death/loot, inventories, skills en scripts.
- `api/net`: RSProt/Netty-protocol en login; `api/account`, `api/db`, `api/db-gateway`: accountopslag/PostgreSQL en databasewerk.
- `or-cache/`: cachebuilder, gamevals, definities, tabellen, interfaces/CS2 en generated code; `api/generated/src` ontstaat tijdens cachegeneratie.
- `tools/osrs-mcp`: wiki/cache/gameval-onderzoek; `tools/wiki-dumping`: data-import; `tools/progress`: inventarisgenerator.
- Nero Studio/client/bridge heeft een afzonderlijke repository; servercode alleen bewijst geen clientcompatibiliteit.
- `content/`: `areas`, `bosses`, `drops`, `events`, `generic`, `interfaces`, `other`, `quest`, `skills`, `travel`.
- Elke module heeft `build.gradle.kts`; `settings.gradle.kts` ontdekt modules automatisch. Declareer moduledependencies expliciet.
- `pack/` is een afzonderlijke cache/data-module; Gradle noemt deze automatisch `<parent>-pack`. Houd gamecode buiten packs om bootstrap-cirkels te voorkomen.
- `PluginScript`-subklassen worden automatisch geladen; registreer handlers in `startup()` en injecteer services. Geen centrale lijst handmatig aanvullen.

## DSL's en API's

De script-extensies hieronder staan in `api/script/src/main/kotlin/org/rsmod/api/script/`.

- Bosses: `boss {}`, phases, abilities en effects in `api/bosses/src/main/kotlin/org/rsmod/api/bosses/dsl/` (`BossDsl.kt`, `EffectBuilders.kt`).
  `api/bosses/src/main/kotlin/org/rsmod/api/bosses/runtime/` bevat `BossPluginScript`, `BossCombat`, `BossEncounter`; gebruik native combat/death/instance-hooks.
- NPC/loc/item: `NpcScriptEventExtensions.kt`, `LocScriptEventExtensions.kt`, `HeldScriptEventExtensions.kt`: `onOpNpc1..5`, `onOpLoc1..5`, `onOpHeld1..5`, `onOpHeldU(first, second)`, `onOpLocU`.
  Ground items en interfaces: `ObjScriptEventExtensions.kt` en `InterfaceScriptEventExtensions.kt`.
- Dialogen: `api/player/src/main/kotlin/org/rsmod/api/player/dialogue/Dialogue.kt`; `startDialogue`, keuzes en wachtacties via `api/player/src/main/kotlin/org/rsmod/api/player/protect/ProtectedAccess.kt`.
- Timers/queues: `PlayerScriptEventExtensions.kt`, `NpcScriptEventExtensions.kt`, `EngineQueueScriptEventExtensions.kt`; plannen via `ProtectedAccess` of `api/npc/src/main/kotlin/org/rsmod/api/npc/access/StandardNpcAccess.kt`.
  De game-loop gebruikt ticks van 600 ms; `delay(1)` is één tick. Gebruik gameplayqueues/timers voor tickwerk.
- Events: `ScriptEventExtensions.kt` biedt `onEvent`/`onProtectedEvent`; dispatch staat in `engine/events/src/main/kotlin/org/rsmod/events/EventBus.kt`. Houd featurevoorwaarden in de consument.
- Drops: `api/drop-table/src/main/kotlin/dtx/rs/RSDropTable.kt` en `api/drop-table-plugin/src/main/kotlin/org/rsmod/api/droptable/` (`DropWeightedTableScope`, `DropChanceTableScope`, `DropGuaranteedTableScope`).
  Kotlin gebruikt `@RegisterDropTable`; TOML staat in `content/drops/src/main/resources/drops/tables/`. Details: `docs/drops.md`; voorkom dubbele NPC-/objectregistratie.
- Inventories: `api/invtx/` biedt `invTransaction`; instances: `api/instances/`; death/kill hooks: `api/death/`; Collection Log: `content/interfaces/collection-log/`.

## Kleine referenties: lees alleen het passende voorbeeld

- Boss DSL: `content/bosses/kbd/src/main/kotlin/org/rsmod/content/bosses/kbd/KingBlackDragon.kt`: compacte declaratieve attacks/phases zonder eigen encountercontroller.
- Complexere bosses: `content/bosses/kraken` voor pools/eigendom en tests; `content/bosses/corporeal-beast` voor een extra actor; `content/bosses/araxxor` voor private lifecycle/cleanup. Geen standaardtemplate voor een eenvoudige boss.
- Activiteit: `content/events/shooting-stars`: scripts, manager, instellingen en data gescheiden; dit is een wereldactiviteit, geen complete minigame-template.
- Skill met data: `content/skills/fishing`: `scripts/Fishing.kt` onder de Kotlin-package en `pack/src/main/kotlin/org/rsmod/content/skills/fishing/pack/FishingTable.kt` scheiden gedrag en data.
- Interface: `content/other/pets/src/main/kotlin/org/rsmod/content/other/pets/PetMenu.kt` plus `content/other/pets/pack/src/main/resources/pack/interfaces/pet_menu.if3`: named components, navigatie en close/logout-cleanup.
- Drop table: `content/bosses/barrows/src/main/kotlin/org/rsmod/content/bosses/barrows/BarrowsChestDropTable.kt`: klein, echte rewardberekening/modifiers/clue-transform; zie `BarrowsLootTest`.
- Quest: `content/quest/src/main/kotlin/org/rsmod/content/quest/area/wizardstower/ImpCatcher.kt`: native progress-varbit, journal, atomic hand-in en interactiontests.
- Dit zijn structuurvoorbeelden; aanwezige code is geen bewijs dat iedere mechanic volledig geverifieerd is.

## Commands vanuit de repo-root

Bronnen: `.github/workflows/ci.yml`, `.github/workflows/release-server.yml`, root/module-builds; Java 21.
Deze commands zijn uit source/CI gecontroleerd, niet lokaal opnieuw uitgevoerd bij het schrijven van dit bestand.
Op Windows vervangt `.\gradlew.bat` hieronder `./gradlew`.

| Doel | Command |
|---|---|
| Alleen schone geïsoleerde checkout zonder cache | `./gradlew :or-cache:freshCache :or-cache:mergePluginGamevals` |
| Bestaande cache, gewijzigde packs/gamevals of ontbrekende generated code | `./gradlew :or-cache:buildCache :or-cache:mergePluginGamevals` |
| Hele project bouwen + aangesloten tests | `./gradlew assemble test` |
| Alle aangesloten tests | `./gradlew test` |
| Eén bestaande module testen | `./gradlew :content:bosses:kraken:test` |
| Runtime-JAR bouwen | `./gradlew :server:app:shadowJar` |
| Ontbrekende RSA-sleutels genereren | `./gradlew generateRsa` |
| Ontwikkelserver starten | `./gradlew run` |

- Genereer cache/gamevals vóór een clean build: `api/generated/src` wordt niet meegecommit. Gebruik uitsluitend de bestaande revisie.
- JAR-output: `server/app/build/libs/server.jar`; `shadowJar` vervangt tests niet. Start vereist passende cache, RSA, `game.yml` en centrale services.
- Maak `game.yml` alleen uit `game.example.yml` als het ontbreekt; behoud bestaande configuratie. Start alleen een geïsoleerde testserver.
- Gebruik `install`/`cleanInstall` niet als automatische reparatie: die kunnen configuratie, cache en sleutels vervangen/verwijderen.
- `test` omvat de aangesloten suites; historische `src/integration`-bestanden zijn momenteel niet aangesloten. Claim daarvoor geen testdekking.

## Definitie van klaar

- Boss: toegang/requirements, volledige afgesproken encounter/attacks/fases, reset/respawn, correcte native drops, Collection Log, kill count en relevante Slayer-/timerintegratie.
- Minigame/activiteit: toegang, deelname, start/voortgang/winst/verlies, individuele/groepsstate, herhalen, rewards en toepasselijke Collection Log/completion count.
- Skill: afgesproken trainbare loop, levels/XP, input/output, success/failure, resources/respawn, unlocks/UI en toepasselijke rewards/progressie.
- Voor alle types: ownership en cleanup bij einde, death, logout en verlaten; geen dubbele beloningen, achterblijvende NPCs/objects/timers of onbedoelde invloed op andere spelers.
- Relevante tests moeten slagen: echte interactie/eventketen, volle inventory/rollback, herhaalde actie, onderbreking en save/load waar state wijzigt; build en geïsoleerde boot passend bij de wijziging.
- **In-game acceptatie doet de gebruiker.** Lever korte concrete teststappen; markeer resterende scope en onbewezen gedrag. Werk `PROGRESS.md` en relevante detaildocs bij.

## Valkuilen en conventies

- Gebruik bestaande gamevalsymbolen (`npc.*`, `loc.*`, `obj.*`, enz.). Vergelijk identiteiten via resolved IDs, niet via display-/internal-name-strings; aliassen kunnen verschillen.
- Nieuwe symbolen horen in `src/main/resources/gamevals.toml` van de contentmodule; controleer ID-conflicten en regenereer mappings/cache. Raad geen IDs of clientscript-signatures.
- Basisstate via varbits/varps; persistentie via permanente native state/inventoryvars. Attrs alleen voor data die daarin niet past; runtime-objectreferenties niet opslaan.
- Verbruik en beloning moeten samen slagen via inventorytransacties. Publiceer XP/completion pas na succesvolle output; bescherm stale itemreferences en bezitbeperkingen.
- Test producer én consument: zelf een verwacht event publiceren bewijst geen echte skillactie. Bewaar onafhankelijke encounter-/puzzelvoortgang afzonderlijk.
- Controleer bestaande verkrijgingsroutes bij nieuwe rewards. `Drops Need Manual` en `condition { true }` zijn geen uitgewerkte voorwaarden; bestaande 1/1-casketdrops verdienen expliciete controle.
- Behoud normale NPC/loc/dig-fallback bij featurehooks; verwijder registraties en eigen NPCs/queues bij cleanup/unload.
- Interfaces gebruiken actuele `.if3`/CS2 en named components; de oude `SpawnInterface.kt`/`buildInterface()`-verwijzingen zijn hier geen werkende template.
- Mutabele game-entities horen op de game-thread; maak snapshots voordat achtergrondwerk ermee werkt. Onderzoek gedeelde databaseverbindingen voordat je parallelle saves toevoegt.
- De inventarisgenerator heeft een valkuil: `--check` schrijft `CONTENT_INVENTORY.md` alsnog en controleert alleen het README-blok; behandel dit niet als read-only validatie.
