package com.tinyyana.griefPreventionAddon.teleport

import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.lycoLib.audit.AuditLog
import com.tinyyana.lycoLib.config.Messages
import me.ryanhamshire.GriefPrevention.Claim
import org.bukkit.Sound
import org.bukkit.entity.Player

class ClaimTeleportService(
    private val bridge: GriefPreventionBridge,
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

        val claimId = top.id ?: return
        val targetLoc = bridge.getSafeTeleportLocation(top)
        targetLoc.yaw = player.location.yaw
        targetLoc.pitch = player.location.pitch

        if (closeInventory) {
            player.closeInventory()
        }

        player.teleportAsync(targetLoc).thenAccept { success ->
            if (success) {
                player.playSound(targetLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f)
                player.sendMessage(
                    messages.get(
                        "teleport.success",
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
}
