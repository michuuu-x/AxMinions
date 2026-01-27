package com.artillexstudios.axminions.integrations.prices

import com.artillexstudios.axminions.api.integrations.types.PricesIntegration
import org.bukkit.inventory.ItemStack
import su.nightexpress.nexshop.ShopAPI
import su.nightexpress.nexshop.api.shop.type.TradeType

class ExcellentShopIntegration : PricesIntegration {

    override fun getPrice(itemStack: ItemStack): Double {
        val virtualShop = ShopAPI.getVirtualShop() ?: return 0.0
        val product = virtualShop.getBestProductFor(itemStack, TradeType.SELL)
        if (product == null || !product.isSellable) {
            return 0.0
        }
        return product.getPrice(TradeType.SELL) / product.unitAmount * itemStack.amount
    }
    override fun register() {
    }
}
