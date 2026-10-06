package org.rsmod.content.bosses.doom

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.obj.Obj
import org.rsmod.content.interfaces.collectionlog.CollectionLog

/** Ground samples preserve run, escrow and kill counts; testloot can register delivered obtains. */
class DoomTestLoot @Inject internal constructor(
    private val rewards: DoomRewards,
    private val objs: ObjRepository,
    private val collectionLog: CollectionLog,
) {
    fun generate(player: Player, count: Int, level: Int = 8, logRewards: Boolean = false) {
        require(count in 1..1000 && level in 1..1000)
        val npc = Npc(checkNotNull(ServerCacheManager.getNpc(DoomNpcs.BOSS.asRSCM())), player.coords)
        repeat(count) {
            for (item in rewards.roll(player, npc, level)) {
                val delivered = objs.add(Obj.fromOwner(player, player.coords, item), duration = 300)
                if (delivered && logRewards) collectionLog.grant(player, item.id, item.count, "Doom of Mokhaiotl")
            }
        }
    }
}
