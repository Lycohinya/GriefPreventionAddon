package com.tinyyana.griefPreventionAddon.command

import com.tinyyana.griefPreventionAddon.display.ClaimDisplayName
import com.tinyyana.griefPreventionAddon.i18n.escapeForMiniMessageTemplate
import com.tinyyana.griefPreventionAddon.gui.ClaimSettingsGuiService
import com.tinyyana.griefPreventionAddon.i18n.LanguageManager
import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class ClaimSettingsCommand(
    private val bridge: GriefPreventionBridge,
    private val store: ClaimSettingsStore,
    private val guiService: ClaimSettingsGuiService,
    private val lang: LanguageManager,
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

        val isAdmin = player.hasPermission("griefpreventionaddon.settings.others") ||
            player.hasPermission("griefpreventionaddon.admin") ||
            player.isOp

        val playerClaims = bridge.getClaimsForPlayer(player.uniqueId)

        val claim = if (args.isNotEmpty()) {
            val input = args[0]
            val claimId = store.findClaimId(input, player.uniqueId, playerClaims)
            if (claimId == null) {
                player.sendMessage(lang.get(player, "teleport.invalid-target", "target" to escapeForMiniMessageTemplate(input)))
                return true
            }
            val target = bridge.getClaim(claimId)
            if (target == null) {
                player.sendMessage(lang.get(player, "teleport.claim-not-found", "claimId" to claimId.toString()))
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
                    player.sendMessage(lang.get(player, "claim.not-in-claim"))
                    return true
                }
            }
        }

        val isOwner = claim.ownerID == player.uniqueId
        val isTrusted = claim.allowGrantPermission(player) == null || claim.allowAccess(player) == null
        if (!isOwner && !isTrusted && !isAdmin) {
            player.sendMessage(lang.get(player, "system.no-permission"))
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
                // 一塊花域一個候選:有別名就只給別名,沒有才給 #編號(兩種輸入 findClaimId 都認)
                suggestions.add(ClaimDisplayName.resolve(store, id).completion)
            }
            return suggestions.filter { it.startsWith(args[0], ignoreCase = true) }
        }
        return emptyList()
    }
}
