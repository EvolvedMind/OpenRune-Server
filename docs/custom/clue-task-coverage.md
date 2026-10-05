# Clue skill-task coverage

Cache revision 240. This is a task-level implementation audit, not a claim that
the entire Treasure Trails feature is playable. Mimic is scheduled last.

| Tier | Task | Current support |
|---|---|---|
| Beginner | I need to give Charlie a cooked trout. | Hand-in consumes the item; assignment and self-production enforcement pending. |
| Beginner | I need to give Charlie a cooked pike. | Hand-in consumes the item; assignment and self-production enforcement pending. |
| Beginner | I need to give Charlie a raw herring. | Hand-in consumes the item; assignment and self-production enforcement pending. |
| Beginner | I need to give Charlie a raw trout. | Hand-in consumes the item; assignment and self-production enforcement pending. |
| Beginner | I need to give Charlie a piece of iron ore. | Hand-in consumes the item; assignment and self-production enforcement pending. |
| Beginner | I need to give Charlie one iron dagger. | Hand-in consumes the item; assignment and self-production enforcement pending. |
| Beginner | I need to give Charlie a leather body. | Hand-in consumes the item; assignment and self-production enforcement pending. |
| Beginner | I need to give Charlie some leather chaps. | Hand-in consumes the item; assignment and self-production enforcement pending. |
| Elite | Equip a Dragon Scimitar. | Pending. |
| Elite | Enchant a piece of dragonstone jewellery. | Pending. |
| Elite | Craft a nature rune. | Pending. |
| Elite | Catch a mottled eel with aerial fishing in Lake Molch. | Pending. |
| Elite | Score a goal in skullball. | Pending. |
| Elite | Complete a lap of Ape atoll agility course. | Pending. |
| Elite | Create a super defence potion. | Pending. |
| Elite | Steal from a chest in Ardougne Castle. | Pending. |
| Elite | Craft a green dragonhide body. | Assigned-task production event implemented; requires return to Sherlock. |
| Elite | String a yew longbow. | Pending. |
| Elite | Slay a dust devil. | Pending. |
| Elite | Catch a black warlock. | Pending. |
| Elite | Catch a red chinchompa. | Pending. |
| Elite | Mine a mithril ore. | Assigned-task production event implemented; requires return to Sherlock. |
| Elite | Smith a Mithril 2h Sword. | Pending. |
| Elite | Catch a raw shark. | Assigned-task production event implemented; requires return to Sherlock. |
| Elite | Cut a yew log. | Assigned-task production event implemented; requires return to Sherlock. |
| Elite | Fix a magical lamp in Dorgesh-Kaan. | Pending. |
| Elite | Burn a yew log. | Pending. |
| Elite | Cook a swordfish | Pending. |
| Elite | Craft multiple cosmic runes from a single essence. | Pending. |
| Elite | Plant a watermelon seed. | Pending. |
| Elite | Activate the Chivalry prayer. | Pending. |
| Elite | Smith a tier 2 or above Shayzien platebody. | Pending. |
| Elite | Mine some nickel. | Pending. |
| Master | Equip an abyssal whip in front of the abyssal demons of the Slayer Tower. | Pending. |
| Master | Smith a runite med helm. | Pending. |
| Master | Teleport to a spirit tree you planted yourself. | Pending. |
| Master | Create a Barrows teleport tablet. | Pending. |
| Master | Slay a Nechryael in the Slayer Tower. | Pending. |
| Master | Kill the spiritual, magic and godly whilst representing their own god. | Pending. |
| Master | Create an unstrung dragonstone amulet at a furnace. | Assigned-task production event implemented; requires return to Sherlock. |
| Master | Burn a magic log. | Pending. |
| Master | Burn a redwood log. | Pending. |
| Master | Complete a lap of the Rellekka rooftop agility course whilst sporting the finest amount of grace. | Pending. |
| Master | Mix an anti-venom potion. | Pending. |
| Master | Mine a piece of runite ore whilst sporting the finest mining gear. | Assigned-task production event and worn outfit checks implemented. |
| Master | Steal a gem from the Ardougne market. | Pending. |
| Master | Pickpocket an elf. | Pending. |
| Master | Bind a blood rune at the Blood Altar. | Pending. |
| Master | Mix a ranging mix potion. | Pending. |
| Master | Fletch a rune dart. | Pending. |
| Master | Cremate a set of fiyr remains. | Pending. |
| Master | Dissect a sacred eel. | Pending. |
| Master | Kill a lizardman shaman. | Pending. |
| Master | Angle for an Anglerfish whilst sporting the finest fishing gear. | Assigned-task production event and worn outfit checks implemented. |
| Master | Chop a redwood log whilst sporting the finest lumberjack gear. | Assigned-task production event and worn outfit checks implemented. |
| Master | Craft a light orb in the Dorgesh-Kaan bank. | Pending. |
| Master | Kill a reanimated abyssal. | Pending. |
| Master | Kill a Fiyr shade inside Mort'tons shade catacombs. | Pending. |
| Master | Combine the torn clue scroll parts. | Pending. |
| Master | Catch a tecu salamander. | Pending. |

The production-event implementation currently covers six gathering tasks and
two crafting tasks. Elite Sherlock assignment retains the current trail step.
Inventory safety and task-state tests are separate from live gameplay acceptance.

Outfit checks run when the product is awarded. Each required slot must be worn.
Supported variants include golden prospector, Varrock armour 4 as the mining top,
spirit angler and forestry pieces, including mixed sets. Bonus products do not count.
References: [Varrock armour substitution](https://github.com/runelite/runelite/issues/12023),
[Lumberjack and Forestry](https://oldschool.runescape.wiki/w/Lumberjack_outfit).
