package com.tinyyana.griefPreventionAddon.gui

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent

/** 只做分派;物品搬移與拖曳由 LycoLib 的 MenuGuardListener 統一擋(holder 是 GuardedMenu)。 */
class ClaimMenuListener(private val menu: ClaimMenu) : Listener {

    @EventHandler(priority = EventPriority.HIGH)
    fun onClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? ClaimMenuHolder ?: return
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        if (event.clickedInventory != event.view.topInventory) return
        if (player.uniqueId != holder.viewer) return
        menu.handle(player, holder, event.slot, event.click)
    }
}
