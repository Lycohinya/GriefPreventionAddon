package com.tinyyana.griefPreventionAddon.gui

import com.tinyyana.griefPreventionAddon.audit.AuditLogger
import com.tinyyana.griefPreventionAddon.command.ClaimSpawnCommand
import com.tinyyana.griefPreventionAddon.gui.menu.MenuSize
import com.tinyyana.griefPreventionAddon.gui.menu.NavigationSlots
import com.tinyyana.griefPreventionAddon.i18n.LanguageManager
import com.tinyyana.griefPreventionAddon.integration.ClaimInfoResult
import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsKeys
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
import com.tinyyana.griefPreventionAddon.teleport.ClaimTeleportService
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
    private val lang: LanguageManager,
    private val auditLogger: AuditLogger? = null,
) : Listener {

    @EventHandler(priority = EventPriority.HIGH)
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? ClaimSettingsGuiHolder ?: return
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        if (event.clickedInventory != event.view.topInventory) return

        val nav = NavigationSlots.resolve(MenuSize.MEDIUM)

        // 1. Close button
        if (event.slot == nav.rightClose) {
            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.7f, 1.0f)
            player.closeInventory()
            return
        }

        // 2. Help button
        if (event.slot == nav.help) {
            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.7f, 1.2f)
            return
        }

        // 3. Teleport Action (Slot 1)
        if (event.slot == ClaimSettingsGuiService.TELEPORT_SLOT) {
            teleportService.teleport(player, holder.claim, closeInventory = true)
            return
        }

        // 4. Rename / Set Alias Action (Slot 2)
        if (event.slot == ClaimSettingsGuiService.RENAME_SLOT) {
            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.7f, 1.2f)
            player.closeInventory()
            val currentAlias = store.getAlias(holder.claimId) ?: (lang.raw(player, "gui.card-status-no-alias") ?: "None")
            player.sendMessage(lang.get(player, "name.gui-prompt-header"))
            player.sendMessage(lang.get(player, "name.gui-prompt-actions",
                "claimId" to holder.claimId.toString(),
                "current" to currentAlias
            ))
            return
        }

        // 4b. Custom Spawn Point (Slot 4): Left click sets spawn here, Right click clears spawn
        if (event.slot == ClaimSettingsGuiService.SPAWN_SLOT) {
            if (!holder.canEdit(player)) {
                player.sendMessage(lang.get(player, "gui.no-permission"))
                player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f)
                return
            }
            if (event.isRightClick) {
                if (store.getSpawnRaw(holder.claimId) == null) {
                    player.sendMessage(lang.get(player, "spawn.already-default", "claimId" to holder.claimId.toString()))
                } else {
                    store.setSpawnRaw(holder.claimId, null)
                    auditLogger?.log(player.name, "claim-spawn.clear", "claim=${holder.claimId}")
                    player.sendMessage(lang.get(player, "spawn.cleared", "claimId" to holder.claimId.toString()))
                    player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.7f, 1.0f)
                }
            } else {
                spawnCommand.setSpawnHere(player, holder.claim, holder.claimId)
            }
            refresh(event, holder, player)
            return
        }

        // 5. Admin Panel Shortcut (Slot 3)
        if (event.slot == ClaimSettingsGuiService.ADMIN_PANEL_SLOT && (holder.isAdminViewer || player.hasPermission("griefpreventionaddon.admin"))) {
            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.7f, 1.2f)
            adminGuiService.open(player)
            return
        }

        // 6. Setting Toggle Cards
        val def = guiService.slotToSetting[event.slot] ?: return

        if (!holder.canEdit(player)) {
            player.sendMessage(lang.get(player, "gui.no-permission"))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f)
            return
        }

        val newState = def.toggle(store, holder.claimId)
        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.7f, 1.2f)

        auditLogger?.log(player.name, "claim-setting.${def.key}", "claim=${holder.claimId} state=$newState")

        if (def.key == ClaimSettingsKeys.TNT) {
            bridge.syncClaimExplosives(holder.claimId, newState)
        }

        // Broadcast or send notifications when PvP or other impactful settings change
        if (def.key == ClaimSettingsKeys.PVP) {
            val msgKey = if (newState) "pvp.allowed" else "pvp.blocked"
            val recipients = bridge.playersInsideClaim(holder.claimId).toSet() + player
            recipients.forEach { it.sendMessage(lang.get(it, msgKey)) }
        }

        refresh(event, holder, player)
    }

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
        guiService.render(event.view.topInventory, holder, info, player)
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onInventoryDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.holder is ClaimSettingsGuiHolder) {
            event.isCancelled = true
        }
    }
}
