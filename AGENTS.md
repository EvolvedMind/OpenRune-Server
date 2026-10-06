# Startkaart voor EvolvedMind/OpenRune-Server

Persoonlijke modulaire OpenRune/RSMod/Alter-fork; behoud custom gameplay en de geaccepteerde runtime.

## Sessiegrenzen en snel starten

- Eén taak per sessie; bij grotere taken eerst een kort plan. Rond stappen binnen de opdracht zelfstandig af; houd antwoorden kort.
- Begin met `git status --short`, `git branch --show-current` en `git log -1 --oneline`; herken detached HEAD en behoud bestaand werk.
- Bij "continue": controleer de relevante voortgang, branch/commit en lokale diff. Verzin geen ontbrekende voortgang; signaleer tegenstrijdigheden.
- Zoek status gericht in `PROGRESS.md`, zo nodig `CUSTOM_PROGRESS.md` en bijbehorende `docs/custom/`. Roadmap/ideeën: `tools/progress/roadmap.json`.
- Lees alleen taakrelevante fragmenten; gebruik eerst `rg`/`rg --files`. Geen nieuwe reposcan of standaard volledige lezing van lange workflowdocs.
- `CONTENT_INVENTORY.md` is gegenereerd (~64 KB): nooit volledig lezen of handmatig bewerken; alleen gericht `rg`/grep gebruiken.
- Runtime blijft revisie **240**: geen protocol-, cache- of revisie-upgrades. Bestaande cache voor content opnieuw pakken mag; behoud de gekoppelde client.
- Wijzig geen bestaande modules buiten de opdracht; signaleer noodzakelijke scope-uitbreiding. Hervat geparkeerde features alleen op gebruikersopdracht.
- Behoud geaccepteerde checkpoints; geen vervanging van live JAR/cache/playerdata zonder installatieopdracht.
- Delegeer alleen afgebakende onafhankelijke deeltaken met beperkte context; vermijd dubbele verkenning.
- Bij gewijzigde featurestatus: werk de relevante status/notitie bij met branch/commit, wat werkt, exacte tests/resultaten, open punten en eerstvolgende stap.

## Nieuwe content: eerst upstream controleren

