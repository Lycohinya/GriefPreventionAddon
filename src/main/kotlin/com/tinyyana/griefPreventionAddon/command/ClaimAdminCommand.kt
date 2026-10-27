package com.tinyyana.griefPreventionAddon.command

import com.tinyyana.griefPreventionAddon.i18n.escapeForMiniMessageTemplate
import com.tinyyana.griefPreventionAddon.audit.AuditLogger
import com.tinyyana.griefPreventionAddon.claim.ClaimDepthService
import com.tinyyana.griefPreventionAddon.gui.ClaimAdminListGuiService
import com.tinyyana.griefPreventionAddon.gui.ClaimMenu
import com.tinyyana.griefPreventionAddon.gui.MenuPage
import com.tinyyana.griefPreventionAddon.gui.MenuState
import com.tinyyana.griefPreventionAddon.i18n.LanguageManager
import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.policy.ClaimFlags
import com.tinyyana.griefPreventionAddon.policy.ClaimSettingsService
import com.tinyyana.griefPreventionAddon.policy.SetOutcome
import com.tinyyana.griefPreventionAddon.policy.TriState
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
    private val menu: ClaimMenu,
    private val settings: ClaimSettingsService,
    private val trust: com.tinyyana.griefPreventionAddon.trust.ClaimTrustService,
    private val fireStatus: () -> String,
    private val teleportService: ClaimTeleportService,
    private val lang: LanguageManager,
    private val auditLogger: AuditLogger? = null,
): CommandExecutor, TabCompleter {

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
                player.sendMessage(lang.get(player, "teleport.invalid-target", "target" to escapeForMiniMessageTemplate(args[1])))
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
                sender.sendMessage(lang.get(player, "name.alias-set-success", "claimId" to id.toString(), "name" to escapeForMiniMessageTemplate(newName)))
            }
            auditLogger?.log(sender.name, "admin-set-alias", "claim=$id alias=$newName")
            return true
        }

        // 6. /cadmin set <claimId> <settingKey> <allow|deny|inherit|true|false>
        //    true/false 是儲存值(沿用舊腳本的語意,例如 no-hostile-spawn true = 禁止生成);
        //    allow/deny/inherit 是玩家看到的三態。範圍必須真的存在,子花域就寫子花域,不會改寫主花域。
        if (sub == "set") {
            if (args.size < 4) {
                sender.sendMessage(lang.get(player, "admin.usage-set"))
                return true
            }
            val id = args[1].removePrefix("#").toLongOrNull()
            val flag = ClaimFlags.byKey(args[2])
            val raw = args[3].lowercase()
            val state = when (raw) {
                "true" -> flag?.stateOf(true)
                "false" -> flag?.stateOf(false)
                else -> TriState.parse(raw)
            }
            if (id == null || flag == null || state == null) {
                sender.sendMessage(lang.get(player, "admin.invalid-set-args"))
                return true
            }
            val result = settings.set(sender, id, flag, state)
            when (result.outcome) {
                SetOutcome.OK, SetOutcome.UNCHANGED ->
                    sender.sendMessage(lang.get(player, "admin.set-success", "claimId" to id.toString(), "key" to flag.key, "value" to state.name.lowercase()))
                SetOutcome.CLAIM_GONE -> sender.sendMessage(lang.get(player, "teleport.claim-not-found", "claimId" to id.toString()))
                SetOutcome.UNAVAILABLE -> sender.sendMessage(lang.get(player, "flag.unavailable"))
                SetOutcome.NO_PERMISSION -> sender.sendMessage(lang.get(player, "system.no-permission"))
            }
            return true
        }

        // 6b. /cadmin inspect <claimId> — 診斷:這個範圍每項規則的本地值/實際值/來源、GP 原生欄位與信任。
        //     純文字、ASCII 標記開頭,RCON 與自動化測試直接讀(玩家文字不走這裡)。
        if (sub == "inspect") {
            // inspect at <world> <x> <y> <z>:查座標所在的精確範圍(子花域就是子花域)
            val claim = if (args.getOrNull(1).equals("at", ignoreCase = true) && args.size >= 6) {
                val world = org.bukkit.Bukkit.getWorld(args[2])
                val x = args[3].toDoubleOrNull()
                val y = args[4].toDoubleOrNull()
                val z = args[5].toDoubleOrNull()
                if (world == null || x == null || y == null || z == null) null
                else bridge.getClaimAt(org.bukkit.Location(world, x, y, z))
            } else {
                args.getOrNull(1)?.removePrefix("#")?.toLongOrNull()?.let { bridge.getClaim(it) }
            }
            if (claim == null) {
                sender.sendMessage("[inspect] claim=${args.getOrNull(1)} missing")
                return true
            }
            val lesser = claim.lesserBoundaryCorner
            val greater = claim.greaterBoundaryCorner
            sender.sendMessage(
                "[inspect] claim=${claim.getID()} parent=${claim.parent?.getID() ?: "-"} owner=${claim.getOwnerID() ?: "admin"} " +
                    "world=${lesser.world?.name} box=(${lesser.blockX},${lesser.blockZ})-(${greater.blockX},${greater.blockZ}) " +
                    "children=${claim.children.size} explosives=${claim.areExplosivesAllowed} restrictSub=${claim.getSubclaimRestrictions()}",
            )
            lesser.world?.let { w ->
                val floor = ClaimDepthService.effectiveFloor(claim)
                sender.sendMessage("[inspect] y=$floor..${w.maxHeight} lesserY=${lesser.blockY} world=[${w.minHeight},${w.maxHeight}) gap=${floor - w.minHeight}")
            }
            ClaimFlags.ALL.forEach { flag ->
                val r = settings.resolve(flag, claim)
                sender.sendMessage(
                    "[inspect] flag=${flag.key} local=${r.local} effective=${if (r.allowed) "allow" else "deny"} " +
                        "source=${r.source} inherit=${if (r.inheritedAllowed) "allow" else "deny"} available=${settings.isAvailable(flag)}",
                )
            }
            trust.entries(claim).forEach { e ->
                sender.sendMessage("[inspect] trust id=${e.id} name=${e.displayName} level=${e.level?.name ?: "-"} manager=${e.manager}")
            }
            return true
        }

        // 6b2. /cadmin depth <world> <x> <z> — 診斷:這根 XZ 柱每一層是不是同一塊花域(見 ClaimDepthService.probe)
        if (sub == "depth") {
            runDepth(sender, args)
            return true
        }

        // 6c. /cadmin fire — 診斷:火焰接管狀態與三個事件目前的 LOWEST 處理者
        if (sub == "fire") {
            sender.sendMessage("[fire] ${fireStatus()}")
            listOf(
                org.bukkit.event.block.BlockIgniteEvent.getHandlerList(),
                org.bukkit.event.block.BlockSpreadEvent.getHandlerList(),
                org.bukkit.event.block.BlockBurnEvent.getHandlerList(),
            ).zip(listOf("ignite", "spread", "burn")).forEach { (handlers, name) ->
                val lowest = handlers.registeredListeners.filter { it.priority == org.bukkit.event.EventPriority.LOWEST }
                    .joinToString { "${it.plugin.name}:${it.listener.javaClass.simpleName.ifEmpty { "anon" }}" }
                sender.sendMessage("[fire] $name LOWEST=[$lowest]")
            }
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
            menu.open(player, MenuState(MenuPage.OVERVIEW, claim.getID()))
            return true
        }

        sender.sendMessage(lang.get(player, "admin.help"))
        return true
    }

    private fun runDepth(sender: CommandSender, args: Array<out String>) {
        val world = args.getOrNull(1)?.let { org.bukkit.Bukkit.getWorld(it) }
        val x = args.getOrNull(2)?.toIntOrNull()
        val z = args.getOrNull(3)?.toIntOrNull()
        if (!bridge.isAvailable() || world == null || x == null || z == null) {
            sender.sendMessage("[claimdepth] usage: /cadmin depth <world> <x> <z>")
            return
        }
        ClaimDepthService.probe(sender, world, x, z)
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
        val claimId = bridge.getClaimAtIgnoringHeight(location)?.id
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
            val list = mutableListOf("list", "tp", "delete", "name", "set", "inspect", "depth", "fire", "spawntest", "help")
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
            if (sub == "depth") {
                return org.bukkit.Bukkit.getWorlds().map { it.name }.filter { it.startsWith(args[1], ignoreCase = true) }
            }
            if (sub == "tp" || sub == "delete" || sub == "name" || sub == "set" || sub == "inspect") {
                val allClaims = GriefPrevention.instance.dataStore.claims ?: emptyList()
                return allClaims.take(20).mapNotNull { it.id?.toString() }.filter { it.startsWith(args[1], ignoreCase = true) }
            }
        }

        if (args.size == 3 && args[0].equals("set", ignoreCase = true)) {
            return ClaimFlags.ALL.map { it.key }.filter { it.startsWith(args[2], ignoreCase = true) }
        }

        if (args.size == 4 && args[0].equals("set", ignoreCase = true)) {
            return listOf("allow", "deny", "inherit", "true", "false").filter { it.startsWith(args[3], ignoreCase = true) }
        }

        return emptyList()
    }
}
