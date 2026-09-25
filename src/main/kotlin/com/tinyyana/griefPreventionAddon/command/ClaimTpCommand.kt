package com.tinyyana.griefPreventionAddon.command

import com.tinyyana.griefPreventionAddon.i18n.escapeForMiniMessageTemplate
import com.tinyyana.griefPreventionAddon.display.ClaimDisplayName
import com.tinyyana.griefPreventionAddon.i18n.LanguageManager
import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
import com.tinyyana.griefPreventionAddon.teleport.ClaimTeleportService
import me.ryanhamshire.GriefPrevention.Claim
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class ClaimTpCommand(
    private val bridge: GriefPreventionBridge,
    private val store: ClaimSettingsStore,
    private val teleportService: ClaimTeleportService,
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

        val playerClaims = bridge.getClaimsForPlayer(player.uniqueId)

        if (args.isEmpty()) {
            val currentClaim = bridge.getTopClaim(player.location)
            if (currentClaim != null) {
                teleportService.teleport(player, currentClaim)
                return true
            }

            if (playerClaims.size == 1) {
                teleportService.teleport(player, playerClaims.first())
                return true
            }

            if (playerClaims.isEmpty()) {
                player.sendMessage(lang.get(player, "teleport.no-claims"))
                return true
            }

            sendClaimsList(player, playerClaims)
            return true
        }

        val input = args[0]
        val claimId = store.findClaimId(input, player.uniqueId, playerClaims)

        if (claimId == null) {
            player.sendMessage(lang.get(player, "teleport.invalid-target", "target" to escapeForMiniMessageTemplate(input)))
            if (playerClaims.isNotEmpty()) {
                sendClaimsList(player, playerClaims)
            }
            return true
        }

        val claim = bridge.getClaim(claimId)
        if (claim == null) {
            player.sendMessage(lang.get(player, "teleport.claim-not-found", "claimId" to claimId.toString()))
            return true
        }

        teleportService.teleport(player, claim)
        return true
    }

    private fun sendClaimsList(player: Player, claims: List<Claim>) {
        player.sendMessage(lang.get(player, "name.list-header"))
        for (claim in claims) {
            val id = claim.id ?: continue
            val display = ClaimDisplayName.resolve(store, id)
            player.sendMessage(
                lang.get(
                    player,
                    "name.claim-entry",
                    "target" to display.targetParam,
                    "name" to escapeForMiniMessageTemplate(display.name),
                    "claimId" to id.toString(),
                    "width" to claim.width.toString(),
                    "height" to claim.height.toString(),
                ),
            )
        }
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        val player = sender as? Player ?: return emptyList()
        if (args.size == 1) {
            val playerClaims = bridge.getClaimsForPlayer(player.uniqueId)
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
