package com.tinyyana.griefPreventionAddon.gui

import com.tinyyana.griefPreventionAddon.gui.menu.MenuBuilder
import com.tinyyana.griefPreventionAddon.gui.menu.MenuSize
import com.tinyyana.griefPreventionAddon.gui.menu.NavigationSlots
import com.tinyyana.griefPreventionAddon.i18n.LanguageManager
import com.tinyyana.griefPreventionAddon.integration.ClaimInfoResult
import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
import com.tinyyana.griefPreventionAddon.teleport.ClaimTeleportService
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory

class ClaimSettingsGuiService(
    private val bridge: GriefPreventionBridge,
    private val store: ClaimSettingsStore,
    private val teleportService: ClaimTeleportService,
    private val lang: LanguageManager,
    private val builder: MenuBuilder = MenuBuilder(),
) {
    companion object {
        const val HEADER_SLOT = 0
        const val TELEPORT_SLOT = 1
        const val RENAME_SLOT = 2
        const val ADMIN_PANEL_SLOT = 3
        const val SPAWN_SLOT = 4

        const val TNT_SLOT = 9
        const val PVP_SLOT = 10
        const val SETHOME_SLOT = 11

        const val MOB_HOSTILE_SLOT = 18
        const val MOB_RAIDER_SLOT = 19
        const val MOB_PHANTOM_SLOT = 20
        const val MOB_SLIME_SLOT = 21
        const val MOB_AMBIENT_SLOT = 22
    }

    val slotToSetting = mutableMapOf<Int, ClaimSettingDefinition>()

    init {
        val settings = ClaimSettingRegistry.ALL_SETTINGS
        if (settings.size >= 8) {
            slotToSetting[TNT_SLOT] = settings[0] // TNT
            slotToSetting[PVP_SLOT] = settings[1] // PVP
            slotToSetting[SETHOME_SLOT] = settings[2] // SETHOME

            slotToSetting[MOB_HOSTILE_SLOT] = settings[3] // Hostile
            slotToSetting[MOB_RAIDER_SLOT] = settings[4] // Raider
            slotToSetting[MOB_PHANTOM_SLOT] = settings[5] // Phantom
            slotToSetting[MOB_SLIME_SLOT] = settings[6] // Slime
            slotToSetting[MOB_AMBIENT_SLOT] = settings[7] // Ambient
        }
    }

    fun open(player: Player, info: ClaimInfoResult, isAdmin: Boolean) {
        val isOwner = info.ownerUuid != null && info.ownerUuid == player.uniqueId
        val holder = ClaimSettingsGuiHolder(
            claimId = info.claimId,
            claim = info.topClaim,
            isOwner = isOwner,
            isAdminViewer = isAdmin && !isOwner,
            ownerName = info.ownerName,
            ownerUuid = info.ownerUuid,
        )

        val titleKey = if (holder.isAdminViewer) "gui.admin-title" else "gui.title"
        val titleTemplate = lang.raw(player, titleKey) ?: "Claim Settings #{claimId}"
        val title = titleTemplate.replace("{claimId}", info.claimId.toString())
        val inv = builder.build(holder, MenuSize.MEDIUM, title)
        holder.setInventory(inv)

        render(inv, holder, info, player)
        player.openInventory(inv)
    }

    fun render(inv: Inventory, holder: ClaimSettingsGuiHolder, info: ClaimInfoResult, player: Player? = null) {
        inv.clear()

        val nav = NavigationSlots.resolve(MenuSize.MEDIUM)
        val safeLoc = bridge.getSafeTeleportLocation(holder.claim)

        // 1. Context Band: Status Card (Slot 0)
        val typeStr = when {
            info.isAdminClaim -> lang.raw(player, "gui.type-admin") ?: "Admin Claim"
            info.isSubdivision -> lang.raw(player, "gui.type-subdivision") ?: "Subdivision"
            else -> lang.raw(player, "gui.type-normal") ?: "Player Claim"
        }
        val ownerStr = info.ownerName ?: lang.raw(player, "gui.owner-admin") ?: "Admin / Server"
        val alias = store.getAlias(holder.claimId)
        val aliasDisplay = if (!alias.isNullOrBlank()) "「$alias」" else null

        val titleTemplate = if (aliasDisplay != null) {
            lang.raw(player, "gui.card-status-title-named") ?: "<color:#ff8fc4><bold>Claim Status: {alias}</bold></color> <color:#a8a8a8>#{claimId}</color>"
        } else {
            lang.raw(player, "gui.card-status-title") ?: "<color:#ff8fc4><bold>Claim Status</bold></color> <color:#a8a8a8>#{claimId}</color>"
        }
        val titleText = titleTemplate.replace("{claimId}", holder.claimId.toString()).replace("{alias}", alias ?: "")

        val headerLore = mutableListOf(
            lang.raw(player, "gui.card-status-desc") ?: "<gray>Detailed information of the current claim</gray>",
            "",
            (lang.raw(player, "gui.card-status-owner") ?: "<dark_gray>Owner</dark_gray> <color:#f5f5f5>{owner}</color>").replace("{owner}", ownerStr),
            if (aliasDisplay != null) {
                (lang.raw(player, "gui.card-status-alias") ?: "<dark_gray>Alias</dark_gray> <color:#ffd166>{alias}</color>").replace("{alias}", alias.orEmpty())
            } else {
                lang.raw(player, "gui.card-status-no-alias") ?: "<dark_gray>Alias</dark_gray> <color:#a8a8a8>None</color>"
            },
            (lang.raw(player, "gui.card-status-size") ?: "<dark_gray>Size</dark_gray> <color:#f5f5f5>{width} × {height}</color> <color:#a8a8a8>({area} blocks)</color>")
                .replace("{width}", info.width.toString())
                .replace("{height}", info.height.toString())
                .replace("{area}", info.area.toString()),
            (lang.raw(player, "gui.card-status-center") ?: "<dark_gray>Center</dark_gray> <color:#a8a8a8>{world} ({x}, {z})</color>")
                .replace("{world}", safeLoc.world?.name ?: "world")
                .replace("{x}", safeLoc.blockX.toString())
                .replace("{z}", safeLoc.blockZ.toString()),
            (lang.raw(player, "gui.card-status-type") ?: "<dark_gray>Type</dark_gray> <color:#f5f5f5>{type}</color>").replace("{type}", typeStr),
        )
        if (holder.isAdminViewer) {
            headerLore.add("")
            headerLore.add(lang.raw(player, "gui.card-status-admin-hint") ?: "<color:#fca5a5>※ Admin Mode: Viewing another player's claim</color>")
        }

        builder.place(
            inventory = inv,
            slot = HEADER_SLOT,
            material = Material.GOLDEN_SHOVEL,
            name = titleText,
            lore = headerLore,
        )

        // 2. Context Band: Teleport Action (Slot 1)
        val customSpawn = teleportService.resolveCustomSpawn(holder.claim)
        val destLoc = customSpawn ?: safeLoc
        val teleportTitle = if (customSpawn != null) {
            lang.raw(player, "gui.btn-teleport-name-spawn") ?: "<color:#ff8fc4><bold>Teleport to Custom Spawn</bold></color>"
        } else {
            lang.raw(player, "gui.btn-teleport-name-center") ?: "<color:#ff8fc4><bold>Teleport to Claim Center</bold></color>"
        }
        val teleportDesc = if (customSpawn != null) {
            lang.raw(player, "gui.btn-teleport-desc-spawn") ?: "<gray>Click to teleport to your custom spawn point</gray>"
        } else {
            lang.raw(player, "gui.btn-teleport-desc-center") ?: "<gray>Click to teleport to the safe center of this claim</gray>"
        }
        val teleportTarget = (lang.raw(player, "gui.btn-teleport-target") ?: "<dark_gray>Target</dark_gray> <color:#6fd8e8>{target}</color>")
            .replace("{target}", aliasDisplay ?: "#${holder.claimId}")
        val teleportCoords = (lang.raw(player, "gui.btn-teleport-coords") ?: "<dark_gray>Coords</dark_gray> <color:#a8a8a8>({world} {x}, {z})</color>")
            .replace("{world}", destLoc.world?.name ?: "world")
            .replace("{x}", destLoc.blockX.toString())
            .replace("{z}", destLoc.blockZ.toString())
        val teleportAction = lang.raw(player, "gui.btn-teleport-action") ?: "<yellow><bold>Left-Click</bold></yellow><white> Teleport Now</white>"

        val teleportLore = listOf(
            teleportDesc,
            "",
            teleportTarget,
            teleportCoords,
            "",
            teleportAction,
        )
        builder.place(
            inventory = inv,
            slot = TELEPORT_SLOT,
            material = Material.ENDER_PEARL,
            name = teleportTitle,
            lore = teleportLore,
            glint = true,
        )

        // 3. Context Band: Rename / Alias Action (Slot 2)
        val renameTitle = lang.raw(player, "gui.btn-rename-name") ?: "<color:#ffd166><bold>Set Claim Alias</bold></color>"
        val renameDesc = lang.raw(player, "gui.btn-rename-desc") ?: "<gray>Assign a memorable name</gray>"
        val renameCurrent = if (aliasDisplay != null) {
            (lang.raw(player, "gui.btn-rename-current") ?: "<dark_gray>Current</dark_gray> <color:#ffd166>{alias}</color>").replace("{alias}", alias.orEmpty())
        } else {
            (lang.raw(player, "gui.btn-rename-current-none") ?: "<dark_gray>Current</dark_gray> <color:#a8a8a8>None</color>").replace("{claimId}", holder.claimId.toString())
        }
        val renameBenefit = lang.raw(player, "gui.btn-rename-benefit") ?: "<dark_gray>Benefit</dark_gray> <color:#6fd8e8>Directly use /ctp <alias>!</color>"
        val renameFormat = lang.raw(player, "gui.btn-rename-format") ?: "<dark_gray>Format</dark_gray> <color:#a8a8a8>1~20 chars</color>"
        val renameAction = lang.raw(player, "gui.btn-rename-action") ?: "<yellow><bold>Left-Click</bold></yellow><white> Type /cname in chat</white>"

        val renameLore = listOf(
            renameDesc,
            "",
            renameCurrent,
            renameBenefit,
            renameFormat,
            "",
            renameAction,
        )
        builder.place(
            inventory = inv,
            slot = RENAME_SLOT,
            material = Material.NAME_TAG,
            name = renameTitle,
            lore = renameLore,
            glint = aliasDisplay != null,
        )

        // 3b. Context Band: Spawn Point (Slot 4)
        val spawn = teleportService.resolveCustomSpawn(holder.claim)
        val spawnRawSet = store.getSpawnRaw(holder.claimId) != null
        val spawnTitle = lang.raw(player, "gui.btn-spawn-name") ?: "<color:#ffb7d5><bold>Claim Spawn Point</bold></color>"
        val spawnDesc = lang.raw(player, "gui.btn-spawn-desc") ?: "<gray>Configure landing location for /ctp</gray>"

        val spawnLore = mutableListOf(
            spawnDesc,
            "",
        )
        when {
            spawn != null -> {
                val currentText = (lang.raw(player, "gui.btn-spawn-current") ?: "<dark_gray>Current</dark_gray> <color:#6fd8e8>({x}, {y}, {z})</color>")
                    .replace("{x}", spawn.blockX.toString())
                    .replace("{y}", spawn.blockY.toString())
                    .replace("{z}", spawn.blockZ.toString())
                spawnLore.add(currentText)
            }
            spawnRawSet -> spawnLore.add(lang.raw(player, "gui.btn-spawn-outside") ?: "<dark_gray>Current</dark_gray> <color:#fca5a5>Outside claim boundary</color>")
            else -> spawnLore.add(lang.raw(player, "gui.btn-spawn-none") ?: "<dark_gray>Current</dark_gray> <color:#a8a8a8>Default (Geometric Center)</color>")
        }
        spawnLore.add(lang.raw(player, "gui.btn-spawn-howto") ?: "<dark_gray>How-to</dark_gray> <color:#a8a8a8>Stand at spot and use /cspawn</color>")
        spawnLore.add("")
        spawnLore.add(lang.raw(player, "gui.btn-spawn-action-set") ?: "<yellow><bold>Left-Click</bold></yellow><white> Set spawn here</white>")
        if (spawnRawSet) {
            spawnLore.add(lang.raw(player, "gui.btn-spawn-action-clear") ?: "<yellow><bold>Right-Click</bold></yellow><white> Clear spawn</white>")
        }
        builder.place(
            inventory = inv,
            slot = SPAWN_SLOT,
            material = Material.LODESTONE,
            name = spawnTitle,
            lore = spawnLore,
            glint = spawn != null,
        )

        // 4. Context Band: Admin panel shortcut (Slot 3)
        if (holder.isAdminViewer) {
            val adminTitle = lang.raw(player, "gui.btn-admin-panel-name") ?: "<color:#fca5a5><bold>Server Claims Admin Panel</bold></color>"
            val adminDesc = lang.raw(player, "gui.btn-admin-panel-desc") ?: "<gray>Manage and inspect all server claims</gray>"
            val adminUuid = (lang.raw(player, "gui.btn-admin-panel-uuid") ?: "<dark_gray>Owner UUID</dark_gray> <color:#a8a8a8>{uuid}</color>")
                .replace("{uuid}", holder.ownerUuid?.toString() ?: "Admin Claim")
            val adminText = lang.raw(player, "gui.btn-admin-panel-text") ?: "<dark_gray>Admin</dark_gray> <color:#6fd8e8>/cadmin</color>"
            val adminAction = lang.raw(player, "gui.btn-admin-panel-action") ?: "<yellow><bold>Left-Click</bold></yellow><white> Open panel</white>"

            val adminLore = listOf(
                adminDesc,
                "",
                adminUuid,
                adminText,
                "",
                adminAction,
            )
            builder.place(
                inventory = inv,
                slot = ADMIN_PANEL_SLOT,
                material = Material.BEACON,
                name = adminTitle,
                lore = adminLore,
            )
        }

        // 5. Setting Toggle Cards (Row 1 & Row 2)
        slotToSetting.forEach { (slot, def) ->
            val enabled = def.isEnabled(store, holder.claimId)
            val stateText = def.getStatusLabel(lang, player, enabled)
            val actionHint = def.getActionHint(lang, player, enabled)

            val name = "<color:#ffb7d5><bold>${def.getName(lang, player)}</bold></color>"
            val desc = "<gray>${def.getDescription(lang, player)}</gray>"
            val statusLine = (lang.raw(player, "settings.current-status") ?: "<dark_gray>Status</dark_gray> {state}").replace("{state}", stateText)
            val actionLine = (lang.raw(player, "settings.action-hint") ?: "<yellow><bold>Left-Click</bold></yellow><white> {action}</white>").replace("{action}", actionHint)

            val lore = listOf(
                desc,
                "",
                statusLine,
                "",
                actionLine,
            )
            builder.place(
                inventory = inv,
                slot = slot,
                material = def.fallbackMaterial,
                name = name,
                lore = lore,
                glint = enabled,
            )
        }

        // 6. Footer Navigation
        val helpName = lang.raw(player, "gui.footer-help-name") ?: "<color:#ffb7d5><bold>Help</bold></color>"
        val helpLore = lang.rawList(player, "gui.footer-help-lore")
        builder.place(
            inventory = inv,
            slot = nav.help,
            material = Material.KNOWLEDGE_BOOK,
            name = helpName,
            lore = helpLore,
        )

        val closeName = lang.raw(player, "gui.footer-close-name") ?: "<color:#a8a8a8><bold>Close Menu</bold></color>"
        val closeAction = lang.raw(player, "gui.footer-close-action") ?: "<yellow><bold>Left-Click</bold></yellow><white> Close</white>"
        builder.place(
            inventory = inv,
            slot = nav.rightClose,
            material = Material.BARRIER,
            name = closeName,
            lore = listOf(closeAction),
        )

        // 7. Fill background panes
        builder.fillEmpty(inv, Material.GRAY_STAINED_GLASS_PANE)
    }
}
