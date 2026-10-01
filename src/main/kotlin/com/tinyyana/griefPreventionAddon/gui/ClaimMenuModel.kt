package com.tinyyana.griefPreventionAddon.gui

import com.tinyyana.griefPreventionAddon.policy.ClaimFlag
import com.tinyyana.lycoLib.menu.GuardedMenu
import me.ryanhamshire.GriefPrevention.ClaimPermission
import org.bukkit.Material
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import java.util.UUID

/** 花域選單的頁面。前四個是左欄分頁,其餘是從分頁進去的子頁。 */
enum class MenuPage(val tab: MenuPage? = null) {
    OVERVIEW, RULES, MEMBERS, MANAGE,
    SCOPES(OVERVIEW), MY_CLAIMS(OVERVIEW),
    MEMBER_DETAIL(MEMBERS), ADD_MEMBER(MEMBERS),
    CONFIRM;

    /** 這一頁在左欄亮哪個分頁。 */
    val highlightedTab: MenuPage? get() = if (this in TABS) this else tab

    companion object {
        val TABS = listOf(OVERVIEW, RULES, MEMBERS, MANAGE)
    }
}

/** 需要二次確認的操作。 */
enum class ConfirmKind(val returnPage: MenuPage) {
    ABANDON(MenuPage.MANAGE),
    ADMIN_DELETE(MenuPage.MANAGE),
    REMOVE_MEMBER(MenuPage.MEMBER_DETAIL),
    CLEAR_TRUST(MenuPage.MEMBERS),
    RESET_RULES(MenuPage.RULES),
}

/**
 * 格子被點時要做的事。畫面只描述意圖;真正執行時 [ClaimMenuListener] 依 id 重新取範圍、重新檢查權限。
 */
sealed interface MenuAction {
    data class Go(val page: MenuPage, val scopeId: Long? = null, val memberId: String? = null, val pageIndex: Int = 0) : MenuAction
    data class Flag(val flag: ClaimFlag) : MenuAction
    data class MemberLevel(val memberId: String, val level: ClaimPermission) : MenuAction
    data class MemberManager(val memberId: String, val grant: Boolean) : MenuAction
    data class SubRestriction(val restrict: Boolean) : MenuAction
    data class AskConfirm(val kind: ConfirmKind) : MenuAction
    data class Execute(val kind: ConfirmKind) : MenuAction
    data class Turn(val delta: Int) : MenuAction
    data object Teleport : MenuAction
    data object Rename : MenuAction
    data object Spawn : MenuAction
    data object SubdivideMode : MenuAction
    data object BasicMode : MenuAction
    data object AdminPanel : MenuAction
    data object Close : MenuAction
}

/** 一格要畫什麼。[head] 有值時畫該玩家的頭(成員清單)。 */
data class Cell(
    val iconId: String,
    val fallback: Material,
    val name: String,
    val lore: List<String> = emptyList(),
    val glint: Boolean = false,
    val action: MenuAction? = null,
    val head: UUID? = null,
)

/** 一組:看得到的組名 + 這組的格子。 */
data class Band(val id: String, val heading: String, val cells: List<Cell>)

/** 頁尾固定位置(NavigationSlots)。 */
enum class FooterSlot { BACK, HOME, PREVIOUS, INDICATOR, NEXT, HELP, CLOSE }

/** 一頁的完整內容;[ClaimMenuRenderer] 只吃這個。 */
data class PageModel(
    val title: String,
    val bands: List<Band>,
    val footer: Map<FooterSlot, Cell>,
)

/** 這個視窗目前在看什麼。換頁就是換一份 state 重開視窗(Paper 26.2 不能改已開視窗的標題)。 */
data class MenuState(
    val page: MenuPage,
    val scopeId: Long,
    val pageIndex: Int = 0,
    val memberId: String? = null,
    val confirm: ConfirmKind? = null,
)

class ClaimMenuHolder(val viewer: UUID, val state: MenuState) : InventoryHolder, GuardedMenu {
    private lateinit var inv: Inventory
    val actions = HashMap<Int, MenuAction>()

    /** 這一次開啟時算出來的背景狀態;同頁重繪沿用,不重新判斷。 */
    var decorated: Boolean = false

    fun setInventory(inventory: Inventory) {
        inv = inventory
    }

    override fun getInventory(): Inventory = inv
}
