package com.artillexstudios.axminions.api.integrations.types

import org.bukkit.Location
import org.bukkit.entity.Player

interface ProtectionIntegrations {

    fun getProtectionIntegrations(): List<ProtectionIntegration>

    fun canBuildAt(player: Player, location: Location): Boolean

    /**
     * Whether every protection allows the player to place a minion at the location, see [ProtectionIntegration.canPlaceMinionAt].
     */
    fun canPlaceMinionAt(player: Player, location: Location): Boolean
}
