package com.artillexstudios.axminions.listeners

import com.artillexstudios.axapi.utils.ItemBuilder
import com.artillexstudios.axapi.utils.StringUtils
import com.artillexstudios.axminions.AxMinionsPlugin
import com.artillexstudios.axminions.api.AxMinionsAPI
import com.artillexstudios.axminions.api.config.Config
import com.artillexstudios.axminions.api.config.Messages
import com.artillexstudios.axminions.api.events.MinionToolEvent
import com.artillexstudios.axminions.api.minions.Direction
import com.artillexstudios.axminions.api.minions.Minion
import com.artillexstudios.axminions.api.minions.miniontype.MinionTypes
import com.artillexstudios.axminions.api.utils.CoolDown
import com.artillexstudios.axminions.api.utils.Keys
import com.artillexstudios.axminions.api.utils.fastFor
import com.artillexstudios.axminions.users.TrustPermission
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import java.util.Locale
import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

class MinionInventoryListener : Listener {
    private companion object {
        // Players who can open the menu only because the owner trusts them need the owner to allow these,
        // the same goes for changing the tool, and for picking the minion up (in the Minion)
        val TRUST_PERMISSIONS = mapOf(
            "upgrade" to TrustPermission.UPGRADE,
            "link" to TrustPermission.LINK,
            "charge" to TrustPermission.CHARGE,
            "toggle" to TrustPermission.TOGGLE,
            "rotate" to TrustPermission.ROTATE,
            "statistics" to TrustPermission.STATISTICS
        )

        // Everyone has these unless they are taken away, the pickup one is in the Minion
        val ACTION_PERMISSIONS = mapOf(
            "upgrade" to "axminions.use.upgrade",
            "link" to "axminions.use.link",
            "charge" to "axminions.use.charge",
            "toggle" to "axminions.use.toggle",
            "rotate" to "axminions.use.rotate",
            "statistics" to "axminions.use.statistics"
        )

        // Not a button, it is putting a tool in the minion or taking it out
        const val TOOL_PERMISSION = "axminions.use.tool"
    }

    private val coolDown = CoolDown<Player>()

    @EventHandler
    fun onInventoryDragEvent(event: InventoryDragEvent) {
        if (event.inventory.holder !is Minion) return

        event.isCancelled = true
    }

