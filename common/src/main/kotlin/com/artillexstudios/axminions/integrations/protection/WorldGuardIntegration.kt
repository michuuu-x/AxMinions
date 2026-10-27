package com.artillexstudios.axminions.integrations.protection

import com.artillexstudios.axminions.AxMinionsPlugin
import com.artillexstudios.axminions.api.integrations.types.ProtectionIntegration
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.bukkit.WorldGuardPlugin
import com.sk89q.worldguard.protection.flags.Flags
import com.sk89q.worldguard.protection.flags.StateFlag
import org.bukkit.Location
import org.bukkit.entity.Player

class WorldGuardIntegration : ProtectionIntegration {
    companion object {
        private const val PLACE_FLAG = "axminions-place"

        // Null if the flag could not be registered, then the regions have no say in where the minions can be placed
        private var placeFlag: StateFlag? = null

        /**
         * WorldGuard only takes new flags until it enables, so this has to be called when the plugin loads.
         * The flag is allowed where it is not set, a region can deny it to not let minions be placed in it.
         */
        fun registerFlags() {
            val registry = WorldGuard.getInstance().flagRegistry
            try {
                val flag = StateFlag(PLACE_FLAG, true)
                registry.register(flag)
                placeFlag = flag
            } catch (exception: Exception) {
                // Another plugin could have the name already, or WorldGuard could be enabled already when the plugin gets reloaded
                val existing = registry.get(PLACE_FLAG)
                if (existing is StateFlag) {
                    placeFlag = existing
                } else {
                    AxMinionsPlugin.INSTANCE.logger.warning("Could not register the WorldGuard flag $PLACE_FLAG: ${exception.message}")
                }
            }
        }
    }

    override fun canBuildAt(player: Player, location: Location): Boolean {
        val localPlayer = WorldGuardPlugin.inst().wrapPlayer(player)
        val world = BukkitAdapter.adapt(player.world)

        if (WorldGuard.getInstance().platform.sessionManager.hasBypass(localPlayer, world)) {
            return true
        }

        val container = WorldGuard.getInstance().platform.regionContainer
        val query = container.createQuery()

        return query.testState(BukkitAdapter.adapt(location), localPlayer, Flags.BUILD)
    }

    override fun canPlaceMinionAt(player: Player, location: Location): Boolean {
        val flag = placeFlag ?: return true
        val localPlayer = WorldGuardPlugin.inst().wrapPlayer(player)
        val world = BukkitAdapter.adapt(player.world)

        if (WorldGuard.getInstance().platform.sessionManager.hasBypass(localPlayer, world)) {
            return true
        }

        val query = WorldGuard.getInstance().platform.regionContainer.createQuery()
        return query.testState(BukkitAdapter.adapt(location), localPlayer, flag)
    }

    override fun register() {

    }
}
