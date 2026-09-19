package com.tinyyana.griefPreventionAddon.gui

import com.tinyyana.lycoLib.menu.GuardedMenu
import me.ryanhamshire.GriefPrevention.Claim
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

class ClaimAdminListGuiHolder(
    var page: Int = 1,
    var totalPages: Int = 1,
    var filterType: String = "ALL", // ALL, PLAYER, ADMIN
    var targetPlayerName: String? = null,
    val pageClaims: MutableList<Claim> = mutableListOf(),
) : InventoryHolder, GuardedMenu {
    private var inventory: Inventory? = null

    /** 見 [com.tinyyana.griefPreventionAddon.gui.ClaimAdminListGuiService.open] 的同名欄位說明。 */
    var decorated: Boolean = false

    fun setInventory(inv: Inventory) {
        this.inventory = inv
    }

    override fun getInventory(): Inventory {
        return inventory ?: throw IllegalStateException("Inventory not initialized for ClaimAdminListGuiHolder")
    }
}
