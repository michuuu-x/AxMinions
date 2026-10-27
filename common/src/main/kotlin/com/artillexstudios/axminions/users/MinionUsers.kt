package com.artillexstudios.axminions.users

import com.artillexstudios.axapi.config.Config
import com.artillexstudios.axapi.libs.boostedyaml.dvs.versioning.BasicVersioning
import com.artillexstudios.axapi.libs.boostedyaml.settings.dumper.DumperSettings
import com.artillexstudios.axapi.libs.boostedyaml.settings.general.GeneralSettings
import com.artillexstudios.axapi.libs.boostedyaml.settings.loader.LoaderSettings
import com.artillexstudios.axapi.libs.boostedyaml.settings.updater.UpdaterSettings
import com.artillexstudios.axapi.scheduler.Scheduler
import com.artillexstudios.axapi.utils.ItemBuilder
import com.artillexstudios.axapi.utils.StringUtils
import com.artillexstudios.axminions.AxMinionsPlugin
import com.artillexstudios.axminions.api.config.Messages
import com.artillexstudios.axminions.api.minions.Minion
import com.artillexstudios.axminions.utils.HeadTextures
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import java.io.File
import java.io.InputStream
import java.util.EnumSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * The players an owner trusts, and the menus opened by /minions users where they can be managed.
 * A trusted player can open the menu of every minion of the owner, what else they can do is up to the owner,
 * see [TrustPermission]. [Minion.hasFullAccess] is what is always left to the owner (and to the ones with full access).
 */
class MinionUsers(file: File, stream: InputStream) {
    abstract class MenuHolder(val owner: UUID) : InventoryHolder {
        private lateinit var inventory: Inventory

        internal fun setInventory(inventory: Inventory) {
            this.inventory = inventory
        }

        override fun getInventory(): Inventory {
            return inventory
        }
    }

    /**
     * The list of the trusted players.
     */
    class Holder(owner: UUID) : MenuHolder(owner) {
        var page = 0

        // The trusted player shown in a slot, filled by the last render
        internal val shown = HashMap<Int, UUID>()
    }

    /**
     * The permissions of one trusted player, [page] is the page of the list it was opened from.
     */
    class PermissionsHolder(owner: UUID, val user: UUID, val page: Int) : MenuHolder(owner) {
        // The permission shown in a slot, filled by the last render
        internal val shown = HashMap<Int, TrustPermission>()
    }

    private val config = Config(
        file,
        stream,
        GeneralSettings.builder().setUseDefaults(false).build(),
        LoaderSettings.builder().setAutoUpdate(true).build(),
        DumperSettings.DEFAULT,
        UpdaterSettings.builder().setVersioning(BasicVersioning("config-version")).build()
    )

    // Read by the minions on every click, so this has to stay in memory.
    // The permissions of a player are never changed, the set gets replaced by a new one.
    private val trusted = ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, Set<TrustPermission>>>()

    // The players who are about to type the name of a new trusted player in the chat, with the time the prompt runs out
    private val prompts = ConcurrentHashMap<UUID, Long>()

    private var size = 27
    private var userSlots = listOf<Int>()
    private var addSlot = 4
    private var backSlot = 18
    private var nextSlot = 26

    private var permissionsSize = 36
    private var permissionsBackSlot = 27

    // What a new trusted player is allowed to do
    private var defaults: Set<TrustPermission> = emptySet()

    init {
        load()

        for ((owner, users) in AxMinionsPlugin.dataHandler.loadTrustedUsers()) {
            val map = ConcurrentHashMap<UUID, Set<TrustPermission>>()
            for ((user, saved) in users) {
                map[user] = TrustPermission.parse(saved) ?: defaults
            }

            trusted[owner] = map
        }
    }

    fun reload() {
        config.reload()
        load()
    }

