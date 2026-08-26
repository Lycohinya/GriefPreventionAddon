package com.tinyyana.griefPreventionAddon.gui.menu

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

/**
 * Clean inventory GUI builder using Paper Adventure Components and MiniMessage.
 *
 * Supports Lycohinya custom_model_data resource-pack icons and forces upright (non-italic)
 * rendering by default for all item names and lores.
 */
class MenuBuilder(private val mm: MiniMessage = MiniMessage.miniMessage()) {

    fun deserializeUpright(text: String): Component =
        mm.deserialize(text).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)

    fun deserializeUpright(component: Component): Component =
        component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)

    fun build(holder: InventoryHolder, size: MenuSize, title: Component): Inventory =
        Bukkit.createInventory(holder, size.slotCount, deserializeUpright(title))

    fun build(holder: InventoryHolder, size: MenuSize, title: String): Inventory =
        Bukkit.createInventory(holder, size.slotCount, deserializeUpright(title))

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
            meta.displayName(deserializeUpright(name))
            if (lore.isNotEmpty()) {
                meta.lore(lore.map { deserializeUpright(it) })
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
            meta.displayName(deserializeUpright(name))
            if (lore.isNotEmpty()) {
                meta.lore(lore.map { deserializeUpright(it) })
            }
            if (glint) {
                meta.setEnchantmentGlintOverride(true)
            }
        }
        inventory.setItem(slot, stack)
    }

    fun placeIcon(
        inventory: Inventory,
        slot: Int,
        iconId: String?,
        name: String,
        lore: List<String> = emptyList(),
        fallback: Material = Material.PAPER,
        glint: Boolean = false,
    ) {
        val material = if (!iconId.isNullOrBlank()) (ICON_HOSTS[iconId] ?: fallback) else fallback
        val stack = ItemStack(material)
        if (!iconId.isNullOrBlank()) {
            val meta = stack.itemMeta
            if (meta != null) {
                val cmd = meta.customModelDataComponent
                cmd.setStrings(listOf("$NAMESPACE:$iconId"))
                meta.setCustomModelDataComponent(cmd)
                stack.itemMeta = meta
            }
        }
        stack.editMeta { meta ->
            meta.displayName(deserializeUpright(name))
            if (lore.isNotEmpty()) {
                meta.lore(lore.map { deserializeUpright(it) })
            }
            if (glint) {
                meta.setEnchantmentGlintOverride(true)
            }
        }
        inventory.setItem(slot, stack)
    }

    fun fillEmpty(
        inventory: Inventory,
        filler: Material = Material.GRAY_STAINED_GLASS_PANE,
        iconId: String = "filler",
    ) {
        val material = ICON_HOSTS[iconId] ?: filler
        val fillerStack = ItemStack(material)
        val meta = fillerStack.itemMeta
        if (meta != null) {
            val cmd = meta.customModelDataComponent
            cmd.setStrings(listOf("$NAMESPACE:$iconId"))
            meta.setCustomModelDataComponent(cmd)
            fillerStack.itemMeta = meta
        }
        fillerStack.editMeta { m ->
            m.displayName(deserializeUpright(" "))
        }
        for (slot in 0 until inventory.size) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, fillerStack)
            }
        }
    }

    companion object {
        const val NAMESPACE = "lycohinya"

        val ICON_HOSTS: Map<String, Material> = mapOf(
            "back" to Material.ARROW,
            "home" to Material.NETHER_STAR,
            "close" to Material.BARRIER,
            "previous_page" to Material.ARROW,
            "next_page" to Material.ARROW,
            "search" to Material.SPYGLASS,
            "filter" to Material.HOPPER,
            "sort" to Material.COMPARATOR,
            "help" to Material.KNOWLEDGE_BOOK,
            "confirm" to Material.LIME_DYE,
            "cancel" to Material.RED_DYE,
            "unavailable" to Material.GRAY_DYE,
            "locked" to Material.IRON_BARS,
            "warning" to Material.TNT,
            "tnt" to Material.TNT,
            "pvp" to Material.DIAMOND_SWORD,
            "bed" to Material.RED_BED,
            "claim" to Material.GOLDEN_SHOVEL,
            "travel" to Material.ENDER_PEARL,
            "rename" to Material.NAME_TAG,
            "warp_point" to Material.ENDER_PEARL,
            "admin" to Material.BEACON,
            "settings" to Material.COMPARATOR,
            "page_indicator" to Material.PAPER,
            "info" to Material.PAPER,
            "filler" to Material.GRAY_STAINED_GLASS_PANE,
            "mob_hostile" to Material.ZOMBIE_HEAD,
            "mob_raider" to Material.CROSSBOW,
            "mob_phantom" to Material.PHANTOM_MEMBRANE,
            "mob_slime" to Material.SLIME_BALL,
            "mob_ambient" to Material.FEATHER,
        )
    }
}
