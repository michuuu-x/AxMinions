package com.artillexstudios.axminions.listeners

import com.artillexstudios.axapi.scheduler.Scheduler
import com.artillexstudios.axapi.utils.StringUtils
import com.artillexstudios.axminions.AxMinionsPlugin
import com.artillexstudios.axminions.api.AxMinionsAPI
import com.artillexstudios.axminions.api.config.Config
import com.artillexstudios.axminions.api.config.Messages
import com.artillexstudios.axminions.api.events.PreMinionPlaceEvent
import com.artillexstudios.axminions.api.minions.Direction
import com.artillexstudios.axminions.api.minions.miniontype.MinionTypes
import com.artillexstudios.axminions.api.utils.Keys
import com.artillexstudios.axminions.minions.Minion
import com.artillexstudios.axminions.minions.Minions
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

class MinionPlaceListener : Listener {

    // true if the material is a full block (no partial hitbox)
    private fun hasFullHitbox(material: Material): Boolean {
        val nameU = material.name.uppercase()

        // Exact non-full names
        val nonFull = Config.COLLISION_NON_FULL().map { it.uppercase() }.toSet()
        if (nonFull.contains(nameU)) return false

        // Substring material checks
        val materialChecks = Config.COLLISION_MATERIALS().map { it.uppercase() }
        for (m in materialChecks) {
            if (nameU.contains(m)) return false
        }

        return true
    }

    // true if material/block should be treated as collision (blocks placement)
    private fun hasCollision(material: Material): Boolean {
        val nameU = material.name.uppercase()
        val blockChecks = Config.COLLISION_BLOCKS().map { it.uppercase() }
        for (b in blockChecks) {
            if (nameU.contains(b)) return true
        }

        return false
    }

    @EventHandler
    fun onPlayerInteractEvent(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK && event.action != Action.RIGHT_CLICK_AIR) return
        if (event.clickedBlock == null) return
        val item = event.player.inventory.getItem(event.hand ?: return) ?: return
        var meta = item.itemMeta ?: return

        val type = meta.persistentDataContainer.get(Keys.MINION_TYPE, PersistentDataType.STRING) ?: return
        val minionType = MinionTypes.valueOf(type) ?: return
        event.isCancelled = true

        if (!AxMinionsPlugin.integrations.getProtectionIntegration()
                .canBuildAt(event.player, event.clickedBlock!!.location)
        ) {
            if (Config.DEBUG()) {
                event.player.sendMessage(
                    "Could not place due to protection hook!"
                )
            }
            return
        }

        // Blok pod lokalizacją gdzie będzie stał minion
        val minionLocation = event.clickedBlock!!.getRelative(event.blockFace).location
        val blockBelow = minionLocation.clone().subtract(0.0, 1.0, 0.0).block
        val blockAtMinion = minionLocation.block


        // Sprawdź czy blok w miejscu miniona nie jest blokiem z hitboxem (np. trawa, kwiaty itp.)
        // Zmieniamy warunek tak, by korzystał z konfiguracji (hasFullHitbox) zamiast polegać wyłącznie
        // na vanilla property isSolid. Dzięki temu możemy oznaczyć niektóre SOLID bloki jako "non-full"
        // w konfiguracji i pozwolić na postawienie miniona na nich.
        if (!blockAtMinion.type.isAir && (hasFullHitbox(blockAtMinion.type) || hasCollision(blockAtMinion.type))) {
            event.player.sendMessage(StringUtils.formatToString(Messages.PREFIX() + Messages.INVALID_PLACEMENT_LOCATION()))
            return
        }

        // Sprawdź czy blok pod minionem ma pełny hitbox
        if (!hasFullHitbox(blockBelow.type) || !blockBelow.type.isSolid) {
            event.player.sendMessage(StringUtils.formatToString(Messages.PREFIX() + Messages.INVALID_PLACEMENT_LOCATION()))
            return
        }

        val prePlaceEvent = PreMinionPlaceEvent(event.player, event.clickedBlock!!.location)

        val level = meta.persistentDataContainer.get(Keys.LEVEL, PersistentDataType.INTEGER) ?: 0
        val stats = meta.persistentDataContainer.get(Keys.STATISTICS, PersistentDataType.LONG) ?: 0
        val charge = meta.persistentDataContainer.get(Keys.CHARGE, PersistentDataType.LONG) ?: 0

        if (Config.PLACE_PERMISSION() && !event.player.hasPermission("axminions.place.${minionType.getName()}")) {
            event.player.sendMessage(StringUtils.formatToString(Messages.PREFIX() + Messages.PLACE_MISSING_PERMISSION()))
            return
        }

        if (meta.persistentDataContainer.has(Keys.PLACED, PersistentDataType.BYTE)) return

        Bukkit.getPluginManager().callEvent(prePlaceEvent)
        if (prePlaceEvent.isCancelled) return

        meta.persistentDataContainer.set(Keys.PLACED, PersistentDataType.BYTE, 0)
        item.itemMeta = meta

        val location = minionLocation

        val maxMinions = AxMinionsAPI.INSTANCE.getMinionLimit(event.player)