    @EventHandler
    fun onInventoryClickEvent(event: InventoryClickEvent) {
        val minion = event.inventory.holder as? Minion ?: return
        if (event.clickedInventory == null) return
        event.isCancelled = true
        val player = event.whoClicked as Player

        // The type got disabled while the GUI was open
        if (!minion.getType().isEnabled()) {
            player.closeInventory()
            return
        }

        if (coolDown.contains(player)) {
            return
        }

        coolDown.add(player, 250)

        val allowedTools = arrayListOf<Material>()
        run breaking@{
            minion.getType().getConfig().getStringList("tool.material").fastFor {
                if (it.equals("*")) {
                    allowedTools.addAll(Material.entries)
                    return@breaking
                }

                allowedTools.add(Material.matchMaterial(it) ?: return@fastFor)
            }
        }

        if (event.clickedInventory == player.inventory && event.currentItem != null) {
            // A charge item in the inventory charges the minion, it is not a tool
            if (chargeWithItem(player, minion, event.currentItem!!, event.isShiftClick)) {
                minion.updateInventories()
                return
            }

            if (event.currentItem!!.type !in allowedTools) {
                player.sendMessage(StringUtils.formatToString(Messages.PREFIX() + Messages.WRONG_TOOL()))
                return
            }

            if (denied(player, minion, TrustPermission.TOOL)) return
            if (noPermission(player, TOOL_PERMISSION)) return

            val minionToolEvent = MinionToolEvent(minion, player, event.currentItem!!.clone(), minion.getTool()!!.clone())
            Bukkit.getPluginManager().callEvent(minionToolEvent)
            if (minionToolEvent.isCancelled) return

            if (minion.getTool()?.type != Material.AIR) {
                val current = minionToolEvent.newTool
                val tool = minionToolEvent.oldTool
                minion.setTool(current)
                minion.updateArmour()
                event.currentItem!!.amount = 0
                event.clickedInventory!!.addItem(tool)
            } else {
                minion.setTool(minionToolEvent.newTool)
                event.currentItem!!.amount = 0
            }

            minion.updateInventories()
            return
        }

        if (event.slot == AxMinionsAPI.INSTANCE.getConfig().get("gui.items.item.slot")) {
            if (minion.getTool()?.type == Material.AIR) return
            if (denied(player, minion, TrustPermission.TOOL)) return
            if (noPermission(player, TOOL_PERMISSION)) return
            if (player.inventory.firstEmpty() == -1) {
                player.sendMessage(StringUtils.formatToString(Messages.PREFIX() + Messages.ERROR_INVENTORY_FULL()))
                return
            }
            val tool = minion.getTool()?.clone() ?: return

            val minionToolEvent = MinionToolEvent(minion, player, ItemStack(Material.AIR), tool)
            Bukkit.getPluginManager().callEvent(minionToolEvent)
            if (minionToolEvent.isCancelled) return

            minion.setTool(ItemStack(Material.AIR))
            minion.updateArmour()
            val toolMeta = minionToolEvent.oldTool.itemMeta ?: return
            toolMeta.persistentDataContainer.remove(Keys.GUI)
            minionToolEvent.oldTool.setItemMeta(toolMeta)

            player.inventory.addItem(minionToolEvent.oldTool)
            minion.updateInventories()
            return
        }

        if (!(event.clickedInventory?.getItem(event.slot)?.hasItemMeta() ?: return)) {
            return
        }

        val meta = event.clickedInventory?.getItem(event.slot)?.itemMeta ?: return
        if (!meta.persistentDataContainer.has(Keys.GUI, PersistentDataType.STRING)) return
        val type = meta.persistentDataContainer.get(Keys.GUI, PersistentDataType.STRING)

        val trustPermission = type?.let { TRUST_PERMISSIONS[it] }
        if (trustPermission != null && denied(player, minion, trustPermission)) return

        val permission = type?.let { ACTION_PERMISSIONS[it] }
        if (permission != null && noPermission(player, permission)) return

        when (type) {
            "rotate" -> {
                when (minion.getDirection()) {
                    Direction.NORTH -> minion.setDirection(Direction.WEST)
                    Direction.EAST -> minion.setDirection(Direction.NORTH)
                    Direction.SOUTH -> minion.setDirection(Direction.EAST)
                    Direction.WEST -> minion.setDirection(Direction.SOUTH)
                }
            }

            "toggle" -> {
                minion.setWorking(!minion.isWorking())
            }

            "link" -> {
                if (event.isRightClick) {
                    // The container that was linked last
                    val chest = minion.getLinkedChests().lastOrNull() ?: return

                    minion.removeLinkedChest(chest)
                    player.sendMessage(StringUtils.formatToString(Messages.PREFIX() + Messages.LINK_UNLINK()))
                } else if (event.isLeftClick) {
                    // A minion that can have one container swaps it, the others have to unlink one first
                    val max = minion.getMaxLinkedChests()
                    if (max > 1 && minion.getLinkedChests().size >= max) {
                        player.sendMessage(
                            StringUtils.formatToString(
                                Messages.PREFIX() + Messages.LINK_MAX_REACHED(),
                                Placeholder.unparsed("max", max.toString()),
                                Placeholder.unparsed("level", minion.getLevel().toString())
                            )
                        )
                        return
                    }

                    player.sendMessage(StringUtils.formatToString(Messages.PREFIX() + Messages.LINK_START()))
                    LinkingListener.linking[player] = minion
                    player.closeInventory()
                } else {
                    return
                }
            }

            "upgrade" -> {
                val money = minion.getType().getDouble("requirements.money", minion.getLevel() + 1)
                val actions = minion.getType().getDouble("requirements.actions", minion.getLevel() + 1)

                if (minion.getType().hasReachedMaxLevel(minion)) {
                    return
                }

                if (minion.getActionAmount() < actions) {
                    sendFail(player)
                    return
                }

                AxMinionsPlugin.integrations.getEconomyIntegration()?.let {
                    if (it.getBalance(player) < money) {
                        sendFail(player)
                        return
                    }

                    it.takeBalance(player, money)
                }

                if (Config.UPGRADE_SOUND().isNotBlank()) {
                    player.playSound(
                        player,
                        Sound.valueOf(Config.UPGRADE_SOUND().uppercase(Locale.ENGLISH)),
                        1.0f,
                        1.0f
                    )
                }

                val containers = minion.getMaxLinkedChests()
                minion.setLevel(minion.getLevel() + 1)

                if (minion.getMaxLinkedChests() > containers) {
                    player.sendMessage(
                        StringUtils.formatToString(
                            Messages.PREFIX() + Messages.UPGRADE_CONTAINERS_UNLOCKED(),
                            Placeholder.unparsed("max", minion.getMaxLinkedChests().toString())
                        )
                    )
                }
            }

            "statistics" -> {
                val stored = minion.getStorage()

                if (stored == 0.0) {
                    return
                }

                if (minion.getType() == MinionTypes.getMinionTypes()["seller"]) {
                    AxMinionsPlugin.integrations.getEconomyIntegration()?.let {
                        minion.getOwner()?.let { player ->
                            it.giveBalance(player, stored)
                            minion.setStorage(0.0)
                        }
                    }
                } else {
                    player.giveExp(stored.toInt())
                    minion.setStorage(0.0)
                }

                AxMinionsPlugin.dataQueue.submit {
                    AxMinionsPlugin.dataHandler.saveMinion(minion)
                }
            }

            "charge" -> {
                if (event.isShiftClick) {
                    val itemCache = mutableListOf<Pair<ItemStack, String>>()
                    val section = Config.CHARGE_ITEMS()

                    for (key in section.keys) {
                        itemCache.add(Pair(ItemBuilder.create(section.getSection(key.toString())).get(), key.toString()))
                    }

                    while (true) {
                        val chargeSeconds = (minion.getCharge() - System.currentTimeMillis()) / 1000

                        if ((Config.MAX_CHARGE() * 60) - chargeSeconds < Config.MINIMUM_CHARGE()) {
                            player.sendMessage(StringUtils.formatToString(Messages.PREFIX() + Messages.CHARGE_NOT_ENOUGH_TIME_PASSED()))
                            return
                        }

                        var chargeAmount = Config.CHARGE_AMOUNT()
                        var itemCharge = false

                        for (pair in itemCache) {
                            if (player.inventory.containsAtLeast(pair.first, 1)) {
                                itemCharge = true
                                chargeAmount = section.getSection(pair.second).getInt("charge")
                                player.inventory.removeItem(pair.first)
                                break
                            }
                        }

                        if (!itemCharge && !payCharge(player)) return

                        // A free charge of nothing would never end
                        if (chargeAmount <= 0) return

                        if (!addCharge(player, minion, chargeSeconds, chargeAmount)) return
                    }
                } else {
                    val chargeSeconds = (minion.getCharge() - System.currentTimeMillis()) / 1000

                    if ((Config.MAX_CHARGE() * 60) - chargeSeconds < Config.MINIMUM_CHARGE()) {
                        player.sendMessage(StringUtils.formatToString(Messages.PREFIX() + Messages.CHARGE_NOT_ENOUGH_TIME_PASSED()))
                        return
                    }

                    var chargeAmount = Config.CHARGE_AMOUNT()
                    var itemCharge = false
                    val section = Config.CHARGE_ITEMS()

                    for (key in section.keys) {
                        val item = ItemBuilder.create(section.getSection(key.toString())).get()
                        if (player.inventory.containsAtLeast(item, 1)) {
                            itemCharge = true
                            chargeAmount = section.getSection(key.toString()).getInt("charge")
                            player.inventory.removeItem(item)
                            break
                        }
                    }

                    if (!itemCharge && !payCharge(player)) return

                    addCharge(player, minion, chargeSeconds, chargeAmount)
                }
            }
        }

        minion.updateInventories()
    }

