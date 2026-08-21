package com.tinyyana.griefPreventionAddon.command

import com.tinyyana.griefPreventionAddon.gui.ClaimSettingsGuiService
import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
import com.tinyyana.lycoLib.config.Messages
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class ClaimSettingsCommand(
    private val bridge: GriefPreventionBridge,
    private val store: ClaimSettingsStore,
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

        val playerClaims = bridge.getClaimsForPlayer(player.uniqueId)

        val claim = if (args.isNotEmpty()) {
            val input = args[0]
            val claimId = store.findClaimId(input, player.uniqueId, playerClaims)
            if (claimId == null) {
                player.sendMessage(messages.get("teleport.invalid-target", "target" to input))
                return true
            }
            val target = bridge.getClaim(claimId)
            if (target == null) {
                player.sendMessage(messages.get("teleport.claim-not-found", "claimId" to claimId.toString()))
                return true
            }
            target
        } else {
            val locClaim = bridge.getTopClaim(player.location)
            if (locClaim != null) {
                locClaim
            } else {
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
            val playerClaims = bridge.getClaimsForPlayer(sender.uniqueId)
            val suggestions = mutableListOf<String>()
            for (c in playerClaims) {
                val id = c.id ?: continue
                val name = store.getAlias(id)
                if (!name.isNullOrBlank()) {
                    suggestions.add(name)
                }
                suggestions.add(id.toString())
            }
            return suggestions.filter { it.startsWith(args[0], ignoreCase = true) }
        }
        return emptyList()
    }
}

