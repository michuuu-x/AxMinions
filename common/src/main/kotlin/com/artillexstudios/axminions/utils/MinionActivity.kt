package com.artillexstudios.axminions.utils

import com.artillexstudios.axminions.api.AxMinionsAPI
import com.artillexstudios.axminions.api.config.Config
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * What the placeholders say about the minions of a player that work, and how long their charge lasts. The placeholders
 * can be asked every tick, and finding the minions of a player goes through all the minions of the server,
 * so the numbers are counted once a second at most for every player.
 */
object MinionActivity {
    /**
     * [active] is how many minions work right now, which are the ones the status of /minions list shows as Active.
     * [lowestCharge] is the least charge in milliseconds of the minions that use it up, null if no minion does
     * (charge is turned off, or all the minions are stopped).
     */
    class Activity(val active: Int, val lowestCharge: Long?)

    private class Entry(val activity: Activity, val countedAt: Long)

    private const val MAX_AGE = 1_000L

    private val entries = ConcurrentHashMap<UUID, Entry>()

    fun get(uuid: UUID): Activity {
        val now = System.currentTimeMillis()
        val entry = entries[uuid]
        if (entry != null && now - entry.countedAt <= MAX_AGE) {
            return entry.activity
        }

        val activity = count(uuid)
        entries[uuid] = Entry(activity, now)
        return activity
    }

    fun forget(uuid: UUID) {
        entries.remove(uuid)
    }

    private fun count(uuid: UUID): Activity {
        val chargeEnabled = Config.CHARGE_ENABLED()
        var active = 0
        var lowestCharge: Long? = null

        for (minion in AxMinionsAPI.INSTANCE.getMinions()) {
            if (minion.getOwnerUUID() != uuid || !minion.getType().isEnabled() || !minion.isWorking()) continue

            // A stopped minion keeps its charge, so only the ones that work use it up. One that ran out of it counts as 0
            val left = if (chargeEnabled) minion.getChargeLeft().coerceAtLeast(0L) else null
            if (left != null && (lowestCharge == null || left < lowestCharge)) {
                lowestCharge = left
            }

            if ((left == null || left > 0L) && minion.getWarning() == null && minion.isTicking()) {
                active++
            }
        }

        return Activity(active, lowestCharge)
    }
}
