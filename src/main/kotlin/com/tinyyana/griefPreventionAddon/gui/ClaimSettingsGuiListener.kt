package com.tinyyana.griefPreventionAddon.gui

import com.tinyyana.griefPreventionAddon.command.ClaimSpawnCommand
import com.tinyyana.griefPreventionAddon.integration.ClaimInfoResult
import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsKeys
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
import com.tinyyana.griefPreventionAddon.teleport.ClaimTeleportService
import com.tinyyana.lycoLib.audit.AuditLog
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

class ClaimSettingsGuiListener(
    private val guiService: ClaimSettingsGuiService,
    private val adminGuiService: ClaimAdminListGuiService,
    private val bridge: GriefPreventionBridge,
    private val store: ClaimSettingsStore,
    private val teleportService: ClaimTeleportService,
    private val spawnCommand: ClaimSpawnCommand,
    private val messages: Messages,
) : Listener {

    @EventHandler(priority = EventPriority.HIGH)
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? ClaimSettingsGuiHolder ?: return
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        if (event.clickedInventory != event.view.topInventory) return

        val nav = NavigationSlots.resolve(MenuSize.MEDIUM)

        // 1. 關閉按鈕
        if (event.slot == nav.rightClose) {
            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.7f, 1.0f)
            player.closeInventory()
            return
        }

        // 2. 說明按鈕
        if (event.slot == nav.help) {
            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.7f, 1.2f)
            return
        }

        // 3. 傳送至花域主要行動 (Slot 1)
        if (event.slot == ClaimSettingsGuiService.TELEPORT_SLOT) {
            teleportService.teleport(player, holder.claim, closeInventory = true)
            return
        }

        // 4. 設定花域別名行動 (Slot 2)
        if (event.slot == ClaimSettingsGuiService.RENAME_SLOT) {
            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.7f, 1.2f)
            player.closeInventory()
            val currentAlias = store.getAlias(holder.claimId) ?: "未設定"
            player.sendMessage(messages.get("name.gui-prompt-header"))
            player.sendMessage(messages.get("name.gui-prompt-actions",
                "claimId" to holder.claimId.toString(),
                "current" to currentAlias
            ))
            return
        }

        // 4b. 花域落腳點 (Slot 4):左鍵設成現在站的位置,右鍵清除
        if (event.slot == ClaimSettingsGuiService.SPAWN_SLOT) {
            if (!holder.canEdit(player)) {
                player.sendMessage(messages.get("gui.no-permission"))
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f)
                return
            }
            if (event.isRightClick) {
                if (store.getSpawnRaw(holder.claimId) == null) {
                    player.sendMessage(messages.get("spawn.already-default", "claimId" to holder.claimId.toString()))
                } else {
                    store.setSpawnRaw(holder.claimId, null)
                    AuditLog.log("GriefPreventionAddon", player.name, "claim-spawn.clear", "claim=${holder.claimId}")
                    player.sendMessage(messages.get("spawn.cleared", "claimId" to holder.claimId.toString()))
                    player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.7f, 1.0f)
                }
            } else {
                spawnCommand.setSpawnHere(player, holder.claim, holder.claimId)
            }
            refresh(event, holder, player)
            return
        }

        // 5. 管理員面板捷徑 (Slot 3)
        if (event.slot == ClaimSettingsGuiService.ADMIN_PANEL_SLOT && (holder.isAdminViewer || player.hasPermission("griefpreventionaddon.admin"))) {
            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.7f, 1.2f)
            adminGuiService.open(player)
            return
        }

        // 6. 設定開關卡片
        val def = guiService.slotToSetting[event.slot] ?: return

        if (!holder.canEdit(player)) {
            player.sendMessage(messages.get("gui.no-permission"))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f)
            return
        }

        val newState = def.toggle(store, holder.claimId)
        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.7f, 1.2f)

        AuditLog.log("GriefPreventionAddon", player.name, "claim-setting.${def.key}", "claim=${holder.claimId} state=$newState")

        // PVP 切換時通知域內玩家
        if (def.key == ClaimSettingsKeys.PVP) {
            val pvpMsg = messages.get(if (newState) "pvp.allowed" else "pvp.blocked")
            val recipients = bridge.playersInsideClaim(holder.claimId).toSet() + player
            recipients.forEach { it.sendMessage(pvpMsg) }
        }

        refresh(event, holder, player)
    }

    /** 重畫介面,讓剛剛的變更立刻反映在卡片上 */
    private fun refresh(event: InventoryClickEvent, holder: ClaimSettingsGuiHolder, player: Player) {
        val info = bridge.getClaimInfo(player.location) ?: ClaimInfoResult(
            claimId = holder.claimId,
            ownerName = holder.ownerName,
            ownerUuid = holder.ownerUuid,
            width = holder.claim.width,
            height = holder.claim.height,
            area = holder.claim.area,
            isAdminClaim = holder.claim.isAdminClaim(),
            isSubdivision = holder.claim.parent != null,
            topClaim = holder.claim,
        )
        guiService.render(event.view.topInventory, holder, info)
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onInventoryDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.holder is ClaimSettingsGuiHolder) {
            event.isCancelled = true
        }
    }
}
