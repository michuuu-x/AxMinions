package com.artillexstudios.axminions.nms.v1_21_R7

import com.mojang.authlib.GameProfile
import net.minecraft.advancements.AdvancementHolder
import net.minecraft.advancements.AdvancementProgress
import net.minecraft.server.MinecraftServer
import net.minecraft.server.PlayerAdvancements
import net.minecraft.server.level.ClientInformation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import java.nio.file.Path

/**
 * A fake player that doesn't track advancements or statistics
 */
class MinionFakePlayer(
    server: MinecraftServer,
    level: ServerLevel,
    profile: GameProfile,
    clientInfo: ClientInformation
) : ServerPlayer(server, level, profile, clientInfo) {

    private val dummyAdvancements = DummyPlayerAdvancements(this)

    override fun getAdvancements(): PlayerAdvancements {
        return dummyAdvancements
    }

    /**
     * Dummy PlayerAdvancements that does nothing
     */
    private class DummyPlayerAdvancements(player: ServerPlayer) : PlayerAdvancements(
        MinecraftServer.getServer().fixerUpper,
        MinecraftServer.getServer().playerList,
        MinecraftServer.getServer().advancements,
        Path.of("dummy_advancements_${player.uuid}.json"),
        player
    ) {
        override fun award(advancement: AdvancementHolder, criterionName: String): Boolean {
            // Don't award any advancements
            return false
        }

        override fun revoke(advancement: AdvancementHolder, criterionName: String): Boolean {
            return false
        }

        override fun getOrStartProgress(advancement: AdvancementHolder): AdvancementProgress {
            return AdvancementProgress()
        }

        override fun save() {
            // Don't save anything
        }

        override fun flushDirty(player: ServerPlayer, showAdvancements: Boolean) {
            // Don't send any advancement packets
        }
    }
}
