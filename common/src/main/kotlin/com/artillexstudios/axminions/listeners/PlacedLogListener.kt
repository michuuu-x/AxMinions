package com.artillexstudios.axminions.listeners

import com.artillexstudios.axminions.api.utils.PlacedLogTracker
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent

class PlacedLogListener : Listener {

    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        val type: Material = event.blockPlaced.type
        val name = type.name
        if (name.endsWith("_LOG") || name.endsWith("_WOOD")) {
            PlacedLogTracker.markPlaced(event.blockPlaced.location)
        }
    }

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        val type: Material = event.block.type
        val name = type.name
        if (name.endsWith("_LOG") || name.endsWith("_WOOD")) {
            PlacedLogTracker.unmark(event.block.location)
        }
    }
}