    private fun load() {
        size = (config.getInt("size", 27).coerceIn(18, 54) + 8) / 9 * 9
        addSlot = config.getInt("add.slot", 4)
        backSlot = config.getInt("back.slot", 18)
        nextSlot = config.getInt("next.slot", 26)

        permissionsSize = (config.getInt("permissions-menu.size", 36).coerceIn(18, 54) + 8) / 9 * 9
        permissionsBackSlot = config.getInt("permissions-menu.back.slot", 27)

        val permissions = EnumSet.noneOf(TrustPermission::class.java)
        for (key in config.getStringList("default-permissions", listOf("upgrade", "charge", "toggle"))) {
            val permission = TrustPermission.fromKey(key)
            if (permission == null) {
                AxMinionsPlugin.INSTANCE.logger.warning("users.yml: '$key' is not a valid permission in default-permissions, skipping it!")
                continue
            }

            permissions.add(permission)
        }

        defaults = permissions

        val slots = linkedSetOf<Int>()
        for (entry in config.getStringList("user-slots", listOf("9-17"))) {
            val range = entry.split('-', limit = 2)
            val from = range[0].trim().toIntOrNull()
            val to = (range.getOrNull(1) ?: range[0]).trim().toIntOrNull()
            if (from == null || to == null || from > to) {
                AxMinionsPlugin.INSTANCE.logger.warning("users.yml: '$entry' is not a valid slot or slot range, skipping it!")
                continue
            }

            for (slot in from..to) {
                if (slot < 0 || slot >= size) {
                    AxMinionsPlugin.INSTANCE.logger.warning("users.yml: the slot $slot is outside of the menu, skipping it!")
                    continue
                }

                slots.add(slot)
            }
        }

        if (slots.isEmpty()) {
            AxMinionsPlugin.INSTANCE.logger.warning("users.yml: there are no valid user-slots, using the second row!")
            slots.addAll(9..17)
        }

        userSlots = slots.toList()
    }

    fun isTrusted(owner: UUID, player: UUID): Boolean {
        return trusted[owner]?.containsKey(player) == true
    }

    /**
     * Returns if the owner allowed the player to do this, a player who isn't trusted has no permissions at all.
     */
    fun hasPermission(owner: UUID, player: UUID, permission: TrustPermission): Boolean {
        return trusted[owner]?.get(player)?.contains(permission) == true
    }

    fun getTrusted(owner: UUID): List<UUID> {
        return trusted[owner]?.keys?.toList() ?: emptyList()
    }

    fun getTrustedCount(owner: UUID): Int {
        return trusted[owner]?.size ?: 0
    }

    /**
     * The name shown for the permission, in the message sent to the player who isn't allowed to do it.
     */
    fun getLabel(permission: TrustPermission): String {
        return config.getString("permissions-menu.permissions.${permission.key}.label", permission.key)
    }

    fun open(player: Player, page: Int = 0) {
        val holder = Holder(player.uniqueId)
        holder.page = page
        val inventory = Bukkit.createInventory(holder, size, StringUtils.formatToString(config.getString("title", "Trusted Players")))
        holder.setInventory(inventory)

        render(holder)
        player.openInventory(inventory)
    }

    fun click(player: Player, holder: MenuHolder, slot: Int, click: ClickType) {
        when (holder) {
            is Holder -> clickList(player, holder, slot, click)
            is PermissionsHolder -> clickPermissions(player, holder, slot)
        }
    }

    private fun clickList(player: Player, holder: Holder, slot: Int, click: ClickType) {
        when (slot) {
            addSlot -> {
                startPrompt(player)
                return
            }

            backSlot -> {
                if (holder.page == 0) {
                    player.closeInventory()
                    return
                }

                holder.page--
            }

            nextSlot -> holder.page++
            else -> {
                val user = holder.shown[slot] ?: return

                // Shift + Right Click removes the player, Left Click opens what they are allowed to do
                if (click.isShiftClick && click.isRightClick) {
                    remove(holder.owner, user)
                } else if (click.isLeftClick) {
                    openPermissions(player, holder.owner, user, holder.page)
                    return
                } else {
                    return
                }
            }
        }

        render(holder)
    }

    private fun clickPermissions(player: Player, holder: PermissionsHolder, slot: Int) {
        if (slot == permissionsBackSlot) {
            open(player, holder.page)
            return
        }

        val permission = holder.shown[slot] ?: return

        // The player was removed while the menu was open
        if (!toggle(holder.owner, holder.user, permission)) {
            open(player, holder.page)
            return
        }

        renderPermissions(holder)
    }

    private fun openPermissions(player: Player, owner: UUID, user: UUID, page: Int) {
        if (!isTrusted(owner, user)) return

        val name = Bukkit.getOfflinePlayer(user).name ?: "???"
        val holder = PermissionsHolder(owner, user, page)
        val title = StringUtils.formatToString(
            config.getString("permissions-menu.title", "<#5555FF>Permissions: <player>"),
            Placeholder.unparsed("player", name)
        )

        val inventory = Bukkit.createInventory(holder, permissionsSize, title)
        holder.setInventory(inventory)

        renderPermissions(holder)
        player.openInventory(inventory)
    }

