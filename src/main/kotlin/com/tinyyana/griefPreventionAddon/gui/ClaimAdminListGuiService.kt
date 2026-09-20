package com.tinyyana.griefPreventionAddon.gui

import com.tinyyana.lycoLib.menu.MenuBuilder
import com.tinyyana.lycoLib.menu.MenuShell
import com.tinyyana.lycoLib.menu.MenuSize
import com.tinyyana.lycoLib.menu.NavigationSlots
import com.tinyyana.griefPreventionAddon.i18n.LanguageManager
import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsKeys
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
import me.ryanhamshire.GriefPrevention.Claim
import me.ryanhamshire.GriefPrevention.GriefPrevention
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import kotlin.math.ceil

class ClaimAdminListGuiService(
    private val bridge: GriefPreventionBridge,
    private val store: ClaimSettingsStore,
    private val lang: LanguageManager,
    private val builder: MenuBuilder = MenuBuilder(),
) {
    companion object {
        const val PAGE_SIZE = 36
        const val STATS_SLOT = 36
        const val FILTER_SLOT = 38
    }

    fun open(player: Player, page: Int = 1, filter: String = "ALL", targetPlayer: String? = null) {
        val dataStore = GriefPrevention.instance.dataStore
        val allClaims = dataStore.claims?.toList() ?: emptyList<Claim>()

        val filtered = allClaims.filter { claim ->
            val matchPlayer = if (targetPlayer.isNullOrBlank()) {
                true
            } else {
                claim.ownerName?.equals(targetPlayer, ignoreCase = true) == true ||
                        claim.ownerID?.toString()?.equals(targetPlayer, ignoreCase = true) == true
            }
            val matchFilter = when (filter) {
                "ADMIN" -> claim.isAdminClaim()
                "PLAYER" -> !claim.isAdminClaim()
                else -> true
            }
            matchPlayer && matchFilter
        }.sortedBy { it.id }

        val totalPages = maxOf(1, ceil(filtered.size / PAGE_SIZE.toDouble()).toInt())
        val currentPage = page.coerceIn(1, totalPages)
        val pageClaims = filtered.drop((currentPage - 1) * PAGE_SIZE).take(PAGE_SIZE).toMutableList()

        val holder = ClaimAdminListGuiHolder(
            page = currentPage,
            totalPages = totalPages,
            filterType = filter,
            targetPlayerName = targetPlayer,
            pageClaims = pageClaims,
        )

        val titleTemplate = lang.raw(player, "admin.list-title") ?: "Claims Management ({page}/{totalPages})"
        val title = titleTemplate
            .replace("{page}", currentPage.toString())
            .replace("{totalPages}", totalPages.toString())
        // 2.0 殼層背景。沒有資源包時 title 退回純文字、`decorated` 是 false,收尾照舊填玻璃。
        //
        // 兩組:上面四列是領地卡片,第五列是統計與篩選。它們是兩種東西(內容 vs 對內容的操作),
        // 以前畫在同一片底上,所以最後一列讀起來像「第三十七張卡片」。
        val shell = MenuShell.banded(MenuSize.LARGE, listOf(0, 4))
        holder.decorated = MenuShell.decorated(player, shell)
        val inv = builder.build(holder, MenuSize.LARGE, MenuShell.title(player, shell, title))
        holder.setInventory(inv)

        render(inv, holder, allClaims, filtered, player)
        player.openInventory(inv)
    }

    fun render(inv: Inventory, holder: ClaimAdminListGuiHolder, allClaims: List<Claim>, filteredClaims: List<Claim>, player: Player? = null) {
        inv.clear()
        val nav = NavigationSlots.resolve(MenuSize.LARGE)

        // 1. Render claim cards (0 ~ 35)
        holder.pageClaims.forEachIndexed { index, claim ->
            val claimId = claim.id ?: return@forEachIndexed
            val isSub = claim.parent != null
            val isAdmin = claim.isAdminClaim()
            val alias = store.getAlias(claimId)
            val ownerStr = claim.ownerName ?: lang.raw(player, "gui.owner-admin") ?: "Admin / Server"
            val width = claim.width
            val height = claim.height
            val area = claim.area
            val worldName = claim.lesserBoundaryCorner?.world?.name ?: "world"
            val centerX = (claim.lesserBoundaryCorner.blockX + claim.greaterBoundaryCorner.blockX) / 2
            val centerZ = (claim.lesserBoundaryCorner.blockZ + claim.greaterBoundaryCorner.blockZ) / 2

            val tntAllowed = store.getBoolean(ClaimSettingsKeys.TNT, claimId)
            val pvpAllowed = store.getBoolean(ClaimSettingsKeys.PVP, claimId)
            val sethomeAllowed = store.isSethomeAllowed(claimId)

            val mat = when {
                isAdmin -> Material.BEACON
                isSub -> Material.AMETHYST_CLUSTER
                else -> Material.GRASS_BLOCK
            }

            val cardTitleTemplate = if (!alias.isNullOrBlank()) {
                lang.raw(player, "admin.card-title-named") ?: "<color:#ff8fc4><bold>Claim #{claimId}</bold></color> <color:#ffd166>\"{alias}\"</color>"
            } else {
                lang.raw(player, "admin.card-title") ?: "<color:#ff8fc4><bold>Claim #{claimId}</bold></color>"
            }
            val cardTitle = cardTitleTemplate.replace("{claimId}", claimId.toString()).replace("{alias}", alias ?: "")

            val typeBadge = when {
                isAdmin -> lang.raw(player, "admin.card-type-admin") ?: "<color:#fca5a5>Admin Claim</color>"
                isSub -> lang.raw(player, "admin.card-type-sub") ?: "<color:#6fd8e8>Subdivision</color>"
                else -> lang.raw(player, "admin.card-type-player") ?: "<color:#a7f3d0>Player Claim</color>"
            }

            val tntText = if (tntAllowed) "<green>ON</green>" else "<red>OFF</red>"
            val pvpText = if (pvpAllowed) "<green>ON</green>" else "<red>OFF</red>"
            val sethomeText = if (sethomeAllowed) "<green>ON</green>" else "<red>OFF</red>"

            val loreTemplate = lang.rawList(player, "admin.card-lore")
            val lore = loreTemplate.map { line ->
                line.replace("{type}", typeBadge)
                    .replace("{owner}", ownerStr)
                    .replace("{width}", width.toString())
                    .replace("{height}", height.toString())
                    .replace("{area}", area.toString())
                    .replace("{world}", worldName)
                    .replace("{x}", centerX.toString())
                    .replace("{z}", centerZ.toString())
                    .replace("{tnt}", tntText)
                    .replace("{pvp}", pvpText)
                    .replace("{sethome}", sethomeText)
            }

            val iconId = if (isAdmin) "admin" else "claim"
            builder.placeIcon(
                inventory = inv,
                slot = index,
                iconId = iconId,
                name = cardTitle,
                lore = lore,
                fallback = mat,
                glint = isAdmin,
            )
        }

        // 2. Statistics Card (Slot 36)
        val totalArea = allClaims.sumOf { it.area.toLong() }
        val adminCount = allClaims.count { it.isAdminClaim() }
        val playerCount = allClaims.size - adminCount

        val statsName = lang.raw(player, "admin.stats-name") ?: "<color:#6fd8e8><bold>Global Claims Statistics</bold></color>"
        val statsLoreTemplate = lang.rawList(player, "admin.stats-lore")
        val statsLore = statsLoreTemplate.map { line ->
            line.replace("{total}", allClaims.size.toString())
                .replace("{player}", playerCount.toString())
                .replace("{admin}", adminCount.toString())
                .replace("{area}", totalArea.toString())
        }
        builder.placeIcon(
            inventory = inv,
            slot = STATS_SLOT,
            iconId = "help",
            name = statsName,
            lore = statsLore,
            fallback = Material.KNOWLEDGE_BOOK,
        )

        // 3. Filter Button (Slot 38)
        val filterLabel = when (holder.filterType) {
            "ADMIN" -> lang.raw(player, "admin.filter-admin") ?: "Admin Claims Only"
            "PLAYER" -> lang.raw(player, "admin.filter-player") ?: "Player Claims Only"
            else -> lang.raw(player, "admin.filter-all") ?: "Show All Claims"
        }
        val filterName = lang.raw(player, "admin.filter-name") ?: "<color:#ffd166><bold>Filter Claims List</bold></color>"
        val filterLoreTemplate = lang.rawList(player, "admin.filter-lore")
        val filterLore = filterLoreTemplate.map { line ->
            line.replace("{current}", filterLabel)
                .replace("{count}", filteredClaims.size.toString())
        }
        builder.placeIcon(
            inventory = inv,
            slot = FILTER_SLOT,
            iconId = "filter",
            name = filterName,
            lore = filterLore,
            fallback = Material.HOPPER,
        )

        // 4. Pagination Controls
        if (holder.page > 1) {
            val prevName = lang.raw(player, "admin.prev-page-name") ?: "<color:#6fd8e8><bold>Previous Page</bold></color>"
            val prevLore = (lang.raw(player, "admin.prev-page-lore") ?: "<gray>Go to page {page}</gray>").replace("{page}", (holder.page - 1).toString())
            builder.placeIcon(
                inventory = inv,
                slot = nav.previousPage,
                iconId = "previous_page",
                name = prevName,
                lore = listOf(prevLore),
                fallback = Material.ARROW,
            )
        }

        // Page Indicator
        val pageIndicatorName = (lang.raw(player, "admin.page-indicator-name") ?: "<color:#ffd166><bold>Page {page} / {totalPages}</bold></color>")
            .replace("{page}", holder.page.toString())
            .replace("{totalPages}", holder.totalPages.toString())
        val pageIndicatorLore = (lang.raw(player, "admin.page-indicator-lore") ?: "<gray>{count} total claims</gray>")
            .replace("{count}", filteredClaims.size.toString())
        builder.placeIcon(
            inventory = inv,
            slot = nav.pageIndicator,
            iconId = "page_indicator",
            name = pageIndicatorName,
            lore = listOf(pageIndicatorLore),
            fallback = Material.PAPER,
        )

        if (holder.page < holder.totalPages) {
            val nextName = lang.raw(player, "admin.next-page-name") ?: "<color:#6fd8e8><bold>Next Page</bold></color>"
            val nextLore = (lang.raw(player, "admin.next-page-lore") ?: "<gray>Go to page {page}</gray>").replace("{page}", (holder.page + 1).toString())
            builder.placeIcon(
                inventory = inv,
                slot = nav.nextPage,
                iconId = "next_page",
                name = nextName,
                lore = listOf(nextLore),
                fallback = Material.ARROW,
            )
        }

        // Close Button
        val closeName = lang.raw(player, "admin.close-name") ?: "<color:#fca5a5><bold>Close Panel</bold></color>"
        val closeLore = lang.raw(player, "admin.close-lore") ?: "<gray>Click to close</gray>"
        builder.placeIcon(
            inventory = inv,
            slot = nav.rightClose,
            iconId = "close",
            name = closeName,
            lore = listOf(closeLore),
            fallback = Material.BARRIER,
        )

        // 5. 有背景就只放分享按鈕,沒背景才填玻璃(見 PLAYER_SHELL.md §3)
        builder.finish(inv, holder.decorated)
    }
}
