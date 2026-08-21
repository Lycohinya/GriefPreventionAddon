package com.tinyyana.griefPreventionAddon.gui

import me.ryanhamshire.GriefPrevention.Claim
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import java.util.UUID

class ClaimSettingsGuiHolder(
    val claimId: Long,
    val claim: Claim,
    val isOwner: Boolean,
    val isAdminViewer: Boolean,
    val ownerName: String?,
    val ownerUuid: UUID?,
) : InventoryHolder {

    private lateinit var inv: Inventory

    fun setInventory(inventory: Inventory) {
        this.inv = inventory
    }

    override fun getInventory(): Inventory = inv

    fun canEdit(player: Player): Boolean {
        if (isOwner) return true
        if (player.hasPermission("griefpreventionaddon.settings.others") ||
            player.hasPermission("griefpreventionaddon.admin") ||
            player.isOp
        ) {
            return true
        }
        return false
    }
}