    /**
     * Switches the permission of the trusted player, returns false if the player isn't trusted (anymore).
     */
    private fun toggle(owner: UUID, user: UUID, permission: TrustPermission): Boolean {
        val users = trusted[owner] ?: return false

        var updated: Set<TrustPermission>? = null
        users.computeIfPresent(user) { _, current ->
            val next = EnumSet.noneOf(TrustPermission::class.java)
            next.addAll(current)
            if (!next.remove(permission)) {
                next.add(permission)
            }

            updated = next
            next
        }

        val permissions = updated ?: return false
        AxMinionsPlugin.dataQueue.submit {
            AxMinionsPlugin.dataHandler.setTrustedPermissions(owner, user, TrustPermission.serialize(permissions))
        }

        return true
    }

    private fun startPrompt(player: Player) {
        val timeout = config.getInt("add.timeout", 30).coerceAtLeast(1)
        prompts[player.uniqueId] = System.currentTimeMillis() + timeout * 1000L

        player.closeInventory()
        player.sendMessage(
            StringUtils.formatToString(
                Messages.PREFIX() + Messages.USERS_PROMPT(),
                Placeholder.unparsed("seconds", timeout.toString())
            )
        )
    }

    fun cancelPrompt(player: UUID) {
        prompts.remove(player)
    }

    /**
     * Gets the chat messages of players with a running prompt, returns true if the message was used as one.
     * Runs on the thread of the chat, which is not the one of the player.
     */
    fun handleChat(player: Player, message: String): Boolean {
        val expires = prompts.remove(player.uniqueId) ?: return false
        // The player forgot about the prompt, so this is just a normal message
        if (System.currentTimeMillis() > expires) return false

        val name = message.trim()
        if (!name.equals("cancel", true)) {
            add(player, name)
        } else {
            player.sendMessage(StringUtils.formatToString(Messages.PREFIX() + Messages.USERS_CANCELLED()))
        }

        // The menu belongs to the thread of the player
        Scheduler.get().run(player, { open(player) }, {})
        return true
    }

    private fun add(player: Player, name: String) {
        val target = find(name)
        if (target == null) {
            player.sendMessage(
                StringUtils.formatToString(
                    Messages.PREFIX() + Messages.USERS_NOT_FOUND(),
                    Placeholder.unparsed("player", name)
                )
            )
            return
        }

        val targetName = target.name ?: name
        val resolver = Placeholder.unparsed("player", targetName)

        if (target.uniqueId == player.uniqueId) {
            player.sendMessage(StringUtils.formatToString(Messages.PREFIX() + Messages.USERS_SELF(), resolver))
            return
        }

        val permissions = defaults
        if (trusted.getOrPut(player.uniqueId) { ConcurrentHashMap() }.putIfAbsent(target.uniqueId, permissions) != null) {
            player.sendMessage(StringUtils.formatToString(Messages.PREFIX() + Messages.USERS_ALREADY_TRUSTED(), resolver))
            return
        }

        AxMinionsPlugin.dataQueue.submit {
            AxMinionsPlugin.dataHandler.addTrustedUser(player.uniqueId, target.uniqueId, TrustPermission.serialize(permissions))
        }

        player.sendMessage(StringUtils.formatToString(Messages.PREFIX() + Messages.USERS_ADDED(), resolver))
    }

    private fun remove(owner: UUID, user: UUID) {
        val users = trusted[owner] ?: return
        if (users.remove(user) == null) return

        AxMinionsPlugin.dataQueue.submit {
            AxMinionsPlugin.dataHandler.removeTrustedUser(owner, user)
        }

        val name = Bukkit.getOfflinePlayer(user).name ?: "???"
        Bukkit.getPlayer(owner)?.sendMessage(
            StringUtils.formatToString(
                Messages.PREFIX() + Messages.USERS_REMOVED(),
                Placeholder.unparsed("player", name)
            )
        )

        // Whoever is using a minion of the owner only because of the trust has to stop now
        val removed = Bukkit.getPlayer(user) ?: return
        Scheduler.get().run(removed, {
            val holder = removed.openInventory.topInventory.holder
            if (holder is Minion && holder.getOwnerUUID() == owner && !holder.hasFullAccess(removed)) {
                removed.closeInventory()
            }
        }, {})
    }

    /**
     * Finds the player by the name they typed, only players who joined the server before are valid.
     * This can ask the profile cache or the servers of Mojang, so it must never run on the main thread.
     */
    private fun find(name: String): OfflinePlayer? {
        Bukkit.getPlayerExact(name)?.let { return it }

        @Suppress("DEPRECATION")
        val offline = Bukkit.getOfflinePlayer(name)
        return if (offline.hasPlayedBefore()) offline else null
    }

