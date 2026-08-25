package com.tinyyana.griefPreventionAddon.command

import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
import com.tinyyana.griefPreventionAddon.teleport.ClaimSpawnPoint
import com.tinyyana.griefPreventionAddon.teleport.ClaimTeleportService
import com.tinyyana.lycoLib.audit.AuditLog
import com.tinyyana.lycoLib.config.Messages
import me.ryanhamshire.GriefPrevention.Claim
import org.bukkit.Sound
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * `/cspawn` —— 把玩家現在站的位置設成這塊花域的落腳點(`/ctp` 的目的地)。
 *
 * 設計取捨:落腳點一定是「玩家站著的那一格」,不接受手打座標。
 * 手打座標會讓人設到牆裡、岩漿上或別人的花域邊界,而且沒有任何介面能讓他看見自己設到哪;
 * 站著設則是所見即所得,而且天然保證那個位置站得住人。
 */
class ClaimSpawnCommand(
    private val bridge: GriefPreventionBridge,
    private val store: ClaimSettingsStore,
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

        val sub = args.firstOrNull()?.lowercase()

        if (sub == "help" || sub == "?") {
            sendHelp(player)
            return true
        }

        // clear 允許站在花域外執行(玩家可能就是因為落腳點怪怪的才想清掉),
        // 站在花域內時清目前這塊,否則要求他站進去——比接受編號參數簡單且不會清錯塊
        val claim = bridge.getTopClaim(player.location) ?: run {
            player.sendMessage(messages.get("spawn.not-in-claim"))
            sendHelp(player)
            return true
        }
        val claimId = claim.id ?: run {
            player.sendMessage(messages.get("teleport.failed"))
            return true
        }

        if (!canEdit(player, claim)) {
            player.sendMessage(messages.get("claim.not-owner"))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f)
            return true
        }

        if (sub == "clear" || sub == "reset") {
            if (store.getSpawnRaw(claimId) == null) {
                player.sendMessage(messages.get("spawn.already-default", "claimId" to claimId.toString()))
                return true
            }
            store.setSpawnRaw(claimId, null)
            AuditLog.log("GriefPreventionAddon", player.name, "claim-spawn.clear", "claim=$claimId")
            player.sendMessage(messages.get("spawn.cleared", "claimId" to claimId.toString()))
            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.7f, 1.0f)
            return true
        }

        if (sub != null && sub != "set") {
            sendHelp(player)
            return true
        }

        setSpawnHere(player, claim, claimId)
        return true
    }

    /** GUI 按鈕也走這條,行為與 `/cspawn` 完全一致 */
    fun setSpawnHere(player: Player, claim: Claim, claimId: Long) {
        val loc = player.location
        if (!teleportService.isInsideClaim(claim, loc)) {
            player.sendMessage(messages.get("spawn.outside-claim"))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f)
            return
        }

        val point = ClaimSpawnPoint.fromLocation(loc) ?: run {
            player.sendMessage(messages.get("teleport.failed"))
            return
        }

        store.setSpawnRaw(claimId, point.encode())
        AuditLog.log(
            "GriefPreventionAddon",
            player.name,
            "claim-spawn.set",
            "claim=$claimId point=(${loc.blockX},${loc.blockY},${loc.blockZ})",
        )
        player.sendMessage(
            messages.get(
                "spawn.set",
                "claimId" to claimId.toString(),
                "x" to loc.blockX.toString(),
                "y" to loc.blockY.toString(),
                "z" to loc.blockZ.toString(),
            ),
        )
        player.playSound(player.location, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.4f)
    }

    fun canEdit(player: Player, claim: Claim): Boolean {
        val top = claim.parent ?: claim
        if (top.ownerID != null && top.ownerID == player.uniqueId) return true
        return player.hasPermission("griefpreventionaddon.settings.others") ||
            player.hasPermission("griefpreventionaddon.admin") ||
            player.isOp
    }

    private fun sendHelp(player: Player) {
        player.sendMessage(messages.get("spawn.help-header"))
        player.sendMessage(messages.get("spawn.help-set"))
        player.sendMessage(messages.get("spawn.help-clear"))
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (args.size == 1) {
            return listOf("set", "clear").filter { it.startsWith(args[0], ignoreCase = true) }
        }
        return emptyList()
    }
}
