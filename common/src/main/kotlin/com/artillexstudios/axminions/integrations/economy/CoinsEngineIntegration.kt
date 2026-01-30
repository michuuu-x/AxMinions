package com.artillexstudios.axminions.integrations.economy

import com.artillexstudios.axminions.api.config.Config
import com.artillexstudios.axminions.api.integrations.types.EconomyIntegration
import org.bukkit.OfflinePlayer
import su.nightexpress.coinsengine.api.CoinsEngineAPI
import su.nightexpress.coinsengine.api.currency.Currency

class CoinsEngineIntegration : EconomyIntegration {

    private var currency: Currency? = null

    override fun register() {
        val currencyName = Config.COINSENGINE_CURRENCY()
        currency = CoinsEngineAPI.getCurrency(currencyName)
    }

    override fun getBalance(player: OfflinePlayer): Double {
        val cur = currency ?: return 0.0
        return CoinsEngineAPI.getBalance(player.uniqueId, cur)
    }

    override fun giveBalance(player: OfflinePlayer, amount: Double) {
        val cur = currency ?: return
        CoinsEngineAPI.addBalance(player.uniqueId, cur, amount)
    }

    override fun takeBalance(player: OfflinePlayer, amount: Double) {
        val cur = currency ?: return
        CoinsEngineAPI.removeBalance(player.uniqueId, cur, amount)
    }
}
