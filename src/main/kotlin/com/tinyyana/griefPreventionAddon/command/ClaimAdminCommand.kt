package com.tinyyana.griefPreventionAddon.command

import com.tinyyana.griefPreventionAddon.audit.AuditLogger
import com.tinyyana.griefPreventionAddon.gui.ClaimAdminListGuiService
import com.tinyyana.griefPreventionAddon.gui.ClaimSettingRegistry
import com.tinyyana.griefPreventionAddon.gui.ClaimSettingsGuiService
import com.tinyyana.griefPreventionAddon.i18n.LanguageManager
import com.tinyyana.griefPreventionAddon.integration.ClaimInfoResult
import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsKeys
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
import com.tinyyana.griefPreventionAddon.teleport.ClaimTeleportService
import com.tinyyana.griefPreventionAddon.toggle.ClaimToggleListener
import me.ryanhamshire.GriefPrevention.GriefPrevention
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.entity.CreatureSpawnEvent

class ClaimAdminCommand(
    private val bridge: GriefPreventionBridge,
    private val store: ClaimSettingsStore,
    private val adminGuiService: ClaimAdminListGuiService,
    private val settingsGuiService: ClaimSettingsGuiService,
    private val teleportService: ClaimTeleportService,
    private val lang: LanguageManager,
    private val auditLogger: AuditLogger? = null,
) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player
        if (!sender.hasPermission("griefpreventionaddon.admin") && !sender.isOp) {
            sender.sendMessage(lang.get(player, "system.no-permission"))
            return true
        }

        // 1. No args: open admin claims GUI
        if (args.isEmpty()) {
            if (player == null) {
                sender.sendMessage(lang.get("admin.help"))
                return true
            }
            adminGuiService.open(player)
            return true
        }

        val sub = args[0].lowercase()

        // 2. /cadmin list [player]
        if (sub == "list" || sub == "menu" || sub == "gui") {
            if (player == null) {
                sender.sendMessage(lang.get("system.player-only"))
                return true
            }
            val targetPlayer = if (args.size > 1) args[1] else null
            adminGuiService.open(player, page = 1, filter = "ALL", targetPlayer = targetPlayer)
            return true
        }

        // 3. /cadmin tp <claimId|alias>
        if (sub == "tp" || sub == "teleport") {
            if (player == null) {
                sender.sendMessage(lang.get("system.player-only"))
                return true
            }
            if (args.size < 2) {
                sender.sendMessage(lang.get(player, "admin.usage-tp"))
                return true
            }
            val allClaims = GriefPrevention.instance.dataStore.claims?.toList() ?: emptyList()
            val targetClaimId = store.findClaimId(args[1], player.uniqueId, allClaims)
            val claim = if (targetClaimId != null) bridge.getClaim(targetClaimId) else null
            if (claim == null) {
                player.sendMessage(lang.get(player, "teleport.invalid-target", "target" to args[1]))
                return true
            }
            player.sendMessage(lang.get(player, "admin.teleported", "claimId" to claim.id.toString()))
            teleportService.teleport(player, claim, closeInventory = true)
            auditLogger?.log(player.name, "admin-teleport", "claim=${claim.id}")
            return true
        }

        // 4. /cadmin delete <claimId>
        if (sub == "delete" || sub == "remove" || sub == "purge") {
            if (args.size < 2) {
                sender.sendMessage(lang.get(player, "admin.usage-delete"))
                return true
            }
            val id = args[1].removePrefix("#").toLongOrNull()
            if (id == null) {
                sender.sendMessage(lang.get(player, "admin.invalid-id"))
                return true
            }
            val claim = bridge.getClaim(id)
            if (claim == null) {
                sender.sendMessage(lang.get(player, "teleport.claim-not-found", "claimId" to id.toString()))
                return true
            }
            GriefPrevention.instance.dataStore.deleteClaim(claim)
            store.purgeClaim(id)
            sender.sendMessage(lang.get(player, "admin.claim-deleted", "claimId" to id.toString()))
            auditLogger?.log(sender.name, "admin-delete-claim", "claim=$id")
            return true
        }

        // 5. /cadmin name <claimId> <alias|clear>
        if (sub == "name" || sub == "alias") {
            if (args.size < 3) {
                sender.sendMessage(lang.get(player, "admin.usage-name"))
                return true
            }
            val id = args[1].removePrefix("#").toLongOrNull()
            if (id == null) {
                sender.sendMessage(lang.get(player, "admin.invalid-id"))
                return true
            }
            val claim = bridge.getClaim(id)
            if (claim == null) {
                sender.sendMessage(lang.get(player, "teleport.claim-not-found", "claimId" to id.toString()))
                return true
            }
            val newName = if (args[2].equals("clear", ignoreCase = true)) null else args[2].trim()
            store.setAlias(id, newName)
            if (newName == null) {
                sender.sendMessage(lang.get(player, "name.cleared", "claimId" to id.toString()))
            } else {
                sender.sendMessage(lang.get(player, "name.set-success", "claimId" to id.toString(), "name" to newName))
            }
            auditLogger?.log(sender.name, "admin-set-alias", "claim=$id alias=$newName")
            return true
        }

        // 6. /cadmin set <claimId> <settingKey> <true|false>
        if (sub == "set") {
            if (args.size < 4) {
                sender.sendMessage(lang.get(player, "admin.usage-set"))
                return true
            }
            val id = args[1].removePrefix("#").toLongOrNull()
            val key = args[2].lowercase()
            val value = args[3].toBooleanStrictOrNull()
            if (id == null || value == null) {
                sender.sendMessage(lang.get(player, "admin.invalid-set-args"))
                return true
            }
            store.setBoolean(key, id, value)
            if (key == ClaimSettingsKeys.TNT) {
                bridge.syncClaimExplosives(id, value)
            }
            sender.sendMessage(lang.get(player, "admin.set-success", "claimId" to id.toString(), "key" to key, "value" to value.toString()))
            auditLogger?.log(sender.name, "admin-set-setting", "claim=$id key=$key value=$value")
            return true
        }

        // 7. /cadmin spawntest <entityType> [spawnReason] — 在腳下用指定來源實際觸發一次生成，驗證 /cmob 過濾
        if (sub == "spawntest") {
            if (player == null) {
                sender.sendMessage(lang.get("system.player-only"))
                return true
            }
            runSpawnTest(player, args)
            return true
        }

        // 8. /cadmin <claimId|alias> (directly open claim settings GUI)
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

        sender.sendMessage(lang.get(player, "admin.help"))
        return true
    }

    /**
     * 管理員診斷：在原地以指定 [CreatureSpawnEvent.SpawnReason] 實際生成一次生物，走完整事件鏈，
     * 回報 /cmob 的分類、目前所在領地與這次是否被擋。用來驗證生怪過濾，不是玩家功能。
     */
    private fun runSpawnTest(player: Player, args: Array<out String>) {
        val typeArg = args.getOrNull(1)
        if (typeArg == null) {
            player.sendMessage("§e/cadmin spawntest <entityType> [spawnReason] §7— 預設 NATURAL")
            return
        }
        val type = runCatching { EntityType.valueOf(typeArg.uppercase()) }.getOrNull()
        val entityClass = type?.entityClass
        if (type == null || entityClass == null || !LivingEntity::class.java.isAssignableFrom(entityClass)) {
            player.sendMessage("§c無法辨識的生物類型：$typeArg")
            return
        }
        val reasonArg = args.getOrNull(2) ?: "NATURAL"
        val reason = runCatching { CreatureSpawnEvent.SpawnReason.valueOf(reasonArg.uppercase()) }.getOrNull()
        if (reason == null) {
            player.sendMessage("§c無法辨識的生成來源：$reasonArg")
            return
        }

        val location = player.location
        val claimId = bridge.getTopClaimIgnoringHeight(location)?.id
        @Suppress("UNCHECKED_CAST")
        val livingClass = entityClass as Class<out LivingEntity>

        // 先用不受過濾的 CUSTOM 生一隻出來取分類(隨即移除),再用要驗的來源實際跑一次事件鏈
        val probe = runCatching {
            player.world.spawn(location, livingClass, CreatureSpawnEvent.SpawnReason.CUSTOM, false) { }
        }.getOrNull()
        val key = probe?.let { ClaimToggleListener.categoryKey(it) }
        probe?.remove()

        val spawned = runCatching {
            player.world.spawn(location, livingClass, reason, false) { }
        }.getOrNull()
        val blocked = spawned == null || !spawned.isValid
        spawned?.takeIf { it.isValid }?.remove()

        player.sendMessage(
            "§b[spawntest] §f${type.name} §7reason=§f${reason.name} §7claim=§f${claimId ?: "無"} " +
                "§7類別=§f${key ?: "不歸類"} §7結果=" + if (blocked) "§a已擋下" else "§c放行",
        )
        auditLogger?.log(player.name, "admin-spawntest", "type=${type.name} reason=${reason.name} claim=$claimId blocked=$blocked")
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (!sender.hasPermission("griefpreventionaddon.admin") && !sender.isOp) return emptyList()

        if (args.size == 1) {
            val list = mutableListOf("list", "tp", "delete", "name", "set", "spawntest", "help")
            val allClaims = GriefPrevention.instance.dataStore.claims ?: emptyList()
            list.addAll(allClaims.take(20).mapNotNull { it.id?.toString() })
            return list.filter { it.startsWith(args[0], ignoreCase = true) }
        }

        if (args.size == 2) {
            val sub = args[0].lowercase()
            if (sub == "spawntest") {
                return EntityType.entries
                    .filter { it.isSpawnable && it.entityClass?.let(LivingEntity::class.java::isAssignableFrom) == true }
                    .map { it.name.lowercase() }
                    .filter { it.startsWith(args[1], ignoreCase = true) }
            }
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
