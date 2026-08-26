package com.tinyyana.griefPreventionAddon.command

import com.tinyyana.griefPreventionAddon.audit.AuditLogger
import com.tinyyana.griefPreventionAddon.i18n.LanguageManager
import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsKeys
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class ClaimPvpCommand(
    private val bridge: GriefPreventionBridge,
    private val store: ClaimSettingsStore,
    private val lang: LanguageManager,
    private val auditLogger: AuditLogger? = null,
) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: run {
            sender.sendMessage(lang.get("system.player-only"))
            return true
        }

        if (!bridge.isAvailable()) {
            player.sendMessage(lang.get(player, "claim.gp-missing"))
            return true
        }

        val ownership = bridge.getTopClaimOwnership(player)
        if (ownership == null) {
            player.sendMessage(lang.get(player, "claim.not-in-claim"))
            return true
        }

        val canModify = ownership.isOwner ||
            player.hasPermission("griefpreventionaddon.settings.others") ||
            player.hasPermission("griefpreventionaddon.admin") ||
            player.isOp

        if (!canModify) {
            player.sendMessage(lang.get(player, "claim.not-owner"))
            return true
        }

        val state = runCatching { store.toggle(ClaimSettingsKeys.PVP, ownership.claimId) }.getOrElse {
            player.sendMessage(lang.get(player, "claim.save-failed"))
            return true
        }

        val msgKey = if (state) "pvp.allowed" else "pvp.blocked"
        val recipients = bridge.playersInsideClaim(ownership.claimId).toSet() + player
        recipients.forEach { it.sendMessage(lang.get(it, msgKey)) }

        auditLogger?.log(player.name, "claim-toggle.pvp", "claim=${ownership.claimId} state=$state")
        return true
    }
}