    // Adds the charge to the minion, returns false if it is full now, so there is no point in charging it more
    private fun addCharge(player: Player, minion: Minion, chargeSeconds: Long, chargeAmount: Int): Boolean {
        if (chargeSeconds + chargeAmount > Config.MAX_CHARGE() * 60L) {
            minion.setCharge(System.currentTimeMillis() + Config.MAX_CHARGE() * 60L * 1000L)
            return false
        }

        if (minion.getCharge() < System.currentTimeMillis()) {
            minion.setCharge(System.currentTimeMillis() + chargeAmount * 1000)
        } else {
            minion.setCharge(minion.getCharge() + chargeAmount * 1000)
        }

        if (Messages.CHARGE().isNotBlank()) {
            player.sendMessage(StringUtils.formatToString(Messages.PREFIX() + Messages.CHARGE()))
        }

        return true
    }

    // Clicking a charge item (charge.items in the config.yml) in the inventory charges the minion with it, like the
    // charge button does. Shift-click uses as many of them as the minion takes. Returns false if the item is not a charge item.
    private fun chargeWithItem(player: Player, minion: Minion, clicked: ItemStack, all: Boolean): Boolean {
        if (!Config.CHARGE_ENABLED()) return false

        val section = Config.CHARGE_ITEMS()
        var item: ItemStack? = null
        var chargeAmount = 0
        for (key in section.keys) {
            val candidate = ItemBuilder.create(section.getSection(key.toString())).get()
            if (candidate.isSimilar(clicked)) {
                item = candidate
                chargeAmount = section.getSection(key.toString()).getInt("charge")
                break
            }
        }

        if (item == null) return false

        if (denied(player, minion, TrustPermission.CHARGE)) return true
        if (noPermission(player, ACTION_PERMISSIONS.getValue("charge"))) return true

        while (true) {
            val chargeSeconds = (minion.getCharge() - System.currentTimeMillis()) / 1000

            if ((Config.MAX_CHARGE() * 60) - chargeSeconds < Config.MINIMUM_CHARGE()) {
                player.sendMessage(StringUtils.formatToString(Messages.PREFIX() + Messages.CHARGE_NOT_ENOUGH_TIME_PASSED()))
                break
            }

            // A charge of nothing would never end
            if (chargeAmount <= 0 || !player.inventory.containsAtLeast(item, 1)) break

            player.inventory.removeItem(item)
            if (!addCharge(player, minion, chargeSeconds, chargeAmount) || !all) break
        }

        return true
    }

