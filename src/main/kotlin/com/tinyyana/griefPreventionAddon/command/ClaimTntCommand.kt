package com.tinyyana.griefPreventionAddon.command

import com.tinyyana.griefPreventionAddon.audit.AuditLogger
import com.tinyyana.griefPreventionAddon.i18n.LanguageManager
import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsKeys
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class ClaimTntCommand(
    private val bridge: GriefPreventionBridge,
    private val store: ClaimSettingsStore,
    private val lang: LanguageManager,
    private val auditLogger: AuditLogger? = null,
) : CommandExecutor, TabCompleter {

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

        val sub = args.firstOrNull()?.lowercase()

        if (sub == "status" || sub == "info") {
            val allowed = store.isTntAllowed(ownership.claimId)
            val msgKey = if (allowed) "tnt.status-on" else "tnt.status-off"
            player.sendMessage(lang.get(player, msgKey, "claimId" to ownership.claimId.toString()))
            return true
        }

        val canModify = (ownership.isOwner && player.hasPermission("griefpreventionaddon.tnt")) ||
            player.hasPermission("griefpreventionaddon.tnt.others") ||
            player.hasPermission("griefpreventionaddon.admin") ||
            player.isOp

        if (!canModify) {
            player.sendMessage(lang.get(player, "claim.not-owner"))
            return true
        }

        val newState = when (sub) {
            "on", "enable", "allow", "true" -> {
                store.setBoolean(ClaimSettingsKeys.TNT, ownership.claimId, true)
                true
            }
            "off", "disable", "deny", "false" -> {
                store.setBoolean(ClaimSettingsKeys.TNT, ownership.claimId, false)
                false
            }
            else -> store.toggle(ClaimSettingsKeys.TNT, ownership.claimId)
        }

        bridge.syncClaimExplosives(ownership.claimId, newState)

        val msgKey = if (newState) "tnt.allowed" else "tnt.blocked"
        player.sendMessage(lang.get(player, msgKey, "claimId" to ownership.claimId.toString()))

        auditLogger?.log(player.name, "claim-tnt", "claim=${ownership.claimId} state=$newState")
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (args.size == 1) {
            return listOf("on", "off", "status").filter { it.startsWith(args[0], ignoreCase = true) }
        }
        return emptyList()
    }
}
