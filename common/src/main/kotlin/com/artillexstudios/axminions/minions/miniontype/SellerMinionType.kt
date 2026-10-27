package com.artillexstudios.axminions.minions.miniontype

import com.artillexstudios.axminions.AxMinionsPlugin
import com.artillexstudios.axminions.api.minions.Minion
import com.artillexstudios.axminions.api.minions.miniontype.MinionType
import com.artillexstudios.axminions.api.warnings.Warnings
import com.artillexstudios.axminions.minions.MinionTicker
import com.artillexstudios.axminions.utils.Enchantments
import com.artillexstudios.axminions.utils.MaterialList
import kotlin.math.roundToInt
import org.bukkit.Material
import org.bukkit.block.Container
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta

class SellerMinionType : MinionType("seller", AxMinionsPlugin.INSTANCE.getResource("minions/seller.yml")!!) {
    private val neverSell = MaterialList("never-sell")

    override fun shouldRun(minion: Minion): Boolean {
        return MinionTicker.getTick() % minion.getNextAction() == 0L
    }

    override fun onToolDirty(minion: Minion) {
        val minionImpl = minion as com.artillexstudios.axminions.minions.Minion
        minionImpl.setRange(getDouble("range", minion.getLevel()))
        val tool = Enchantments.EFFICIENCY?.let { minion.getTool()?.getEnchantmentLevel(it)?.div(10.0) } ?: 0.1
        val efficiency = 1.0 - if (tool > 0.9) 0.9 else tool
        minionImpl.setNextAction((getLong("speed", minion.getLevel()) * efficiency).roundToInt())
    }

    override fun run(minion: Minion) {
        if (minion.hasContainerSpace()) {
            Warnings.remove(minion, Warnings.CONTAINER_FULL)
        }

        minion.validateLinkedChests()
        val inventories = minion.getLinkedInventories()
        if (inventories.isEmpty()) {
            Warnings.NO_CONTAINER.display(minion)
            return
        }

        Warnings.remove(minion, Warnings.NO_CONTAINER)

        if (!minion.canUseTool()) {
            Warnings.NO_TOOL.display(minion)
            return
        }

        Warnings.remove(minion, Warnings.NO_TOOL)

        if (AxMinionsPlugin.integrations.getPricesIntegration() === null) {
            return
        }

        val kept = neverSell.get(this)

        // Every linked container is sold from
        for (inventory in inventories) {
            for (it in inventory.contents) {
                if (it == null || it.type == Material.AIR) {
                    continue
                }

                // Left alone completely, also when it is a container
                if (it.type in kept) {
                    continue
                }

                if (sellContainerContents(minion, it, kept)) {
                    continue
                }

                if (sellItemStack(minion, it)) {
                    it.amount = 0
                }
            }
        }
    }

    // Sells the contents of an item that holds an inventory (shulker box etc.), returns true if the item is a container
    private fun sellContainerContents(minion: Minion, it: ItemStack, kept: Set<Material>): Boolean {
        if (!it.hasItemMeta()) {
            return false
        }

        val itemMeta = it.itemMeta
        if (itemMeta !is BlockStateMeta) {
            return false
        }

        val blockState = itemMeta.blockState
        if (blockState !is Container) {
            return false
        }

        val inventory = blockState.inventory
        val stacks = arrayOfNulls<ItemStack>(inventory.size)
        var changed = false
        for ((slot, stack) in inventory.contents.withIndex()) {
            if (stack == null || stack.type == Material.AIR) {
                continue
            }

            if (stack.type in kept) {
                stacks[slot] = stack
                continue
            }

            if (sellItemStack(minion, stack)) {
                changed = true
            } else {
                stacks[slot] = stack
            }
        }

        if (changed) {
            inventory.contents = stacks
            itemMeta.blockState = blockState
            it.itemMeta = itemMeta
        }

        return true
    }

    // Returns if the item should be removed
    private fun sellItemStack(minion: Minion, it: ItemStack): Boolean {
        var price = AxMinionsPlugin.integrations.getPricesIntegration()!!.getPrice(it)

        if (price <= 0) {
            return getConfig().get("delete-unsellable")
        }

        price *= getDouble("multiplier", minion.getLevel())

        if (minion.getStorage() + price > getDouble("storage", minion.getLevel())) {
            return false
        }

        minion.setActions(minion.getActionAmount() + it.amount)
        minion.damageTool()
        minion.setStorage(minion.getStorage() + price)
        return true
    }
}