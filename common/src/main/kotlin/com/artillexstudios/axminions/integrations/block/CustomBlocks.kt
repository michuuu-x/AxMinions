package com.artillexstudios.axminions.integrations.block

import com.artillexstudios.axminions.AxMinionsPlugin
import org.bukkit.block.Block
import org.bukkit.inventory.ItemStack

/**
 * A block that comes from a plugin that adds its own blocks (ItemsAdder, Nexo), which Bukkit sees as some other block.
 */
interface CustomBlockHandle {

    /**
     * What the block gives when it is broken with the tool, without dropping it in the world.
     */
    fun getLoot(tool: ItemStack?): List<ItemStack>

    /**
     * Removes the block, without dropping anything. Take the loot with [getLoot] first.
     */
    fun remove()
}

object CustomBlocks {

    /**
     * The custom block the block is, null if it is not one or the plugin that adds it is not installed.
     */
    fun get(block: Block): CustomBlockHandle? {
        // The classes of the plugins are only touched when the plugin is there
        if (AxMinionsPlugin.integrations.itemsAdderIntegration) {
            ItemsAdderBlock.of(block)?.let { return it }
        }

        if (AxMinionsPlugin.integrations.nexoIntegration) {
            NexoBlock.of(block)?.let { return it }
        }

        return null
    }
}
