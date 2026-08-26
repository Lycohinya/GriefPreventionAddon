package com.tinyyana.griefPreventionAddon.gui.menu

data class NavigationSlots(
    val back: Int,
    val home: Int,
    val previousPage: Int,
    val pageIndicator: Int,
    val nextPage: Int,
    val help: Int,
    val rightClose: Int,
) {
    val centeredClose: Int get() = pageIndicator

    companion object {
        fun resolve(size: MenuSize): NavigationSlots {
            val base = (size.rows - 1) * 9
            return NavigationSlots(
                back = base,
                home = base + 1,
                previousPage = base + 3,
                pageIndicator = base + 4,
                nextPage = base + 5,
                help = base + 7,
                rightClose = base + 8,
            )
        }
    }
}
