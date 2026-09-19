package com.tinyyana.griefPreventionAddon.gui

import com.tinyyana.lycoLib.menu.GuardedMenu
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
) : InventoryHolder, GuardedMenu {

    private lateinit var inv: Inventory

    /** 這一輪開啟時算出來的殼層背景狀態(見 [com.tinyyana.griefPreventionAddon.gui.ClaimSettingsGuiService.open]);
     * `render()` 重繪同一個視窗時收尾要看同一個值,不重新判斷。 */
    var decorated: Boolean = false

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