        val chunk = location.chunk

        AxMinionsPlugin.dataQueue.submit {
            val placed = AxMinionsPlugin.dataHandler.getMinionAmount(event.player.uniqueId)

            var islandLimit = Config.ISLAND_LIMIT()
            var islandPlaced = 0
            var islandId = ""
            if (islandLimit > 0 && AxMinionsAPI.INSTANCE.getIntegrations().getIslandIntegration() != null) {
                islandId = AxMinionsAPI.INSTANCE.getIntegrations().getIslandIntegration()!!.getIslandAt(location)
                islandLimit += AxMinionsAPI.INSTANCE.getIntegrations().getIslandIntegration()!!.getExtra(location)

                if (Config.DEBUG()) {
                    event.player.sendMessage("Island ID: $islandId, limit: $islandLimit")
                }

                if (islandId.isNotBlank()) {
                    islandPlaced = AxMinionsAPI.INSTANCE.getDataHandler().getIsland(islandId)
                    if (Config.DEBUG()) {
                        event.player.sendMessage("Placed: $islandPlaced")
                    }

                    if (islandPlaced >= islandLimit && !event.player.hasPermission("axminions.limit.*")) {
                        if (Config.DEBUG()) {
                            event.player.sendMessage("Return")
                        }

                        event.player.sendMessage(
                            StringUtils.formatToString(
                                Messages.PREFIX() + Messages.ISLAND_LIMIT_REACHED(),
                                Placeholder.unparsed("placed", islandPlaced.toString()),
                                Placeholder.unparsed("max", islandLimit.toString())
                            )
                        )

                        Scheduler.get().run { _ ->
                            meta = item.itemMeta!!
                            meta.persistentDataContainer.remove(Keys.PLACED)
                            item.itemMeta = meta
                        }
                        return@submit
                    } else {
                        if (Config.DEBUG()) {
                            event.player.sendMessage("Not return ${islandPlaced >= islandLimit} ${!event.player.hasPermission("axminions.limit.*")}")
                        }
                        islandPlaced++
                    }
                }
            }

            if (placed >= maxMinions && !prePlaceEvent.getShouldOverridePlayerLimit() && !event.player.hasPermission("axminions.limit.*")) {
                event.player.sendMessage(
                    StringUtils.formatToString(
                        Messages.PREFIX() + Messages.PLACE_LIMIT_REACHED(),
                        Placeholder.unparsed("placed", placed.toString()),
                        Placeholder.unparsed("max", maxMinions.toString())
                    )
                )

                Scheduler.get().run { _ ->
                    meta = item.itemMeta!!
                    meta.persistentDataContainer.remove(Keys.PLACED)
                    item.itemMeta = meta
                }
                return@submit
            }

            if (AxMinionsPlugin.dataHandler.isMinion(location)) {
                event.player.sendMessage(StringUtils.formatToString(Messages.PREFIX() + Messages.PLACE_MINION_AT_LOCATION()))

                Scheduler.get().run { _ ->
                    meta = item.itemMeta!!
                    meta.persistentDataContainer.remove(Keys.PLACED)
                    item.itemMeta = meta
                }
                return@submit
            }

            val locationId = AxMinionsPlugin.dataHandler.getLocationID(location)
            val minion = Minion(
                location,
                event.player.uniqueId,
                event.player,
                minionType,
                level,
                ItemStack(Material.AIR),
                null,
                Direction.NORTH,
                stats,
                0.0,
                locationId,
                0,
                charge
            )
            Minions.startTicking(chunk)


            Scheduler.get().run { _ ->
                meta = item.itemMeta!!
                meta.persistentDataContainer.remove(Keys.PLACED)
                item.itemMeta = meta
                item.amount = item.amount.minus(1)
            }

            if (Config.DEBUG()) {
                event.player.sendMessage(
                    "Placed minion $minion. Ticking? ${minion.isTicking()} Is chunk ticking? ${
                        Minions.isTicking(
                            chunk
                        )
                    } Chunk x: ${chunk.x} z: ${chunk.z} location x: ${location.x} y: ${location.y} z: ${location.z}"
                )
            }

            minion.setOwnerOnline(true)
            AxMinionsPlugin.dataHandler.saveMinion(minion)

            if (islandId.isNotBlank()) {
                AxMinionsPlugin.dataHandler.islandPlace(islandId)
            }

            event.player.sendMessage(
                StringUtils.formatToString(
                    Messages.PREFIX() + Messages.PLACE_SUCCESS(),
                    Placeholder.unparsed("type", minionType.getName()),
                    Placeholder.unparsed("placed", (placed + 1).toString()),
                    Placeholder.unparsed("max", maxMinions.toString()),
                    Placeholder.unparsed("island-placed", islandPlaced.toString()),
                    Placeholder.unparsed("island-max", islandLimit.toString()),
                )
            )
        }
    }

    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        val blockLocation = event.block.location

        if (AxMinionsPlugin.dataHandler.isMinion(blockLocation)) {
            event.isCancelled = true
        }
    }
}