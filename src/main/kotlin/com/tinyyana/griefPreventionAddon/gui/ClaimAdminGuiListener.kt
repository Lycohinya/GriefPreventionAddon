package com.tinyyana.griefPreventionAddon.gui

import com.tinyyana.griefPreventionAddon.gui.menu.MenuSize
import com.tinyyana.griefPreventionAddon.gui.menu.NavigationSlots
import com.tinyyana.griefPreventionAddon.i18n.LanguageManager
import com.tinyyana.griefPreventionAddon.integration.ClaimInfoResult
import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.teleport.ClaimTeleportService
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
    private val lang: LanguageManager,
) : Listener {

    @EventHandler(priority = EventPriority.HIGH)
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? ClaimAdminListGuiHolder ?: return
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        if (event.clickedInventory != event.view.topInventory) return
        val nav = NavigationSlots.resolve(MenuSize.LARGE)

        // 1. Close button
        if (event.slot == nav.rightClose) {
            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.7f, 1.0f)
            player.closeInventory()
            return
        }

        // 2. Previous page
        if (event.slot == nav.previousPage && holder.page > 1) {
            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.7f, 1.2f)
            adminGuiService.open(player, holder.page - 1, holder.filterType, holder.targetPlayerName)
            return
        }

        // 3. Next page
        if (event.slot == nav.nextPage && holder.page < holder.totalPages) {
            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.7f, 1.2f)
            adminGuiService.open(player, holder.page + 1, holder.filterType, holder.targetPlayerName)
            return
        }

        // 4. Cycle Filter (Slot 38)
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

        // 5. Claim Cards (0 ~ 35)
        if (event.slot in 0 until holder.pageClaims.size) {
            val claim = holder.pageClaims[event.slot]
            val claimId = claim.id ?: return

            if (event.isShiftClick) {
                // Shift+Left-Click: Fast teleport
                player.sendMessage(lang.get(player, "admin.teleported", "claimId" to claimId.toString()))
                teleportService.teleport(player, claim, closeInventory = true)
                return
            }

            // Left-Click: Open claim settings GUI in admin mode
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
