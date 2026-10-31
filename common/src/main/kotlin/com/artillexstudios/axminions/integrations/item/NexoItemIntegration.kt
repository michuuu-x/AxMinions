package com.artillexstudios.axminions.integrations.item

import com.artillexstudios.axminions.AxMinionsPlugin
import com.artillexstudios.axminions.api.integrations.types.ItemIntegration
import com.nexomc.nexo.api.NexoItems
import java.util.concurrent.ConcurrentHashMap
import org.bukkit.inventory.ItemStack

class NexoItemIntegration : ItemIntegration {
    // The items that were told about being missing, so the console isn't filled with the same message every time a menu is opened
    private val missingItems = ConcurrentHashMap.newKeySet<String>()

    override fun register() {

    }

    override fun getItem(material: String): ItemStack? {
        val id = idOf(material) ?: return null
        val item = NexoItems.itemFromId(id)?.build()
        if (item != null) return item

        // The items are loaded some time after the server started, so an item that is missing is only a mistake once there are some
        if (NexoItems.itemNames().isNotEmpty() && missingItems.add(id)) {
            AxMinionsPlugin.INSTANCE.logger.warning("There is no Nexo item with the id '$id' (from '$material')! The id has to be the same as in the Nexo items.")
        }

        return null
    }

    override fun isItem(material: String, item: ItemStack): Boolean {
        val id = idOf(material) ?: return false
        return NexoItems.idFromItem(item) == id
    }

    private fun idOf(material: String): String? {
        return PATTERN.matchEntire(material.trim())?.groupValues?.get(1)
    }

    private companion object {
        // nexo_<id>, or nexo:<id>
        val PATTERN = Regex("nexo[_:](.+)", RegexOption.IGNORE_CASE)
    }
}
