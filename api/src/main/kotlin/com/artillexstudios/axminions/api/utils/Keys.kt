package com.artillexstudios.axminions.api.utils

import com.artillexstudios.axminions.api.AxMinionsAPI
import org.bukkit.NamespacedKey

object Keys {
    @JvmField
    val MINION_TYPE = NamespacedKey(AxMinionsAPI.INSTANCE.getAxMinionsInstance(), "minion_type")
    @JvmField
    val LEVEL = NamespacedKey(AxMinionsAPI.INSTANCE.getAxMinionsInstance(), "level")
    @JvmField
    val GUI = NamespacedKey(AxMinionsAPI.INSTANCE.getAxMinionsInstance(), "gui_item")
    @JvmField
    val STATISTICS = NamespacedKey(AxMinionsAPI.INSTANCE.getAxMinionsInstance(), "statistics")
    @JvmField
    val PLACED = NamespacedKey(AxMinionsAPI.INSTANCE.getAxMinionsInstance(), "placed")
    @JvmField
    val CHARGE = NamespacedKey(AxMinionsAPI.INSTANCE.getAxMinionsInstance(), "charge")
    // On the player, set if they turned off the alerts about their minions
    @JvmField
    val ALERTS_OFF = NamespacedKey(AxMinionsAPI.INSTANCE.getAxMinionsInstance(), "alerts_off")
}