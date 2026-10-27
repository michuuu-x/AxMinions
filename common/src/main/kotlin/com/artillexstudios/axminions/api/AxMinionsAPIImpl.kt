package com.artillexstudios.axminions.api

import com.artillexstudios.axapi.AxPlugin
import com.artillexstudios.axminions.AxMinionsPlugin
import com.artillexstudios.axminions.api.config.Config
import com.artillexstudios.axminions.api.config.Messages
import com.artillexstudios.axminions.api.data.DataHandler
import com.artillexstudios.axminions.api.integrations.Integrations
import com.artillexstudios.axminions.api.minions.Minion
import com.artillexstudios.axminions.api.minions.miniontype.MinionType
import com.artillexstudios.axminions.minions.MinionTicker
import com.artillexstudios.axminions.minions.Minions
import com.artillexstudios.axminions.utils.Bypass
import org.bukkit.entity.Player
import java.io.File
import java.util.Locale

class AxMinionsAPIImpl(private val plugin: AxMinionsPlugin) : AxMinionsAPI {
    companion object {
        private const val LIMIT_PERMISSION = "axminions.limit."

        /**
         * The minion limit from the default one and the permissions of the player, Int.MAX_VALUE means no limit.
         * The extra slots are not in it, those come from the database.
         */
        fun permissionLimit(player: Player): Int {
            if (Bypass.limit(player)) {
                return Int.MAX_VALUE
            }

            var limit = Config.DEFAULT_MINION_LIMIT()

            for (info in player.effectivePermissions) {
                // A permission that is taken away (-axminions.limit.20) must not count
                if (!info.value) continue

                val permission = info.permission
                if (permission.equals("*", true)) {
                    return Int.MAX_VALUE
                }

                if (!permission.startsWith(LIMIT_PERMISSION)) continue
                // axminions.limit.<type>.<amount> only limits one type, that is for getTypeLimit
                if (permission.indexOf('.', LIMIT_PERMISSION.length) != -1) continue
                if (permission.contains("*")) {
                    return Int.MAX_VALUE
                }

                val value = permission.substring(LIMIT_PERMISSION.length).toIntOrNull() ?: continue
                if (value > limit) {
                    limit = value
                }
            }

            return limit
        }
    }

    override fun getAxMinionsDataFolder(): File {
        return plugin.dataFolder
    }

    override fun getMessages(): Messages {
        return AxMinionsPlugin.messages
    }

    override fun getConfig(): Config {
        return AxMinionsPlugin.config
    }

    override fun getDataHandler(): DataHandler {
        return AxMinionsPlugin.dataHandler
    }

    override fun getMinions(): List<Minion> {
        return Minions.getMinions()
    }

    override fun getAxMinionsInstance(): AxPlugin {
        return plugin
    }

    override fun getMinionLimit(player: Player): Int {
        val limit = permissionLimit(player)
        if (limit == Int.MAX_VALUE) return limit

        return limit + AxMinionsPlugin.dataHandler.getExtraSlots(player.uniqueId)
    }

    override fun getTypeLimit(player: Player, type: String): Int? {
        if (Bypass.limit(player)) {
            return null
        }

        val prefix = "$LIMIT_PERMISSION${type.lowercase(Locale.ENGLISH)}."
        var limit: Int? = null

        for (info in player.effectivePermissions) {
            if (!info.value) continue

            val permission = info.permission
            if (!permission.startsWith(prefix)) continue

            val amount = permission.substring(prefix.length)
            if (amount == "*") return Int.MAX_VALUE

            val value = amount.toIntOrNull() ?: continue
            if (limit == null || value > limit) {
                limit = value
            }
        }

        return limit
    }

    override fun getIntegrations(): Integrations {
        return AxMinionsPlugin.integrations
    }

    override fun getTick(): Long {
        return MinionTicker.getTick()
    }
}