# EvolvedMind/OpenRune-Server — AGENTS.md

Projectregels voor Codex en Claude Code. Kotlin, Java 21, OpenRune/RSMod/Alter, revisie **240**.

## 1. Werkwijze

- Bepaal doel en acceptatiecriteria; plan alleen grotere opdrachten. Rond noodzakelijke deelstappen zelfstandig af. Vraag alleen bij een ontbrekende noodzakelijke keuze of bevoegdheid. Start geen ongevraagde of geparkeerde features.
- Begin met `git status --short`, `git branch --show-current` en `git log -1 --oneline`. Herken detached HEAD en bestaand werk.
- Bij hervatten: controleer relevante voortgang, branch/commit en lokale diff. Verzin geen eerdere resultaten.
- Zoek eerst met `rg`/`rg --files`. Lees gericht; verbreed alleen voor de opdracht of een concrete afhankelijkheid. Herhaal geen afgerond onderzoek zonder aanleiding.
- Toets technische documentatie aan actuele code, buildconfiguratie en tests. Corrigeer relevante verouderde verwijzingen; verander gebruikersafspraken niet zelfstandig.
- Delegeer alleen onafhankelijke deeltaken met afgebakende bestanden en beperkte context. Voorkom dubbel werk/gelijktijdige edits en controleer de integratie.

## 2. Bestaande server behouden

- Behoud revisie **240** en de geaccepteerde client/server/cachecombinatie. Contentpacks/gamevals opnieuw bouwen mag; upgrades vereisen een expliciete upgradeopdracht.
- Behoud wijzigingen en checkpoints; gebruik zo nodig een aparte branch/worktree. Geen destructieve resets of herschrijven van gedeelde geschiedenis buiten de opdracht.
- Houd `main` stabiel en commits gericht. Verwijder tijdelijke branches pas nadat unieke inhoud met `main` is vergeleken en veilig is verwerkt of bewaard.
- Bouw en test geïsoleerd. Vervang live JAR, cache, RSA of playerdata alleen binnen een expliciete installatieopdracht.
- Gebruik bestaande contentmodules/API's. Wijzig gedeelde core alleen als de opdracht dit vereist en bestaande uitbreidingsmogelijkheden niet volstaan; controleer de geraakte systemen.
- Nero Studio/client/bridge staat in een andere repo. Een serverbuild bewijst geen clientcompatibiliteit.

## 3. Nieuwe content: eerst upstream controleren

Voor nieuwe content of een grote uitbreiding:

