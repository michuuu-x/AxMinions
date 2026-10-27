package com.artillexstudios.axminions.utils

import org.bukkit.entity.Player

/**
 * The permissions that let a player get around a rule, each one only one rule.
 * They are all in axminions.bypass.*, which axminions.* includes.
 */
object Bypass {
    const val OWNER = "axminions.bypass.owner"
    const val LIMIT = "axminions.bypass.limit"
    const val ISLAND = "axminions.bypass.island"

    /**
     * Open the menu of the minions of other players, and pick them up.
     */
    fun owner(player: Player): Boolean {
        return player.hasPermission(OWNER)
    }

    /**
     * Place minions without the limit of the player (the total one and the one of a type), the island limit is not included.
     * axminions.limit.* does the same, that is the permission that was used for it before.
     */
    fun limit(player: Player): Boolean {
        return player.hasPermission(LIMIT) || player.hasPermission("axminions.limit.*")
    }

    /**
     * Place minions without the limit of the island.
     */
    fun island(player: Player): Boolean {
        return player.hasPermission(ISLAND)
    }
}
