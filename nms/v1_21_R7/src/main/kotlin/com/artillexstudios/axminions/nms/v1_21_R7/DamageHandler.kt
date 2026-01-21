package com.artillexstudios.axminions.nms.v1_21_R7

import com.artillexstudios.axminions.api.events.PreMinionDamageEntityEvent
import com.artillexstudios.axminions.api.minions.Minion
import com.artillexstudios.axminions.listeners.MinionPlayerEventBlocker
import com.mojang.authlib.GameProfile
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ClientInformation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Mth
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.EnchantmentHelper
import org.bukkit.Bukkit
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.craftbukkit.entity.CraftEntity
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.craftbukkit.inventory.CraftItemStack
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import net.minecraft.world.entity.EquipmentSlotGroup
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.core.component.DataComponents
import net.minecraft.core.Holder
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.EntityReference
import org.bukkit.plugin.Plugin
import java.util.*

@Suppress("DEPRECATION")
object DamageHandler {
    private var FAKE_PLAYER: MinionFakePlayer? = null
    private var minion: Minion? = null

    /**
     * Simple MetadataValue implementation to mark fake player as NPC.
     * Note: MetadataValue is deprecated in Paper but still works and is the standard way
     * for plugins like Essentials to detect NPCs.
     */
    private class NpcMetadataValue(private val owningPlugin: Plugin) : org.bukkit.metadata.MetadataValue {
        override fun value(): Any = true
        override fun asInt(): Int = 1
        override fun asFloat(): Float = 1f
        override fun asDouble(): Double = 1.0
        override fun asLong(): Long = 1L
        override fun asShort(): Short = 1
        override fun asByte(): Byte = 1
        override fun asBoolean(): Boolean = true
        override fun asString(): String = "true"
        override fun getOwningPlugin(): Plugin = owningPlugin
        override fun invalidate() {}
    }

    private fun getOrCreateFakePlayer(): MinionFakePlayer {
        if (FAKE_PLAYER == null) {
            val world = Bukkit.getWorlds().get(0)
            val serverLevel = (world as CraftWorld).handle
            val profile = GameProfile(UUID.randomUUID(), "[Minion]")
            FAKE_PLAYER = MinionFakePlayer(
                MinecraftServer.getServer(),
                serverLevel,
                profile,
                ClientInformation.createDefault()
            )
            // Mark as NPC so Essentials and other plugins ignore this fake player
            val plugin = Bukkit.getPluginManager().getPlugin("AxMinions")
            if (plugin != null) {
                FAKE_PLAYER!!.bukkitEntity.setMetadata("NPC", NpcMetadataValue(plugin))
            }
        }
        return FAKE_PLAYER!!
    }

    fun getUUID(): UUID {
        return getOrCreateFakePlayer().uuid
    }

    fun getMinion(): Minion? {
        return minion
    }

