package com.tinyyana.griefPreventionAddon.command

import com.tinyyana.griefPreventionAddon.i18n.LanguageManager
import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
import me.ryanhamshire.GriefPrevention.GriefPrevention
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class ClaimsListCommand(
    private val bridge: GriefPreventionBridge,
    private val store: ClaimSettingsStore,
    private val lang: LanguageManager,
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

        val playerClaims = bridge.getClaimsForPlayer(player.uniqueId)
        if (playerClaims.isEmpty()) {
            player.sendMessage(lang.get(player, "teleport.no-claims"))
            return true
        }

        player.sendMessage(lang.get(player, "name.list-header"))
        val defaultName = lang.raw(player, "gui.card-status-no-alias") ?: "Unnamed Claim"
        for (claim in playerClaims) {
            val id = claim.id ?: continue
            val alias = store.getAlias(id)
            val displayName = if (!alias.isNullOrBlank()) alias else defaultName
            val targetParam = alias ?: id.toString()
            player.sendMessage(
                lang.get(
                    player,
                    "name.list-entry",
                    "target" to targetParam,
                    "name" to displayName,
                    "claimId" to id.toString(),
                    "width" to claim.width.toString(),
                    "height" to claim.height.toString(),
                ),
            )
        }

        // Available claim blocks
        runCatching {
            val pd = GriefPrevention.instance.dataStore.getPlayerData(player.uniqueId)
            if (pd != null) {
                val remaining = pd.getRemainingClaimBlocks()
                val total = pd.accruedClaimBlocks + pd.bonusClaimBlocks
                val msg = (lang.raw(player, "name.claim-blocks-info") ?: "<color:#a8a8a8>Available Claim Blocks: <color:#ffd166><bold>{remaining}</bold></color> / {total} blocks</color>")
                    .replace("{remaining}", remaining.toString())
                    .replace("{total}", total.toString())
                player.sendMessage(lang.render(msg, false, player))
            }
        }
        return true
    }
}
