package com.artillexstudios.axminions.integrations.block

import dev.lone.itemsadder.api.CustomBlock
import org.bukkit.block.Block
import org.bukkit.inventory.ItemStack

internal class ItemsAdderBlock private constructor(private val block: CustomBlock) : CustomBlockHandle {

    override fun getLoot(tool: ItemStack?): List<ItemStack> {
        return block.getLoot(tool, false)
    }

    override fun remove() {
        block.remove()
    }

    companion object {
        fun of(block: Block): ItemsAdderBlock? {
            return CustomBlock.byAlreadyPlaced(block)?.let { ItemsAdderBlock(it) }
        }
    }
}
