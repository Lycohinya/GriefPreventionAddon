package com.tinyyana.griefPreventionAddon.gui

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RailLayoutTest {

    private fun plan(vararg bands: Pair<String, Int>): RailLayout.Plan? = RailLayout.plan(bands.toList())

    @Test
    fun `規則頁 5 2 2 1 排進四列 項目從第 4 欄 組名在第 2 欄`() {
        val p = assertNotNull(plan("spawn" to 5, "combat" to 2, "fire" to 2, "visitor" to 1))

        assertEquals(listOf(0, 1, 2, 3), p.groupStartRows)
        assertEquals(listOf(0, 1, 2, 3), p.bands.map { it.row })
        // 組名牌在 col 2
        assertEquals(listOf(2, 11, 20, 29), p.bands.map { it.labelSlot })
        // 項目從 col 4 起、一列最多五個
        assertEquals(listOf(4, 5, 6, 7, 8), p.bands[0].itemSlots)
        assertEquals(listOf(13, 14), p.bands[1].itemSlots)
        assertEquals(listOf(22, 23), p.bands[2].itemSlots)
        assertEquals(listOf(31), p.bands[3].itemSlots)
        assertEquals(listOf("spawn", "combat", "fire", "visitor"), p.bands.map { it.id })
    }

    @Test
    fun `一組超過一列時往下一列接 後面的組跟著下移`() {
        val p = assertNotNull(plan("a" to 7, "b" to 1))

        assertEquals(listOf(4, 5, 6, 7, 8, 13, 14), p.bands[0].itemSlots)
        assertEquals(0, p.bands[0].row)
        assertEquals(2, p.bands[1].row)
        assertEquals(2 * 9 + 2, p.bands[1].labelSlot)
        assertEquals(listOf(2 * 9 + 4), p.bands[1].itemSlots)
        assertEquals(listOf(0, 2), p.groupStartRows)
    }

    @Test
    fun `項目數為 0 的組不畫`() {
        val p = assertNotNull(plan("a" to 0, "b" to 3, "c" to 0, "d" to 1))

        assertEquals(listOf("b", "d"), p.bands.map { it.id })
        assertEquals(listOf(0, 1), p.groupStartRows)
    }

    @Test
    fun `全部都是 0 項時是空計畫`() {
        val p = assertNotNull(plan("a" to 0))
        assertEquals(emptyList(), p.bands)
        assertEquals(emptyList(), p.groupStartRows)
    }

    @Test
    fun `剛好五列放得下 超過五列回傳 null`() {
        assertNotNull(plan("a" to 5, "b" to 5, "c" to 5, "d" to 5, "e" to 5))
        assertNull(plan("a" to 5, "b" to 5, "c" to 5, "d" to 5, "e" to 5, "f" to 1))
        // 單組放滿 25 格可以,26 格要第六列
        assertNotNull(plan("a" to RailLayout.CAPACITY))
        assertNull(plan("a" to RailLayout.CAPACITY + 1))
        // 換行造成超過
        assertNull(plan("a" to 6, "b" to 6, "c" to 6))
    }

    @Test
    fun `rowsFor 與常數`() {
        assertEquals(1, RailLayout.rowsFor(0))
        assertEquals(1, RailLayout.rowsFor(5))
        assertEquals(2, RailLayout.rowsFor(6))
        assertEquals(5, RailLayout.ITEMS_PER_ROW)
        assertEquals(25, RailLayout.CAPACITY)
    }

    @Test
    fun `所有項目格互不重疊 且都在項目欄`() {
        val p = assertNotNull(plan("a" to 5, "b" to 7, "c" to 4))
        val slots = p.bands.flatMap { it.itemSlots }
        assertEquals(slots.size, slots.toSet().size)
        slots.forEach { assertTrue(it % 9 >= RailLayout.ITEM_COLUMN, "slot $it 不在項目欄") }
    }
}
