package com.artillexstudios.axminions.minions

import com.artillexstudios.axapi.events.PacketEntityInteractEvent
import com.artillexstudios.axapi.hologram.Hologram
import com.artillexstudios.axapi.hologram.HologramType
import com.artillexstudios.axapi.hologram.HologramTypes
import com.artillexstudios.axapi.hologram.page.HologramPage
import com.artillexstudios.axapi.hologram.page.TextDisplayHologramPage
import com.artillexstudios.axapi.packetentity.meta.entity.DisplayMeta
import com.artillexstudios.axapi.packetentity.meta.entity.TextDisplayMeta
import com.artillexstudios.axapi.items.WrappedItemStack
import com.artillexstudios.axapi.nms.NMSHandlers
import com.artillexstudios.axapi.packetentity.PacketEntity
import com.artillexstudios.axapi.packetentity.meta.entity.ArmorStandMeta
import com.artillexstudios.axapi.packetentity.meta.serializer.Accessors
import com.artillexstudios.axapi.scheduler.Scheduler
import com.artillexstudios.axapi.utils.EquipmentSlot
import com.artillexstudios.axapi.utils.ItemBuilder
import com.artillexstudios.axapi.utils.StringUtils
import com.artillexstudios.axminions.AxMinionsPlugin
import com.artillexstudios.axminions.api.AxMinionsAPI
import com.artillexstudios.axminions.api.config.Config
import com.artillexstudios.axminions.api.config.Messages
import com.artillexstudios.axminions.api.events.PreMinionPickupEvent
import com.artillexstudios.axminions.api.minions.Direction
import com.artillexstudios.axminions.api.minions.Minion
import com.artillexstudios.axminions.api.minions.miniontype.MinionType
import com.artillexstudios.axminions.api.minions.miniontype.MinionTypes
import com.artillexstudios.axminions.api.utils.Keys
import com.artillexstudios.axminions.api.utils.NumberFormatUtils
import com.artillexstudios.axminions.api.utils.MinionUtils
import com.artillexstudios.axminions.api.utils.TimeUtils
import com.artillexstudios.axminions.api.utils.fastFor
import com.artillexstudios.axminions.api.hologram.HologramStyler
import com.artillexstudios.axminions.api.warnings.OwnerAlerts
import com.artillexstudios.axminions.api.warnings.Warning
import com.artillexstudios.axminions.api.warnings.Warnings
import com.artillexstudios.axminions.listeners.LinkingListener
import com.artillexstudios.axminions.utils.Enchantments
import com.artillexstudios.axminions.utils.PlayerStats
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.block.Container
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.entity.Pose
import org.bukkit.inventory.DoubleChestInventory
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.EulerAngle
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

