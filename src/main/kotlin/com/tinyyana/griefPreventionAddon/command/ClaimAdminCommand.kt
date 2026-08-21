package com.tinyyana.griefPreventionAddon.command

import com.tinyyana.griefPreventionAddon.gui.ClaimAdminListGuiService
import com.tinyyana.griefPreventionAddon.gui.ClaimSettingRegistry
import com.tinyyana.griefPreventionAddon.gui.ClaimSettingsGuiService
import com.tinyyana.griefPreventionAddon.integration.ClaimInfoResult
import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
import com.tinyyana.griefPreventionAddon.teleport.ClaimTeleportService
import com.tinyyana.lycoLib.audit.AuditLog
import com.tinyyana.lycoLib.config.Messages
import me.ryanhamshire.GriefPrevention.GriefPrevention
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class ClaimAdminCommand(
    private val bridge: GriefPreventionBridge,
    private val store: ClaimSettingsStore,
    private val adminGuiService: ClaimAdminListGuiService,
    private val settingsGuiService: ClaimSettingsGuiService,
    private val teleportService: ClaimTeleportService,
    private val messages: Messages,
) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("griefpreventionaddon.admin") && !sender.isOp) {
            sender.sendMessage(messages.get("system.no-permission"))
            return true
        }

        val player = sender as? Player

        // 1. 無參數: 直接開啟管理員花域清單 GUI
        if (args.isEmpty()) {
            if (player == null) {
                sender.sendMessage(messages.get("admin.help"))
                return true
            }
            adminGuiService.open(player)
            return true
        }

        val sub = args[0].lowercase()

        // 2. /cadmin list [player]
        if (sub == "list" || sub == "menu" || sub == "gui") {
            if (player == null) {
                sender.sendMessage(messages.get("system.player-only"))
                return true
            }
            val targetPlayer = if (args.size > 1) args[1] else null
            adminGuiService.open(player, page = 1, filter = "ALL", targetPlayer = targetPlayer)
            return true
        }

        // 3. /cadmin tp <claimId|alias>
        if (sub == "tp" || sub == "teleport") {
            if (player == null) {
                sender.sendMessage(messages.get("system.player-only"))
                return true
            }
            if (args.size < 2) {
                sender.sendMessage("<yellow>用法：/cadmin tp <花域編號或別名></yellow>")
                return true
            }
            val allClaims = GriefPrevention.instance.dataStore.claims?.toList() ?: emptyList()
            val targetClaimId = store.findClaimId(args[1], player.uniqueId, allClaims)
            val claim = if (targetClaimId != null) bridge.getClaim(targetClaimId) else null
            if (claim == null) {
                player.sendMessage(messages.get("teleport.invalid-target", "target" to args[1]))
                return true
            }
            player.sendMessage(messages.get("admin.teleported", "claimId" to claim.id.toString()))
            teleportService.teleport(player, claim, closeInventory = true)
            AuditLog.log("GriefPreventionAddon", player.name, "admin-teleport", "claim=${claim.id}")
            return true
        }

        // 4. /cadmin delete <claimId>
        if (sub == "delete" || sub == "remove" || sub == "purge") {
            if (args.size < 2) {
                sender.sendMessage("<yellow>用法：/cadmin delete <花域編號></yellow>")
                return true
            }
            val id = args[1].removePrefix("#").toLongOrNull()
            if (id == null) {
                sender.sendMessage("<red>無效的花域編號！</red>")
                return true
            }
            val claim = bridge.getClaim(id)
            if (claim == null) {
                sender.sendMessage(messages.get("teleport.claim-not-found", "claimId" to id.toString()))
                return true
            }
            GriefPrevention.instance.dataStore.deleteClaim(claim)
            store.purgeClaim(id)
            sender.sendMessage(messages.get("admin.claim-deleted", "claimId" to id.toString()))
            AuditLog.log("GriefPreventionAddon", sender.name, "admin-delete-claim", "claim=$id")
            return true
        }

        // 5. /cadmin name <claimId> <別名|clear>
        if (sub == "name" || sub == "alias") {
            if (args.size < 3) {
                sender.sendMessage("<yellow>用法：/cadmin name <花域編號> <新別名|clear></yellow>")
                return true
            }
            val id = args[1].removePrefix("#").toLongOrNull()
            if (id == null) {
                sender.sendMessage("<red>無效的花域編號！</red>")
                return true
            }
            val claim = bridge.getClaim(id)
            if (claim == null) {
                sender.sendMessage(messages.get("teleport.claim-not-found", "claimId" to id.toString()))
                return true
            }
            val newName = if (args[2].equals("clear", ignoreCase = true)) null else args[2].trim()
            store.setAlias(id, newName)
            if (newName == null) {
                sender.sendMessage(messages.get("name.cleared", "claimId" to id.toString()))
            } else {
                sender.sendMessage(messages.get("name.set-success", "claimId" to id.toString(), "name" to newName))
            }
            AuditLog.log("GriefPreventionAddon", sender.name, "admin-set-alias", "claim=$id alias=$newName")
            return true
        }

        // 6. /cadmin set <claimId> <settingKey> <true|false>
        if (sub == "set") {
            if (args.size < 4) {
                sender.sendMessage("<yellow>用法：/cadmin set <花域編號> <tnt|pvp|mob_hostile|...> <true|false></yellow>")
                return true
            }
            val id = args[1].removePrefix("#").toLongOrNull()
            val key = args[2].lowercase()
            val value = args[3].toBooleanStrictOrNull()
            if (id == null || value == null) {
                sender.sendMessage("<red>參數格式錯誤！請提供正確的花域編號與 true/false。</red>")
                return true
            }
            store.setBoolean(key, id, value)
            sender.sendMessage("<color:#f5f5f5>已將花域 #$id 的 <color:#6fd8e8>$key</color> 強制設定為：<color:#ffd166>$value</color></color>")
            AuditLog.log("GriefPreventionAddon", sender.name, "admin-set-setting", "claim=$id key=$key value=$value")
            return true
        }

        // 7. /cadmin <claimId|alias> (直接開啟該花域的 GUI 管理介面)
        val allClaims = GriefPrevention.instance.dataStore.claims?.toList() ?: emptyList()
        val claimId = store.findClaimId(args[0], player?.uniqueId, allClaims)
        val claim = if (claimId != null) bridge.getClaim(claimId) else null

        if (claim != null && player != null) {
            val info = ClaimInfoResult(
                claimId = claim.id,
                ownerName = claim.ownerName,
                ownerUuid = claim.ownerID,
                width = claim.width,
                height = claim.height,
                area = claim.area,
                isAdminClaim = claim.isAdminClaim(),
                isSubdivision = claim.parent != null,
                topClaim = claim,
            )
            settingsGuiService.open(player, info, isAdmin = true)
            return true
        }

        sender.sendMessage(messages.get("admin.help"))
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (!sender.hasPermission("griefpreventionaddon.admin") && !sender.isOp) return emptyList()

        if (args.size == 1) {
            val list = mutableListOf("list", "tp", "delete", "name", "set", "help")
            val allClaims = GriefPrevention.instance.dataStore.claims ?: emptyList()
            list.addAll(allClaims.take(20).mapNotNull { it.id?.toString() })
            return list.filter { it.startsWith(args[0], ignoreCase = true) }
        }

        if (args.size == 2) {
            val sub = args[0].lowercase()
            if (sub == "tp" || sub == "delete" || sub == "name" || sub == "set") {
                val allClaims = GriefPrevention.instance.dataStore.claims ?: emptyList()
                return allClaims.take(20).mapNotNull { it.id?.toString() }.filter { it.startsWith(args[1], ignoreCase = true) }
            }
        }

        if (args.size == 3 && args[0].equals("set", ignoreCase = true)) {
            return ClaimSettingRegistry.ALL_SETTINGS.map { it.key }.filter { it.startsWith(args[2], ignoreCase = true) }
        }

        if (args.size == 4 && args[0].equals("set", ignoreCase = true)) {
            return listOf("true", "false").filter { it.startsWith(args[3], ignoreCase = true) }
        }

        return emptyList()
    }
}
