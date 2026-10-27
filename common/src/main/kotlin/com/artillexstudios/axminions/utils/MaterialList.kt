package com.artillexstudios.axminions.utils

import com.artillexstudios.axminions.AxMinionsPlugin
import com.artillexstudios.axminions.api.minions.miniontype.MinionType
import java.util.EnumSet
import java.util.Locale
import org.bukkit.Material

/**
 * A list of materials from the config of a minion type, for the filters (whitelists and the like).
 * The names are not case-sensitive, and can have the minecraft: in front. The list is only parsed again when it changed in the config, so a name
 * that is not a material is warned about once, not by every minion. A missing or empty list gives an empty set.
 *
 * [aliases] turns a name into another before it is looked up, for the ones people write as an item but the minion
 * checks as a block.
 */
class MaterialList(private val route: String, private val aliases: Map<String, String> = emptyMap()) {
    private class Parsed(val names: List<String>, val materials: Set<Material>)

    @Volatile
    private var parsed = Parsed(emptyList(), emptySet())

    fun get(type: MinionType): Set<Material> {
        val names = type.getConfig().getStringList(route, emptyList())
        val current = parsed
        if (names == current.names) return current.materials

        val materials = EnumSet.noneOf(Material::class.java)
        for (name in names) {
            val upper = name.trim().lowercase(Locale.ENGLISH).removePrefix("minecraft:").uppercase(Locale.ENGLISH)
            val material = Material.matchMaterial(aliases[upper] ?: upper)
            if (material == null) {
                AxMinionsPlugin.INSTANCE.logger.warning("minions/${type.getName()}.yml: '$name' in $route is not a material, skipping it!")
                continue
            }

            materials.add(material)
        }

        parsed = Parsed(ArrayList(names), materials)
        return materials
    }
}
