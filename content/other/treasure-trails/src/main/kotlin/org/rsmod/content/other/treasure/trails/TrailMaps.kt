package org.rsmod.content.other.treasure.trails

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM

/** Only associations explicitly named in both cache item and interface definitions. */
internal object TrailMaps {
    private val names = listOf(
        "trail_clue_easy_map006",
        "trail_clue_hard_map006", "trail_clue_hard_map007",
        "trail_clue_medium_map008", "trail_clue_medium_map009", "trail_clue_medium_map010",
        "trail_clue_medium_map011", "trail_clue_medium_map012",
    )
    val interfaces by lazy {
        names.associate { name ->
            val item = "obj.$name".asRSCM()
            val modal = "interface.$name"
            checkNotNull(ServerCacheManager.getItem(item))
            checkNotNull(ServerCacheManager.getInterface(modal.asRSCM()))
            item to modal
        }
    }
}
