package com.tinyyana.griefPreventionAddon.command

import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsKeys
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
import com.tinyyana.lycoLib.audit.AuditLog
import com.tinyyana.lycoLib.config.Messages
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class ClaimPvpCommand(
    private val bridge: GriefPreventionBridge,
    private val store: ClaimSettingsStore,
    private val messages: Messages,
) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: run {
            sender.sendMessage(messages.get("system.player-only"))
            return true
        }

        if (!bridge.isAvailable()) {
            player.sendMessage(messages.get("claim.gp-missing"))
            return true
        }

        val ownership = bridge.getTopClaimOwnership(player)
        if (ownership == null) {
            player.sendMessage(messages.get("claim.not-in-claim"))
            return true
        }

        val canModify = ownership.isOwner ||
            player.hasPermission("griefpreventionaddon.settings.others") ||
            player.hasPermission("griefpreventionaddon.admin") ||
            player.isOp

        if (!canModify) {
            player.sendMessage(messages.get("claim.not-owner"))
            return true
        }

        val state = runCatching { store.toggle(ClaimSettingsKeys.PVP, ownership.claimId) }.getOrElse {
            player.sendMessage(messages.get("claim.save-failed"))
            return true
        }

        val message = messages.get(if (state) "pvp.allowed" else "pvp.blocked")
        val recipients = bridge.playersInsideClaim(ownership.claimId).toSet() + player
        recipients.forEach { it.sendMessage(message) }

        AuditLog.log("GriefPreventionAddon", player.name, "claim-toggle.pvp", "claim=${ownership.claimId} state=$state")
        return true
    }
}
