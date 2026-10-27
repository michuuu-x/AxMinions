package com.artillexstudios.axminions.minions.miniontype

import com.artillexstudios.axminions.AxMinionsPlugin
import com.artillexstudios.axminions.api.minions.Minion
import com.artillexstudios.axminions.api.minions.miniontype.MinionType
import com.artillexstudios.axminions.api.utils.fastFor
import com.artillexstudios.axminions.api.warnings.Warnings
import com.artillexstudios.axminions.minions.MinionTicker
import com.artillexstudios.axminions.nms.NMSHandler
import kotlin.math.roundToInt
import org.bukkit.entity.Ageable
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Piglin
import org.bukkit.entity.Player
import org.bukkit.entity.Tameable
import org.bukkit.entity.Zoglin
import org.bukkit.entity.Zombie

class SlayerMinionType : MinionType("slayer", AxMinionsPlugin.INSTANCE.getResource("minions/slayer.yml")!!) {

    override fun shouldRun(minion: Minion): Boolean {
        return MinionTicker.getTick() % minion.getNextAction() == 0L
    }

    override fun onToolDirty(minion: Minion) {
        val minionImpl = minion as com.artillexstudios.axminions.minions.Minion
        minionImpl.setRange(getDouble("range", minion.getLevel()))
        val tool = com.artillexstudios.axminions.utils.Enchantments.EFFICIENCY
            ?.let { minion.getTool()?.getEnchantmentLevel(it)?.div(10.0) } ?: 0.1
        val efficiency = 1.0 - if (tool > 0.9) 0.9 else tool
        minionImpl.setNextAction((getLong("speed", minion.getLevel()) * efficiency).roundToInt())
    }

    override fun run(minion: Minion) {
        if (minion.hasContainerSpace()) {
            Warnings.remove(minion, Warnings.CONTAINER_FULL)
        }

        minion.validateLinkedChests()

        if (!minion.canUseTool()) {
            Warnings.NO_TOOL.display(minion)
            return
        }

        if (minion.areContainersFull()) {
            Warnings.CONTAINER_FULL.display(minion)
            return
        }

        Warnings.remove(minion, Warnings.NO_TOOL)

        minion.getLocation().world!!.getNearbyEntities(
            minion.getLocation(),
            minion.getRange(),
            minion.getRange(),
            minion.getRange()
        ).filterIsInstance<LivingEntity>().fastFor {
            if (it is Player) return@fastFor

            if (!getConfig().getBoolean("damage-animals") && NMSHandler.get().isAnimal(it)) {
                return@fastFor
            }

            if (!getConfig().getBoolean("damage-renamed") && it.customName != null) {
                return@fastFor
            }

            if (!getConfig().getBoolean("damage-tamed") && it is Tameable && it.isTamed) {
                return@fastFor
            }

            if (!getConfig().getBoolean("damage-baby", true) && isBaby(it)) {
                return@fastFor
            }

            NMSHandler.get().attack(minion, it)
            minion.damageTool()
        }
    }

    // The hostile mobs that can be babies don't all share an interface with the animals
    private fun isBaby(entity: LivingEntity): Boolean {
        return when (entity) {
            is Ageable -> !entity.isAdult
            is Zombie -> entity.isBaby
            is Piglin -> entity.isBaby
            is Zoglin -> entity.isBaby
            else -> false
        }
    }
}