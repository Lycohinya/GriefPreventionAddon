package com.tinyyana.griefPreventionAddon.gui

import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsKeys
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
import com.tinyyana.lycoLib.config.Messages
import com.tinyyana.lycoLib.menu.MenuBuilder
import com.tinyyana.lycoLib.menu.MenuSize
import com.tinyyana.lycoLib.menu.NavigationSlots
import me.ryanhamshire.GriefPrevention.Claim
import me.ryanhamshire.GriefPrevention.GriefPrevention
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import kotlin.math.ceil

class ClaimAdminListGuiService(
    private val bridge: GriefPreventionBridge,
    private val store: ClaimSettingsStore,
    private val messages: Messages,
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

        // 篩選領地
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

        val title = "全服花域管理面板 ($currentPage/$totalPages)"
        val inv = builder.build(holder, MenuSize.LARGE, title)
        holder.setInventory(inv)

        render(inv, holder, allClaims, filtered)
        player.openInventory(inv)
    }

    fun render(inv: Inventory, holder: ClaimAdminListGuiHolder, allClaims: List<Claim>, filteredClaims: List<Claim>) {
        inv.clear()
        val nav = NavigationSlots.resolve(MenuSize.LARGE)

        // 1. 渲染本頁領地卡片 (0 ~ 35)
        holder.pageClaims.forEachIndexed { index, claim ->
            val claimId = claim.id ?: return@forEachIndexed
            val isSub = claim.parent != null
            val isAdmin = claim.isAdminClaim()
            val alias = store.getAlias(claimId)
            val ownerStr = claim.ownerName ?: "管理員 / 公共"
            val width = claim.width
            val height = claim.height
            val area = claim.area
            val worldName = claim.lesserBoundaryCorner?.world?.name ?: "world"
            val centerX = (claim.lesserBoundaryCorner.blockX + claim.greaterBoundaryCorner.blockX) / 2
            val centerZ = (claim.lesserBoundaryCorner.blockZ + claim.greaterBoundaryCorner.blockZ) / 2

            val tntAllowed = store.getBoolean(ClaimSettingsKeys.TNT, claimId)
            val pvpAllowed = store.getBoolean(ClaimSettingsKeys.PVP, claimId)

            val mat = when {
                isAdmin -> Material.BEACON
                isSub -> Material.AMETHYST_CLUSTER
                else -> Material.GRASS_BLOCK
            }

            val cardTitle = if (!alias.isNullOrBlank()) {
                "<color:#ff8fc4><bold>花域 #$claimId</bold></color> <color:#ffd166>「$alias」</color>"
            } else {
                "<color:#ff8fc4><bold>花域 #$claimId</bold></color>"
            }

            val typeBadge = when {
                isAdmin -> "<color:#fca5a5>管理員領地</color>"
                isSub -> "<color:#6fd8e8>子花域</color>"
                else -> "<color:#a7f3d0>玩家花域</color>"
            }

            val lore = listOf(
                "<dark_gray>類型</dark_gray> $typeBadge",
                "<dark_gray>地主</dark_gray> <color:#f5f5f5>$ownerStr</color>",
                "<dark_gray>尺寸</dark_gray> <color:#f5f5f5>$width × $height</color> <color:#a8a8a8>($area 格)</color>",
                "<dark_gray>位置</dark_gray> <color:#a8a8a8>$worldName ($centerX, $centerZ)</color>",
                "<dark_gray>狀態</dark_gray> TNT: ${if (tntAllowed) "<green>允許</green>" else "<red>阻止</red>"} | PVP: ${if (pvpAllowed) "<green>允許</green>" else "<red>保護</red>"}",
                "",
                "<yellow><bold>左鍵</bold></yellow><white> 開啟花域管理設定</white>",
                "<yellow><bold>Shift+左鍵</bold></yellow><white> 瞬間傳送至此花域</white>",
            )

            builder.placeIcon(
                inventory = inv,
                slot = index,
                iconId = if (isAdmin) "admin_claim" else "claim",
                name = cardTitle,
                lore = lore,
                fallback = mat,
                glint = isAdmin,
            )
        }

        // 2. 統計卡片 (Slot 36)
        val totalArea = allClaims.sumOf { it.area.toLong() }
        val adminCount = allClaims.count { it.isAdminClaim() }
        val playerCount = allClaims.size - adminCount
        val statsLore = listOf(
            "<gray>伺服器花域全局統計資料</gray>",
            "",
            "<dark_gray>花域總數</dark_gray> <color:#ffd166>${allClaims.size}</color> <color:#a8a8a8>個</color>",
            "<dark_gray>玩家花域</dark_gray> <color:#a7f3d0>$playerCount</color> <color:#a8a8a8>個</color>",
            "<dark_gray>管理員花域</dark_gray> <color:#fca5a5>$adminCount</color> <color:#a8a8a8>個</color>",
            "<dark_gray>受保護總面積</dark_gray> <color:#6fd8e8>$totalArea</color> <color:#a8a8a8>格</color>",
        )
        builder.placeIcon(
            inventory = inv,
            slot = STATS_SLOT,
            iconId = "stats",
            name = "<color:#6fd8e8><bold>花域總體統計</bold></color>",
            lore = statsLore,
            fallback = Material.KNOWLEDGE_BOOK,
        )

        // 3. 篩選按鈕 (Slot 38)
        val filterLabel = when (holder.filterType) {
            "ADMIN" -> "僅管理員花域"
            "PLAYER" -> "僅玩家花域"
            else -> "顯示全部花域"
        }
        val filterLore = listOf(
            "<gray>點擊切換清單篩選條件</gray>",
            "",
            "<dark_gray>目前篩選</dark_gray> <color:#ffd166>$filterLabel</color>",
            "<dark_gray>匹配結果</dark_gray> <color:#a8a8a8>${filteredClaims.size} 個花域</color>",
            "",
            "<yellow><bold>左鍵</bold></yellow><white> 切換篩選 (全部 → 玩家 → 管理員)</white>",
        )
        builder.placeIcon(
            inventory = inv,
            slot = FILTER_SLOT,
            iconId = "filter",
            name = "<color:#ffd166><bold>篩選花域清單</bold></color>",
            lore = filterLore,
            fallback = Material.HOPPER,
        )

        // 4. 分頁控制
        if (holder.page > 1) {
            builder.placeIcon(
                inventory = inv,
                slot = nav.previousPage,
                iconId = "prev_page",
                name = "<color:#6fd8e8><bold>上一頁</bold></color>",
                lore = listOf("<gray>前往第 ${holder.page - 1} 頁</gray>"),
                fallback = Material.ARROW,
            )
        }

        // 頁碼指示器 (Slot 49 / pageIndicator)
        builder.placeIcon(
            inventory = inv,
            slot = nav.pageIndicator,
            iconId = "page_info",
            name = "<color:#ffd166><bold>第 ${holder.page} / ${holder.totalPages} 頁</bold></color>",
            lore = listOf("<gray>共 ${filteredClaims.size} 筆花域資料</gray>"),
            fallback = Material.PAPER,
        )

        if (holder.page < holder.totalPages) {
            builder.placeIcon(
                inventory = inv,
                slot = nav.nextPage,
                iconId = "next_page",
                name = "<color:#6fd8e8><bold>下一頁</bold></color>",
                lore = listOf("<gray>前往第 ${holder.page + 1} 頁</gray>"),
                fallback = Material.ARROW,
            )
        }

        // 關閉按鈕
        builder.placeIcon(
            inventory = inv,
            slot = nav.rightClose,
            iconId = "close",
            name = "<color:#fca5a5><bold>關閉面板</bold></color>",
            lore = listOf("<gray>點擊關閉此管理視窗</gray>"),
            fallback = Material.BARRIER,
        )
    }
}
