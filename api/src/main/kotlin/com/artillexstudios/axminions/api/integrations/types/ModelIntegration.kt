package com.artillexstudios.axminions.api.integrations.types

import com.artillexstudios.axminions.api.integrations.Integration
import com.artillexstudios.axminions.api.minions.Minion
import com.artillexstudios.axminions.api.minions.MinionModel

interface ModelIntegration : Integration {

    /**
     * Whether the models are loaded. They are some time after the server started,
     * so a model that doesn't exist is only a mistake once this is true.
     */
    fun isReady(): Boolean

    /**
     * Shows the model with the id on the minion, [scale] times as big as it is made, with the animation called [idleAnimation]
     * playing while nothing else does. Returns null if there is no such model (yet).
     */
    fun create(minion: Minion, id: String, scale: Double, idleAnimation: String): MinionModel?
}
