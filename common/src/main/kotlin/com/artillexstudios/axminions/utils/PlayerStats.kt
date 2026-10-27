package com.artillexstudios.axminions.utils

import com.artillexstudios.axminions.AxMinionsPlugin
import com.artillexstudios.axminions.api.config.Config
import com.artillexstudios.axminions.api.integrations.types.IslandIntegration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.bukkit.Location
import org.bukkit.entity.Player

/**
 * The amounts from the database the placeholders show. The placeholders can be asked every tick, from the main thread,
 * so they must not wait for the database: the amounts are loaded on the database queue and reloaded when they get old
 * or when [invalidate] says they changed.
 */
object PlayerStats {
    /**
     * [types] has the amount of minions by the name of their type, the types the player has none of are not in it.
     */
    class Stats(val placed: Int, val extraSlots: Int, val types: Map<String, Int>)

    /**
     * The extra slots are the ones the island integration adds to the island-limit of the config.
     */
    class IslandStats(val placed: Int, val extraSlots: Int)

    private class Entry(val stats: Stats, val loadedAt: Long)

    private class IslandEntry(val stats: IslandStats, val loadedAt: Long)

    // The changes that don't call invalidate (the converter, other plugins using the API) are seen after this much time
    private const val MAX_AGE = 30_000L

    private val entries = ConcurrentHashMap<UUID, Entry>()
    private val loading = ConcurrentHashMap.newKeySet<UUID>()

    private val islands = ConcurrentHashMap<String, IslandEntry>()
    private val loadingIslands = ConcurrentHashMap.newKeySet<String>()

    /**
     * Returns the last loaded amounts, or null if they are not loaded yet. Starts a reload in the background if they are old.
     */
    fun get(uuid: UUID): Stats? {
        val entry = entries[uuid]
        if (entry == null || System.currentTimeMillis() - entry.loadedAt > MAX_AGE) {
            load(uuid)
        }

        return entry?.stats
    }

    /**
     * Returns the last loaded amounts of the island the player is on, or null if they are not loaded yet, the player
     * is not on an island, or the island limit is turned off. Starts a reload in the background if they are old.
     */
    fun getIsland(player: Player): IslandStats? {
        if (Config.ISLAND_LIMIT() <= 0) return null

        val integration = AxMinionsPlugin.integrations.getIslandIntegration() ?: return null
        val location = player.location
        val islandId = integration.getIslandAt(location)
        if (islandId.isBlank()) return null

        val entry = islands[islandId]
        if (entry == null || System.currentTimeMillis() - entry.loadedAt > MAX_AGE) {
            loadIsland(islandId, integration, location)
        }

        return entry?.stats
    }

    /**
     * Makes the next [get] reload the amounts, to be called after the minions or the extra slots of the player changed.
     * It has to run after the change was made on the database queue, so the reload sees it.
     */
    fun invalidate(uuid: UUID) {
        entries.computeIfPresent(uuid) { _, entry -> Entry(entry.stats, 0L) }
    }

    /**
     * Makes the next [getIsland] on the island reload the amounts, to be called after the minions of the island changed.
     * It has to run after the change was made on the database queue, so the reload sees it.
     */
    fun invalidateIsland(islandId: String) {
        islands.computeIfPresent(islandId) { _, entry -> IslandEntry(entry.stats, 0L) }
    }

    /**
     * The same as [invalidateIsland] for every island, as the extra slots of a player can be on any of them.
     */
    fun invalidateIslands() {
        islands.replaceAll { _, entry -> IslandEntry(entry.stats, 0L) }
    }

    fun forget(uuid: UUID) {
        entries.remove(uuid)
    }

    private fun load(uuid: UUID) {
        if (!loading.add(uuid)) return

        AxMinionsPlugin.dataQueue.submit {
            try {
                val stats = Stats(
                    AxMinionsPlugin.dataHandler.getMinionAmount(uuid),
                    AxMinionsPlugin.dataHandler.getExtraSlots(uuid),
                    AxMinionsPlugin.dataHandler.getMinionAmounts(uuid)
                )
                entries[uuid] = Entry(stats, System.currentTimeMillis())
            } catch (exception: Exception) {
                AxMinionsPlugin.INSTANCE.logger.warning("Could not load the amounts of $uuid for the placeholders: ${exception.message}")
            } finally {
                loading.remove(uuid)
            }
        }
    }

    private fun loadIsland(islandId: String, integration: IslandIntegration, location: Location) {
        if (!loadingIslands.add(islandId)) return

        AxMinionsPlugin.dataQueue.submit {
            try {
                val stats = IslandStats(
                    AxMinionsPlugin.dataHandler.getIsland(islandId),
                    integration.getExtra(location)
                )
                islands[islandId] = IslandEntry(stats, System.currentTimeMillis())
            } catch (exception: Exception) {
                AxMinionsPlugin.INSTANCE.logger.warning("Could not load the amounts of the island $islandId for the placeholders: ${exception.message}")
            } finally {
                loadingIslands.remove(islandId)
            }
        }
    }
}
