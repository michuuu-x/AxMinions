package com.artillexstudios.axminions.minions.miniontype

import com.artillexstudios.axminions.AxMinionsPlugin
import com.artillexstudios.axminions.api.AxMinionsAPI
import com.artillexstudios.axminions.api.minions.Minion
import com.artillexstudios.axminions.api.minions.miniontype.MinionType
import com.artillexstudios.axminions.api.utils.fastFor
import com.artillexstudios.axminions.api.warnings.Warnings
import com.artillexstudios.axminions.minions.MinionTicker
import com.artillexstudios.axminions.utils.Enchantments
import com.artillexstudios.axminions.utils.MaterialList
import kotlin.math.roundToInt
import org.bukkit.entity.Item

class CollectorMinionType : MinionType("collector", AxMinionsPlugin.INSTANCE.getResource("minions/collector.yml")!!) {
    private val whitelist = MaterialList("whitelist")

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
        if (minion.getLinkedInventories().isEmpty()) {
            Warnings.NO_CONTAINER.display(minion)
            return
        }

        Warnings.remove(minion, Warnings.NO_CONTAINER)

        if (!minion.canUseTool()) {
            Warnings.NO_TOOL.display(minion)
            return
        }

        Warnings.remove(minion, Warnings.NO_TOOL)

        val entities = minion.getLocation().world?.getNearbyEntities(
            minion.getLocation(),
            minion.getRange(),
            minion.getRange(),
            minion.getRange()
        )

        // An empty whitelist picks up every item
        val allowed = whitelist.get(this)

        entities?.filterIsInstance<Item>()?.fastFor { item ->
            if (allowed.isNotEmpty() && item.itemStack.type !in allowed) {
                return@fastFor
            }

            if (minion.areContainersFull()) {
                Warnings.CONTAINER_FULL.display(minion)
                return
            }

            val amount = AxMinionsPlugin.integrations.getStackerIntegration().getStackSize(item)
            val stack = item.itemStack.clone()
            stack.amount = amount.toInt()


            val map = minion.addWithRemaining(stack) ?: return@fastFor
            if (map.isEmpty() || stack.amount <= 0) {
                item.remove()
            } else {
                AxMinionsAPI.INSTANCE.getIntegrations().getStackerIntegration().setStackSize(item, stack.amount)
            }

            minion.setActions(minion.getActionAmount() + amount)
            minion.damageTool()
        }
    }
}