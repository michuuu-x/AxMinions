package com.artillexstudios.axminions.api.data

import com.artillexstudios.axminions.api.minions.Minion
import com.artillexstudios.axminions.api.minions.miniontype.MinionType
import org.bukkit.Location
import org.bukkit.World
import java.util.UUID

interface DataHandler {

    fun getType(): String

    fun setup()

    fun insertType(minionType: MinionType)

    fun loadMinionsForWorld(minionType: MinionType, world: World)

    fun getLocationID(location: Location): Int

    fun getLocation(locationId: Int): Location?

    fun getWorld(worldId: Int): World?

    fun saveMinion(minion: Minion)

    fun deleteMinion(minion: Minion)

    fun getMinionAmount(uuid: UUID): Int

    /**
     * Returns the amount of minions the player has of the type with the given name.
     */
    fun getMinionAmount(uuid: UUID, type: String): Int

    /**
     * Returns the amount of minions the player has by the name of their type, the types the player has none of are not in the map.
     */
    fun getMinionAmounts(uuid: UUID): Map<String, Int>

    fun isMinion(location: Location): Boolean

    fun islandPlace(island: String)

    fun islandBreak(island: String)

    fun islandReset(island: String)

    fun getIsland(island: String): Int

    fun addUser(uuid: UUID, name: String)

    fun addExtraSlot(user: UUID, amount: Int)

    fun getExtraSlots(user: UUID): Int

    /**
     * Returns every owner with the players they trust to use their minions.
     */
    fun loadTrustedUsers(): Map<UUID, Set<UUID>>

    fun addTrustedUser(owner: UUID, trusted: UUID)

    fun removeTrustedUser(owner: UUID, trusted: UUID)

    fun disable()
}