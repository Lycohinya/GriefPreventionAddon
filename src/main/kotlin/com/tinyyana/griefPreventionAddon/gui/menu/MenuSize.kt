package com.tinyyana.griefPreventionAddon.gui.menu

enum class MenuSize(val rows: Int) {
    ROW2(2),
    SMALL(3),
    MEDIUM(4),
    TALL(5),
    LARGE(6),
    ;

    val slotCount: Int get() = rows * 9

    companion object {
        fun ofRows(rows: Int): MenuSize? = entries.firstOrNull { it.rows == rows }
        fun ofSlotCount(slots: Int): MenuSize? = entries.firstOrNull { it.slotCount == slots }
    }
}
