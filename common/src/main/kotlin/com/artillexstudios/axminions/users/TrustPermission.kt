package com.artillexstudios.axminions.users

import java.util.EnumSet

/**
 * The things an owner can allow a trusted player to do with their minions.
 * Opening the menu of a minion is what the trust itself gives, everything else is one of these.
 */
enum class TrustPermission(val key: String) {
    UPGRADE("upgrade"),
    LINK("link"),
    CHARGE("charge"),
    PICKUP("pickup"),
    TOOL("tool"),
    TOGGLE("toggle"),
    ROTATE("rotate"),
    STATISTICS("statistics");

    companion object {
        private val byKey = entries.associateBy { it.key }

        fun fromKey(key: String): TrustPermission? {
            return byKey[key.trim().lowercase()]
        }

        /**
         * Reads the permissions saved by [serialize], the keys that don't exist (anymore) are skipped.
         * Returns null if there is nothing saved, which means the player never got their own permissions.
         */
        fun parse(text: String?): Set<TrustPermission>? {
            if (text == null) return null

            val permissions = EnumSet.noneOf(TrustPermission::class.java)
            for (key in text.split(',')) {
                fromKey(key)?.let { permissions.add(it) }
            }

            return permissions
        }

        fun serialize(permissions: Set<TrustPermission>): String {
            return permissions.sortedBy { it.ordinal }.joinToString(",") { it.key }
        }
    }
}
