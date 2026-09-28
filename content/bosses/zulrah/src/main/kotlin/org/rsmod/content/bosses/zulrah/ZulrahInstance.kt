package org.rsmod.content.bosses.zulrah

import dev.openrune.ServerCacheManager
import dev.openrune.types.ObjectServerType
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.instances.BossInstanceRegistry
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceScript
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onProtectedEvent
import org.rsmod.plugin.scripts.ScriptContext

class ZulrahInstance
@Inject
constructor(
    registry: BossInstanceRegistry,
    private val encounters: ZulrahEncounterManager,
    private val recovery: ZulrahDeathRecovery,
    private val protectedAccess: ProtectedAccessLauncher,
) : InstanceScript(registry) {
    override fun settingsRow(): String = "dbrow.instance_zulrah"

    override fun area(): InstanceArea = ARENA

    override fun destroyWhenEmpty(): Boolean = true

    override fun ScriptContext.configure() {
        onEnterObject { enterZulrah() }
        onExitObject { defaultLeaveFlow() }
        bindBoatVariants()
        onInstancePlayerJoin {
            val session = manager.sessionForId(instanceId) ?: return@onInstancePlayerJoin
            encounters.start(player, session)
        }
        onInstancePlayerLeave { encounters.stop(instanceId) }
        onInstanceEnded { encounters.stop(instanceId) }

        onCommand("zulrah") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Enter a private Zulrah encounter, or leave with ::zulrah leave"
            invalidArgs = "Use ::zulrah or ::zulrah leave"
            cheat {
                val leave = args.singleOrNull()?.equals("leave", ignoreCase = true) == true
                if (args.isNotEmpty() && !leave) {
                    player.mes("Use ::zulrah or ::zulrah leave")
                    return@cheat
                }
                protectedAccess.launch(player, "Please finish your current action first.") {
                    if (!leave) {
                        enterZulrah()
                    } else if (manager.sessionForPlayer(player)?.key == key) {
                        defaultLeaveFlow()
                    } else {
                        mes("You are not inside a Zulrah instance.")
                    }
                }
            }
        }
    }

    private fun ScriptContext.bindBoatVariants() {
        val bases = settingsRowData().enterObject
        val baseIds = bases.map { it.id }.toSet()
        for (boat in boatTypes(bases)) {
            if (boat.id !in baseIds) {
                onProtectedEvent<LocEvents.Op1>(boat.id) { enterZulrah() }
            }
            if (boat.actions.getOpOrNull(1).equals("Quick-board", ignoreCase = true)) {
                onProtectedEvent<LocEvents.Op2>(boat.id) { enterZulrah() }
            }
        }
    }

    private suspend fun ProtectedAccess.enterZulrah() {
        if (recovery.hasItems(player)) {
            mes("Speak to the priest beside the boat to reclaim your lost items first.")
            return
        }
        defaultInstanceEntry()
    }

    internal companion object {
        val ARENA: InstanceArea.CopyRegions =
            InstanceArea.copyRegions(regionIds = listOf(9007, 9008))

        fun boatTypes(bases: List<ObjectServerType>): List<ObjectServerType> {
            val pending = ArrayDeque(bases)
            val resolved = linkedMapOf<Int, ObjectServerType>()
            while (pending.isNotEmpty()) {
                val boat = pending.removeFirst()
                if (resolved.putIfAbsent(boat.id, boat) != null) continue
                for (id in boat.multiLoc.asList() + boat.multiDefault) {
                    if (id < 0 || id in resolved) continue
                    val variant = checkNotNull(ServerCacheManager.getObject(id)) {
                        "Missing sacrificial boat transform: $id"
                    }
                    pending.addLast(variant)
                }
            }
            return resolved.values.toList()
        }
    }
}
