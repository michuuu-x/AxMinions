package com.artillexstudios.axminions.nms.v1_21_R6

import com.artillexstudios.axminions.api.minions.Minion
import com.mojang.authlib.GameProfile
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ClientInformation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.storage.loot.BuiltInLootTables
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import net.minecraft.world.phys.Vec3
import org.bukkit.Location
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.craftbukkit.entity.CraftEntity
import org.bukkit.craftbukkit.inventory.CraftItemStack
import org.bukkit.entity.Entity
import org.bukkit.entity.ExperienceOrb
import org.bukkit.inventory.ItemStack
import java.util.*
import kotlin.collections.ArrayList

object LootHandler {

    fun generateFishingLoot(minion: Minion, waterLocation: Location): List<ItemStack> {
        val nmsItem: net.minecraft.world.item.ItemStack = if (minion.getTool() == null) {
            net.minecraft.world.item.ItemStack.EMPTY
        } else {
            CraftItemStack.asNMSCopy(minion.getTool())
        }

        val level = (minion.getLocation().world as CraftWorld).handle

        val lootparams = LootParams.Builder(level).withParameter(
            LootContextParams.ORIGIN, Vec3(waterLocation.x, waterLocation.y, waterLocation.z)
        ).withParameter(LootContextParams.TOOL, nmsItem).withOptionalParameter(LootContextParams.THIS_ENTITY, null)
            .create(LootContextParamSets.FISHING)

        val lootTable = MinecraftServer.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING)

        return lootTable.getRandomItems(lootparams).stream().map { original: net.minecraft.world.item.ItemStack? ->
            CraftItemStack.asBukkitCopy(
                original
            )
        }.toList()
    }

    // NOWA METODA: Zwraca listę dropów "Player Kill"
    fun dropPlayerKillLoot(victim: Entity): List<ItemStack> {
        val drops = ArrayList<ItemStack>()

        val nmsVictim = (victim as CraftEntity).handle
        if (nmsVictim !is net.minecraft.world.entity.LivingEntity) return emptyList()
        val world = nmsVictim.level() as? ServerLevel ?: return emptyList()

        // 1. Fake Player
        val fakePlayer = ServerPlayer(
            world.server,
            world,
            GameProfile(UUID.randomUUID(), "[MinionLoot]"),
            ClientInformation.createDefault()
        )

        // 2. Parametry lootu
        val lootParams = LootParams.Builder(world)
            .withParameter(LootContextParams.ORIGIN, nmsVictim.position())
            .withParameter(LootContextParams.THIS_ENTITY, nmsVictim)
            .withParameter(LootContextParams.ATTACKING_ENTITY, fakePlayer)
            .withParameter(LootContextParams.DAMAGE_SOURCE, world.damageSources().playerAttack(fakePlayer))
            .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, fakePlayer)
            .create(LootContextParamSets.ENTITY)

        // 3. Pobranie i obsługa tabeli
        val lootTableKey = nmsVictim.getLootTable().orElse(null) ?: return emptyList()
        val lootTable = world.server.reloadableRegistries().getLootTable(lootTableKey)

        lootTable.getRandomItems(lootParams).forEach { nmsItem ->
            drops.add(CraftItemStack.asBukkitCopy(nmsItem))
        }

        // 4. XP
        val xpAmount = nmsVictim.getExperienceReward(world, fakePlayer)
        if (xpAmount > 0) {
            (victim.world.spawn(victim.location, ExperienceOrb::class.java)).experience = xpAmount
        }

        return drops
    }
}
