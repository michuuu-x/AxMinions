package com.artillexstudios.axminions.data

import org.bukkit.Bukkit
import org.bukkit.Location

/**
 * The containers of a minion after the first one are saved in one column, as "x;y;z;world" separated by "|",
 * the first one has its own column with a location id like it always had.
 */
object ChestLocations {

    /**
     * Returns null when there is nothing to save.
     */
    fun serialize(locations: List<Location>): String? {
        val entries = locations.mapNotNull { location ->
            val world = location.world?.name ?: return@mapNotNull null
            "${location.blockX};${location.blockY};${location.blockZ};$world"
        }

        return if (entries.isEmpty()) null else entries.joinToString("|")
    }

    /**
     * The entries that are damaged or in a world that isn't loaded are skipped.
     */
    fun deserialize(value: String?): List<Location> {
        if (value.isNullOrBlank()) return emptyList()

        val locations = ArrayList<Location>()
        for (entry in value.split('|')) {
            // The world is last, so the name of it may have a ';' in it
            val parts = entry.split(';', limit = 4)
            if (parts.size != 4) continue

            val x = parts[0].toIntOrNull() ?: continue
            val y = parts[1].toIntOrNull() ?: continue
            val z = parts[2].toIntOrNull() ?: continue
            val world = Bukkit.getWorld(parts[3]) ?: continue
            locations.add(Location(world, x.toDouble(), y.toDouble(), z.toDouble()))
        }

        return locations
    }
}
