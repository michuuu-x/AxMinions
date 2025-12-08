package com.artillexstudios.axminions.api.utils

import org.bukkit.Location

object PlacedLogTracker {

    private val placedLogs = HashSet<String>()

    private fun key(location: Location): String {
        val world = location.world ?: return ""
        return world.uid.toString() + ":" + location.blockX + ":" + location.blockY + ":" + location.blockZ
    }

    fun markPlaced(location: Location) {
        val k = key(location)
        if (k.isNotEmpty()) {
            placedLogs.add(k)
        }
    }

    fun unmark(location: Location) {
        val k = key(location)
        if (k.isNotEmpty()) {
            placedLogs.remove(k)
        }
    }

    fun isPlayerPlaced(location: Location): Boolean {
        val k = key(location)
        if (k.isEmpty()) return false
        return placedLogs.contains(k)
    }
}
