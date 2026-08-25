package com.tinyyana.griefPreventionAddon.gui

import com.tinyyana.griefPreventionAddon.integration.ClaimInfoResult
import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
import com.tinyyana.griefPreventionAddon.teleport.ClaimTeleportService
import com.tinyyana.lycoLib.config.Messages
import com.tinyyana.lycoLib.menu.MenuBand
import com.tinyyana.lycoLib.menu.MenuBuilder
import com.tinyyana.lycoLib.menu.MenuSize
import com.tinyyana.lycoLib.menu.NavigationSlots
import com.tinyyana.lycoLib.menu.SectionLayout
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory

class ClaimSettingsGuiService(
    private val bridge: GriefPreventionBridge,
    private val store: ClaimSettingsStore,
    private val teleportService: ClaimTeleportService,
    private val messages: Messages,
    private val builder: MenuBuilder = MenuBuilder(),
) {
    companion object {
        const val CONTEXT_BAND = "context"
        const val SAFETY_BAND = "safety"
        const val MOBS_BAND = "mobs"

        const val HEADER_SLOT = 0
        const val TELEPORT_SLOT = 1
        const val RENAME_SLOT = 2
        const val ADMIN_PANEL_SLOT = 3
        const val SPAWN_SLOT = 4

        const val TNT_SLOT = 9
        const val PVP_SLOT = 10

        const val MOB_HOSTILE_SLOT = 18
        const val MOB_RAIDER_SLOT = 19
        const val MOB_PHANTOM_SLOT = 20
        const val MOB_SLIME_SLOT = 21
        const val MOB_AMBIENT_SLOT = 22
    }

    val slotToSetting = mutableMapOf<Int, ClaimSettingDefinition>()

    init {
        // 帶狀排版規劃:
        // Band 0 (Context): Slot 0 (資訊卡), Slot 1 (傳送主要行動)
        // Band 1 (Safety):  Slot 9 (TNT), Slot 10 (PVP)
        // Band 2 (Mobs):    Slot 18 (Hostile), 19 (Raider), 20 (Phantom), 21 (Slime), 22 (Ambient)
        // Band 3 (Footer):  Slot 34 (Help), Slot 35 (Close)
        val settings = ClaimSettingRegistry.ALL_SETTINGS
        if (settings.size >= 7) {
            slotToSetting[TNT_SLOT] = settings[0] // TNT
            slotToSetting[PVP_SLOT] = settings[1] // PVP

            slotToSetting[MOB_HOSTILE_SLOT] = settings[2] // Hostile
            slotToSetting[MOB_RAIDER_SLOT] = settings[3] // Raider
            slotToSetting[MOB_PHANTOM_SLOT] = settings[4] // Phantom
            slotToSetting[MOB_SLIME_SLOT] = settings[5] // Slime
            slotToSetting[MOB_AMBIENT_SLOT] = settings[6] // Ambient
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
        val title = messages.raw(titleKey)?.replace("{claimId}", info.claimId.toString()) ?: "花域設定 #${info.claimId}"
        val inv = builder.build(holder, MenuSize.MEDIUM, title)
        holder.setInventory(inv)

        render(inv, holder, info)
        player.openInventory(inv)
    }

    fun render(inv: Inventory, holder: ClaimSettingsGuiHolder, info: ClaimInfoResult) {
        inv.clear()

        val nav = NavigationSlots.resolve(MenuSize.MEDIUM)
        val safeLoc = bridge.getSafeTeleportLocation(holder.claim)

        // 1. Context Band: 狀態卡 (Slot 0)
        val typeStr = if (info.isAdminClaim) "管理員花域" else (if (info.isSubdivision) "子花域" else "一般花域")
        val ownerStr = info.ownerName ?: "管理員 / 公共"
        val alias = store.getAlias(holder.claimId)
        val aliasDisplay = if (!alias.isNullOrBlank()) "「$alias」" else null
        val titleText = if (aliasDisplay != null) {
            "<color:#ff8fc4><bold>花域狀態: $alias</bold></color> <color:#a8a8a8>#${holder.claimId}</color>"
        } else {
            "<color:#ff8fc4><bold>花域狀態</bold></color> <color:#a8a8a8>#${holder.claimId}</color>"
        }
        val headerLore = mutableListOf(
            "<gray>你目前所在的花域領地詳細資料</gray>",
            "",
            "<dark_gray>地主</dark_gray> <color:#f5f5f5>$ownerStr</color>",
            "<dark_gray>別名</dark_gray> " + if (aliasDisplay != null) "<color:#ffd166>$alias</color>" else "<color:#a8a8a8>未設定 (輸入 /cname 設定)</color>",
            "<dark_gray>尺寸</dark_gray> <color:#f5f5f5>${info.width} × ${info.height}</color> <color:#a8a8a8>(${info.area} 格)</color>",
            "<dark_gray>中心</dark_gray> <color:#a8a8a8>${safeLoc.world?.name ?: "world"} (${safeLoc.blockX}, ${safeLoc.blockZ})</color>",
            "<dark_gray>類型</dark_gray> <color:#f5f5f5>$typeStr</color>",
        )
        if (holder.isAdminViewer) {
            headerLore.add("")
            headerLore.add("<color:#fca5a5>※ 管理員模式：正在查看他人花域</color>")
        }

        builder.placeIcon(
            inventory = inv,
            slot = HEADER_SLOT,
            iconId = "claim",
            name = titleText,
            lore = headerLore,
            fallback = Material.GOLDEN_SHOVEL,
        )

        // 2. Context Band: 傳送主要行動 (Slot 1)
        // 設了落腳點就傳落腳點,標題與座標都要跟著換——顯示「中心」卻傳到別處是最糟的介面
        val customSpawn = teleportService.resolveCustomSpawn(holder.claim)
        val destLoc = customSpawn ?: safeLoc
        val teleportLore = listOf(
            if (customSpawn != null) {
                "<gray>點擊傳送到你自己設定的落腳點</gray>"
            } else {
                "<gray>點擊瞬間傳送至此花域的中心安全地面</gray>"
            },
            "",
            "<dark_gray>目標</dark_gray> <color:#6fd8e8>${aliasDisplay ?: "#${holder.claimId}"}</color>",
            "<dark_gray>座標</dark_gray> <color:#a8a8a8>(${destLoc.world?.name ?: "world"} ${destLoc.blockX}, ${destLoc.blockZ})</color>",
            "",
            "<yellow><bold>左鍵</bold></yellow><white> 立即傳送</white>",
        )
        builder.placeIcon(
            inventory = inv,
            slot = TELEPORT_SLOT,
            iconId = "travel",
            name = if (customSpawn != null) {
                "<color:#ff8fc4><bold>傳送至花域落腳點</bold></color>"
            } else {
                "<color:#ff8fc4><bold>傳送至花域中心</bold></color>"
            },
            lore = teleportLore,
            fallback = Material.ENDER_PEARL,
            glint = true,
        )

        // 3. Context Band: 設定別名行動 (Slot 2)
        val renameLore = listOf(
            "<gray>為這塊花域設定好記的名字（如主家、農場、商店）</gray>",
            "",
            "<dark_gray>目前別名</dark_gray> " + if (aliasDisplay != null) "<color:#ffd166>$alias</color>" else "<color:#a8a8a8>未設定 (預設 #${holder.claimId})</color>",
            "<dark_gray>命名好處</dark_gray> <color:#6fd8e8>之後可直接輸入 /ctp <別名> 傳送！</color>",
            "<dark_gray>格式限制</dark_gray> <color:#a8a8a8>1~20 字元，支援中英文、數字、底線</color>",
            "",
            "<yellow><bold>左鍵</bold></yellow><white> 點擊關閉選單並在聊天框輸入 /cname</white>",
        )
        builder.placeIcon(
            inventory = inv,
            slot = RENAME_SLOT,
            iconId = "name_tag",
            name = "<color:#ffd166><bold>設定花域別名 (改名)</bold></color>",
            lore = renameLore,
            fallback = Material.NAME_TAG,
            glint = aliasDisplay != null,
        )

        // 3b. Context Band: 自訂落腳點 (Slot 4)
        val spawn = teleportService.resolveCustomSpawn(holder.claim)
        val spawnRawSet = store.getSpawnRaw(holder.claimId) != null
        val spawnLore = mutableListOf(
            "<gray>指定 /ctp 傳送過來時要落在哪裡,不設就是花域中心地面</gray>",
            "",
        )
        when {
            spawn != null -> spawnLore.add(
                "<dark_gray>目前落腳點</dark_gray> <color:#6fd8e8>(${spawn.blockX}, ${spawn.blockY}, ${spawn.blockZ})</color>",
            )
            // 設過但解析不出來或已經在花域外:要講清楚,不然玩家只會覺得「我設了但沒用」
            spawnRawSet -> spawnLore.add("<dark_gray>目前落腳點</dark_gray> <color:#fca5a5>已在花域範圍外,傳送會退回中心</color>")
            else -> spawnLore.add("<dark_gray>目前落腳點</dark_gray> <color:#a8a8a8>未設定 (使用花域中心)</color>")
        }
        spawnLore.add("<dark_gray>怎麼設</dark_gray> <color:#a8a8a8>站到想要的位置,再按這一格或輸入 /cspawn</color>")
        spawnLore.add("")
        spawnLore.add("<yellow><bold>左鍵</bold></yellow><white> 把你現在站的位置設成落腳點</white>")
        if (spawnRawSet) {
            spawnLore.add("<yellow><bold>右鍵</bold></yellow><white> 清除落腳點,恢復用花域中心</white>")
        }
        builder.placeIcon(
            inventory = inv,
            slot = SPAWN_SLOT,
            iconId = "warp_point",
            name = "<color:#ffb7d5><bold>花域落腳點</bold></color>",
            lore = spawnLore,
            fallback = Material.LODESTONE,
            glint = spawn != null,
        )

        // 4. Context Band: 管理員捷徑 (Slot 3, 僅管理員檢視模式顯示)
        if (holder.isAdminViewer) {
            val adminLore = listOf(
                "<gray>以管理員身分管理與檢視伺服器所有花域</gray>",
                "",
                "<dark_gray>地主 UUID</dark_gray> <color:#a8a8a8>${holder.ownerUuid ?: "管理員領地"}</color>",
                "<dark_gray>全服管理</dark_gray> <color:#6fd8e8>開啟全伺服器花域管理面板 (/cadmin)</color>",
                "",
                "<yellow><bold>左鍵</bold></yellow><white> 開啟全伺服器花域管理面板</white>",
            )
            builder.placeIcon(
                inventory = inv,
                slot = ADMIN_PANEL_SLOT,
                iconId = "admin",
                name = "<color:#fca5a5><bold>全服花域管理面板</bold></color>",
                lore = adminLore,
                fallback = Material.BEACON,
            )
        }

        // 5. 各功能開關卡片 (Row 1 & Row 2)
        slotToSetting.forEach { (slot, def) ->
            val enabled = def.isEnabled(store, holder.claimId)
            val stateText = if (enabled) def.enabledLabel else def.disabledLabel
            val actionHint = if (enabled) {
                if (def.key == "tnt") "切換為受到保護" else if (def.key == "pvp") "切換為領地保護" else "恢復自然生成"
            } else {
                if (def.key == "tnt") "切換為允許破壞" else if (def.key == "pvp") "開放 PVP 對戰" else "禁止自然生成"
            }

            val lore = listOf(
                "<gray>${def.description}</gray>",
                "",
                "<dark_gray>目前狀態</dark_gray> $stateText",
                "",
                "<yellow><bold>左鍵</bold></yellow><white> $actionHint</white>",
            )
            builder.placeIcon(
                inventory = inv,
                slot = slot,
                iconId = def.iconId,
                name = "<color:#ffb7d5><bold>${def.name}</bold></color>",
                lore = lore,
                fallback = def.fallbackMaterial,
                glint = enabled,
            )
        }

        // 4. Footer 導覽列
        builder.placeIcon(
            inventory = inv,
            slot = nav.help,
            iconId = "help",
            name = "<color:#ffb7d5><bold>花域設定說明</bold></color>",
            lore = listOf(
                "<gray>領地設定會即時套用於整塊花域</gray>",
                "",
                "<dark_gray>權限</dark_gray> <white>僅地主或管理員可變更設定</white>",
                "<dark_gray>快捷</dark_gray> <white>可用 /ctp、/cname、/ctnt、/pvp、/cmob</white>",
            ),
            fallback = Material.KNOWLEDGE_BOOK,
        )

        builder.placeIcon(
            inventory = inv,
            slot = nav.rightClose,
            iconId = "close",
            name = "<color:#a8a8a8><bold>關閉選單</bold></color>",
            lore = listOf("<yellow><bold>左鍵</bold></yellow><white> 關閉設定介面</white>"),
            fallback = Material.BARRIER,
        )

        // 5. 補齊背景 (使用全域 panel filler)
        builder.fillEmpty(inv, Material.GRAY_STAINED_GLASS_PANE)
    }
}
