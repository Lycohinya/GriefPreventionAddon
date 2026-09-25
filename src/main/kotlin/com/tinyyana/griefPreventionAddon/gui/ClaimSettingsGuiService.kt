package com.tinyyana.griefPreventionAddon.gui

import com.tinyyana.lycoLib.menu.MenuBuilder
import com.tinyyana.lycoLib.menu.MenuShell
import com.tinyyana.lycoLib.menu.MenuSize
import com.tinyyana.lycoLib.menu.NavigationSlots
import com.tinyyana.griefPreventionAddon.display.ClaimDisplayName
import com.tinyyana.griefPreventionAddon.i18n.LanguageManager
import com.tinyyana.griefPreventionAddon.i18n.escapeForMiniMessageTemplate
import com.tinyyana.griefPreventionAddon.integration.ClaimInfoResult
import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
import com.tinyyana.griefPreventionAddon.teleport.ClaimTeleportService
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory

class ClaimSettingsGuiService(
    private val bridge: GriefPreventionBridge,
    private val store: ClaimSettingsStore,
    private val teleportService: ClaimTeleportService,
    private val lang: LanguageManager,
    private val builder: MenuBuilder = MenuBuilder(),
) {
    companion object {
        const val HEADER_SLOT = 0
        const val TELEPORT_SLOT = 1
        const val RENAME_SLOT = 2
        const val SPAWN_SLOT = 3
        const val ADMIN_PANEL_SLOT = 4

        // 2026-09-19 第二輪:「進出與權限」與「生物與環境」各自一列,col 0 放群組標籤、
        // 內容從 col 1 起連續排(見 CHEST_UI_DESIGN_SYSTEM.md §9.1 第 1 條)。
        // 之前 8 顆開關擠在同一片不分組的格子裡,玩家只能一格一格 hover 猜哪些是同一類。
        const val ACCESS_GROUP_LABEL_SLOT = 9
        const val TNT_SLOT = 10
        const val PVP_SLOT = 11
        const val SETHOME_SLOT = 12

        const val MOB_GROUP_LABEL_SLOT = 18
        const val MOB_HOSTILE_SLOT = 19
        const val MOB_RAIDER_SLOT = 20
        const val MOB_PHANTOM_SLOT = 21
        const val MOB_SLIME_SLOT = 22
        const val MOB_AMBIENT_SLOT = 23
    }

    val slotToSetting = mutableMapOf<Int, ClaimSettingDefinition>()

    init {
        val settings = ClaimSettingRegistry.ALL_SETTINGS
        if (settings.size >= 8) {
            slotToSetting[TNT_SLOT] = settings[0] // TNT
            slotToSetting[PVP_SLOT] = settings[1] // PVP
            slotToSetting[SETHOME_SLOT] = settings[2] // SETHOME

            slotToSetting[MOB_HOSTILE_SLOT] = settings[3] // Hostile
            slotToSetting[MOB_RAIDER_SLOT] = settings[4] // Raider
            slotToSetting[MOB_PHANTOM_SLOT] = settings[5] // Phantom
            slotToSetting[MOB_SLIME_SLOT] = settings[6] // Slime
            slotToSetting[MOB_AMBIENT_SLOT] = settings[7] // Ambient
        }
    }

    fun open(player: Player, info: ClaimInfoResult, isAdmin: Boolean) {
        val isOwner = info.ownerUuid != null && info.ownerUuid == player.uniqueId
        val holder = ClaimSettingsGuiHolder(
            claimId = info.claimId,
            claim = info.topClaim,
            isOwner = isOwner,
            isAdminViewer = isAdmin && !isOwner,
            ownerName = info.ownerName,
            ownerUuid = info.ownerUuid,
        )

        val titleKey = if (holder.isAdminViewer) "gui.admin-title" else "gui.title"
        val titleTemplate = lang.raw(player, titleKey) ?: "Claim Settings #{claimId}"
        val title = titleTemplate.replace("{claimId}", info.claimId.toString())
        // 2.0 殼層背景。沒有資源包時 title 退回純文字、`decorated` 是 false,收尾照舊填玻璃。
        //
        // 三組東西各自一列(狀態與操作 / 進出與權限 / 生物與環境),但背景一直是 `page` 的
        // 單一底面——分組只存在於格位,畫面上仍然是一整片同質的開關(2026-09-20 Yanaa)。
        // `banded` 讓每一組落在自己的底面上,分組與背景從此不可能分岔。
        val shell = MenuShell.banded(MenuSize.MEDIUM, listOf(0, 1, 2))
        holder.decorated = MenuShell.decorated(player, shell)
        val inv = builder.build(holder, MenuSize.MEDIUM, MenuShell.title(player, shell, title))
        holder.setInventory(inv)

        render(inv, holder, info, player)
        player.openInventory(inv)
    }

    fun render(inv: Inventory, holder: ClaimSettingsGuiHolder, info: ClaimInfoResult, player: Player? = null) {
        inv.clear()

        val nav = NavigationSlots.resolve(MenuSize.MEDIUM)
        val safeLoc = bridge.getSafeTeleportLocation(holder.claim)

        // 1. Context Band: Status Card (Slot 0)
        val typeStr = when {
            info.isAdminClaim -> lang.raw(player, "gui.type-admin") ?: "Admin Claim"
            info.isSubdivision -> lang.raw(player, "gui.type-subdivision") ?: "Subdivision"
            else -> lang.raw(player, "gui.type-normal") ?: "Player Claim"
        }
        val ownerStr = info.ownerName ?: lang.raw(player, "gui.owner-admin") ?: "Admin / Server"
        val display = ClaimDisplayName.resolve(store, holder.claimId)
        val aliasDisplay = if (display.hasAlias) display.decorated else null
        // GUI 的 name/lore 也是走 MiniMessage 解析(見 MenuBuilder.placeIcon),別名一樣要跳脫,
        // 否則 /cadmin name(完全沒驗證字元)塞入 `<`、`>` 會破壞這裡的標籤結構
        // ——同一顆 escapeForMiniMessageTemplate 給聊天訊息(LanguageManager.get)跟這裡共用。
        val escapedAlias = display.alias?.let { escapeForMiniMessageTemplate(it) }
        val escapedAliasDisplay = escapedAlias?.let { "「$it」" }

        val titleTemplate = if (aliasDisplay != null) {
            lang.raw(player, "gui.card-status-title-named") ?: "<color:#ff8fc4><bold>Claim Status: {alias}</bold></color> <color:#a8a8a8>#{claimId}</color>"
        } else {
            lang.raw(player, "gui.card-status-title") ?: "<color:#ff8fc4><bold>Claim Status</bold></color> <color:#a8a8a8>#{claimId}</color>"
        }
        val titleText = titleTemplate.replace("{claimId}", holder.claimId.toString()).replace("{alias}", escapedAlias ?: "")

        val headerLore = mutableListOf(
            lang.raw(player, "gui.card-status-desc") ?: "<gray>Detailed information of the current claim</gray>",
            "",
            (lang.raw(player, "gui.card-status-owner") ?: "<dark_gray>Owner</dark_gray> <color:#f5f5f5>{owner}</color>").replace("{owner}", ownerStr),
            if (aliasDisplay != null) {
                (lang.raw(player, "gui.card-status-alias") ?: "<dark_gray>Alias</dark_gray> <color:#ffd166>{alias}</color>").replace("{alias}", escapedAlias.orEmpty())
            } else {
                lang.raw(player, "gui.card-status-no-alias") ?: "<dark_gray>Alias</dark_gray> <color:#a8a8a8>None</color>"
            },
            (lang.raw(player, "gui.card-status-size") ?: "<dark_gray>Size</dark_gray> <color:#f5f5f5>{width} × {height}</color> <color:#a8a8a8>({area} blocks)</color>")
                .replace("{width}", info.width.toString())
                .replace("{height}", info.height.toString())
                .replace("{area}", info.area.toString()),
            (lang.raw(player, "gui.card-status-center") ?: "<dark_gray>Center</dark_gray> <color:#a8a8a8>{world} ({x}, {z})</color>")
                .replace("{world}", safeLoc.world?.name ?: "world")
                .replace("{x}", safeLoc.blockX.toString())
                .replace("{z}", safeLoc.blockZ.toString()),
            (lang.raw(player, "gui.card-status-type") ?: "<dark_gray>Type</dark_gray> <color:#f5f5f5>{type}</color>").replace("{type}", typeStr),
        )
        if (holder.isAdminViewer) {
            headerLore.add("")
            headerLore.add(lang.raw(player, "gui.card-status-admin-hint") ?: "<color:#fca5a5>※ Admin Mode: Viewing another player's claim</color>")
        }

        builder.placeIcon(
            inventory = inv,
            slot = HEADER_SLOT,
            iconId = "claim",
            name = titleText,
            lore = headerLore,
            fallback = Material.GOLDEN_SHOVEL,
        )

        // 2. Context Band: Teleport Action (Slot 1)
        val customSpawn = teleportService.resolveCustomSpawn(holder.claim)
        val destLoc = customSpawn ?: safeLoc
        val teleportTitle = if (customSpawn != null) {
            lang.raw(player, "gui.btn-teleport-name-spawn") ?: "<color:#ff8fc4><bold>Teleport to Custom Spawn</bold></color>"
        } else {
            lang.raw(player, "gui.btn-teleport-name-center") ?: "<color:#ff8fc4><bold>Teleport to Claim Center</bold></color>"
        }
        val teleportDesc = if (customSpawn != null) {
            lang.raw(player, "gui.btn-teleport-desc-spawn") ?: "<gray>Click to teleport to your custom spawn point</gray>"
        } else {
            lang.raw(player, "gui.btn-teleport-desc-center") ?: "<gray>Click to teleport to the safe center of this claim</gray>"
        }
        val teleportTarget = (lang.raw(player, "gui.btn-teleport-target") ?: "<dark_gray>Target</dark_gray> <color:#6fd8e8>{target}</color>")
            .replace("{target}", escapedAliasDisplay ?: "#${holder.claimId}")
        val teleportCoords = (lang.raw(player, "gui.btn-teleport-coords") ?: "<dark_gray>Coords</dark_gray> <color:#a8a8a8>({world} {x}, {z})</color>")
            .replace("{world}", destLoc.world?.name ?: "world")
            .replace("{x}", destLoc.blockX.toString())
            .replace("{z}", destLoc.blockZ.toString())
        val teleportAction = lang.raw(player, "gui.btn-teleport-action") ?: "<yellow><bold>Left-Click</bold></yellow><white> Teleport Now</white>"

        val teleportLore = listOf(
            teleportDesc,
            "",
            teleportTarget,
            teleportCoords,
            "",
            teleportAction,
        )
        // 「傳送」是常駐可點功能,不是這一頁此刻在要求玩家做的那件事,不掛光暈——
        // 光暈留給「主要行動」(見 CHEST_UI_DESIGN_SYSTEM.md §3.1/§3.2,一頁至多 0 或 1 個)。
        builder.placeIcon(
            inventory = inv,
            slot = TELEPORT_SLOT,
            iconId = "travel",
            name = teleportTitle,
            lore = teleportLore,
            fallback = Material.ENDER_PEARL,
        )

        // 3. Context Band: Rename / Alias Action (Slot 2)
        val renameTitle = lang.raw(player, "gui.btn-rename-name") ?: "<color:#ffd166><bold>Set Claim Alias</bold></color>"
        val renameDesc = lang.raw(player, "gui.btn-rename-desc") ?: "<gray>Assign a memorable name</gray>"
        val renameCurrent = if (aliasDisplay != null) {
            (lang.raw(player, "gui.btn-rename-current") ?: "<dark_gray>Current</dark_gray> <color:#ffd166>{alias}</color>").replace("{alias}", escapedAlias.orEmpty())
        } else {
            (lang.raw(player, "gui.btn-rename-current-none") ?: "<dark_gray>Current</dark_gray> <color:#a8a8a8>None</color>").replace("{claimId}", holder.claimId.toString())
        }
        val renameBenefit = lang.raw(player, "gui.btn-rename-benefit") ?: "<dark_gray>Benefit</dark_gray> <color:#6fd8e8>Directly use /ctp <alias>!</color>"
        val renameFormat = lang.raw(player, "gui.btn-rename-format") ?: "<dark_gray>Format</dark_gray> <color:#a8a8a8>1~20 chars</color>"
        val renameAction = lang.raw(player, "gui.btn-rename-action") ?: "<yellow><bold>Left-Click</bold></yellow><white> Type /cname in chat</white>"

        val renameLore = listOf(
            renameDesc,
            "",
            renameCurrent,
            renameBenefit,
            renameFormat,
            "",
            renameAction,
        )
        // 是否已設別名這件事已經寫在 lore 的「目前別名」那行,不需要再靠光暈重複講一次
        // ——光暈只留給主要行動(見上一顆按鈕的註解)。
        builder.placeIcon(
            inventory = inv,
            slot = RENAME_SLOT,
            iconId = "rename",
            name = renameTitle,
            lore = renameLore,
            fallback = Material.NAME_TAG,
        )

        // 3b. Context Band: Spawn Point (Slot 4)
        val spawn = teleportService.resolveCustomSpawn(holder.claim)
        val spawnRawSet = store.getSpawnRaw(holder.claimId) != null
        val spawnTitle = lang.raw(player, "gui.btn-spawn-name") ?: "<color:#ffb7d5><bold>Claim Spawn Point</bold></color>"
        val spawnDesc = lang.raw(player, "gui.btn-spawn-desc") ?: "<gray>Configure landing location for /ctp</gray>"

        val spawnLore = mutableListOf(
            spawnDesc,
            "",
        )
        when {
            spawn != null -> {
                val currentText = (lang.raw(player, "gui.btn-spawn-current") ?: "<dark_gray>Current</dark_gray> <color:#6fd8e8>({x}, {y}, {z})</color>")
                    .replace("{x}", spawn.blockX.toString())
                    .replace("{y}", spawn.blockY.toString())
                    .replace("{z}", spawn.blockZ.toString())
                spawnLore.add(currentText)
            }
            spawnRawSet -> spawnLore.add(lang.raw(player, "gui.btn-spawn-outside") ?: "<dark_gray>Current</dark_gray> <color:#fca5a5>Outside claim boundary</color>")
            else -> spawnLore.add(lang.raw(player, "gui.btn-spawn-none") ?: "<dark_gray>Current</dark_gray> <color:#a8a8a8>Default (Geometric Center)</color>")
        }
        spawnLore.add(lang.raw(player, "gui.btn-spawn-howto") ?: "<dark_gray>How-to</dark_gray> <color:#a8a8a8>Stand at spot and use /cspawn</color>")
        spawnLore.add("")
        spawnLore.add(lang.raw(player, "gui.btn-spawn-action-set") ?: "<yellow><bold>Left-Click</bold></yellow><white> Set spawn here</white>")
        if (spawnRawSet) {
            spawnLore.add(lang.raw(player, "gui.btn-spawn-action-clear") ?: "<yellow><bold>Right-Click</bold></yellow><white> Clear spawn</white>")
        }
        // 同上,是否已設落腳點已經寫在 lore 的「目前落腳點」那行,不用光暈重複講
        builder.placeIcon(
            inventory = inv,
            slot = SPAWN_SLOT,
            iconId = "warp_point",
            name = spawnTitle,
            lore = spawnLore,
            fallback = Material.LODESTONE,
        )

        // 4. Context Band: Admin panel shortcut (Slot 3)
        if (holder.isAdminViewer) {
            val adminTitle = lang.raw(player, "gui.btn-admin-panel-name") ?: "<color:#fca5a5><bold>Server Claims Admin Panel</bold></color>"
            val adminDesc = lang.raw(player, "gui.btn-admin-panel-desc") ?: "<gray>Manage and inspect all server claims</gray>"
            val adminUuid = (lang.raw(player, "gui.btn-admin-panel-uuid") ?: "<dark_gray>Owner UUID</dark_gray> <color:#a8a8a8>{uuid}</color>")
                .replace("{uuid}", holder.ownerUuid?.toString() ?: "Admin Claim")
            val adminText = lang.raw(player, "gui.btn-admin-panel-text") ?: "<dark_gray>Admin</dark_gray> <color:#6fd8e8>/cadmin</color>"
            val adminAction = lang.raw(player, "gui.btn-admin-panel-action") ?: "<yellow><bold>Left-Click</bold></yellow><white> Open panel</white>"

            val adminLore = listOf(
                adminDesc,
                "",
                adminUuid,
                adminText,
                "",
                adminAction,
            )
            builder.placeIcon(
                inventory = inv,
                slot = ADMIN_PANEL_SLOT,
                iconId = "admin",
                name = adminTitle,
                lore = adminLore,
                fallback = Material.BEACON,
            )
        }

        // 5. Setting Toggle Cards (Row 1 & Row 2),各自一條 group 帶:col 0 群組標籤、
        // 內容從 col 1 起連續排(見 CHEST_UI_DESIGN_SYSTEM.md §9.1 第 1 條)。
        // 純裝飾,不註冊 action、lore 至多一行——跟 §3.1「群組標籤」角色一致。
        builder.place(
            inventory = inv,
            slot = ACCESS_GROUP_LABEL_SLOT,
            material = Material.OAK_SIGN,
            name = "<color:#ffd166><bold>進出與權限</bold></color>",
            lore = listOf("<gray>誰能不能在這塊花域裡做什麼</gray>"),
        )
        builder.place(
            inventory = inv,
            slot = MOB_GROUP_LABEL_SLOT,
            material = Material.OAK_LEAVES,
            name = "<color:#ffd166><bold>生物與環境</bold></color>",
            lore = listOf("<gray>哪些生物不會在這裡自然出現</gray>"),
        )
        slotToSetting.forEach { (slot, def) ->
            val enabled = def.isEnabled(store, holder.claimId)
            val stateText = def.getStatusLabel(lang, player, enabled)
            val actionHint = def.getActionHint(lang, player, enabled)

            val name = "<color:#ffb7d5><bold>${def.getName(lang, player)}</bold></color>"
            val desc = "<gray>${def.getDescription(lang, player)}</gray>"
            val statusLine = (lang.raw(player, "settings.current-status") ?: "<dark_gray>Status</dark_gray> {state}").replace("{state}", stateText)
            val actionLine = (lang.raw(player, "settings.action-hint") ?: "<yellow><bold>Left-Click</bold></yellow><white> {action}</white>").replace("{action}", actionHint)

            val lore = listOf(
                desc,
                "",
                statusLine,
                "",
                actionLine,
            )
            builder.placeIcon(
                inventory = inv,
                slot = slot,
                iconId = def.iconId,
                name = name,
                lore = lore,
                fallback = def.fallbackMaterial,
                glint = enabled,
            )
        }

        // 6. Footer Navigation
        val helpName = lang.raw(player, "gui.footer-help-name") ?: "<color:#ffb7d5><bold>Help</bold></color>"
        val helpLore = lang.rawList(player, "gui.footer-help-lore")
        builder.placeIcon(
            inventory = inv,
            slot = nav.help,
            iconId = "help",
            name = helpName,
            lore = helpLore,
            fallback = Material.KNOWLEDGE_BOOK,
        )

        val closeName = lang.raw(player, "gui.footer-close-name") ?: "<color:#a8a8a8><bold>Close Menu</bold></color>"
        val closeAction = lang.raw(player, "gui.footer-close-action") ?: "<yellow><bold>Left-Click</bold></yellow><white> Close</white>"
        builder.placeIcon(
            inventory = inv,
            slot = nav.rightClose,
            iconId = "close",
            name = closeName,
            lore = listOf(closeAction),
            fallback = Material.BARRIER,
        )

        // 7. 有背景就只放分享按鈕,沒背景才填玻璃(見 PLAYER_SHELL.md §3)
        builder.finish(inv, holder.decorated)
    }
}
