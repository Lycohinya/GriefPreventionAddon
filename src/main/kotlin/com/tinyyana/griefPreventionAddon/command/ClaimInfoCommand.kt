package com.tinyyana.griefPreventionAddon.command

import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.lycoLib.config.Messages
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class ClaimInfoCommand(
    private val bridge: GriefPreventionBridge,
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

        val info = bridge.getClaimInfo(player.location)
        if (info == null) {
            player.sendMessage(messages.get("claim-info.wilderness"))
            return true
        }

        if (info.isAdminClaim) {
            player.sendMessage(
                messages.get(
                    "claim-info.admin-claim",
                    "width" to info.width.toString(),
                    "height" to info.height.toString(),
                    "area" to info.area.toString(),
                ),
            )
        } else {
            player.sendMessage(
                messages.get(
                    "claim-info.owned",
                    "owner" to (info.ownerName ?: "未知"),
                    "width" to info.width.toString(),
                    "height" to info.height.toString(),
                    "area" to info.area.toString(),
                ),
            )
        }

        // 提供 [傳送至此花域] 與 [開啟設定選單] 點擊捷徑
        player.sendMessage(messages.get("claim-info.actions", "claimId" to info.claimId.toString()))
        return true
    }
}
