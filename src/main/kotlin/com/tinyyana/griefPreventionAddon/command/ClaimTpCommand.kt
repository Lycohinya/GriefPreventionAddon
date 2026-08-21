package com.tinyyana.griefPreventionAddon.command

import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.teleport.ClaimTeleportService
import com.tinyyana.lycoLib.config.Messages
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class ClaimTpCommand(
    private val bridge: GriefPreventionBridge,
    private val teleportService: ClaimTeleportService,
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

        if (args.isEmpty()) {
            // 若玩家當前站在領地內，直接傳送到當前領地中心
            val currentClaim = bridge.getTopClaim(player.location)
            if (currentClaim != null) {
                teleportService.teleport(player, currentClaim)
                return true
            }

            // 若在野外，尋找自己擁有的領地
            val playerClaims = bridge.getClaimsForPlayer(player.uniqueId)
            if (playerClaims.isEmpty()) {
                player.sendMessage(messages.get("teleport.no-claims"))
                return true
            }

            val targetClaim = playerClaims.first()
            teleportService.teleport(player, targetClaim)

            if (playerClaims.size > 1) {
                val otherIds = playerClaims.mapNotNull { it.id }.joinToString(", ") { "#$it" }
                player.sendMessage(messages.get("teleport.multiple-claims-hint", "claims" to otherIds))
            }
            return true
        }

        val claimId = args[0].removePrefix("#").toLongOrNull()
        if (claimId == null) {
            player.sendMessage(messages.get("teleport.invalid-id"))
            return true
        }

        val claim = bridge.getClaim(claimId)
        if (claim == null) {
            player.sendMessage(messages.get("teleport.claim-not-found", "claimId" to claimId.toString()))
            return true
        }

        teleportService.teleport(player, claim)
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        val player = sender as? Player ?: return emptyList()
        if (args.size == 1) {
            val claims = bridge.getClaimsForPlayer(player.uniqueId)
            return claims.mapNotNull { it.id?.toString() }.filter { it.startsWith(args[0]) }
        }
        return emptyList()
    }
}
