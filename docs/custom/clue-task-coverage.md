# Clue skill-task coverage

Cache revision 240. This is a task-level implementation audit, not a claim that
the entire Treasure Trails feature is playable. Mimic is scheduled last.

Current implementation coverage: 50 of 60 task rows have completion handling
(6 gathering, 3 crafting, 3 Herblore, 5 combat, 2 equipment, 5 Firemaking,
3 Runecrafting, 8 Charlie hand-ins, 3 Cooking/Smithing, 2 Fletching,
1 Chivalry, 1 nickel, 1 stall theft, 1 eel dissection, 1 elf pickpocket, 1 watermelon planting,
1 jewellery enchantment, 1 chest theft, 1 Shayzien smithing, 1 tablet creation).
The other 10 remain incomplete. The eight new actions are on `feature/clue-tasks-50`;
see [native actions and activity limits](clue-native-tasks.md). These counts describe
code and focused tests, not live acceptance or complete supporting skills.

Charlie accepts items from any source following assignment. Self-production is
not required since the [30 November 2022 Jagex update](https://secure.runescape.com/m=news/the-garden-of-death--more?oldschool=1).
The previous audit's self-production requirement was incorrect.

| Tier | Task | Current support |
|---|---|---|
| Beginner | I need to give Charlie a cooked trout. | Assignment retained; unnoted item hand-in consumes one item and advances once. Any source accepted. |
| Beginner | I need to give Charlie a cooked pike. | Assignment retained; unnoted item hand-in consumes one item and advances once. Any source accepted. |
| Beginner | I need to give Charlie a raw herring. | Assignment retained; unnoted item hand-in consumes one item and advances once. Any source accepted. |
| Beginner | I need to give Charlie a raw trout. | Assignment retained; unnoted item hand-in consumes one item and advances once. Any source accepted. |
| Beginner | I need to give Charlie a piece of iron ore. | Assignment retained; unnoted item hand-in consumes one item and advances once. Any source accepted. |
| Beginner | I need to give Charlie one iron dagger. | Assignment retained; unnoted item hand-in consumes one item and advances once. Any source accepted. |
| Beginner | I need to give Charlie a leather body. | Assignment retained; unnoted item hand-in consumes one item and advances once. Any source accepted. |
| Beginner | I need to give Charlie some leather chaps. | Assignment retained; unnoted item hand-in consumes one item and advances once. Any source accepted. |
| Elite | Equip a Dragon Scimitar. | Native equip transaction hook; assignment and removal checks tested. |
| Elite | Enchant a piece of dragonstone jewellery. | Native Lvl-5 spell target, four outputs, rune/staff/pouch validation and committed XP; assigned task completes before return to Sherlock. Live acceptance pending. |
| Elite | Craft a nature rune. | Standard altar output hook; cosmic requires base multiplier >= 2. Live acceptance pending. |
| Elite | Catch a mottled eel with aerial fishing in Lake Molch. | Pending. |
| Elite | Score a goal in skullball. | Pending. |
| Elite | Complete a lap of Ape atoll agility course. | Pending. |
| Elite | Create a super defence potion. | Production hook implemented; assignment and dose variants tested. Live brewing validation pending. |
| Elite | Steal from a chest in Ardougne Castle. | Native mapped castle chest Search-for-traps action; level, atomic loot, restock, teleport and source-specific clue handling. Live acceptance pending. |
| Elite | Craft a green dragonhide body. | Assigned-task production event implemented; requires return to Sherlock. |
| Elite | String a yew longbow. | Native stringing recipe and successful output event implemented; live acceptance pending. |
| Elite | Slay a dust devil. | Kill-credit hook implemented for ordinary variants after assignment; tower location checked where required. |
| Elite | Catch a black warlock. | Pending. |
| Elite | Catch a red chinchompa. | Pending. |
| Elite | Mine a mithril ore. | Assigned-task production event implemented; requires return to Sherlock. |
| Elite | Smith a Mithril 2h Sword. | Successful smithing output event implemented; assignment checks tested. |
| Elite | Catch a raw shark. | Assigned-task production event implemented; requires return to Sherlock. |
| Elite | Cut a yew log. | Assigned-task production event implemented; requires return to Sherlock. |
| Elite | Fix a magical lamp in Dorgesh-Kaan. | Native orb-on-broken-lamp and Fix actions, committed orb consumption, repaired object and XP. Ten shared lamps become repairable at startup; personal OSRS lamp distribution/bonus remain outside this slice. Live acceptance pending. |
| Elite | Burn a yew log. | Successful ground-fire hook after assignment. Campfire tending and live acceptance pending. |
| Elite | Cook a swordfish | Successful cooking output event implemented; burnt outputs excluded. |
| Elite | Craft multiple cosmic runes from a single essence. | Standard altar output hook; cosmic requires base multiplier >= 2. Live acceptance pending. |
| Elite | Plant a watermelon seed. | Native allotment planting commits three seeds, saved patch state and XP before assigned-task completion. Native loc/multiloc and Sherlock/casket tests pass; live acceptance pending. [Farming scope](clue-farming.md). |
| Elite | Activate the Chivalry prayer. | Direct and quick-prayer activation events; requires prayer actually enabled after assignment. |
| Elite | Smith a tier 2 or above Shayzien platebody. | Native bars-on-anvil production UI and four-bar transaction; tiers 2–5 qualify, tier 1 does not. Smithing and cached Mining requirements checked. Live acceptance pending. |
| Elite | Mine some nickel. | Existing mining output hook recognizes nickel ore; assignment checks tested. |
| Master | Equip an abyssal whip in front of the abyssal demons of the Slayer Tower. | Assigned equip event checks worn weapon, Slayer Tower area, same-floor abyssal demon within 10 tiles. Whip/ornament/tentacle variants tested; proximity parity and live acceptance pending. |
| Master | Smith a runite med helm. | Successful smithing output event implemented; assignment checks tested. |
| Master | Teleport to a spirit tree you planted yourself. | Pending. |
| Master | Create a Barrows teleport tablet. | Existing native Arceuus lectern, real spell/rune/essence requirements, atomic tablet creation and assigned-task handling. Live acceptance pending. |
| Master | Slay a Nechryael in the Slayer Tower. | Kill-credit hook implemented for ordinary variants after assignment; tower location checked where required. |
| Master | Kill the spiritual, magic and godly whilst representing their own god. | One credited spiritual mage kill with its god's equipment worn; all four factions, wrong-god/unworn/Slayer checks and native death/Sherlock route tested. Live acceptance pending. |
| Master | Create an unstrung dragonstone amulet at a furnace. | Assigned-task production event implemented; requires return to Sherlock. |
| Master | Burn a magic log. | Successful ground-fire hook after assignment. Campfire tending and live acceptance pending. |
| Master | Burn a redwood log. | Successful ground-fire hook after assignment. Campfire tending and live acceptance pending. |
| Master | Complete a lap of the Rellekka rooftop agility course whilst sporting the finest amount of grace. | Pending. |
| Master | Mix an anti-venom potion. | Production hook implemented; assignment and dose variants tested. Live brewing validation pending. |
| Master | Mine a piece of runite ore whilst sporting the finest mining gear. | Assigned-task production event and worn outfit checks implemented. |
| Master | Steal a gem from the Ardougne market. | Native stall theft with level, loot, inventory and restock handling; completion checks the successful theft source and cache-verified Ardougne stall tile. Live acceptance pending. |
| Master | Pickpocket an elf. | Adapted upstream target/loot data for 53 native NPCs; committed loot and XP publish success, assigned task retains the clue until Sherlock. Native Op3 and contextual Sherlock/casket tests pass. Live acceptance pending. |
| Master | Bind a blood rune at the Blood Altar. | Successful output with verified altar identity; Kourend and true Blood Altar accepted. Live acceptance pending. |
| Master | Mix a ranging mix potion. | Production hook implemented; assignment and dose variants tested. Live brewing validation pending. |
| Master | Fletch a rune dart. | Native feathering recipe and successful output event implemented; partial batches and rollback tested. |
| Master | Cremate a set of fiyr remains. | Native Mort'ton pyre action with magic/redwood pyre logs, tinderbox and Fiyr remains; committed reward and XP precede clue completion. Partial pyres and full Shades activity remain outside this slice. Live acceptance pending. |
| Master | Dissect a sacred eel. | Knife-on-eel production with level-dependent scales and Cooking XP; atomic replacement and source-specific assigned-task completion. Native eel fishing is separate outstanding skill support. Live acceptance pending. |
| Master | Kill a lizardman shaman. | Kill-credit hook implemented for ordinary variants after assignment; tower location checked where required. |
| Master | Angle for an Anglerfish whilst sporting the finest fishing gear. | Assigned-task production event and worn outfit checks implemented. |
| Master | Chop a redwood log whilst sporting the finest lumberjack gear. | Assigned-task production event and worn outfit checks implemented. |
| Master | Craft a light orb in the Dorgesh-Kaan bank. | Existing wire-and-orb recipe output checked inside the ground-floor bank; outside/floor/wrong-product tests. Live acceptance pending. |
| Master | Kill a reanimated abyssal. | Pending. |
| Master | Kill a Fiyr shade inside Mort'tons shade catacombs. | Native shadow activation, credited death inside the cached catacombs and Firemaking requirement; model restoration at native respawn tested. Door/key access and live acceptance remain open. |
| Master | Combine the torn clue scroll parts. | Pending. |
| Master | Catch a tecu salamander. | Pending. |

Elite Sherlock, Charlie and Falo assignment retain the current trail step.
Watson stores partial deposits persistently and exchanges four tiers atomically;
full inventories and an existing banked master do not consume stored progress.
Inventory safety and task-state tests are separate from live gameplay acceptance.

Outfit checks run when the product is awarded. Each required slot must be worn.
Supported variants include golden prospector, Varrock armour 4 as the mining top,
spirit angler and forestry pieces, including mixed sets. Bonus products do not count.
References: [Varrock armour substitution](https://github.com/runelite/runelite/issues/12023),
[Lumberjack and Forestry](https://oldschool.runescape.wiki/w/Lumberjack_outfit).

Combat task hooks use the death system's credited player, not nearby players.
They mark the assigned task complete and retain the current clue step until return
to Sherlock. Superior Slayer and raid-specific variants still require validation.
