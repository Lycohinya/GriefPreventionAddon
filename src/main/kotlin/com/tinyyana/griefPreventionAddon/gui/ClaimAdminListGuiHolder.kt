package com.tinyyana.griefPreventionAddon.gui

import me.ryanhamshire.GriefPrevention.Claim
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

class ClaimAdminListGuiHolder(
    var page: Int = 1,
    var totalPages: Int = 1,
    var filterType: String = "ALL", // ALL, PLAYER, ADMIN
    var targetPlayerName: String? = null,
    val pageClaims: MutableList<Claim> = mutableListOf(),
) : InventoryHolder {
    private var inventory: Inventory? = null

    fun setInventory(inv: Inventory) {
        this.inventory = inv
    }

    override fun getInventory(): Inventory {
        return inventory ?: throw IllegalStateException("Inventory not initialized for ClaimAdminListGuiHolder")
    }
}
