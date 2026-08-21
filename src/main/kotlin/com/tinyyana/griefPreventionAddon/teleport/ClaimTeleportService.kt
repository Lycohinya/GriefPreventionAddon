package com.tinyyana.griefPreventionAddon.teleport

import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
import com.tinyyana.lycoLib.audit.AuditLog
import com.tinyyana.lycoLib.config.Messages
import me.ryanhamshire.GriefPrevention.Claim
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.entity.Player

class ClaimTeleportService(
    private val bridge: GriefPreventionBridge,
    private val store: ClaimSettingsStore,
    private val messages: Messages,
) {

    fun canTeleport(player: Player, claim: Claim): Boolean {
        val top = claim.parent ?: claim
        val isOwner = top.ownerID != null && top.ownerID == player.uniqueId
        if (isOwner) return true

        if (player.hasPermission("griefpreventionaddon.admin") ||
            player.hasPermission("griefpreventionaddon.tp.others") ||
            player.isOp
        ) {
            return true
        }

        if (top.isAdminClaim()) {
            return player.hasPermission("griefpreventionaddon.tp.admin")
        }

        // 信任玩家亦可傳送
        return top.allowAccess(player) == null
    }

    fun teleport(player: Player, claim: Claim, closeInventory: Boolean = true) {
        val top = claim.parent ?: claim
        if (!canTeleport(player, top)) {
            player.sendMessage(messages.get("teleport.no-permission"))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f)
            return
        }

        val claimId = top.id ?: run {
            player.sendMessage(messages.get("teleport.failed"))
            return
        }

        val lesser = top.lesserBoundaryCorner ?: run {
            player.sendMessage(messages.get("teleport.failed"))
            return
        }
        val greater = top.greaterBoundaryCorner ?: run {
            player.sendMessage(messages.get("teleport.failed"))
            return
        }
        val world = lesser.world ?: run {
            player.sendMessage(messages.get("teleport.failed"))
            return
        }

        val centerX = (lesser.blockX + greater.blockX) / 2
        val centerZ = (lesser.blockZ + greater.blockZ) / 2
        val chunkX = centerX shr 4
        val chunkZ = centerZ shr 4

        if (closeInventory) {
            player.closeInventory()
        }

        val plugin = bridge.plugin

        val doTeleport = Runnable {
            val safeY = resolveSafeGroundY(world, centerX, centerZ, lesser.blockY)
            val targetLoc = Location(
                world,
                centerX + 0.5,
                safeY.toDouble(),
                centerZ + 0.5,
                player.location.yaw,
                player.location.pitch,
            )

            player.teleportAsync(targetLoc).thenAccept { success ->
                if (success) {
                    player.playSound(targetLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f)
                    val alias = store.getAlias(claimId)
                    val displayName = if (!alias.isNullOrBlank()) "「$alias」" else "#$claimId"
                    player.sendMessage(
                        messages.get(
                            "teleport.success",
                            "name" to displayName,
                            "claimId" to claimId.toString(),
                            "x" to targetLoc.blockX.toString(),
                            "y" to targetLoc.blockY.toString(),
                            "z" to targetLoc.blockZ.toString(),
                        ),
                    )
                    AuditLog.log(
                        "GriefPreventionAddon",
                        player.name,
                        "claim-tp",
                        "claim=$claimId dest=(${targetLoc.blockX},${targetLoc.blockY},${targetLoc.blockZ})",
                    )
                } else {
                    player.sendMessage(messages.get("teleport.failed"))
                }
            }
        }

        try {
            plugin.server.regionScheduler.execute(plugin, world, chunkX, chunkZ, doTeleport)
        } catch (t: Throwable) {
            // 非 Folia 或測試環境降級執行
            doTeleport.run()
        }
    }

    private fun resolveSafeGroundY(world: World, x: Int, z: Int, fallbackY: Int): Int {
        return try {
            val highest = world.getHighestBlockYAt(x, z)
            if (highest <= world.minHeight) {
                if (fallbackY > world.minHeight) fallbackY else 64
            } else {
                highest + 1
            }
        } catch (e: Throwable) {
            if (fallbackY > world.minHeight) fallbackY else 64
        }
    }
}
