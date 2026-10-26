package com.artillexstudios.axminions.integrations.prices

import com.artillexstudios.axapi.config.Config
import com.artillexstudios.axapi.libs.boostedyaml.settings.dumper.DumperSettings
import com.artillexstudios.axapi.libs.boostedyaml.settings.general.GeneralSettings
import com.artillexstudios.axapi.libs.boostedyaml.settings.loader.LoaderSettings
import com.artillexstudios.axapi.libs.boostedyaml.settings.updater.UpdaterSettings
import com.artillexstudios.axminions.AxMinionsPlugin
import com.artillexstudios.axminions.api.integrations.types.PricesIntegration
import java.io.File
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/**
 * The prices of the prices.yml, used when the prices hook is set to builtin.
 * The file is read when the integration is created, so a reload of the plugin is needed to see the changes.
 */
class BuiltinPricesIntegration : PricesIntegration {
    private val prices = HashMap<Material, Double>()

    override fun getPrice(itemStack: ItemStack): Double {
        val price = prices[itemStack.type] ?: return 0.0
        return price * itemStack.amount
    }

    override fun register() {
        // Auto update would add back every default price that was removed from the file
        val config = Config(
            File(AxMinionsPlugin.INSTANCE.dataFolder, "prices.yml"),
            AxMinionsPlugin.INSTANCE.getResource("prices.yml")!!,
            GeneralSettings.builder().setUseDefaults(false).build(),
            LoaderSettings.builder().setAutoUpdate(false).build(),
            DumperSettings.DEFAULT,
            UpdaterSettings.DEFAULT
        )

        for (key in config.getKeys(false)) {
            val material = Material.matchMaterial(key)
            if (material == null) {
                AxMinionsPlugin.INSTANCE.logger.warning("prices.yml: '$key' is not a valid material, skipping it!")
                continue
            }

            val price = config.get<Any>(key) as? Number
            if (price == null) {
                AxMinionsPlugin.INSTANCE.logger.warning("prices.yml: the price of '$key' is not a number, skipping it!")
                continue
            }

            prices[material] = price.toDouble()
        }
    }
}