class Minion(
    private var location: Location,
    private val ownerUUID: UUID,
    private val owner: OfflinePlayer,
    private val type: MinionType,
    private var level: Int,
    private var tool: ItemStack?,
    linkedChest: Location?,
    private var direction: Direction,
    private var actions: Long,
    private var storage: Double,
    private val locationID: Int,
    private var chestLocationId: Int,
    private var charge: Long,
    // A stopped minion does nothing, the owner can start it again in the menu
    @Volatile private var working: Boolean = true,
    // The containers after the first one, which is filled when the ones before it are full
    extraChests: List<Location> = emptyList()
) : Minion {
    companion object {
        private val equipmentSlots: Array<EquipmentSlot> = arrayOf(
            EquipmentSlot.MAIN_HAND,
            EquipmentSlot.OFF_HAND,
            EquipmentSlot.BOOTS,
            EquipmentSlot.LEGGINGS,
            EquipmentSlot.CHEST_PLATE,
            EquipmentSlot.HELMET
        )
        private val notDurable = arrayListOf<Material>()

        init {
            for (value in Material.entries) {
                if (value.maxDurability == 1.toShort() || value.maxDurability == 0.toShort()) {
                    notDurable.add(value)
                }
            }
        }
    }

    private lateinit var entity: PacketEntity
    private var nextAction = 0
    private var range = 0.0

    @Volatile
    private var dirty = true
    private var armTick = 2.0
    private var warning: Warning? = null
    private var hologram: Hologram? = null
    private var nameHologram: Hologram? = null
    private var statusHologram: Hologram? = null
    private val nameHologramViewers = HashSet<UUID>()
    private val statusHologramViewers = HashSet<UUID>()
    private val warningHologramViewers = HashSet<UUID>()
    private val extraData = hashMapOf<String, String>()
    // A linked container with its inventory, which is only there while the chunk of the container is loaded
    private class LinkedContainer(val location: Location) {
        @Volatile
        var inventory: Inventory? = null
    }

    // In the order they get filled, the first one is saved apart from the others
    private val containers = CopyOnWriteArrayList<LinkedContainer>()
    internal val openInventories = mutableListOf<Inventory>()
    private var toolMeta: ItemMeta? = null

    @Volatile
    private var ticking = false
    private var debugHologram: Hologram? = null
    val broken = AtomicBoolean(false)
    private var ownerOnline = false
    private var unbreakable = false

    init {
        linkedChest?.let { containers.add(LinkedContainer(it.clone())) }
        extraChests.forEach { containers.add(LinkedContainer(it.clone())) }
        spawn()
        Minions.load(this)
    }

    override fun getType(): MinionType {
        return this.type
    }

    override fun spawn() {
        location.x += 0.5
        location.z += 0.5
        entity = NMSHandlers.getNmsHandler().createEntity(EntityType.ARMOR_STAND, location)
        val meta = entity.meta() as ArmorStandMeta

        meta.setNoBasePlate(true)
        meta.small(true)
        meta.showArms(true)

        entity.onInteract { event ->
            if (broken.get()) {
                return@onInteract
            }

            // Left-click no longer does anything; pickup is Shift + Right Click
            if (event.isAttack) {
                return@onInteract
            }

            val pickup = event.player.isSneaking

            // We want to do this, so we don't accidentally cause dupes...
            // Atomic variable; we don't want the scheduler to mess up the state
            if (pickup) {
                broken.set(true)
            }

            Scheduler.get().runAt(location) { task ->
                val canBuildAt = AxMinionsPlugin.integrations.getProtectionIntegration().canBuildAt(
                    event.player,
                    event.packetEntity.location()
                )

                if (pickup) {
                    if (!event.player.hasPermission("axminions.use.pickup")) {
                        broken.set(false)
                        event.player.sendMessage(StringUtils.formatToString(Messages.PREFIX() + Messages.ERROR_NO_PERMISSION()))
                    } else if (event.player.inventory.firstEmpty() == -1) {
                        broken.set(false)
                    } else {
                        if (ownerUUID == event.player.uniqueId) {
                            breakMinion(event)
                        } else if ((canBuildAt && !Config.ONLY_OWNER_BREAK()) || event.player.hasPermission("axminions.*")) {
                            breakMinion(event)
                        } else {
                            broken.set(false)
                        }
                    }
                } else if (!type.isEnabled()) {
                    event.player.sendMessage(
                        StringUtils.formatToString(
                            Messages.PREFIX() + Messages.MINION_TYPE_DISABLED(),
                            Placeholder.unparsed("type", type.getName())
                        )
                    )
                } else {
                    if (ownerUUID == event.player.uniqueId) {
                        openInventory(event.player)
                    } else if ((canBuildAt && !Config.ONLY_OWNER_GUI()) || event.player.hasPermission("axminions.*")) {
                        openInventory(event.player)
                    } else if (AxMinionsPlugin.users.isTrusted(ownerUUID, event.player.uniqueId)) {
                        openInventory(event.player)
                    } else {
                        event.player.sendMessage(StringUtils.formatToString(Messages.PREFIX() + Messages.USERS_NO_ACCESS()))
                    }
                }
            }
        }

        if (Config.DEBUG()) {
            debugHologram = Hologram(location.clone().add(0.0, 2.0, 0.0))
            val page = debugHologram?.createPage(HologramTypes.TEXT)
            page?.setEntityMetaHandler({ meta ->
                val textDisplayMeta = meta as TextDisplayMeta;
                textDisplayMeta.seeThrough(true);
                textDisplayMeta.alignment(TextDisplayMeta.Alignment.CENTER);
                textDisplayMeta.billboardConstrain(DisplayMeta.BillboardConstrain.CENTER);
            })
            page?.setContent(StringUtils.formatToString("ticking: $ticking"));
            page?.spawn();
        }

        meta.metadata().set(Accessors.POSE, Pose.STANDING)
        setDirection(direction, false)
        updateArmour()
        entity.spawn()

        val nameSection = Config.HOLOGRAM_NAME_SECTION()
        nameHologram = Hologram(HologramStyler.offset(location, nameSection))
        val namePage = nameHologram!!.createPage(HologramTypes.TEXT)
        namePage.setEntityMetaHandler { meta ->
            HologramStyler.apply(meta as TextDisplayMeta, nameSection)
        }
        HologramStyler.applyViewDistance(namePage, nameSection)
        namePage.content = StringUtils.formatToString(
            type.getConfig().get("entity.name"),
            Placeholder.unparsed("owner", owner.name ?: "???"),
            Placeholder.unparsed("level", level.toString()),
            Placeholder.parsed("level_color", Messages.LEVEL_COLOR(level))
        )
        namePage.spawn()

        // The status is its own hologram below the name, so the name and the warnings keep their place
        val statusSection = Config.HOLOGRAM_STATUS_SECTION()
        statusHologram = Hologram(HologramStyler.offset(location, statusSection))
        val statusPage = statusHologram!!.createPage(HologramTypes.TEXT)
        statusPage.setEntityMetaHandler { meta ->
            HologramStyler.apply(meta as TextDisplayMeta, statusSection)
        }
        HologramStyler.applyViewDistance(statusPage, statusSection)
        statusPage.content = statusText()
        statusPage.spawn()
    }

    private fun statusText(): String {
        return StringUtils.formatToString(if (working) Messages.STATUS_WORKING() else Messages.STATUS_STOPPED())
    }

    private fun breakMinion(event: PacketEntityInteractEvent) {
        val preBreakEvent = PreMinionPickupEvent(event.player, this)
        Bukkit.getPluginManager().callEvent(preBreakEvent)
        if (preBreakEvent.isCancelled) return

        LinkingListener.linking.remove(event.player)
        remove()
        setTicking(false)
        openInventories.fastFor { it.viewers.fastFor { viewer -> viewer.closeInventory() } }
        val tool = getTool()
        val asItem = getAsItem()
        val remaining = event.player.inventory.addItem(tool, asItem)

        if (getType() == MinionTypes.getMinionTypes()["seller"]) {
            AxMinionsPlugin.integrations.getEconomyIntegration()?.let {
                getOwner().let { player ->
                    it.giveBalance(player, storage)
                    setStorage(0.0)
                }
            }
        } else {
            owner.player?.let {
                it.giveExp(storage.toInt())
                setStorage(0.0)
            }
        }

        remaining.fastFor { _, i ->
            AxMinionsPlugin.integrations.getStackerIntegration().dropItemAt(i, i.amount, location)
        }
    }

    override fun tick() {
        if (dirty) {
            dirty = false
            type.onToolDirty(this)
        }

        if (Config.DEBUG() && debugHologram != null) {
            debugHologram!!.setContent(0, "Ticking: $ticking")
        }

        if (MinionTicker.getTick() % 20L == 0L) {
            updateHologramVisibility()
        }

        // Disabled types don't work, their minions can only be picked up
        if (!type.isEnabled()) {
            warning?.let { Warnings.remove(this, it) }
            return
        }

        // Stopped minions don't work, so they have nothing to complain about either
        if (!working) {
            warning?.let { Warnings.remove(this, it) }
            // Let a swing that is already going finish, so the arm doesn't freeze halfway
            animate()
            return
        }

        if (Config.CHARGE_ENABLED() && getCharge() < System.currentTimeMillis()) {
            Warnings.NO_CHARGE.display(this)
            return
        }

        Warnings.remove(this, Warnings.NO_CHARGE)

        Scheduler.get().executeAt(location) {
            type.tick(this)
        }
        animate()
    }

    private fun updateHologramVisibility() {
        val world = location.world ?: return
        val nameDistance = Config.HOLOGRAM_NAME_SECTION().getDouble("visibility-distance", 10.0) ?: 10.0
        val statusDistance = Config.HOLOGRAM_STATUS_SECTION().getDouble("visibility-distance", 10.0) ?: 10.0
        val warningsDistance = Config.HOLOGRAM_WARNINGS_SECTION().getDouble("visibility-distance", 10.0) ?: 10.0
        Bukkit.getOnlinePlayers().forEach { player ->
            if (player.world != world) return@forEach
            val distance = player.location.distance(location)
            nameHologram?.page(0)?.let { updateViewer(it, player, distance <= nameDistance, nameHologramViewers) }
            statusHologram?.page(0)?.let { updateViewer(it, player, distance <= statusDistance, statusHologramViewers) }
            hologram?.page(0)?.let { updateViewer(it, player, distance <= warningsDistance, warningHologramViewers) }
        }
    }

    private fun updateViewer(page: HologramPage<*, *>, player: Player, shouldSee: Boolean, viewers: MutableSet<UUID>) {
        val currentlySeeing = viewers.contains(player.uniqueId)
        if (shouldSee && !currentlySeeing) {
            HologramStyler.showTo(page, player)
            viewers.add(player.uniqueId)
        } else if (!shouldSee && currentlySeeing) {
            HologramStyler.hideTo(page, player)
            viewers.remove(player.uniqueId)
        }
    }

    override fun getLocation(): Location {
        return this.location
    }

    override fun updateInventories() {
        openInventories.fastFor {
            updateInventory(it)
        }
    }

    private fun updateInventory(inventory: Inventory) {
        AxMinionsAPI.INSTANCE.getConfig().getConfig().getSection("gui.items").getRoutesAsStrings(false).forEach {
            if (it.equals("filler")) return@forEach
            val item: ItemStack?
            if (it.equals("upgrade", true) || it.equals("statistics", true)) {
                val level = Placeholder.parsed("level", level.toString())
                val nextLevel = Placeholder.parsed(
                    "next_level", when (type.hasReachedMaxLevel(this)) {
                        true -> Messages.UPGRADES_MAX_LEVEL_REACHED()
                        else -> (this.level + 1).toString()
                    }
                )
                val nextStorage = Placeholder.parsed(
                    "next_storage",
                    if (type.hasReachedMaxLevel(this)) Messages.UPGRADES_MAX_LEVEL_REACHED() else type.getDouble(
                        "storage",
                        this.level + 1
                    ).toString()
                )
                val range = Placeholder.parsed("range", type.getDouble("range", this.level).toString())
                val nextRange = Placeholder.parsed(
                    "next_range",
                    if (type.hasReachedMaxLevel(this)) Messages.UPGRADES_MAX_LEVEL_REACHED() else type.getDouble(
                        "range",
                        this.level + 1
                    ).toString()
                )
                val chanceKillStackedAmount = Placeholder.parsed("chance_kill_stacked_amount", type.getDouble("chance-kill-stacked-amount", this.level).toString())
                val nextChanceKillStackedAmount = Placeholder.parsed(
                    "next_chance_kill_stacked_amount",
                    if (type.hasReachedMaxLevel(this)) Messages.UPGRADES_MAX_LEVEL_REACHED() else type.getDouble(
                        "chance-kill-stacked-amount",
                        this.level + 1
                    ).toString()
                )
                val stackedAmount = Placeholder.parsed("stacked_amount", type.getDouble("stacked-amount", this.level).toString())
                val nextStackedAmount = Placeholder.parsed(
                    "next_stacked_amount",
                    if (type.hasReachedMaxLevel(this)) Messages.UPGRADES_MAX_LEVEL_REACHED() else type.getDouble(
                        "stacked-amount",
                        this.level + 1
                    ).toString()
                )
                val extra = Placeholder.parsed("extra", type.getDouble("extra", this.level).toString())
                val nextExtra = Placeholder.parsed(
                    "next_extra",
                    if (type.hasReachedMaxLevel(this)) Messages.UPGRADES_MAX_LEVEL_REACHED() else type.getDouble(
                        "extra",
                        this.level + 1
                    ).toString()
                )
                val speed = Placeholder.parsed("speed", type.getDouble("speed", this.level).toString())
                val nextSpeed = Placeholder.parsed(
                    "next_speed",
                    if (type.hasReachedMaxLevel(this)) Messages.UPGRADES_MAX_LEVEL_REACHED() else type.getDouble(
                        "speed",
                        this.level + 1
                    ).toString()
                )
                val price = Placeholder.parsed(
                    "price",
                    if (type.hasReachedMaxLevel(this)) Messages.UPGRADES_MAX_LEVEL_REACHED() else NumberFormatUtils.format(
                        type.getDouble("requirements.money", this.level + 1)
                    )
                )
                val requiredActions =
                    Placeholder.parsed(
                        "required_actions",
                        if (type.hasReachedMaxLevel(this)) Messages.UPGRADES_MAX_LEVEL_REACHED() else type.getDouble(
                            "requirements.actions",
                            this.level + 1
                        ).toString()
                    )
                val stored = Placeholder.parsed("storage", NumberFormatUtils.format(storage))
                val actions = Placeholder.parsed("actions", actions.toString())
                val multiplier = Placeholder.parsed("multiplier", type.getDouble("multiplier", this.level).toString())
                val nextMultiplier = Placeholder.parsed(
                    "next_multiplier",
                    if (type.hasReachedMaxLevel(this)) Messages.UPGRADES_MAX_LEVEL_REACHED() else type.getDouble(
                        "multiplier",
                        this.level + 1
                    ).toString()
                )
                val containers = Placeholder.parsed("containers", getMaxLinkedChests().toString())
                val nextContainers = Placeholder.parsed(
                    "next_containers",
                    if (type.hasReachedMaxLevel(this)) Messages.UPGRADES_MAX_LEVEL_REACHED() else type.getContainers(this.level + 1).toString()
                )

                item = ItemBuilder.create(
                    type.getConfig().getSection("gui.$it"),
                    level,
                    nextLevel,
                    range,
                    nextRange,
                    extra,
                    nextExtra,
                    speed,
                    nextSpeed,
                    price,
                    requiredActions,
                    stored,
                    actions,
                    multiplier,
                    nextMultiplier,
                    nextStorage,
                    chanceKillStackedAmount,
                    nextChanceKillStackedAmount,
                    stackedAmount,
                    nextStackedAmount,
                    containers,
                    nextContainers
                ).get()

                val meta = item.itemMeta!!
                meta.persistentDataContainer.set(Keys.GUI, PersistentDataType.STRING, it)
                item.itemMeta = meta
            } else if (it.equals("item")) {
                item = tool?.clone() ?: ItemStack(Material.AIR)
            } else if (it.equals("toggle")) {
                // The button shows what a click does: "stop" while the minion works, "start" while it is stopped
                val button = AxMinionsAPI.INSTANCE.getConfig().getConfig()
                    .getSection("gui.items.toggle.${if (working) "stop" else "start"}")
                item = ItemBuilder.create(
                    button,
                    Placeholder.parsed("status", if (working) Messages.STATUS_WORKING() else Messages.STATUS_STOPPED())
                ).get()

                val meta = item.itemMeta!!
                meta.persistentDataContainer.set(Keys.GUI, PersistentDataType.STRING, it)
                item.itemMeta = meta
            } else if (it.equals("charge")) {
                if (Config.CHARGE_ENABLED()) {
                    val charge = Placeholder.parsed("charge", TimeUtils.format(charge - System.currentTimeMillis()))
                    item = ItemBuilder.create(
                        AxMinionsAPI.INSTANCE.getConfig().getConfig().getSection("gui.items.$it"),
                        charge
                    ).get()

                    val meta = item.itemMeta!!
                    meta.persistentDataContainer.set(Keys.GUI, PersistentDataType.STRING, it)
                    item.itemMeta = meta
                } else {
                    item = null
                }
            } else {
                val chests = getLinkedChests()
                val resolvers = arrayListOf<TagResolver>(
                    Placeholder.unparsed("direction", Messages.ROTATION_NAME(direction)),
                    // The container that Right-click unlinks, which is the only one for a minion that can have one
                    Placeholder.unparsed("linked", chests.lastOrNull()?.let { chest -> formatLocation(chest) } ?: "---"),
                    Placeholder.unparsed("linked_count", chests.size.toString()),
                    Placeholder.unparsed("max_containers", getMaxLinkedChests().toString())
                )
                // <linked_1>, <linked_2>... for a lore that lists every container, "---" for the ones that aren't linked
                for (index in 1..maxOf(10, chests.size)) {
                    resolvers.add(
                        Placeholder.unparsed("linked_$index", chests.getOrNull(index - 1)?.let { chest -> formatLocation(chest) } ?: "---")
                    )
                }

                item = ItemBuilder.create(
                    AxMinionsAPI.INSTANCE.getConfig().getConfig().getSection("gui.items.$it"),
                    *resolvers.toTypedArray()
                ).get()

                val meta = item.itemMeta!!
                meta.persistentDataContainer.set(Keys.GUI, PersistentDataType.STRING, it)
                item.itemMeta = meta
            }

            if (item != null) {
                inventory.setItem(AxMinionsAPI.INSTANCE.getConfig().get("gui.items.$it.slot"), item)
            }
        }
    }

    override fun openInventory(player: Player) {
        LinkingListener.linking.remove(player)
        val inventory = Bukkit.createInventory(
            this,
            Config.GUI_SIZE(),
            StringUtils.formatToString(
                type.getConfig().get("name"),
                Placeholder.parsed("level_color", Messages.LEVEL_COLOR(level)),
                Placeholder.unparsed("level", level.toString()),
                Placeholder.unparsed("owner", owner.name ?: "???")
            )
        )

        val filler = ItemBuilder.create(AxMinionsAPI.INSTANCE.getConfig().getConfig().getSection("gui.items.filler")).get()
        for (i in 0..<Config.GUI_SIZE()) {
            inventory.setItem(i, filler)
        }

        player.openInventory(inventory)
        updateInventory(inventory)
        openInventories.add(inventory)
    }

    override fun getAsItem(): ItemStack {
        return type.getItem(level, actions, charge)
    }

    override fun getLevel(): Int {
        return this.level
    }

    override fun setActions(actions: Long) {
        this.actions = actions
    }

    override fun setStorage(storage: Double) {
        this.storage = storage
    }

    override fun setWarning(warning: Warning?) {
        this.warning = warning
    }

    override fun getWarning(): Warning? {
        return this.warning
    }

    override fun setWarningHologram(hologram: Hologram?) {
        this.hologram = hologram
    }

    override fun getWarningHologram(): Hologram? {
        return this.hologram
    }

    override fun getOwner(): OfflinePlayer {
        return this.owner
    }

    override fun getOwnerUUID(): UUID {
        return this.ownerUUID
    }

    override fun hasFullAccess(player: Player): Boolean {
        if (ownerUUID == player.uniqueId || player.hasPermission("axminions.*")) {
            return true
        }

        return !Config.ONLY_OWNER_GUI() && AxMinionsPlugin.integrations.getProtectionIntegration().canBuildAt(player, location)
    }

    override fun setTool(tool: ItemStack, save: Boolean) {
        this.tool = tool.clone()
        toolMeta = if (!tool.type.isAir) {
            tool.itemMeta
        } else {
            null
        }

        dirty = true

        if (this.tool?.type == Material.AIR) {
            entity.setItem(EquipmentSlot.MAIN_HAND, null)
            unbreakable = false
        } else {
            entity.setItem(EquipmentSlot.MAIN_HAND, WrappedItemStack.wrap(tool.clone()))
            unbreakable = notDurable.contains(tool.type)
        }

        if (save) {
            AxMinionsPlugin.dataQueue.submit {
                AxMinionsPlugin.dataHandler.saveMinion(this)
            }
        }
    }

    override fun getTool(): ItemStack? {
        return this.tool
    }

    override fun getEntity(): PacketEntity {
        return this.entity
    }

    override fun setLevel(level: Int) {
        this.level = level
        updateArmour()
        updateInventories()

        nameHologram?.setContent(
            0,
            StringUtils.formatToString(
                type.getConfig().get("entity.name"),
                Placeholder.unparsed("owner", owner.name ?: "???"),
                Placeholder.unparsed("level", level.toString()),
                Placeholder.parsed("level_color", Messages.LEVEL_COLOR(level))
            )
        )

        AxMinionsPlugin.dataQueue.submit {
            AxMinionsPlugin.dataHandler.saveMinion(this)
        }
    }

    override fun getData(key: String): String? {
        return extraData[key]
    }

    override fun hasData(key: String): Boolean {
        return extraData.containsKey(key)
    }

    override fun getNextAction(): Int {
        return this.nextAction
    }

    override fun getActionAmount(): Long {
        return this.actions
    }

    override fun getStorage(): Double {
        return this.storage
    }

    override fun getRange(): Double {
        return this.range
    }

    override fun resetAnimation() {
        armTick = 0.0
    }

    override fun animate() {
        if (armTick >= 2) return

        val meta = entity.meta() as ArmorStandMeta
        meta.metadata().set(Accessors.RIGHT_ARM_ROTATION, EulerAngle(-2 + armTick, 0.0, 0.0))
        armTick += 0.2
    }

    private fun formatLocation(location: Location): String {
        return Messages.LOCATION_FORMAT().replace("<world>", location.world?.name ?: "---")
            .replace("<x>", location.blockX.toString()).replace("<y>", location.blockY.toString())
            .replace("<z>", location.blockZ.toString())
    }

    private fun sameBlock(first: Location, second: Location): Boolean {
        return first.world == second.world && first.blockX == second.blockX && first.blockY == second.blockY && first.blockZ == second.blockZ
    }

    // The two halves of a double chest are one inventory, so linking both would use up a second slot for nothing
    private fun isDoubleChestPair(first: Location, second: Location): Boolean {
        if (first.world != second.world || first.blockY != second.blockY) return false
        if (abs(first.blockX - second.blockX) + abs(first.blockZ - second.blockZ) != 1) return false

        val firstData = first.block.blockData as? org.bukkit.block.data.type.Chest ?: return false
        val secondData = second.block.blockData as? org.bukkit.block.data.type.Chest ?: return false
        return firstData.type != org.bukkit.block.data.type.Chest.Type.SINGLE &&
            secondData.type != org.bukkit.block.data.type.Chest.Type.SINGLE &&
            firstData.facing == secondData.facing
    }

    private fun saveLater() {
        AxMinionsPlugin.dataQueue.submit {
            AxMinionsPlugin.dataHandler.saveMinion(this)
        }
    }

    // Reads the inventory of the container from the thread that owns its block
    private fun loadInventory(container: LinkedContainer) {
        Scheduler.get().executeAt(container.location) {
            container.inventory = (container.location.block.state as? Container)?.inventory

            updateInventories()
        }
    }

    @Synchronized
    override fun setLinkedChest(location: Location?) {
        containers.clear()
        if (location != null) {
            val container = LinkedContainer(location.clone())
            containers.add(container)
            loadInventory(container)
        } else {
            updateInventories()
        }

        saveLater()
    }

    override fun getLinkedChest(): Location? {
        return containers.firstOrNull()?.location
    }

    override fun getLinkedChests(): List<Location> {
        return containers.map { it.location }
    }

    override fun getLinkedInventories(): List<Inventory> {
        return containers.mapNotNull { it.inventory }
    }

    override fun getMaxLinkedChests(): Int {
        return type.getContainers(level)
    }

    override fun isLinkedTo(location: Location): Boolean {
        return containers.any { sameBlock(it.location, location) }
    }

    @Synchronized
    override fun addLinkedChest(location: Location): Boolean {
        if (containers.size >= getMaxLinkedChests()) return false
        if (containers.any { sameBlock(it.location, location) || isDoubleChestPair(it.location, location) }) return false

        val container = LinkedContainer(location.clone())
        containers.add(container)
        loadInventory(container)
        saveLater()
        return true
    }

    @Synchronized
    override fun removeLinkedChest(location: Location): Boolean {
        if (!containers.removeIf { sameBlock(it.location, location) }) return false

        updateInventories()
        saveLater()
        return true
    }

    override fun hasContainerSpace(): Boolean {
        for (container in containers) {
            val inventory = container.inventory ?: continue
            if (!MinionUtils.isFull(inventory)) return true
        }

        return false
    }

    override fun areContainersFull(): Boolean {
        var linked = false
        for (container in containers) {
            val inventory = container.inventory ?: continue
            if (!MinionUtils.isFull(inventory)) return false

            linked = true
        }

        return linked
    }

    override fun validateLinkedChests() {
        for (container in containers) {
            val block = container.location.block
            val blockType = block.type
            if (blockType != Material.CHEST && blockType != Material.TRAPPED_CHEST && blockType != Material.BARREL) {
                removeLinkedChest(container.location)
                continue
            }

            val inventory = container.inventory
            if (inventory == null) {
                loadInventory(container)
                continue
            }

            if (blockType == Material.CHEST) {
                val chestOnSide = type.hasChestOnSide(block)
                if ((inventory !is DoubleChestInventory && chestOnSide) || (inventory is DoubleChestInventory && !chestOnSide)) {
                    loadInventory(container)
                }
            }
        }
    }

    override fun setDirection(direction: Direction, save: Boolean) {
        this.direction = direction
        location.yaw = direction.yaw
        entity.teleport(location)

        if (save) {
            AxMinionsPlugin.dataQueue.submit {
                AxMinionsPlugin.dataHandler.saveMinion(this)
            }
        }
    }

    override fun getDirection(): Direction {
        return this.direction
    }

    override fun remove() {
        Warnings.remove(this, warning ?: Warnings.NO_CONTAINER)
        OwnerAlerts.forget(this)
        nameHologram?.remove()
        statusHologram?.remove()
        Minions.remove(this)
        entity.remove()

        AxMinionsPlugin.dataQueue.submit {
            AxMinionsPlugin.dataHandler.deleteMinion(this)
            PlayerStats.invalidate(ownerUUID)

            if (AxMinionsAPI.INSTANCE.getIntegrations().getIslandIntegration() != null) {
                val islandId = AxMinionsAPI.INSTANCE.getIntegrations().getIslandIntegration()!!.getIslandAt(location)
                if (islandId.isNotBlank()) {
                    AxMinionsPlugin.dataHandler.islandBreak(islandId)
                    PlayerStats.invalidateIsland(islandId)
                }
            }
        }
    }

    override fun getLinkedInventory(): Inventory? {
        return containers.firstOrNull()?.inventory
    }

    override fun addToContainerOrDrop(itemStack: ItemStack) {
        val remaining = addWithRemaining(itemStack)
        if (remaining == null) {
            AxMinionsPlugin.integrations.getStackerIntegration().dropItemAt(itemStack, itemStack.amount, location)
            return
        }

        remaining.fastFor { _, u ->
            AxMinionsPlugin.integrations.getStackerIntegration().dropItemAt(u, u.amount, location)
        }
    }

    override fun addWithRemaining(itemStack: ItemStack): HashMap<Int, ItemStack>? {
        var remaining: HashMap<Int, ItemStack>? = null
        var item = itemStack
        for (container in containers) {
            val inventory = container.inventory ?: continue

            // What did not fit into this container goes to the next one
            remaining = inventory.addItem(item)
            item = remaining.values.firstOrNull() ?: return remaining
        }

        return remaining
    }

    override fun addToContainerOrDrop(itemStack: Iterable<ItemStack>) {
        itemStack.forEach {
            addToContainerOrDrop(it)
        }
    }

    override fun updateArmour() {
        for (entry in equipmentSlots) {
            entity.setItem(entry,  null)
        }

        setTool(this.tool ?: ItemStack(Material.AIR), false)

        type.getSection("items.helmet", level)?.let {
            entity.setItem(EquipmentSlot.HELMET, WrappedItemStack.wrap(ItemBuilder.create(it).get()))
        }

        type.getSection("items.chestplate", level)?.let {
            entity.setItem(EquipmentSlot.CHEST_PLATE, WrappedItemStack.wrap(ItemBuilder.create(it).get()))
        }

        type.getSection("items.leggings", level)?.let {
            entity.setItem(EquipmentSlot.LEGGINGS, WrappedItemStack.wrap(ItemBuilder.create(it).get()))
        }

        type.getSection("items.boots", level)?.let {
            entity.setItem(EquipmentSlot.BOOTS, WrappedItemStack.wrap(ItemBuilder.create(it).get()))
        }
    }

    override fun getLocationId(): Int {
        return this.locationID
    }

    override fun getChestLocationId(): Int {
        return this.chestLocationId
    }

    override fun removeOpenInventory(inventory: Inventory) {
        openInventories.remove(inventory)
    }

    override fun isTicking(): Boolean {
        return ticking
    }

    override fun setTicking(ticking: Boolean) {
        this.ticking = ticking

        if (containers.isEmpty()) return

        if (ticking) {
            for (container in containers) {
                Scheduler.get().runAt(container.location) { _ ->
                    val chest = container.location
                    if (chest.world?.isChunkLoaded(chest.blockX shr 4, chest.blockZ shr 4) == true) {
                        container.inventory = (chest.block.state as? Container)?.inventory
                    }
                }
            }
        } else {
            containers.forEach { it.inventory = null }
        }
    }

    override fun setRange(range: Double) {
        this.range = range
    }

    override fun setNextAction(nextAction: Int) {
        this.nextAction = nextAction
    }

    override fun markDirty() {
        dirty = true
    }


    override fun damageTool(amount: Int) {
        if (!Config.USE_DURABILITY()) return
        val tool = tool ?: return
        val toolMeta = toolMeta as? Damageable ?: return

        if (!tool.type.isAir && unbreakable) {
            return
        }

        if (toolMeta.isUnbreakable) {
            return
        }

        val maxDurability = tool.type.maxDurability
        val damage = toolMeta.damage
        val remaining = maxDurability - damage

        val unbreakingLevel = Enchantments.UNBREAKING?.let { toolMeta.getEnchantLevel(it) } ?: 0
        if (Math.random() > 1f / (unbreakingLevel + 1)) return

        if (remaining > amount) {
            // We can damage the tool
            toolMeta.damage += amount
            tool.itemMeta = toolMeta
            updateInventories()
            return
        } else {
            // Tool is breaking
            if (Config.PULL_FROM_CHEST() && swapToolFromChest(tool)) {
                return
            }

            if (Config.CAN_BREAK_TOOLS()) {
                setTool(ItemStack(Material.AIR))
            }
        }
    }

    override fun canUseTool(): Boolean {
        val tool = tool ?: return false
        val toolMeta = toolMeta as? Damageable ?: return false

        if (!tool.type.isAir && unbreakable) {
            return true
        }

        if (!Config.USE_DURABILITY()) {
            return true
        }

        if (toolMeta.isUnbreakable) {
            return true
        }

        val maxDurability = tool.type.maxDurability
        val damage = toolMeta.damage
        val remaining = maxDurability - damage

        if (remaining > 1) {
            // We can damage the tool
            return true
        }

        // Tool is worn out
        if (Config.PULL_FROM_CHEST() && swapToolFromChest(tool)) {
            return true
        }

        if (Config.CAN_BREAK_TOOLS()) {
            setTool(ItemStack(Material.AIR))
        }

        return false
    }

    /**
     * Whether the tool has no durability left to be used (it's at 1 durability or below).
     */
    private fun isWornOut(stack: ItemStack): Boolean {
        if (!Config.USE_DURABILITY() || notDurable.contains(stack.type)) return false

        val meta = stack.itemMeta as? Damageable ?: return false
        if (meta.isUnbreakable) return false

        return stack.type.maxDurability - meta.damage <= 1
    }

    /**
     * Replaces the worn out [oldTool] with a usable one from the linked containers and puts the old one into them.
     * Returns false (and changes nothing) if none of the containers has a usable replacement or room for the old tool.
     */
    private fun swapToolFromChest(oldTool: ItemStack): Boolean {
        val inventories = getLinkedInventories()

        for (inventory in inventories) {
            val item = pullFromChest(inventory)
            if (item.type.isAir) continue

            // The container the new tool came from is the first to get the old one
            val targets = listOf(inventory) + inventories.filter { it !== inventory }
            if (targets.none { it.addItem(oldTool.clone()).isEmpty() }) {
                // No room for the old tool, put the replacement back
                inventory.addItem(item)
                return false
            }

            setTool(item)
            return true
        }

        return false
    }

    private fun pullFromChest(inventory: Inventory): ItemStack {
        val allowedTools = arrayListOf<Material>()
        getType().getConfig().getStringList("tool.material").fastFor {
            allowedTools.add(Material.matchMaterial(it) ?: return@fastFor)
        }

        // Take exactly one item from the first matching stack (Inventory#remove would wipe every identical stack)
        for (slot in 0 until inventory.size) {
            val stack = inventory.getItem(slot) ?: continue
            if (stack.type !in allowedTools || isWornOut(stack)) continue

            val pulled = stack.clone()
            pulled.amount = 1

            if (stack.amount > 1) {
                stack.amount -= 1
                inventory.setItem(slot, stack)
            } else {
                inventory.setItem(slot, null)
            }

            return pulled
        }

        return ItemStack(Material.AIR)
    }

    override fun isWorking(): Boolean {
        return working
    }

    override fun setWorking(working: Boolean) {
        if (this.working == working) return
        this.working = working

        if (!working) {
            warning?.let { Warnings.remove(this, it) }
        }

        statusHologram?.setContent(0, statusText())
        updateInventories()

        AxMinionsPlugin.dataQueue.submit {
            AxMinionsPlugin.dataHandler.saveMinion(this)
        }
    }

    override fun isOwnerOnline(): Boolean {
        return ownerOnline
    }

    override fun setOwnerOnline(online: Boolean) {
        ownerOnline = online
    }

    override fun getCharge(): Long {
        return charge
    }

    override fun setCharge(charge: Long) {
        this.charge = charge

        AxMinionsPlugin.dataQueue.submit {
            AxMinionsPlugin.dataHandler.saveMinion(this)
        }
    }

    override fun getInventory(): Inventory {
        return Bukkit.createInventory(this, 9)
    }
}
