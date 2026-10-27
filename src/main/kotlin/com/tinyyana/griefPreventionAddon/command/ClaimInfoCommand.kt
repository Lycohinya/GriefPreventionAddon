package com.tinyyana.griefPreventionAddon.command

import com.tinyyana.griefPreventionAddon.i18n.escapeForMiniMessageTemplate
import com.tinyyana.griefPreventionAddon.claim.ClaimDepthService
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
            // 判定仍以 GP 的 3D 查詢為準;只在「XZ 在某塊花域裡、腳下低於它目前的下界」時多說一句
            val above = bridge.getClaimAtIgnoringHeight(player.location)
                ?.takeIf { player.location.blockY < ClaimDepthService.effectiveFloor(it) }
            if (above == null) {
                player.sendMessage(lang.get(player, "claim-info.wilderness"))
            } else {
                val top = bridge.root(above)
                val owner = if (top.isAdminClaim()) lang.raw(player, "gui.owner-admin") ?: "Admin" else top.getOwnerName()
                player.sendMessage(
                    lang.get(
                        player,
                        "claim-info.wilderness-below",
                        "owner" to escapeForMiniMessageTemplate(owner),
                        "floor" to ClaimDepthService.effectiveFloor(above).toString(),
                    ),
                )
            }
            return true
        }

        val display = ClaimDisplayName.resolve(store, info.rootId)
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