1. Zoek op naam en relevante aliassen in de eigen repo, de [officiële OpenRune-repo](https://github.com/OpenRune/OpenRune-Server) en de [pull requests](https://github.com/OpenRune/OpenRune-Server/pulls).
2. Controleer relevante open/draft, gemergede en gesloten niet-gemergede PR's. Lees code/diffs en beschikbare reviews/testresultaten.
3. Vergelijk mechanics, custom gedrag, native integraties, cleanup, tests en revisie-240-compatibiliteit. Upstream is niet automatisch beter of compleet.
4. Kies: eigen implementatie behouden, delen aanpassen/hergebruiken of ontbrekende delen bouwen. Motiveer kort; behoud bronvermelding/licentie en voorkom dubbele handlers, drops en registraties.
5. Neem alleen beoordeelde wijzigingen binnen de opdracht over. Geen automatische merge of ongecontroleerde branchimport.
6. Noteer bronlink/PR, commit, keuze en open verschillen in de feature-notitie; hergebruik dit bij hervatten. Meld onbereikbare bronnen als open controlepunt, niet als bewijs dat content ontbreekt.

## 4. Architectuur en startpunten

| Onderdeel | Gebruik |
|---|---|
| `content/` | Gameplayplugins en bijbehorende packs. |
| `api/` | Gameplay-API's; o.a. `bosses`, `script`, `invtx`, `instances`, `death`, `drop-table`. |
| `engine/` | Entities, routing, ticks/coroutines, events en pluginbasis. |
| `server/` | Bootstrap, plugin discovery en achtergrondservices. |
| `or-cache/` | Cachebuilder, gamevals, definities, interfaces/CS2 en codegeneratie. |
| `tools/` | Wiki/cacheonderzoek, imports en voortgangsgeneratie. |

- `settings.gradle.kts` ontdekt modules onder `api/`, `content/`, `engine/` en `server/`. Declareer dependencies; registreer andere roots expliciet.
- `PluginScript` wordt automatisch geladen. Injecteer services; registreer handlers in `startup()`. Gebruik voor bosses `api/bosses` met `boss {}`, phases en abilities/effects.
- `pack/` is een aparte cache/data-module (`<parent>-pack`); houd runtime-gamecode erbuiten om bootstrap-cirkels te voorkomen.
- Kies één passend voorbeeld: `content/bosses/kbd` voor BossDSL, `content/bosses/kraken` voor encounter-lifecycle/tests, `content/skills/fishing` voor skills, `content/other/pets` voor interfaces, `content/bosses/barrows` voor rewards of `ImpCatcher.kt` binnen `content/quest`. Controleer het voorbeeld; veronderstel geen volledigheid.
- Lees alleen relevante systeemdocs, bijvoorbeeld `docs/drops.md` bij droptabellen.

## 5. Code, state en cache

- Volg bestaande Kotlin-conventies, `.editorconfig` en formatter. Geen ongerelateerde refactors; commentaar alleen voor niet-vanzelfsprekende mechanics/invarianten.
- Gebruik gameval-symbolen (`npc.*`, `loc.*`, `obj.*`, enz.). Raad geen IDs, namen of signatures. Vergelijk resolved IDs; aliassen en displaynamen kunnen verschillen.
- Zoek mappings in betrokken `gamevals.toml`, `.data/gamevals/` en `.data/gamevals-binary/`. Een mapping vervangt geen definitie in `pack/configs/`. Controleer varp-scope/varbit-bits; regenereer benodigde cache/gamevals.
- Blijvende voortgang gebruikt permanente native vars/inventoryvars. Tijdelijke encounterstate heeft een duidelijke eigenaar en levensduur. Serialiseer geen entityreferenties of callbacks.
- Gebruik `invTransaction` voor gekoppeld verbruik en beloning; ken XP/completion pas toe na succesvolle output. Bewaak bezit, stale itemreferences en dubbele beloningen.
- Gebruik native combat/death, drops, kill count, Collection Log en instances. Controleer rewardroutes en placeholders zoals `Drops Need Manual` of `condition { true }`.
- Plan gameplay met tickqueues/timers en de bestaande access-DSL. Daar is `delay(1)` één tick (600 ms); `kotlinx.coroutines.delay` gebruikt milliseconden en hoort niet in ticklogica.
- Ruim bij einde/death/logout/vertrek betrokken encounterstate, actors en timers op. Respecteer de lifecycle van gedeelde wereldcontent. Verwijder globale handlers bij plugin-unload, niet na één encounter.
- Behoud normale NPC-, object- en dig-fallbacks. Bewaak voorwaarden in eventconsumenten.

## 6. Interfaces en prestaties

- Controleer `.if3`, CS2-init en symbolen samen. Hergebruik native frames, buttons, scrollbars, fonts en sprites uit een werkende interface in deze fork.
- Houd OSRS-stijl, consistente kleuren/afstand en correcte clickmasks aan. Controleer relevante layouts in fixed/resizable mode en visueel met beschikbare clienttools.
- Houd entitymutaties op de game-thread. Geen blokkerende I/O in ticklogica; gebruik bestaande services en snapshots voor achtergrondwerk.
- Beperk periodieke controles tot betrokken spelers/entities en geef tijdelijke taken een eindconditie. Onderzoek gedeelde databaseverbindingen vóór parallelle saves; meet relevante belasting bij wijzigingen aan gedeelde of druk gebruikte processen.

## 7. Bouwen en starten

Repo-root, Java 21. Windows: vervang `./gradlew` door `.\gradlew.bat`. Kies de kleinste passende opdracht.

| Doel | Command |
|---|---|
| Nieuwe geïsoleerde checkout zonder cache | `./gradlew :or-cache:freshCache :or-cache:mergePluginGamevals` |
| Bestaande cache/packs/gamevals of ontbrekende generated code | `./gradlew :or-cache:buildCache :or-cache:mergePluginGamevals` |
| Moduletests, voorbeeld Kraken | `./gradlew :content:bosses:kraken:test` |
| Build en standaardtests | `./gradlew assemble test` |
| Standaardtests plus extra suites | `./gradlew test docTest konsistTest` |
| Runtime-JAR bouwen | `./gradlew :server:app:shadowJar` |
| Geïsoleerde ontwikkelserver starten | `./gradlew run` |

- Genereer ontbrekende cache/gamevals vóór een clean build. Commit `api/generated/src` niet. JAR: `server/app/build/libs/server.jar`.
- Start met passende cache, RSA, `game.yml` en services in een geïsoleerde testomgeving. Kopieer `game.example.yml` alleen wanneer `game.yml` ontbreekt.
- Geen `install`/`cleanInstall` als automatische reparatie. `generateRsa` schrijft een nieuw paar als één sleutel ontbreekt; herstel een bestaand geldig paar. Genereer nieuwe sleutels alleen voor een expliciete nieuwe setup.

## 8. Valideren en afronden

- Test de gewijzigde module en echte interactie/eventketen: producer én consument. Zelf een verwacht event publiceren bewijst geen gameplayactie.
- Controleer relevante foutgevallen: volle inventory, rollback, dubbele beloning, onderbreking, dood/vertrek/logout en save/load. Verbreed bij gedeelde effecten.
- Controleer testregistratie en uitvoering. `test` omvat niet automatisch `docTest`/`konsistTest`; `src/integration` telt alleen mee als de build haar koppelt. `shadowJar` vervangt geen tests.
- Controleer passende boot- en gameplayscenario's met beschikbare compatibele MCP/clienttools. Ontbreekt clienttoegang, rond overige controles af en geef korte handmatige teststappen. Meld onbewezen gedrag; de gebruiker doet de eindacceptatie.
- Pas onderstaande criteria toe op de afgesproken functionaliteit en geraakte bestaande content.

| Content | Nodig voor afronding |
|---|---|
| Boss | Toegang/requirements, afgesproken attacks/fases, reset/respawn, drops, kill count, Collection Log en toepasselijke Slayer-/timerintegratie. |
| Minigame/activiteit | Toegang/deelname, volledige cyclus/herhaling, winst/verlies, relevante groepsstate en correcte rewards/progressie. |
| Skill | Trainingsloop, levels/XP, input/output, success/failure, resources/respawn en afgesproken unlocks/UI. |

- Herstel fouten binnen de opdracht en verifieer de oplossing; onderscheid bestaande fouten en regressies. Geen serverbuild voor alleen documentatie. Markeer tussenstappen of onbewezen gameplay niet als compleet.

## 9. Voortgang en oplevering

- `PROGRESS.md` is het enige overzicht voor actuele status, prioriteiten en open werk. Werk relevante regels bij; voeg geen tweede statuslog toe. Technische besluiten, broncommits en testbewijs staan in de passende feature-notitie onder `docs/custom/`; de index staat in `docs/custom/README.md`.
- Toekomstig, geparkeerd en migratiewerk staat in `tools/progress/roadmap.json`; het roadmapblok in `PROGRESS.md` wordt daaruit gegenereerd. Verplaats afgerond of actief werk naar het handmatige statusdeel en verwijder het uit de backlogbron.
- `CONTENT_INVENTORY.md` toont automatisch gevonden modules/referenties, geen geverifieerde gameplay. Wijzig gegenereerde inhoud via de bron. Genereer met `node tools/progress/content-progress.mjs`; controleer zonder schrijven met dezelfde opdracht plus `--check`.
- Lever kort op: **gewijzigd**, **gecontroleerd**, **nog open**. Claim alleen uitgevoerde validatie; vermeld een volgende actie alleen als werk resteert.