    @EventHandler
    fun onInventoryCloseEvent(event: InventoryCloseEvent) {
        val holder = event.inventory.holder as? Minion ?: return

        holder.removeOpenInventory(event.inventory)
    }

    // Takes the price of a charge when no charge item was used. Returns false (and tells the player) if it can't be paid.
    // A price of 0 turns paying with money off, so only the charge items work, and a price below 0 is free
    private fun payCharge(player: Player): Boolean {
        val price = Config.CHARGE_PRICE()
        if (price < 0) return true

        if (price == 0.0) {
            player.sendMessage(StringUtils.formatToString(Messages.PREFIX() + Messages.CHARGE_ONLY_ITEMS()))
            return false
        }

        val economy = AxMinionsPlugin.integrations.getEconomyIntegration() ?: return false
        if (economy.getBalance(player) < price) {
            player.sendMessage(StringUtils.formatToString(Messages.PREFIX() + Messages.CHARGE_FAIL()))
            return false
        }

        // The player who charges pays, which is not the owner when the player is trusted
        economy.takeBalance(player, price)
        return true
    }

    // Returns true (and tells the player) if they can open this menu only because the owner trusts them, and the owner didn't allow this
    private fun denied(player: Player, minion: Minion, permission: TrustPermission): Boolean {
        if (minion.hasFullAccess(player) || AxMinionsPlugin.users.hasPermission(minion.getOwnerUUID(), player.uniqueId, permission)) {
            return false
        }

        player.sendMessage(
            StringUtils.formatToString(
                Messages.PREFIX() + Messages.USERS_NO_PERMISSION(),
                Placeholder.unparsed("permission", AxMinionsPlugin.users.getLabel(permission))
            )
        )
        return true
    }

    // Returns true (and tells the player) if the server took the axminions.use.<action> permission away from them
    private fun noPermission(player: Player, permission: String): Boolean {
        if (player.hasPermission(permission)) {
            return false
        }

        player.sendMessage(StringUtils.formatToString(Messages.PREFIX() + Messages.ERROR_NO_PERMISSION()))
        return true
    }

    private fun sendFail(player: Player) {
        when (Config.UPGRADE_FAIL()) {
            "title" -> {
                player.closeInventory()
                player.sendTitle(StringUtils.formatToString(Messages.UPGRADE_FAIL()), "", 10, 70, 20)
            }

            "subtitle" -> {
                player.closeInventory()
                player.sendTitle("", StringUtils.formatToString(Messages.UPGRADE_FAIL()), 10, 70, 20)
            }

            "actionbar" -> {
                player.closeInventory()
                player.spigot().sendMessage(
                    ChatMessageType.ACTION_BAR,
                    *TextComponent.fromLegacyText(StringUtils.formatToString(Messages.UPGRADE_FAIL()))
                )
            }

            else -> {
                player.sendMessage(StringUtils.formatToString(Messages.PREFIX() + Messages.UPGRADE_FAIL()))
            }
        }
    }
}