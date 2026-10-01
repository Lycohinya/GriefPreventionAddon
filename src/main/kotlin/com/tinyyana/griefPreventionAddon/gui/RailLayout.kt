package com.tinyyana.griefPreventionAddon.gui

/**
 * 花域選單的格位幾何(純函式,不碰 Bukkit,可單元測試)。
 *
 * 六列視窗,沿用 LycoLib `MenuShell.rail`/`banded(rail = true)` 這一族背景:
 * ```
 * col 0      分頁(總覽/環境與保護/信任與成員/花域管理),每列一個
 * col 1      分類欄的邊(有資源包時是背景,沒有時填玻璃)
 * col 2..3   組名牌(兩格寬,資源包上是看得到的字;沒有資源包時第一格是一顆組名物品)
 * col 4..8   這一組的項目,一列五個,放不下往下一列接
 * row 5      頁尾(返回/管理/翻頁/說明/關閉),位置照 NavigationSlots
 * ```
 * 頁面只交出「幾組、每組幾個」,格位與背景要畫在哪幾列都由這裡算——版面改了背景跟著改。
 */
object RailLayout {
    const val ROWS = 6
    const val CONTENT_ROWS = 5
    const val TAB_COLUMN = 0
    const val SEPARATOR_COLUMN = 1
    const val LABEL_COLUMN = 2
    const val LABEL_CELLS = 2
    const val ITEM_COLUMN = 4
    const val ITEMS_PER_ROW = 9 - ITEM_COLUMN

    /** 一頁最多放幾個項目(全部只有一組時)。 */
    const val CAPACITY = CONTENT_ROWS * ITEMS_PER_ROW

    data class BandPlan(val id: String, val row: Int, val labelSlot: Int, val itemSlots: List<Int>)

    data class Plan(val bands: List<BandPlan>, val groupStartRows: List<Int>)

    fun tabSlot(index: Int): Int = index * 9 + TAB_COLUMN

    fun separatorSlots(): List<Int> = (0 until CONTENT_ROWS).map { it * 9 + SEPARATOR_COLUMN }

    fun rowsFor(items: Int): Int = maxOf(1, (items + ITEMS_PER_ROW - 1) / ITEMS_PER_ROW)

    /**
     * @param bands 依序的 (id, 項目數);項目數 0 的組不畫
     * @return null = 放不下五列(呼叫端要分頁或減少內容)
     */
    fun plan(bands: List<Pair<String, Int>>): Plan? {
        var row = 0
        val out = ArrayList<BandPlan>()
        for ((id, count) in bands) {
            if (count <= 0) continue
            val rows = rowsFor(count)
            if (row + rows > CONTENT_ROWS) return null
            val slots = (0 until count).map { i -> (row + i / ITEMS_PER_ROW) * 9 + ITEM_COLUMN + i % ITEMS_PER_ROW }
            out += BandPlan(id, row, row * 9 + LABEL_COLUMN, slots)
            row += rows
        }
        return Plan(out, out.map { it.row })
    }
}
