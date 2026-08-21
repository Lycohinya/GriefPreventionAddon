package com.tinyyana.griefPreventionAddon.gui

import com.tinyyana.griefPreventionAddon.integration.ClaimInfoResult
import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.teleport.ClaimTeleportService
import com.tinyyana.lycoLib.config.Messages
import com.tinyyana.lycoLib.menu.MenuSize
import com.tinyyana.lycoLib.menu.NavigationSlots
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent

class ClaimAdminGuiListener(
    private val adminGuiService: ClaimAdminListGuiService,
    private val settingsGuiService: ClaimSettingsGuiService,
    private val bridge: GriefPreventionBridge,
    private val teleportService: ClaimTeleportService,
    private val messages: Messages,
) : Listener {

    @EventHandler(priority = EventPriority.HIGH)
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? ClaimAdminListGuiHolder ?: return
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        if (event.clickedInventory != event.view.topInventory) return
        val nav = NavigationSlots.resolve(MenuSize.LARGE)

        // 1. 關閉按鈕
        if (event.slot == nav.rightClose) {
            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.7f, 1.0f)
            player.closeInventory()
            return
        }

        // 2. 上一頁
        if (event.slot == nav.previousPage && holder.page > 1) {
            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.7f, 1.2f)
            adminGuiService.open(player, holder.page - 1, holder.filterType, holder.targetPlayerName)
            return
        }

        // 3. 下一頁
        if (event.slot == nav.nextPage && holder.page < holder.totalPages) {
            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.7f, 1.2f)
            adminGuiService.open(player, holder.page + 1, holder.filterType, holder.targetPlayerName)
            return
        }

        // 4. 切換篩選條件 (Slot 38)
        if (event.slot == ClaimAdminListGuiService.FILTER_SLOT) {
            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.7f, 1.2f)
            val nextFilter = when (holder.filterType) {
                "ALL" -> "PLAYER"
                "PLAYER" -> "ADMIN"
                else -> "ALL"
            }
            adminGuiService.open(player, 1, nextFilter, holder.targetPlayerName)
            return
        }

        // 5. 點擊領地卡片 (0 ~ 35)
        if (event.slot in 0 until holder.pageClaims.size) {
            val claim = holder.pageClaims[event.slot]
            val claimId = claim.id ?: return

            if (event.isShiftClick) {
                // Shift+左鍵: 瞬間傳送
                player.sendMessage(messages.get("admin.teleported", "claimId" to claimId.toString()))
                teleportService.teleport(player, claim, closeInventory = true)
                return
            }

            // 左鍵: 開啟領地設定 GUI (管理員模式)
            val info = ClaimInfoResult(
                claimId = claimId,
                ownerName = claim.ownerName,
                ownerUuid = claim.ownerID,
                width = claim.width,
                height = claim.height,
                area = claim.area,
                isAdminClaim = claim.isAdminClaim(),
                isSubdivision = claim.parent != null,
                topClaim = claim,
            )
            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.7f, 1.2f)
            settingsGuiService.open(player, info, isAdmin = true)
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onInventoryDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.holder is ClaimAdminListGuiHolder) {
            event.isCancelled = true
        }
    }
}
