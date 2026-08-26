package com.tinyyana.griefPreventionAddon.gui.menu

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

/**
 * Clean inventory GUI builder using Paper Adventure Components and MiniMessage.
 */
class MenuBuilder(private val mm: MiniMessage = MiniMessage.miniMessage()) {

    fun build(holder: InventoryHolder, size: MenuSize, title: Component): Inventory =
        Bukkit.createInventory(holder, size.slotCount, title)

    fun build(holder: InventoryHolder, size: MenuSize, title: String): Inventory =
        Bukkit.createInventory(holder, size.slotCount, mm.deserialize(title))

    fun place(
        inventory: Inventory,
        slot: Int,
        material: Material,
        name: String,
        lore: List<String> = emptyList(),
        glint: Boolean = false,
    ) {
        val stack = ItemStack(material)
        stack.editMeta { meta ->
            meta.displayName(mm.deserialize(name))
            if (lore.isNotEmpty()) {
                meta.lore(lore.map { mm.deserialize(it) })
            }
            if (glint) {
                meta.setEnchantmentGlintOverride(true)
            }
        }
        inventory.setItem(slot, stack)
    }

    fun place(
        inventory: Inventory,
        slot: Int,
        material: Material,
        name: Component,
        lore: List<Component> = emptyList(),
        glint: Boolean = false,
    ) {
        val stack = ItemStack(material)
        stack.editMeta { meta ->
            meta.displayName(name)
            if (lore.isNotEmpty()) {
                meta.lore(lore)
            }
            if (glint) {
                meta.setEnchantmentGlintOverride(true)
            }
        }
        inventory.setItem(slot, stack)
    }

    fun fillEmpty(inventory: Inventory, filler: Material = Material.GRAY_STAINED_GLASS_PANE) {
        val fillerStack = ItemStack(filler)
        fillerStack.editMeta { meta ->
            meta.displayName(Component.text(" "))
        }
        for (slot in 0 until inventory.size) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, fillerStack)
            }
        }
    }
}
