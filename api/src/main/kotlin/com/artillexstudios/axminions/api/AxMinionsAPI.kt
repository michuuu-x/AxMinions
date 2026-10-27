package com.artillexstudios.axminions.api

import com.artillexstudios.axapi.AxPlugin
import com.artillexstudios.axminions.api.config.Config
import com.artillexstudios.axminions.api.config.Messages
import com.artillexstudios.axminions.api.data.DataHandler
import com.artillexstudios.axminions.api.integrations.Integrations
import com.artillexstudios.axminions.api.minions.Minion
import org.bukkit.entity.Player
import java.io.File

interface AxMinionsAPI {

    fun getAxMinionsDataFolder(): File

    fun getMessages(): Messages

    fun getConfig(): Config

    fun getDataHandler(): DataHandler

    fun getMinions(): List<Minion>

    fun getAxMinionsInstance(): AxPlugin

    /**
     * Returns how many minions the player can have, Int.MAX_VALUE means no limit (the player has the
     * axminions.bypass.limit or the axminions.limit.* permission).
     */
    fun getMinionLimit(player: Player): Int

    /**
     * Returns how many minions of the type with the given name the player can have, set with the
     * axminions.limit.<type>.<amount> permission. Null means there is no limit for the type, only the total one.
     */
    fun getTypeLimit(player: Player, type: String): Int?

    fun getIntegrations(): Integrations

    fun getTick(): Long

    companion object {
        @JvmStatic
        lateinit var INSTANCE: AxMinionsAPI
    }
}