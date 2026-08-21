package com.tinyyana.griefPreventionAddon.command

import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
import com.tinyyana.lycoLib.audit.AuditLog
import com.tinyyana.lycoLib.config.Messages
import me.ryanhamshire.GriefPrevention.Claim
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class ClaimNameCommand(
    private val bridge: GriefPreventionBridge,
    private val store: ClaimSettingsStore,
    private val messages: Messages,
) : CommandExecutor, TabCompleter {

    companion object {
        private val INVALID_CHARS = listOf("/", "\\", ":", "*", "?", "\"", "<", ">", "|", " ")
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: run {
            sender.sendMessage(messages.get("system.player-only"))
            return true
        }

        if (!bridge.isAvailable()) {
            player.sendMessage(messages.get("claim.gp-missing"))
            return true
        }

        val playerClaims = bridge.getClaimsForPlayer(player.uniqueId)

        if (args.isEmpty() || args[0].equals("help", ignoreCase = true) || args[0].equals("?", ignoreCase = true)) {
            player.sendMessage(messages.get("name.guide-header"))
            val currentClaim = bridge.getTopClaim(player.location)
            if (currentClaim != null && (currentClaim.ownerID == player.uniqueId || player.hasPermission("griefpreventionaddon.admin") || player.isOp)) {
                val currentAlias = currentClaim.id?.let { store.getAlias(it) } ?: "未設定"
                player.sendMessage(messages.get("name.guide-current", "claimId" to (currentClaim.id?.toString() ?: "?"), "name" to currentAlias))
            } else {
                player.sendMessage(messages.get("name.guide-current-none"))
            }
            player.sendMessage(messages.get("name.guide-line-set"))
            player.sendMessage(messages.get("name.guide-line-clear"))
            player.sendMessage(messages.get("name.guide-line-id"))
            player.sendMessage(messages.get("name.guide-quick-prompt"))
            return true
        }

        // 解析指定領地與目標別名
        val targetClaim: Claim
        val targetAlias: String?

        if (args.size == 1) {
            val input = args[0]
            val currentClaim = bridge.getTopClaim(player.location)
            if (currentClaim != null && (currentClaim.ownerID == player.uniqueId || player.hasPermission("griefpreventionaddon.admin") || player.isOp)) {
                targetClaim = currentClaim
            } else if (playerClaims.size == 1) {
                targetClaim = playerClaims.first()
            } else if (playerClaims.isEmpty()) {
                player.sendMessage(messages.get("teleport.no-claims"))
                return true
            } else {
                player.sendMessage(messages.get("claim.not-in-claim"))
                player.sendMessage(messages.get("name.guide-quick-prompt"))
                return true
            }
            targetAlias = if (input.equals("clear", ignoreCase = true) || input.equals("remove", ignoreCase = true)) null else input
        } else {
            // 2 個參數: /cname <領地ID> <別名|clear>
            val claimId = store.findClaimId(args[0], player.uniqueId, playerClaims)
            if (claimId == null) {
                player.sendMessage(messages.get("teleport.invalid-target", "target" to args[0]))
                return true
            }
            val claim = bridge.getClaim(claimId)
            if (claim == null) {
                player.sendMessage(messages.get("teleport.claim-not-found", "claimId" to claimId.toString()))
                return true
            }
            targetClaim = claim
            val input = args[1]
            targetAlias = if (input.equals("clear", ignoreCase = true) || input.equals("remove", ignoreCase = true)) null else input
        }

        val claimId = targetClaim.id ?: run {
            player.sendMessage(messages.get("claim.save-failed"))
            return true
        }

        // 權限檢查: 地主或管理員
        val isOwner = targetClaim.ownerID != null && targetClaim.ownerID == player.uniqueId
        if (!isOwner && !player.hasPermission("griefpreventionaddon.admin") && !player.isOp) {
            player.sendMessage(messages.get("claim.not-owner"))
            return true
        }

        if (targetAlias == null) {
            store.setAlias(claimId, null)
            player.sendMessage(messages.get("name.cleared", "claimId" to claimId.toString()))
            AuditLog.log("GriefPreventionAddon", player.name, "claim-alias-clear", "claim=$claimId")
            return true
        }

        // 別名檢驗
        val cleanAlias = targetAlias.trim()
        if (cleanAlias.length !in 1..20 || cleanAlias.removePrefix("#").toLongOrNull() != null || INVALID_CHARS.any { cleanAlias.contains(it) }) {
            player.sendMessage(messages.get("name.invalid-name"))
            return true
        }

        // 重複檢查 (同地主下不能有重複別名)
        for (otherClaim in playerClaims) {
            val otherId = otherClaim.id ?: continue
            if (otherId != claimId) {
                val existing = store.getAlias(otherId)
                if (existing != null && existing.equals(cleanAlias, ignoreCase = true)) {
                    player.sendMessage(
                        messages.get(
                            "name.duplicate-name",
                            "name" to cleanAlias,
                            "otherId" to otherId.toString(),
                        ),
                    )
                    return true
                }
            }
        }

        store.setAlias(claimId, cleanAlias)
        player.sendMessage(
            messages.get(
                "name.set-success",
                "claimId" to claimId.toString(),
                "name" to cleanAlias,
            ),
        )
        AuditLog.log("GriefPreventionAddon", player.name, "claim-alias-set", "claim=$claimId alias=$cleanAlias")
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        val player = sender as? Player ?: return emptyList()
        val playerClaims = bridge.getClaimsForPlayer(player.uniqueId)
        if (args.size == 1) {
            val suggestions = mutableListOf("clear")
            suggestions.addAll(playerClaims.mapNotNull { it.id?.toString() })
            return suggestions.filter { it.startsWith(args[0], ignoreCase = true) }
        }
        if (args.size == 2) {
            return listOf("clear").filter { it.startsWith(args[1], ignoreCase = true) }
        }
        return emptyList()
    }
}
