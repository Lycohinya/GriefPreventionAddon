package com.tinyyana.griefPreventionAddon.gui

import com.tinyyana.lycoLib.menu.GuardedMenu
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 領地設定/管理面板 2026-09-19 改用殼層背景,收尾從 `fillEmpty` 換成 `finish`,空格於是**真的空了**。
 *
 * `ClaimSettingsGuiListener`/`ClaimAdminGuiListener` 本來就手動取消 `InventoryDragEvent`,
 * 但那是雙保險,不是唯一防線——唯一保證擋得住拖曳的是 [GuardedMenu] 標記加上
 * `GriefPreventionAddonPlugin` 註冊的 `MenuGuardListener`。漏掉標記不會有例外、不會有 log,
 * 只會有一個玩家某天說「我放進去的東西不見了」(見 docs/ux/PLAYER_SHELL.md §3)。
 */
class GuardedMenuTest {

    @Test
    fun `領地設定選單擋得住拖曳`() {
        assertTrue(
            GuardedMenu::class.java.isAssignableFrom(ClaimSettingsGuiHolder::class.java),
            "ClaimSettingsGuiHolder 沒有實作 GuardedMenu,視窗裡的空格會接受玩家拖進來的物品",
        )
    }

    @Test
    fun `領地管理面板擋得住拖曳`() {
        assertTrue(
            GuardedMenu::class.java.isAssignableFrom(ClaimAdminListGuiHolder::class.java),
            "ClaimAdminListGuiHolder 沒有實作 GuardedMenu,視窗裡的空格會接受玩家拖進來的物品",
        )
    }
}
