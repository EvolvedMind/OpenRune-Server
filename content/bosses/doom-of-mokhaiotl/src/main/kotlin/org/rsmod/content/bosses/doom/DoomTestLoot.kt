package org.rsmod.content.bosses.doom

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/** Ground samples preserve run, escrow, kill counts and collection-log progress. */
class DoomTestLoot @Inject internal constructor(
    private val rewards: DoomRewards,
    private val objs: ObjRepository,
) {
    fun generate(player: Player, count: Int, level: Int = 8) {
        require(count in 1..1000 && level in 1..1000)
        val npc = Npc(checkNotNull(ServerCacheManager.getNpc(DoomNpcs.BOSS.asRSCM())), player.coords)
        repeat(count) {
            for (item in rewards.roll(player, npc, level))
                objs.add(RSCM.getReverseMapping(RSCMType.OBJ, item.id), player.coords, duration = 300, receiver = player, count = item.count)
        }
    }
}
