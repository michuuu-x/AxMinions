package com.artillexstudios.axminions.api.integrations.types

import com.artillexstudios.axminions.api.integrations.Integration
import org.bukkit.inventory.ItemStack

interface ItemIntegration : Integration {

    /**
     * The item of the plugin that the 'material' (or 'type') of an item in the configs points at, like nexo_<id> for Nexo.
     * It is a new item every time. Returns null if it doesn't point at an item of the plugin, or if there is no such item (yet).
     */
    fun getItem(material: String): ItemStack?

    /**
     * Whether the item is the item of the plugin that the material points at (nexo_<id> for Nexo).
     * False if the material doesn't point at an item of the plugin.
     */
    fun isItem(material: String, item: ItemStack): Boolean
}
