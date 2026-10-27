package com.artillexstudios.axminions.api.integrations.types

import com.artillexstudios.axminions.api.integrations.Integration
import org.bukkit.Location
import org.bukkit.entity.Player

interface ProtectionIntegration : Integration {

    fun canBuildAt(player: Player, location: Location): Boolean

    /**
     * Whether the player can place a minion at the location, for the protections that have their own rules for minions
     * (for example a flag). It is checked on top of [canBuildAt], and most protections have no such rules.
     */
    fun canPlaceMinionAt(player: Player, location: Location): Boolean {
        return true
    }
}
