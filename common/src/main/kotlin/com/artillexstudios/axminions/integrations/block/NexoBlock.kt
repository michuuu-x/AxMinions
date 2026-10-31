package com.artillexstudios.axminions.integrations.block

import com.nexomc.nexo.api.NexoBlocks
import com.nexomc.nexo.api.NexoItems
import com.nexomc.nexo.utils.drops.Drop
import org.bukkit.block.Block
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack

internal class NexoBlock private constructor(private val block: Block, private val drop: Drop?) : CustomBlockHandle {

    // The same as what Nexo drops when a player breaks the block, but it goes to the minion instead of the ground
    override fun getLoot(tool: ItemStack?): List<ItemStack> {
        val drop = drop ?: return emptyList()
        if (tool == null || !drop.canDrop(tool)) return emptyList()

        if (drop.isSilktouch && tool.containsEnchantment(Enchantment.SILK_TOUCH)) {
            return listOfNotNull(NexoItems.itemFromId(drop.sourceID)?.build())
        }

        val fortune = drop.fortuneMultiplier(tool)
        return drop.loots.filter { Math.random() <= it.probability }.map { it.getItem(fortune) }
    }

    override fun remove() {
        // No player and no drop, the loot is taken with getLoot
        NexoBlocks.remove(block.location)
    }

    companion object {
        fun of(block: Block): NexoBlock? {
            if (!NexoBlocks.isCustomBlock(block)) return null

            return NexoBlock(block, NexoBlocks.customBlockMechanic(block)?.breakable?.drop)
        }
    }
}
