package com.artillexstudios.axminions.listeners

import com.artillexstudios.axapi.scheduler.Scheduler
import com.artillexstudios.axminions.api.AxMinionsAPI
import com.artillexstudios.axminions.minions.Minion
import com.ticxo.modelengine.api.events.BaseEntityInteractEvent
import com.ticxo.modelengine.api.events.ModelRegistrationEvent
import com.ticxo.modelengine.api.generator.ModelGenerator
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot

/**
 * Only registered when ModelEngine is installed, as its events are not there otherwise.
 */
class ModelEngineListener : Listener {
    // When a player clicked on a model last. ModelEngine can tell about a single click more than once,
    // and nobody clicks twice in this time, so the later ones are skipped
    private val lastClick = ConcurrentHashMap<UUID, Long>()

    // A click on the model of a minion does the same as a click on the minion itself. This can be called from another thread than the main one
    @EventHandler
    fun onBaseEntityInteractEvent(event: BaseEntityInteractEvent) {
        val minion = event.baseEntity.original as? Minion ?: return
        if (event.slot != EquipmentSlot.HAND) return

        val now = System.currentTimeMillis()
        val previous = lastClick.put(event.player.uniqueId, now)
        if (previous != null && now - previous < CLICK_GAP) return

        minion.interact(event.player, event.action == BaseEntityInteractEvent.Action.ATTACK)
    }

    @EventHandler
    fun onPlayerQuitEvent(event: PlayerQuitEvent) {
        lastClick.remove(event.player.uniqueId)
    }

    // The models of the minions that were placed before the models were loaded are not there yet, and the ones that
    // changed (or all, after ModelEngine loaded them again) have to be made again
    @EventHandler
    fun onModelRegistrationEvent(event: ModelRegistrationEvent) {
        if (event.phase != ModelGenerator.Phase.FINISHED) return

        for (minion in AxMinionsAPI.INSTANCE.getMinions()) {
            if (minion !is Minion) continue

            Scheduler.get().executeAt(minion.getLocation()) {
                minion.updateModel(true)
            }
        }
    }

    private companion object {
        const val CLICK_GAP = 100L
    }
}
