package com.artillexstudios.axminions.integrations.placeholder

import com.artillexstudios.axminions.AxMinionsPlugin
import com.artillexstudios.axminions.api.AxMinionsAPIImpl
import com.artillexstudios.axminions.api.config.Config
import com.artillexstudios.axminions.api.minions.miniontype.MinionTypes
import com.artillexstudios.axminions.api.utils.TimeUtils
import com.artillexstudios.axminions.utils.MinionActivity
import com.artillexstudios.axminions.utils.PlayerStats
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.entity.Player

class PlaceholderAPIIntegration : PlaceholderExpansion() {

    override fun getIdentifier(): String {
        return "axminions"
    }

    override fun getAuthor(): String {
        return "Artillex-Studios"
    }

    override fun getVersion(): String {
        return AxMinionsPlugin.INSTANCE.description.version
    }

    // This can run on the main thread every tick, so the database is only read through the cache of PlayerStats.
    // Until the amounts of a player are loaded (a moment after they join) they show as 0.
    override fun onPlaceholderRequest(player: Player?, params: String): String {
        if (player == null) return ""

        return when (params) {
            "placed" -> (PlayerStats.get(player.uniqueId)?.placed ?: 0).toString()
            "limit" -> totalLimit(player).toString()
            // How many more minions the player can place, never below 0. Like the limit, it is the biggest number if there is no limit.
            "remaining" -> {
                val limit = totalLimit(player)
                if (limit == Int.MAX_VALUE) {
                    limit.toString()
                } else {
                    (limit - (PlayerStats.get(player.uniqueId)?.placed ?: 0)).coerceAtLeast(0).toString()
                }
            }

            "extra_slots" -> (PlayerStats.get(player.uniqueId)?.extraSlots ?: 0).toString()
            // The island the player is standing on, both are 0 outside of an island or if the island limit is turned off
            "island_placed" -> (PlayerStats.getIsland(player)?.placed ?: 0).toString()
            "island_limit" -> {
                val island = PlayerStats.getIsland(player)
                if (island == null) "0" else (Config.ISLAND_LIMIT() + island.extraSlots).toString()
            }

            "trusted_count" -> AxMinionsPlugin.users.getTrustedCount(player.uniqueId).toString()
            // How many minions of the player work right now, the ones /minions list shows as Active
            "active" -> MinionActivity.get(player.uniqueId).active.toString()
            // The least charge the minions of the player that work have left, --- if there is none (or the minions don't need charge)
            "lowest_charge" -> MinionActivity.get(player.uniqueId).lowestCharge?.let { TimeUtils.format(it) } ?: "---"
            else -> {
                if (params.startsWith("placed_")) placedOfType(player, params.substring("placed_".length)) else ""
            }
        }
    }

    private fun totalLimit(player: Player): Int {
        val limit = AxMinionsAPIImpl.permissionLimit(player)
        if (limit == Int.MAX_VALUE) return limit

        return limit + (PlayerStats.get(player.uniqueId)?.extraSlots ?: 0)
    }

    private fun placedOfType(player: Player, name: String): String {
        // An unknown type gives nothing instead of a 0, so a typo is noticed
        val type = MinionTypes.getMinionTypes().keys.firstOrNull { it.equals(name, true) } ?: return ""
        return (PlayerStats.get(player.uniqueId)?.types?.get(type) ?: 0).toString()
    }
}
