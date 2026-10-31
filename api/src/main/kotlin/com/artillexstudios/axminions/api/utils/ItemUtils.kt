package com.artillexstudios.axminions.api.utils

import com.artillexstudios.axapi.items.WrappedItemStack
import com.artillexstudios.axapi.libs.boostedyaml.block.implementation.Section
import com.artillexstudios.axapi.utils.ItemBuilder
import com.artillexstudios.axminions.api.AxMinionsAPI
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

object ItemUtils {

    /**
     * Creates the item of the section like [ItemBuilder.create] does, but the 'material' (or 'type') can also be an item
     * of another plugin, like nexo_<id>. The rest of the section (name, lore, amount...) is put on that item the same way.
     */
    fun create(section: Section, vararg resolvers: TagResolver): ItemBuilder {
        val item = getCustomItem(section) ?: return ItemBuilder.create(section, *resolvers)

        // The builder reads the item from the snbt, the same way as the items that /axminions fuel add saves, and the material is not read then
        val config = HashMap<String, Any?>()
        section.getKeys().forEach { config[it.toString()] = section.get(it.toString()) }
        config["snbt"] = WrappedItemStack.wrap(item).toSNBT()
        return ItemBuilder.create(config, *resolvers)
    }

    /**
     * Whether the item is one of the materials: the name of a Bukkit material, or an item of another plugin like nexo_<id>.
     * The ones that are neither are skipped.
     */
    fun matches(materials: Collection<String>, item: ItemStack): Boolean {
        val integration = AxMinionsAPI.INSTANCE.getIntegrations().getItemIntegration()

        for (material in materials) {
            if (integration?.isItem(material, item) == true) return true
            if (Material.matchMaterial(material) == item.type) return true
        }

        return false
    }

    private fun getCustomItem(section: Section): ItemStack? {
        val integration = AxMinionsAPI.INSTANCE.getIntegrations().getItemIntegration() ?: return null
        if (section.contains("snbt")) return null

        val material = section.getString("material") ?: section.getString("type") ?: return null
        return integration.getItem(material)
    }
}
