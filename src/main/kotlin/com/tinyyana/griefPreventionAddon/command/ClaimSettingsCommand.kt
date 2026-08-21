package com.tinyyana.griefPreventionAddon.command

import com.tinyyana.griefPreventionAddon.gui.ClaimSettingsGuiService
import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.lycoLib.config.Messages
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

import org.bukkit.command.TabCompleter

class ClaimSettingsCommand(
    private val bridge: GriefPreventionBridge,
    private val guiService: ClaimSettingsGuiService,
    private val messages: Messages,
) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: run {
            sender.sendMessage(messages.get("system.player-only"))
            return true
        }

        if (!bridge.isAvailable()) {
            player.sendMessage(messages.get("claim.gp-missing"))
            return true
        }

        val isAdmin = player.hasPermission("griefpreventionaddon.settings.others") ||
            player.hasPermission("griefpreventionaddon.admin") ||
            player.isOp

        val claim = if (args.isNotEmpty()) {
            val claimId = args[0].toLongOrNull()
            if (claimId == null) {
                player.sendMessage(messages.get("claim.not-in-claim"))
                return true
            }
            val target = bridge.getClaim(claimId)
            if (target == null) {
                player.sendMessage(messages.get("teleport.claim-not-found", "id" to claimId.toString()))
                return true
            }
            target
        } else {
            val locClaim = bridge.getTopClaim(player.location)
            if (locClaim != null) {
                locClaim
            } else {
                val playerClaims = bridge.getClaimsForPlayer(player.uniqueId)
                if (playerClaims.size == 1) {
                    playerClaims.first()
                } else {
                    player.sendMessage(messages.get("claim.not-in-claim"))
                    return true
                }
            }
        }

        val isOwner = claim.ownerID == player.uniqueId
        val isTrusted = claim.allowGrantPermission(player) == null || claim.allowAccess(player) == null
        if (!isOwner && !isTrusted && !isAdmin) {
            player.sendMessage(messages.get("system.no-permission"))
            return true
        }

        val info = bridge.getClaimInfo(claim)
        guiService.open(player, info, isAdmin)
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>,
    ): List<String> {
        if (args.size == 1 && sender is Player) {
            val query = args[0].lowercase()
            return bridge.getClaimsForPlayer(sender.uniqueId)
                .map { it.id.toString() }
                .filter { it.startsWith(query) }
        }
        return emptyList()
    }
}

