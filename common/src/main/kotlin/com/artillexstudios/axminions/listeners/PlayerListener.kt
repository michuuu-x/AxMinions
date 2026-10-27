package com.artillexstudios.axminions.listeners

import com.artillexstudios.axminions.api.warnings.OwnerAlerts
import com.artillexstudios.axminions.minions.Minions
import com.artillexstudios.axminions.utils.PlayerStats
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class PlayerListener : Listener {

    @EventHandler
    fun onPlayerJoinEvent(event: PlayerJoinEvent) {
        // Loads the amounts for the placeholders in the background, so they are there when the first one is asked
        PlayerStats.get(event.player.uniqueId)
        OwnerAlerts.load(event.player)

        Minions.getMinions().forEach {
            if (it.getOwnerUUID() == event.player.uniqueId) {
                it.setOwnerOnline(true)
            }
        }
    }

    @EventHandler
    fun onPlayerJoinEvent(event: PlayerQuitEvent) {
        PlayerStats.forget(event.player.uniqueId)
        OwnerAlerts.unload(event.player.uniqueId)

        Minions.getMinions().forEach {
            if (it.getOwnerUUID() == event.player.uniqueId) {
                it.setOwnerOnline(false)
            }
        }
    }
}