package com.artillexstudios.axminions.api.warnings

import com.artillexstudios.axapi.utils.StringUtils
import com.artillexstudios.axminions.api.config.Config
import com.artillexstudios.axminions.api.config.Messages
import com.artillexstudios.axminions.api.minions.Minion
import com.artillexstudios.axminions.api.utils.Keys
import com.artillexstudios.axminions.api.utils.TimeUtils
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType

/**
 * Tells the owner of a minion, in the chat and/or the action bar, when it stops working because of a warning,
 * or when its charge is about to run out, as the holograms are easy to miss from far away. Every warning of a minion
 * is told again only after the cooldown of the config.yml (while the problem is still there), and a player can turn
 * the alerts off for themselves.
 */
object OwnerAlerts {
    // The players who turned the alerts off. Kept in memory because the alerts are sent from the threads of the minions,
    // and the persistent data of a player should only be read from their own thread.
    private val disabled = ConcurrentHashMap.newKeySet<UUID>()

    // Until when the owner is not told again about a warning of a minion, by the location id of the minion and the warning name
    private val silentUntil = ConcurrentHashMap<Int, ConcurrentHashMap<String, Long>>()
    private val lastAlert = ConcurrentHashMap<UUID, Long>()

    /**
     * Called every time a warning is displayed for a minion, which is every time it runs while the problem is there,
     * so the checks that most calls stop at come first.
     */
    @JvmStatic
    fun alert(minion: Minion, warning: Warning) {
        alert(minion, warning.getName(), true)
    }

    /**
     * Tells the owner that the charge of the minion runs out soon, [left] is the time it has left in milliseconds.
     * It isn't one of the warnings of the config.yml, 'low-charge' there is how many seconds before the end this is told.
     */
    @JvmStatic
    fun alertLowCharge(minion: Minion, left: Long) {
        alert(minion, "low_charge", false, Placeholder.unparsed("time", TimeUtils.format(left)))
    }

    // [listed] is if the name has to be in the warnings of the config.yml, [extra] are the placeholders of the message besides the location
    private fun alert(minion: Minion, name: String, listed: Boolean, vararg extra: TagResolver) {
        val now = System.currentTimeMillis()
        val until = silentUntil[minion.getLocationId()]?.get(name)
        if (until != null && now < until) return

        val owner = Bukkit.getPlayer(minion.getOwnerUUID()) ?: return
        if (disabled.contains(owner.uniqueId) || !Config.ALERTS_ENABLED()) return

        // The config uses 'no-tool', the warnings are named 'no_tool'
        val route = name.replace('_', '-')
        if (listed && Config.ALERTS_WARNINGS().none { it.replace('_', '-').equals(route, true) }) return

        val message = Messages.ALERT(route)
        if (message.isEmpty()) return

        // Two alerts for the same owner are at least 'min-gap' apart, so the action bar shows each one when several
        // minions stop at once. The one that has to wait is not lost, the next display of its warning tries again.
        val previous = lastAlert[owner.uniqueId]
        if (previous != null && now - previous < Config.ALERTS_MIN_GAP().coerceAtLeast(0) * 1000L) return

        lastAlert[owner.uniqueId] = now
        silentUntil.computeIfAbsent(minion.getLocationId()) { ConcurrentHashMap() }[name] =
            now + Config.ALERTS_COOLDOWN().coerceAtLeast(0) * 1000L
        send(owner, minion, message, extra)
    }

    private fun send(owner: Player, minion: Minion, message: String, extra: Array<out TagResolver>) {
        val location = minion.getLocation()
        val world = location.world?.name ?: "---"
        val formatted = Messages.LOCATION_FORMAT().replace("<world>", world)
            .replace("<x>", location.blockX.toString()).replace("<y>", location.blockY.toString())
            .replace("<z>", location.blockZ.toString())
        val resolvers = arrayOf<TagResolver>(
            Placeholder.unparsed("type", minion.getType().getName()),
            Placeholder.unparsed("location", formatted),
            Placeholder.unparsed("world", world),
            Placeholder.unparsed("x", location.blockX.toString()),
            Placeholder.unparsed("y", location.blockY.toString()),
            Placeholder.unparsed("z", location.blockZ.toString()),
            *extra
        )

        // 'chat' and 'actionbar' are only one of them, anything else is both
        val type = Config.ALERTS_TYPE()
        if (type != "actionbar") {
            owner.sendMessage(StringUtils.formatToString(Messages.PREFIX() + message, *resolvers))
        }

        if (type != "chat") {
            owner.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                *TextComponent.fromLegacyText(StringUtils.formatToString(message, *resolvers))
            )
        }
    }

    /**
     * Reads the choice of the player, to be called on their thread when they join.
     */
    @JvmStatic
    fun load(player: Player) {
        if (player.persistentDataContainer.has(Keys.ALERTS_OFF, PersistentDataType.BYTE)) {
            disabled.add(player.uniqueId)
        } else {
            disabled.remove(player.uniqueId)
        }
    }

    @JvmStatic
    fun unload(uuid: UUID) {
        disabled.remove(uuid)
        lastAlert.remove(uuid)
    }

    @JvmStatic
    fun isEnabled(uuid: UUID): Boolean {
        return !disabled.contains(uuid)
    }

    /**
     * Turns the alerts of the player off if they are on and the other way around, and keeps it for when they come back.
     * To be called on their thread. Returns if the alerts are on now.
     */
    @JvmStatic
    fun toggle(player: Player): Boolean {
        if (disabled.remove(player.uniqueId)) {
            player.persistentDataContainer.remove(Keys.ALERTS_OFF)
            return true
        }

        disabled.add(player.uniqueId)
        player.persistentDataContainer.set(Keys.ALERTS_OFF, PersistentDataType.BYTE, 1)
        return false
    }

    /**
     * To be called when the minion is removed, so its cooldowns don't stay around.
     */
    @JvmStatic
    fun forget(minion: Minion) {
        silentUntil.remove(minion.getLocationId())
    }
}
