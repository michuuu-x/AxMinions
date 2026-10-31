package com.artillexstudios.axminions.integrations.model

import com.artillexstudios.axminions.AxMinionsPlugin
import com.artillexstudios.axminions.api.integrations.types.ModelIntegration
import com.artillexstudios.axminions.api.minions.Minion
import com.artillexstudios.axminions.api.minions.MinionModel
import com.ticxo.modelengine.api.ModelEngineAPI
import com.ticxo.modelengine.api.animation.ModelState
import com.ticxo.modelengine.api.animation.handler.AnimationHandler
import com.ticxo.modelengine.api.entity.Dummy
import com.ticxo.modelengine.api.generator.blueprint.ModelBlueprint
import com.ticxo.modelengine.api.model.ActiveModel
import java.util.concurrent.ConcurrentHashMap
import org.bukkit.Location

class ModelEngineIntegration : ModelIntegration {
    // The animations that were told about being missing (as model:animation), so the console isn't filled with the same message for every minion
    private val missingAnimations = ConcurrentHashMap.newKeySet<String>()

    override fun register() {

    }

    override fun isReady(): Boolean {
        return ModelEngineAPI.getAPI().modelGenerator.isInitialized
    }

    override fun create(minion: Minion, id: String, scale: Double, idleAnimation: String): MinionModel? {
        val blueprint = ModelEngineAPI.getBlueprint(id) ?: return null
        checkAnimations(blueprint, id, idleAnimation, minion.getType().getModelAnimation())

        // A minion is not a real entity, so the model stands on a dummy that is where the minion is.
        // The dummy knows its minion, so a click on the model can be given to it
        val dummy = Dummy(minion)
        dummy.syncLocation(minion.getLocation().clone())

        val modeledEntity = ModelEngineAPI.createModeledEntity(dummy)
        val activeModel = ModelEngineAPI.createActiveModel(blueprint)
        activeModel.setScale(scale)
        activeModel.setHitboxScale(scale)

        // ModelEngine plays the animation of the idle state by itself while nothing else is playing, but it looks for 'idle'
        // (in lower case) unless it is told another name, which is what the config of the type is for
        if (idleAnimation.isNotBlank()) {
            activeModel.animationHandler.setDefaultProperty(
                AnimationHandler.DefaultProperty(ModelState.IDLE, idleAnimation, 0.1, 0.1, 1.0)
            )
        }

        modeledEntity.addModel(activeModel, true)

        return EngineModel(id, dummy, activeModel)
    }

    // An animation that is not in the model is not played, and nothing says so, so the console does
    private fun checkAnimations(blueprint: ModelBlueprint, id: String, vararg animations: String) {
        for (animation in animations) {
            if (animation.isBlank() || blueprint.animations.containsKey(animation)) continue
            if (!missingAnimations.add("$id:$animation")) continue

            AxMinionsPlugin.INSTANCE.logger.warning(
                "The model '$id' has no animation called '$animation' (upper and lower case letters count)! " +
                    "Its animations are: ${blueprint.animations.keys.joinToString(", ").ifEmpty { "none" }}"
            )
        }
    }

    private class EngineModel(
        private val id: String,
        private val dummy: Dummy<Minion>,
        private val model: ActiveModel
    ) : MinionModel {

        override fun getId(): String {
            return id
        }

        override fun isValid(): Boolean {
            return !dummy.isRemoved && !model.isDestroyed
        }

        override fun playAnimation(animation: String) {
            if (animation.isBlank() || !isValid()) return

            // The animation is restarted each time, as the minion does its job again
            model.animationHandler.playAnimation(animation, 0.1, 0.1, 1.0, true)
        }

        override fun teleport(location: Location) {
            dummy.syncLocation(location.clone())
        }

        override fun remove() {
            // ModelEngine takes the model away by itself when the dummy is removed
            dummy.isRemoved = true
        }
    }
}
