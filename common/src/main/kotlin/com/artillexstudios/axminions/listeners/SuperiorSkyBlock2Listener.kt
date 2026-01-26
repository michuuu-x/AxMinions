package com.artillexstudios.axminions.listeners

import com.artillexstudios.axapi.utils.StringUtils
import com.artillexstudios.axminions.api.config.Config
import com.artillexstudios.axminions.api.config.Messages
import com.artillexstudios.axminions.minions.Minions
import com.bgsoftware.superiorskyblock.api.world.Dimension;
import com.bgsoftware.superiorskyblock.api.events.IslandDisbandEvent
import com.bgsoftware.superiorskyblock.api.events.IslandKickEvent
import com.bgsoftware.superiorskyblock.api.island.IslandChunkFlags
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

class SuperiorSkyBlock2Listener : Listener {
    private val ssbChunkFlags = IslandChunkFlags.ONLY_PROTECTED or IslandChunkFlags.NO_EMPTY_CHUNKS

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onIslandDisbandEvent(event: IslandDisbandEvent) {
        val minions = Minions.getMinions()

        Dimension.values().forEach { entry ->
            try {
                event.island.getAllChunksAsync(entry, ssbChunkFlags) { chunk ->
                    minions.forEach { minion ->
                        val ch = minion.getLocation().chunk
                        if (ch.x == chunk.x && ch.z == chunk.z && ch.world == chunk.world) {
                            minion.remove()
                            Bukkit.getPlayer(minion.getOwnerUUID())?.inventory?.addItem(minion.getAsItem())
                        }
                    }
                }
            } catch (_: NullPointerException) {
                // SuperiorSkyBlock api does it this way aswell
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onIslandKickEvent(event: IslandKickEvent) {
        val kicked = event.target.uniqueId
        val kickedPlayer = Bukkit.getPlayer(kicked)
        val minions = Minions.getMinions()

        Dimension.values().forEach { entry ->
            try {
                event.island.getAllChunksAsync(entry, ssbChunkFlags) { chunk ->
                    minions.forEach { minion ->
                        val ch = minion.getLocation().chunk
                        if (minion.getOwnerUUID() == kicked && ch.x == chunk.x && ch.z == chunk.z && ch.world == chunk.world) {
                            minion.remove()
                            kickedPlayer?.inventory?.addItem(minion.getAsItem())
                        }
                    }
                }
            } catch (_: NullPointerException) {
                // SuperiorSkyBlock api does it this way aswell
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPreDisband(event: IslandDisbandEvent) {
        if (!Config.BLOCK_ISLAND_DISBAND_WITH_MINIONS()) return
        val player = event.player?.asPlayer()
        if (player != null && player.hasPermission("axminions.bypass.disband")) return
        val islandUUID = event.island.uniqueId.toString()
        val islandIntegration = com.artillexstudios.axminions.AxMinionsPlugin.integrations.getIslandIntegration()
        val hasMinions = Minions.getMinions().any { minion ->
            val minionIslandUUID = islandIntegration?.getIslandAt(minion.getLocation()) ?: ""
            minionIslandUUID == islandUUID
        }
        if (hasMinions) {
            event.isCancelled = true
            player?.sendMessage(StringUtils.formatToString(Messages.PREFIX() + Messages.ISLAND_DISBAND_BLOCKED()))
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPreKick(event: IslandKickEvent) {
        if (!Config.BLOCK_ISLAND_DISBAND_WITH_MINIONS()) return
        val islandUUID = event.island.uniqueId.toString()
        val kickedUuid = event.target.uniqueId
        val islandIntegration = com.artillexstudios.axminions.AxMinionsPlugin.integrations.getIslandIntegration()
        val hasPlayerMinions = Minions.getMinions().any { minion ->
            if (minion.getOwnerUUID() != kickedUuid) return@any false
            val minionIslandUUID = islandIntegration?.getIslandAt(minion.getLocation()) ?: ""
            minionIslandUUID == islandUUID
        }
        if (hasPlayerMinions) {
            event.isCancelled = true
            event.player?.asPlayer()?.sendMessage(StringUtils.formatToString(Messages.PREFIX() + Messages.ISLAND_KICK_BLOCKED()))
        }
    }

}