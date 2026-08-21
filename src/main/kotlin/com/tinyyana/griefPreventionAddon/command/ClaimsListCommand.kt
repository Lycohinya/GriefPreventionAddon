package com.tinyyana.griefPreventionAddon.command

import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
import com.tinyyana.lycoLib.config.Messages
import me.ryanhamshire.GriefPrevention.GriefPrevention
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class ClaimsListCommand(
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

        val playerClaims = bridge.getClaimsForPlayer(player.uniqueId)
        if (playerClaims.isEmpty()) {
            player.sendMessage(messages.get("teleport.no-claims"))
            return true
        }

        player.sendMessage(messages.get("name.list-header"))
        for (claim in playerClaims) {
            val id = claim.id ?: continue
            val alias = store.getAlias(id)
            val displayName = if (!alias.isNullOrBlank()) alias else "未命名花域"
            val targetParam = alias ?: id.toString()
            player.sendMessage(
                messages.get(
                    "name.list-entry",
                    "target" to targetParam,
                    "name" to displayName,
                    "claimId" to id.toString(),
                    "width" to claim.width.toString(),
                    "height" to claim.height.toString(),
                ),
            )
        }

        // 附加顯示 GP 可用格數資訊 (若有)
        runCatching {
            val pd = GriefPrevention.instance.dataStore.getPlayerData(player.uniqueId)
            if (pd != null) {
                val remaining = pd.getRemainingClaimBlocks()
                val total = pd.accruedClaimBlocks + pd.bonusClaimBlocks
                player.sendMessage("<color:#a8a8a8>可用花域格數：<color:#ffd166><bold>$remaining</bold></color> / $total 格</color>")
            }
        }
        return true
    }
}