- Zoek vóór nieuwe content of een grote uitbreiding gericht in de eigen repo, de [officiële OpenRune-repo](https://github.com/OpenRune/OpenRune-Server) én de [pull requests](https://github.com/OpenRune/OpenRune-Server/pulls), op naam en relevante aliassen.
- Controleer open/draft én gesloten PRs, inclusief niet-gemergede voorstellen. Lees relevante code/diffs en beschikbare reviews/testresultaten; ontbrekend op main betekent niet dat er geen bruikbare implementatie is.
- Vergelijk met onze bestaande modules: mechanics, native integraties (drops, Collection Log, kill count, instances/cleanup), tests en revisie-240-compatibiliteit. Upstream is niet automatisch beter of compleet.
- Kies bewust: eigen implementatie behouden, bruikbare delen aanpassen/hergebruiken, of ontbrekende delen zelf bouwen. Licht de keuze kort toe en behoud bronvermelding/licentie.
- Geen automatische merge of ongecontroleerde PR-/branchimport. Behoud custom gedrag, voorkom dubbele handlers en blijf op revisie 240; neem alleen beoordeelde wijzigingen binnen de opdracht over en test de integratie.
- Leg bronlink/PR, commit en hergebruikbesluit vast in de relevante feature-notitie; gebruik dit bij hervatten en controleer alleen relevante wijzigingen. Bij onbereikbare bronnen: meld de onvoltooide controle; claim niet dat content ontbreekt.

## Architectuurkaart

- `server/app`: bootstrap/game-loop; `server/shared`: plugin discovery; `server/services`: achtergrondservices.
- `engine/`: entities, maps/routing, coroutines, eventbus, transactiekern en pluginbasis.
- `api/`: gameplay-API's voor combat/bosses, players/NPCs, instances, death/loot, inventories, skills en scripts.
- `api/net`: RSProt/Netty-protocol/login; `api/account`, `api/db`, `api/db-gateway`: accounts/PostgreSQL/databasewerk.
- `or-cache/`: cachebuilder, gamevals, definities, tabellen, interfaces/CS2 en codegeneratie; `api/generated/src` wordt gegenereerd.
- `tools/osrs-mcp`: wiki/cache/gameval-onderzoek; `tools/wiki-dumping`: import; `tools/progress`: inventarisgenerator.
- Nero Studio/client/bridge staat in een afzonderlijke repo; servervalidatie bewijst geen clientcompatibiliteit.
- `content/`: `areas`, `bosses`, `drops`, `events`, `generic`, `interfaces`, `other`, `quest`, `skills`, `travel`.
- `settings.gradle.kts` ontdekt `build.gradle.kts` onder `api/`, `content/`, `engine/`, `server/`; andere roots vereisen expliciete registratie. Declareer moduledependencies.
- `pack/` is een aparte cache/data-module, automatisch genoemd `<parent>-pack`; houd gamecode erbuiten om bootstrap-cirkels te voorkomen.
- `PluginScript`-subklassen worden automatisch geladen; registreer handlers in `startup()` en injecteer services.

## DSL's en API's

Script-extensies hieronder: `api/script/src/main/kotlin/org/rsmod/api/script/`.

- Bosses: `boss {}`, phases, abilities/effects in `api/bosses/src/main/kotlin/org/rsmod/api/bosses/dsl/` (`BossDsl.kt`, `EffectBuilders.kt`).
  `api/bosses/src/main/kotlin/org/rsmod/api/bosses/runtime/`: `BossPluginScript`, `BossCombat`, `BossEncounter`; gebruik native combat/death/instance-hooks.
- NPC/loc/item: `NpcScriptEventExtensions.kt`, `LocScriptEventExtensions.kt`, `HeldScriptEventExtensions.kt`: `onOpNpc1..5`, `onOpLoc1..5`, `onOpHeld1..5`, `onOpHeldU(first, second)`, `onOpLocU`.
  Ground items/interfaces: `ObjScriptEventExtensions.kt`, `InterfaceScriptEventExtensions.kt`.
- Dialogen: `api/player/src/main/kotlin/org/rsmod/api/player/dialogue/Dialogue.kt`; `startDialogue`/keuzes via `api/player/src/main/kotlin/org/rsmod/api/player/protect/ProtectedAccess.kt`.
- Timers/queues: `PlayerScriptEventExtensions.kt`, `NpcScriptEventExtensions.kt`, `EngineQueueScriptEventExtensions.kt`; plannen via `ProtectedAccess` of `api/npc/src/main/kotlin/org/rsmod/api/npc/access/StandardNpcAccess.kt`.
  Hun gameplay-`delay(1)` wacht één tick (600 ms); `kotlinx.coroutines.delay` gebruikt milliseconden en hoort niet in ticklogica.
- Events: `ScriptEventExtensions.kt` biedt `onEvent`/`onProtectedEvent`; dispatch: `engine/events/src/main/kotlin/org/rsmod/events/EventBus.kt`. Bewaak voorwaarden in de consument.
- Drops: `api/drop-table/src/main/kotlin/dtx/rs/RSDropTable.kt`; scopes: `api/drop-table-plugin/src/main/kotlin/org/rsmod/api/droptable/` (`DropWeightedTableScope`, `DropChanceTableScope`, `DropGuaranteedTableScope`).
  Kotlin: `@RegisterDropTable`; TOML: `content/drops/src/main/resources/drops/tables/`; uitleg: `docs/drops.md`. Voorkom dubbele NPC-/objectregistratie.
- `api/invtx/`: `invTransaction`; `api/instances/`: instances; `api/death/`: death/kill hooks; `content/interfaces/collection-log/`: Collection Log.

## Referentiemodules: kies alleen het passende voorbeeld

Zoek genoemde bestanden alleen binnen de aangegeven module; voorbeelden zijn geen volledigheidscertificaat.

- Boss DSL: `content/bosses/kbd`, `KingBlackDragon.kt`: compacte declaratieve attacks/phases zonder eigen controller.
- Complexe lifecycle: `content/bosses/kraken` voor pools/eigendom/tests; `content/bosses/corporeal-beast` voor extra actors; `content/bosses/araxxor` voor private encounters/cleanup.
- Activiteit: `content/events/shooting-stars`: aparte scripts, manager, instellingen en data; wereldactiviteit, geen complete minigame-template.
- Skill met data: `content/skills/fishing`: `scripts/Fishing.kt` voor gedrag; `pack/` met `FishingTable.kt` voor data.
- Interface: `content/other/pets`: `PetMenu.kt` plus `pack/src/main/resources/pack/` met `interfaces/pet_menu.if3`, `cs2/script/` en `cs2/symbols/`; named components en cleanup.
- Drops: `content/bosses/barrows`, `BarrowsChestDropTable.kt` en `BarrowsLootTest`: compacte echte rewardberekening, modifiers en clue-transform.
- Quest: `content/quest`, `ImpCatcher.kt`: native progress-varbit, journal, atomic hand-in en interactiontests.

## Commands vanuit de repo-root

Bronnen: root/module-builds, `build-logic/src/main/kotlin/meta-test-suite.gradle.kts`, `.github/workflows/ci.yml` en `release-server.yml`; Java 21.
Commands zijn uit source/CI gecontroleerd, niet lokaal uitgevoerd voor dit document. Windows: vervang `./gradlew` door `.\gradlew.bat`.

| Doel | Command |
|---|---|
| Schone geïsoleerde checkout zonder cache | `./gradlew :or-cache:freshCache :or-cache:mergePluginGamevals` |
| Bestaande cache/packs/gamevals of ontbrekende generated code | `./gradlew :or-cache:buildCache :or-cache:mergePluginGamevals` |
| CI-build met standaardtests | `./gradlew assemble test` |
| Alle standaard module-tests | `./gradlew test` |
| Standaardtests plus geregistreerde extra suites | `./gradlew test docTest konsistTest` |
| Eén bestaande module testen | `./gradlew :content:bosses:kraken:test` |
| Runtime-JAR bouwen | `./gradlew :server:app:shadowJar` |
| Nieuw RSA-paar, alleen geïsoleerde nieuwe setup | `./gradlew generateRsa` |
| Ontwikkelserver starten | `./gradlew run` |

- Cache/gamevals vóór een clean build genereren; `api/generated/src` wordt niet meegecommit. JAR: `server/app/build/libs/server.jar`; `shadowJar` vervangt tests niet.
- Start alleen een geïsoleerde testserver met passende cache, RSA, `game.yml` en services; kopieer `game.example.yml` alleen wanneer `game.yml` ontbreekt.
- `generateRsa` schrijft beide sleutels zodra één ontbreekt: controleer/herstel een bestaand geldig paar. `install`/`cleanInstall` niet als automatische reparatie gebruiken.
- `test` omvat geen `docTest`/`konsistTest`; historische `src/integration`-tests zijn niet aangesloten. Claim alleen daadwerkelijk uitgevoerde validatie.

## Definitie van klaar

Pas criteria toe op de afgesproken nieuwe/gewijzigde functionaliteit; herkwalificeer een bestaande feature alleen waar de wijziging haar raakt.

- Boss: toegang/requirements, afgesproken encounter/attacks/fases, reset/respawn, native drops, Collection Log, kill count en relevante Slayer-/timerintegratie.
- Minigame/activiteit: toegang, deelname, volledige cyclus/herhaling en rewards; winst/verlies, groepsstate, Collection Log/completion count waar van toepassing.
- Skill: afgesproken trainingsloop, levels/XP, input/output, success/failure, resources/respawn, unlocks/UI en relevante progressie.
- Cleanup: bij einde/death/logout/vertrek alleen betrokken speler-/encounterstate, actors en timers opruimen. Gedeelde wereldcontent volgt haar eigen lifecycle.
- Valideer de gewijzigde interactie/eventketen en relevante risico's: rollback/volle inventory, dubbele beloning, onderbreking, save/load. Begin bij moduletests; verbreed bij gedeelde effecten.
- Build/boot passend bij de wijziging; geen volledige testcyclus voor alleen documentatie. **In-game acceptatie doet de gebruiker**; geef korte teststappen en vermeld onbewezen gedrag.

## Valkuilen en conventies

- Gebruik bestaande `npc.*`, `loc.*`, `obj.*` enz.; vergelijk resolved IDs, geen display-/internal-name-strings. Aliassen kunnen verschillen.
- Zoek mappings gericht in de betrokken `gamevals.toml` en `.data/gamevals/`; binaire bron: `.data/gamevals-binary/`. Volg bestaande plaatsing in content- of packmodule.
- Een nieuwe symboolmapping vervangt geen definitie in `pack/configs/`: controleer IDs, varp-scope/varbit-bits en regenereer mappings/cache. Generated files niet handmatig editen.
- Blijvende voortgang: permanente native vars/inventoryvars. Tijdelijke encounterstate mag in de eigen controller leven; serialiseer geen entityreferenties/callbacks.
- Verbruik en beloning samen via `invTransaction`; XP/completion pas na succesvolle output. Bewaak stale itemreferences, bezit en onafhankelijke puzzel-/encounterstate.
- Test producer én consument; een zelf gepubliceerd verwacht event bewijst geen echte skillactie. Controleer bestaande rewardroutes en placeholders zoals `Drops Need Manual`/`condition { true }`.
- Behoud normale NPC/loc/dig-fallback. Verwijder handlerregistraties bij plugin-unload, niet bij het einde van één encounter.
- Controleer `.if3`, CS2-init en symbolen samen; hergebruik native frame-/button-procs. Raad geen IDs/signatures; `SpawnInterface.kt`/`buildInterface()` is hier geen werkende template.
- Houd mutaties van game-entities op de game-thread; maak snapshots voor achtergrondwerk. Onderzoek gedeelde databaseverbindingen vóór parallelle saves.
- Inventarisgenerator: `--check` schrijft `CONTENT_INVENTORY.md` alsnog en controleert alleen het README-blok; dit is geen read-only validatie.
