package com.artillexstudios.axminions.listeners

import com.artillexstudios.axminions.AxMinionsPlugin
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerAdvancementDoneEvent
import org.bukkit.event.player.PlayerStatisticIncrementEvent
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Blocks advancements and statistics for players when minion is attacking on their behalf
 */
class MinionPlayerEventBlocker : Listener {

    companion object {
        private val attackingPlayers = ConcurrentHashMap.newKeySet<UUID>()

        /**
         * Mark player as currently attacking via minion
         */
        fun markAttacking(player: Player) {
            attackingPlayers.add(player.uniqueId)
            player.setMetadata("minion_attacking", FixedMetadataValue(AxMinionsPlugin.INSTANCE, true))
        }

        /**
         * Unmark player from minion attacking state
         */
        fun unmarkAttacking(player: Player) {
            attackingPlayers.remove(player.uniqueId)
            player.removeMetadata("minion_attacking", AxMinionsPlugin.INSTANCE)
        }

        /**
         * Check if player is currently attacking via minion
         */
        fun isAttacking(player: Player): Boolean {
            return attackingPlayers.contains(player.uniqueId)
        }

        /**
         * Check if player UUID is currently attacking via minion
         */
        fun isAttacking(uuid: UUID): Boolean {
            return attackingPlayers.contains(uuid)
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onAdvancement(event: PlayerAdvancementDoneEvent) {
        if (!isAttacking(event.player)) return

        // Revoke the advancement that was just granted
        val advancement = event.advancement
        val progress = event.player.getAdvancementProgress(advancement)

        progress.awardedCriteria.forEach { criteria ->
            progress.revokeCriteria(criteria)
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onStatisticIncrement(event: PlayerStatisticIncrementEvent) {
        if (!isAttacking(event.player)) return

        // Cancel the statistic increment
        event.isCancelled = true
    }
}