    fun damage(source: Minion, entity: Entity) {
        val nmsEntity = (entity as CraftEntity).handle

        // Try to use real owner player if online, otherwise use fake player
        val ownerPlayer = source.getOwner()?.player
        val useRealPlayer = ownerPlayer != null && ownerPlayer.isOnline
        val attackingPlayer: ServerPlayer

        if (useRealPlayer) {
            attackingPlayer = (ownerPlayer as CraftPlayer).handle
            MinionPlayerEventBlocker.markAttacking(ownerPlayer)
        } else {
            attackingPlayer = getOrCreateFakePlayer()
        }

        synchronized(attackingPlayer) {
            try {
                this.minion = source
                var f = 1

                val nmsItem: ItemStack
                if (source.getTool() == null) {
                    nmsItem = ItemStack.EMPTY
                } else {
                    nmsItem = CraftItemStack.asNMSCopy(source.getTool())

                    nmsItem.get(DataComponents.ATTRIBUTE_MODIFIERS)?.forEach(EquipmentSlotGroup.MAINHAND) { h: Holder<Attribute>, m ->
                        if (h.unwrapKey().orElseThrow() == Attributes.ATTACK_DAMAGE.unwrapKey().orElseThrow()) {
                            f += m.amount().toInt()
                        }
                    }
                }

                // Only set item slot for fake player to avoid messing with real player's inventory
                if (!useRealPlayer) {
                    attackingPlayer.setItemSlot(EquipmentSlot.MAINHAND, nmsItem)
                }

                if (!nmsEntity.isAttackable || entity is Player) return
                val f2 = 1.0f

                val damageSource = nmsEntity.damageSources().playerAttack(attackingPlayer)
                var f1 = EnchantmentHelper.modifyDamage(
                    nmsEntity.level() as ServerLevel,
                    nmsItem,
                    nmsEntity,
                    damageSource,
                    f.toFloat()
                )

                f = (f * (0.2f + f2 * f2 * 0.8f)).toInt()
                f1 *= f2

                if (f > 0.0f || f1 > 0.0f) {
                    var flag3 = false
                    val b0: Byte = 0
                    val i = b0 + (source.getTool()?.getEnchantmentLevel(Enchantment.KNOCKBACK) ?: 0)

                    f = (f * 1.5f).toInt()
                    f = (f + f1).toInt()


                    if (nmsItem.item.components().get(DataComponents.WEAPON) != null) {
                        flag3 = true
                    }

                    var f3 = 0.0f
                    var flag4 = false
                    val j = (source.getTool()?.getEnchantmentLevel(Enchantment.FIRE_ASPECT) ?: 0)

                    if (nmsEntity is LivingEntity) {
                        f3 = nmsEntity.health
                        if (j > 0 && !nmsEntity.isOnFire()) {
                            flag4 = true
                            nmsEntity.igniteForSeconds(1.0f, false)
                        }
                    }

                    val event = PreMinionDamageEntityEvent(source, entity as org.bukkit.entity.LivingEntity, f.toDouble())
                    Bukkit.getPluginManager().callEvent(event)
                    if (event.isCancelled) {
                        return
                    }

                    // Set lastHurtByPlayer to enable player-only drops (e.g., Blaze Rod, Piglin drops)
                    // Use real owner when online for proper attribution, fake player when offline for drops to work
                    if (nmsEntity is LivingEntity) {
                        val playerForDrops = if (useRealPlayer) attackingPlayer else getOrCreateFakePlayer()
                        nmsEntity.lastHurtByPlayer = EntityReference.of(playerForDrops)
                        nmsEntity.lastHurtByMob = EntityReference.of(attackingPlayer)
                        nmsEntity.lastHurtByMobTimestamp = nmsEntity.tickCount
                    }

                    val flag5 = nmsEntity.hurtServer((source.getLocation().world as CraftWorld).handle as ServerLevel, damageSource, f.toFloat())

                    // Check if entity died and trigger MinionKillEntityEvent for drop collection
                    if (flag5 && nmsEntity is LivingEntity && nmsEntity.isDeadOrDying) {
                        Bukkit.getPluginManager().callEvent(
                            com.artillexstudios.axminions.api.events.MinionKillEntityEvent(source, entity as org.bukkit.entity.LivingEntity)
                        )
                    }

                    if (flag5) {
                        if (i > 0) {
                            if (nmsEntity is LivingEntity) {
                                (nmsEntity).knockback(
                                    i.toDouble() * 0.5,
                                    Mth.sin(source.getLocation().yaw * 0.017453292).toDouble(),
                                    (-Mth.cos(source.getLocation().yaw * 0.017453292)).toDouble()
                                )
                            } else {
                                nmsEntity.push(
                                    -Mth.sin(source.getLocation().yaw * 0.017453292).toDouble() * i.toDouble() * 0.5,
                                    0.1,
                                    Mth.cos(source.getLocation().yaw * 0.017453292).toDouble() * i.toDouble() * 0.5
                                )
                            }
                        }

                        if (flag3) {
                            val sweep = source.getTool()?.getEnchantmentLevel(Enchantment.SWEEPING_EDGE) ?: 0
                            val f4 =
                                1.0f + if (sweep > 0) getSweepingDamageRatio(sweep) else 0.0f * f
                            val list: List<LivingEntity> = (source.getLocation().world as CraftWorld).handle
                                .getEntitiesOfClass(LivingEntity::class.java, nmsEntity.boundingBox.inflate(1.0, 0.25, 1.0))
                                .filter { it !is Player }
                            val iterator: Iterator<*> = list.iterator()

                            while (iterator.hasNext()) {
                                val entityliving: LivingEntity = iterator.next() as LivingEntity

                                if ((entityliving !is ArmorStand || !(entityliving).isMarker) && source.getLocation()
                                        .distanceSquared(
                                            (entity as Entity).location
                                        ) < 9.0
                                ) {
                                    val damageEvent = PreMinionDamageEntityEvent(
                                        source,
                                        entityliving.bukkitEntity as org.bukkit.entity.LivingEntity,
                                        f4.toDouble()
                                    )
                                    Bukkit.getPluginManager().callEvent(damageEvent)
                                    if (event.isCancelled) {
                                        return
                                    }

                                    // CraftBukkit start - Only apply knockback if the damage hits
                                    if (entityliving.hurtServer((source.getLocation().world as CraftWorld).handle as ServerLevel, nmsEntity.damageSources().playerAttack(attackingPlayer), f4)) {
                                        entityliving.knockback(
                                            0.4,
                                            Mth.sin(source.getLocation().yaw * 0.017453292).toDouble(),
                                            (-Mth.cos(source.getLocation().yaw * 0.017453292)).toDouble()
                                        )
                                    }
                                    // CraftBukkit end
                                }
                            }

                            val d0 = -Mth.sin(source.getLocation().yaw * 0.017453292).toDouble()
                            val d1 = Mth.cos(source.getLocation().yaw * 0.017453292).toDouble()

                            if ((source.getLocation().world as CraftWorld).handle is ServerLevel) {
                                ((source.getLocation().world as CraftWorld).handle as ServerLevel).sendParticles(
                                    ParticleTypes.SWEEP_ATTACK,
                                    source.getLocation().x + d0,
                                    source.getLocation().y + 0.5,
                                    source.getLocation().z + d1,
                                    0,
                                    d0,
                                    0.0,
                                    d1,
                                    0.0
                                )
                            }
                        }

                        if (nmsEntity is LivingEntity) {
                            val f5: Float = f3 - nmsEntity.health

                            if (j > 0) {
                                nmsEntity.igniteForSeconds((j * 4).toFloat(), false)
                            }

                            if ((source.getLocation().world as CraftWorld).handle is ServerLevel && f5 > 2.0f) {
                                val k = (f5.toDouble() * 0.5).toInt()

                                ((source.getLocation().world as CraftWorld).handle).sendParticles(
                                    ParticleTypes.DAMAGE_INDICATOR,
                                    nmsEntity.getX(),
                                    nmsEntity.getY(0.5),
                                    nmsEntity.getZ(),
                                    k,
                                    0.1,
                                    0.0,
                                    0.1,
                                    0.2
                                )
                            }
                        }
                    } else {
                        if (flag4) {
                            nmsEntity.clearFire()
                        }
                    }
                }
                this.minion = null
            } finally {
                // Always unmark the player when done
                if (useRealPlayer && ownerPlayer != null) {
                    MinionPlayerEventBlocker.unmarkAttacking(ownerPlayer)
                }
            }
        }
    }

    fun getSweepingDamageRatio(level: Int): Float {
        return 1.0f - 1.0f / (level + 1).toFloat()
    }
}
