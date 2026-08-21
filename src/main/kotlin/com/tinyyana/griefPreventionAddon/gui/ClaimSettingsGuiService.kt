package com.tinyyana.griefPreventionAddon.gui

import com.tinyyana.griefPreventionAddon.integration.ClaimInfoResult
import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
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
    private val messages: Messages,
    private val builder: MenuBuilder = MenuBuilder(),
) {
    companion object {
        const val CONTEXT_BAND = "context"
        const val SAFETY_BAND = "safety"
        const val MOBS_BAND = "mobs"

        const val HEADER_SLOT = 0
        const val TELEPORT_SLOT = 1

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
        val headerLore = mutableListOf(
            "<gray>你目前所在的花域領地詳細資料</gray>",
            "",
            "<dark_gray>地主</dark_gray> <color:#f5f5f5>$ownerStr</color>",
            "<dark_gray>尺寸</dark_gray> <color:#f5f5f5>${info.width} × ${info.height}</color> <color:#a8a8a8>(${info.area} 格)</color>",
            "<dark_gray>中心</dark_gray> <color:#a8a8a8>${safeLoc.world.name} (${safeLoc.blockX}, ${safeLoc.blockZ})</color>",
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
            name = "<color:#ff8fc4><bold>花域狀態</bold></color> <color:#a8a8a8>#${holder.claimId}</color>",
            lore = headerLore,
            fallback = Material.GOLDEN_SHOVEL,
        )

        // 2. Context Band: 傳送主要行動 (Slot 1)
        val teleportLore = listOf(
            "<gray>點擊瞬間傳送至此花域的中心安全地面</gray>",
            "",
            "<dark_gray>目的地</dark_gray> <color:#6fd8e8>${safeLoc.blockX}, ${safeLoc.blockY}, ${safeLoc.blockZ}</color>",
            "",
            "<yellow><bold>左鍵</bold></yellow><white> 立即傳送</white>",
        )
        builder.placeIcon(
            inventory = inv,
            slot = TELEPORT_SLOT,
            iconId = "travel",
            name = "<color:#ff8fc4><bold>傳送至花域中心</bold></color>",
            lore = teleportLore,
            fallback = Material.ENDER_PEARL,
            glint = true,
        )

        // 3. 各功能開關卡片 (Row 1 & Row 2)
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
                "<dark_gray>快捷</dark_gray> <white>可用 /ctnt、/pvp、/cmob 指令</white>",
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