    /**
     * Fills the menu of the holder with the current trusted players, the page gets clamped to the existing ones.
     */
    private fun render(holder: Holder) {
        val users = getTrusted(holder.owner)
            .map { it to (Bukkit.getOfflinePlayer(it).name ?: "???") }
            .sortedBy { it.second.lowercase() }

        val perPage = userSlots.size
        val pages = ((users.size + perPage - 1) / perPage).coerceAtLeast(1)
        holder.page = holder.page.coerceIn(0, pages - 1)

        val inventory = holder.inventory
        val filler = HeadTextures.item(config.getSection("filler")).get()
        for (i in 0 until inventory.size) {
            inventory.setItem(i, filler)
        }

        holder.shown.clear()
        users.drop(holder.page * perPage).take(perPage).forEachIndexed { index, (uuid, name) ->
            val slot = userSlots[index]
            // The menu can be smaller than it was when it got opened, if the config got reloaded
            if (slot < inventory.size) {
                holder.shown[slot] = uuid
                inventory.setItem(slot, createHead(holder.owner, uuid, name, "user"))
            }
        }

        val resolvers = arrayOf<TagResolver>(
            Placeholder.unparsed("page", (holder.page + 1).toString()),
            Placeholder.unparsed("pages", pages.toString())
        )

        place(inventory, addSlot, HeadTextures.item(config.getSection("add.item")).clonedGet())
        place(inventory, backSlot, HeadTextures.item(config.getSection("back.item"), *resolvers).clonedGet())
        if (holder.page < pages - 1) {
            place(inventory, nextSlot, HeadTextures.item(config.getSection("next.item"), *resolvers).clonedGet())
        }
    }

    /**
     * Fills the permission menu with the head of the player and what they are allowed to do.
     */
    private fun renderPermissions(holder: PermissionsHolder) {
        val granted = trusted[holder.owner]?.get(holder.user) ?: emptySet()
        val name = Bukkit.getOfflinePlayer(holder.user).name ?: "???"

        val inventory = holder.inventory
        val filler = HeadTextures.item(config.getSection("filler")).get()
        for (i in 0 until inventory.size) {
            inventory.setItem(i, filler)
        }

        place(
            inventory,
            config.getInt("permissions-menu.head.slot", 4),
            createHead(holder.owner, holder.user, name, "permissions-menu.head")
        )
        place(inventory, permissionsBackSlot, HeadTextures.item(config.getSection("permissions-menu.back.item")).clonedGet())

        holder.shown.clear()
        for (permission in TrustPermission.entries) {
            val section = config.getSection("permissions-menu.permissions.${permission.key}") ?: continue
            val item = section.getSection("item") ?: continue
            val slot = section.getInt("slot", -1)
            if (slot < 0 || slot >= inventory.size) continue

            val enabled = permission in granted
            val status = if (enabled) {
                config.getString("permissions-menu.status.enabled", "<#33FF33>Enabled")
            } else {
                config.getString("permissions-menu.status.disabled", "<#FF3333>Disabled")
            }

            holder.shown[slot] = permission
            inventory.setItem(
                slot,
                HeadTextures.item(
                    item,
                    Placeholder.unparsed("player", name),
                    Placeholder.parsed("status", status)
                ).glow(enabled).clonedGet()
            )
        }
    }

    private fun place(inventory: Inventory, slot: Int, item: ItemStack) {
        if (slot >= 0 && slot < inventory.size) {
            inventory.setItem(slot, item)
        }
    }

    /**
     * The head of a trusted player, the name and the lore are in the section of the route.
     * Placeholders: <player>, <status> (online or offline), <permissions> (how many they have), <total>
     */
    private fun createHead(owner: UUID, uuid: UUID, name: String, route: String): ItemStack {
        val status = if (Bukkit.getPlayer(uuid) != null) {
            config.getString("status.online", "<#33FF33>Online")
        } else {
            config.getString("status.offline", "<#AAAAAA>Offline")
        }

        val resolvers = arrayOf<TagResolver>(
            Placeholder.unparsed("player", name),
            Placeholder.parsed("status", status),
            Placeholder.unparsed("permissions", (trusted[owner]?.get(uuid)?.size ?: 0).toString()),
            Placeholder.unparsed("total", TrustPermission.entries.size.toString())
        )

        val item = ItemBuilder.create(Material.PLAYER_HEAD)
            .setName(config.getString("$route.name", "<player>"), *resolvers)
            .setLore(config.getStringList("$route.lore", emptyList()), *resolvers)
            .clonedGet()

        val meta = item.itemMeta as? SkullMeta ?: return item
        meta.owningPlayer = Bukkit.getOfflinePlayer(uuid)
        item.itemMeta = meta
        return item
    }
}
