package com.artillexstudios.axminions.listeners

import com.artillexstudios.axapi.scheduler.Scheduler
import com.artillexstudios.axminions.api.AxMinionsAPI
import com.nexomc.nexo.api.events.NexoItemsLoadedEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

/**
 * Only registered when Nexo is installed, as its events are not there otherwise.
 */
class NexoListener : Listener {

    // The minions that were placed before the items were loaded wear what the item of Nexo is not yet, and the ones
    // whose items changed (after /nexo reload) have to wear the new ones
    @EventHandler
    fun onNexoItemsLoadedEvent(event: NexoItemsLoadedEvent) {
        for (minion in AxMinionsAPI.INSTANCE.getMinions()) {
            Scheduler.get().executeAt(minion.getLocation()) {
                minion.updateArmour()
            }
        }
    }
}
