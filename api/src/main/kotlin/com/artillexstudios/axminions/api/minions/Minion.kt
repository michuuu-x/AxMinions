package com.artillexstudios.axminions.api.minions

import com.artillexstudios.axapi.hologram.Hologram
import com.artillexstudios.axapi.packetentity.PacketEntity
import com.artillexstudios.axminions.api.minions.miniontype.MinionType
import com.artillexstudios.axminions.api.warnings.Warning
import java.util.UUID
import org.bukkit.Location
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

interface Minion : InventoryHolder {

    fun getType(): MinionType

    fun spawn()

    fun tick()

    fun getLocation(): Location

    fun updateInventories()

    fun openInventory(player: Player)

    fun getAsItem(): ItemStack

    fun getLevel(): Int

    fun setActions(actions: Long)

    fun setStorage(storage: Double)

    fun setWarning(warning: Warning?)

    fun getWarning(): Warning?

    fun setWarningHologram(hologram: Hologram?)

    fun getWarningHologram(): Hologram?

    fun getOwner(): OfflinePlayer?

    fun getOwnerUUID(): UUID

    /**
     * Whether the player can do everything with the minion, which is not the case for a player who only got
     * access to the menu because the owner trusts them.
     */
    fun hasFullAccess(player: Player): Boolean

    fun setTool(tool: ItemStack, save: Boolean = true)

    fun getTool(): ItemStack?

    fun getEntity(): PacketEntity

    fun setLevel(level: Int)

    fun getData(key: String): String?

    fun hasData(key: String): Boolean

    fun getNextAction(): Int

    fun getActionAmount(): Long

    fun getStorage(): Double

    fun getRange(): Double

    fun resetAnimation()

    fun animate()

    /**
     * Links the minion to this one container only, any other container it was linked to is unlinked.
     * Use [addLinkedChest] to keep the others.
     */
    fun setLinkedChest(location: Location?)

    /**
     * The first container the minion is linked to, which is the one that gets filled first.
     */
    fun getLinkedChest(): Location?

    /**
     * Every container the minion is linked to, in the order they get filled.
     */
    fun getLinkedChests(): List<Location>

    /**
     * The inventories of the linked containers that are loaded, in the order they get filled.
     */
    fun getLinkedInventories(): List<Inventory>

    /**
     * How many containers the minion can be linked to at its level, 'containers' in the upgrades of its type.
     */
    fun getMaxLinkedChests(): Int

    /**
     * Whether the minion is linked to the container at the location.
     */
    fun isLinkedTo(location: Location): Boolean

    /**
     * Links one more container, which gets filled when the ones before it are full.
     * Returns false if the minion can't be linked to more containers or is linked to this one already.
     */
    fun addLinkedChest(location: Location): Boolean

    /**
     * Unlinks the container at the location, returns false if the minion wasn't linked to it.
     */
    fun removeLinkedChest(location: Location): Boolean

    /**
     * Whether at least one of the linked containers has room left, false if there is none.
     */
    fun hasContainerSpace(): Boolean

    /**
     * Whether the minion is linked and every one of its containers is full.
     */
    fun areContainersFull(): Boolean

    /**
     * Unlinks the containers that aren't containers anymore, and loads the inventories again of the chests that
     * became a double chest or stopped being one. To be called by the minion types before they use the containers.
     */
    fun validateLinkedChests()

    fun setDirection(direction: Direction, save: Boolean = true)

    fun getDirection(): Direction

    fun remove()

    /**
     * The inventory of the first linked container, see [getLinkedInventories] for all of them.
     */
    fun getLinkedInventory(): Inventory?

    /**
     * Puts the item into the linked containers, one after the other when the ones before are full.
     * What doesn't fit anywhere is dropped at the minion.
     */
    fun addToContainerOrDrop(itemStack: ItemStack)

    /**
     * Puts the item into the linked containers, one after the other when the ones before are full.
     * Returns what didn't fit anywhere, or null if the minion has no loaded container.
     */
    fun addWithRemaining(itemStack: ItemStack): HashMap<Int, ItemStack>?

    fun addToContainerOrDrop(itemStack: Iterable<ItemStack>)

    fun updateArmour()

    fun getLocationId(): Int

    fun getChestLocationId(): Int

    fun removeOpenInventory(inventory: Inventory)

    fun isTicking(): Boolean

    fun setTicking(ticking: Boolean)

    fun setRange(range: Double)

    fun setNextAction(nextAction: Int)

    fun markDirty()

    fun damageTool(amount: Int = 1)

    fun canUseTool(): Boolean

    /**
     * Whether the minion is allowed to work. A stopped minion (Start/Stop button in its menu) does nothing,
     * and keeps being stopped after a restart.
     */
    fun isWorking(): Boolean

    fun setWorking(working: Boolean)

    fun isOwnerOnline(): Boolean

    fun setOwnerOnline(online: Boolean)

    fun getCharge(): Long

    fun setCharge(charge: Long)
}