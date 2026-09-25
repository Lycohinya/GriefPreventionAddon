package com.tinyyana.griefPreventionAddon.command

import com.tinyyana.griefPreventionAddon.i18n.escapeForMiniMessageTemplate
import com.tinyyana.griefPreventionAddon.display.ClaimDisplayName
import com.tinyyana.griefPreventionAddon.i18n.LanguageManager
import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class ClaimInfoCommand(
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

        val info = bridge.getClaimInfo(player.location)
        if (info == null) {
            player.sendMessage(lang.get(player, "claim-info.wilderness"))
            return true
        }

        val display = ClaimDisplayName.resolve(store, info.claimId)
        val aliasText = if (display.hasAlias) "${display.decorated} " else ""

        if (info.isAdminClaim) {
            player.sendMessage(
                lang.get(
                    player,
                    "claim-info.admin-claim",
                    "claimId" to info.claimId.toString(),
                    "alias" to escapeForMiniMessageTemplate(aliasText),
                    "width" to info.width.toString(),
                    "height" to info.height.toString(),
                    "area" to info.area.toString(),
                ),
            )
        } else {
            val ownerDefault = lang.raw(player, "gui.owner-admin") ?: "Unknown"
            player.sendMessage(
                lang.get(
                    player,
                    "claim-info.owned",
                    "owner" to (info.ownerName ?: ownerDefault),
                    "claimId" to info.claimId.toString(),
                    "alias" to escapeForMiniMessageTemplate(aliasText),
                    "width" to info.width.toString(),
                    "height" to info.height.toString(),
                    "area" to info.area.toString(),
                ),
            )
        }

        player.sendMessage(lang.get(player, "claim-info.actions", "claimId" to info.claimId.toString()))
        return true
    }
}
