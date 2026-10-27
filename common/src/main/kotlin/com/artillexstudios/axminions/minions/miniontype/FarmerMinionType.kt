package com.artillexstudios.axminions.minions.miniontype

import com.artillexstudios.axminions.AxMinionsPlugin
import com.artillexstudios.axminions.api.events.PreFarmerMinionHarvestEvent
import com.artillexstudios.axminions.api.minions.Minion
import com.artillexstudios.axminions.api.minions.miniontype.MinionType
import com.artillexstudios.axminions.api.utils.LocationUtils
import com.artillexstudios.axminions.api.utils.MinionUtils
import com.artillexstudios.axminions.api.utils.fastFor
import com.artillexstudios.axminions.api.warnings.Warnings
import com.artillexstudios.axminions.minions.MinionTicker
import com.artillexstudios.axminions.utils.Enchantments
import com.artillexstudios.axminions.utils.MaterialList
import dev.lone.itemsadder.api.CustomBlock
import org.bukkit.Bukkit
import org.bukkit.Location
import kotlin.math.roundToInt
import org.bukkit.Material
import org.bukkit.block.data.Ageable
import org.bukkit.inventory.ItemStack

class FarmerMinionType : MinionType("farmer", AxMinionsPlugin.INSTANCE.getResource("minions/farmer.yml")!!, true) {
    // The crops can be written as the item (carrot) or as the block (carrots)
    private val crops = MaterialList(
        "crops",
        mapOf(
            "CARROT" to "CARROTS",
            "POTATO" to "POTATOES",
            "BEETROOT" to "BEETROOTS",
            "COCOA_BEANS" to "COCOA",
            "SWEET_BERRIES" to "SWEET_BERRY_BUSH",
            "PITCHER_POD" to "PITCHER_CROP"
        )
    )

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

        if (minion.areContainersFull()) {
            Warnings.CONTAINER_FULL.display(minion)
            return
        }

        if (!minion.canUseTool()) {
            Warnings.NO_TOOL.display(minion)
            return
        }

        Warnings.remove(minion, Warnings.NO_TOOL)

        var size = 0
        val drops = arrayListOf<ItemStack>()
        val blocks = when (getConfig().getString("mode")) {
            "face" -> {
                LocationUtils.getAllBlocksFacing(minion.getLocation(), minion.getRange(), minion.getDirection().facing)
            }
            "line" -> {
                val list = arrayListOf<Location>()
                faces.fastFor {
                    list.addAll(LocationUtils.getAllBlocksFacing(minion.getLocation(), minion.getRange(), minion.getDirection().facing))
                }
                list
            }
            else -> LocationUtils.getAllBlocksInRadius(minion.getLocation(), minion.getRange(), false)
        }

        // If the list is empty, every crop is harvested
        val allowed = crops.get(this)

        blocks.fastFor { location ->
            val block = location.block

            if (AxMinionsPlugin.integrations.itemsAdderIntegration) {
                val customBlock = CustomBlock.byAlreadyPlaced(block)
                if (customBlock !== null) {
                    // The custom blocks can't be named in the list, so they are left alone when there is one
                    if (allowed.isNotEmpty()) return@fastFor
                    val preHarvestEvent = PreFarmerMinionHarvestEvent(minion, block)
                    Bukkit.getPluginManager().callEvent(preHarvestEvent)
                    if (preHarvestEvent.isCancelled) return@fastFor
                    val blockDrops = customBlock.getLoot(minion.getTool(), false)
                    size += blockDrops.size
                    drops.addAll(blockDrops)
                    customBlock.remove()
                    return@fastFor
                }
            }

            if (allowed.isNotEmpty() && block.type !in allowed) return@fastFor

            when (block.type) {
                Material.CACTUS, Material.SUGAR_CANE, Material.BAMBOO -> {
                    val preHarvestEvent = PreFarmerMinionHarvestEvent(minion, block)
                    Bukkit.getPluginManager().callEvent(preHarvestEvent)
                    if (preHarvestEvent.isCancelled) return@fastFor
                    MinionUtils.getPlant(block).fastFor {
                        val blockDrops = it.getDrops(minion.getTool())
                        size++
                        drops.addAll(blockDrops)
                        it.type = Material.AIR
                    }
                }

                Material.MELON, Material.PUMPKIN, Material.TORCHFLOWER -> {
                    val preHarvestEvent = PreFarmerMinionHarvestEvent(minion, block)
                    Bukkit.getPluginManager().callEvent(preHarvestEvent)
                    if (preHarvestEvent.isCancelled) return@fastFor
                    val blockDrops = block.getDrops(minion.getTool())
                    size++
                    drops.addAll(blockDrops)
                    block.type = Material.AIR
                }

                Material.COCOA_BEANS, Material.COCOA, Material.NETHER_WART, Material.WHEAT, Material.CARROTS, Material.BEETROOTS, Material.POTATOES, Material.PITCHER_CROP -> {
                    val ageable = block.blockData as Ageable
                    if (ageable.age != ageable.maximumAge) return@fastFor
                    val preHarvestEvent = PreFarmerMinionHarvestEvent(minion, block)
                    Bukkit.getPluginManager().callEvent(preHarvestEvent)
                    if (preHarvestEvent.isCancelled) return@fastFor
                    val blockDrops = block.getDrops(minion.getTool())
                    size++
                    drops.addAll(blockDrops)
                    ageable.age = 0
                    block.blockData = ageable
                }

                Material.SWEET_BERRY_BUSH -> {
                    val ageable = block.blockData as Ageable
                    if (ageable.age != ageable.maximumAge) return@fastFor
                    val preHarvestEvent = PreFarmerMinionHarvestEvent(minion, block)
                    Bukkit.getPluginManager().callEvent(preHarvestEvent)
                    if (preHarvestEvent.isCancelled) return@fastFor
                    val blockDrops = block.getDrops(minion.getTool())
                    size++
                    drops.addAll(blockDrops)
                    ageable.age = 1
                    block.blockData = ageable
                }

                else -> return@fastFor
            }
        }

        minion.addToContainerOrDrop(drops)
        minion.damageTool(size)
        minion.setActions(minion.getActionAmount() + size)
    }
}
