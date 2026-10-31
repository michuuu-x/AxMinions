package com.artillexstudios.axminions.api.minions

import org.bukkit.Location

/**
 * The model a minion has instead of its armor stand, made by a [com.artillexstudios.axminions.api.integrations.types.ModelIntegration].
 */
interface MinionModel {

    /**
     * The id of the model.
     */
    fun getId(): String

    /**
     * Whether the model is still shown. It isn't when the plugin of the models took it away, for example when it loaded the models again.
     */
    fun isValid(): Boolean

    /**
     * Plays the animation of the model once, from its start. Nothing happens if the model has no animation with that name.
     */
    fun playAnimation(animation: String)

    /**
     * Moves the model to the location and turns it to where the location is facing.
     */
    fun teleport(location: Location)

    fun remove()
}
