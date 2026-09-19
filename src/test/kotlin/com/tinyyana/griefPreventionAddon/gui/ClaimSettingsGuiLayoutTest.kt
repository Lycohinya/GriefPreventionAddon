package com.tinyyana.griefPreventionAddon.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class ClaimSettingsGuiLayoutTest {

    @Test
    fun testSlotLayoutContinuous() {
        assertEquals(0, ClaimSettingsGuiService.HEADER_SLOT)
        assertEquals(1, ClaimSettingsGuiService.TELEPORT_SLOT)
        assertEquals(2, ClaimSettingsGuiService.RENAME_SLOT)
        assertEquals(3, ClaimSettingsGuiService.SPAWN_SLOT, "Spawn slot must be contiguous at slot 3 so there is no gap")
        assertEquals(4, ClaimSettingsGuiService.ADMIN_PANEL_SLOT)

        // 2026-09-19 第二輪重排:col 0 是群組標籤,內容從 col 1 起連續排
        // (CHEST_UI_DESIGN_SYSTEM.md §9.1 第 1 條——超過一列的開關要分組並有小標)。
        assertEquals(9, ClaimSettingsGuiService.ACCESS_GROUP_LABEL_SLOT)
        assertEquals(10, ClaimSettingsGuiService.TNT_SLOT)
        assertEquals(11, ClaimSettingsGuiService.PVP_SLOT)
        assertEquals(12, ClaimSettingsGuiService.SETHOME_SLOT)

        assertEquals(18, ClaimSettingsGuiService.MOB_GROUP_LABEL_SLOT)
        assertEquals(19, ClaimSettingsGuiService.MOB_HOSTILE_SLOT)
        assertEquals(20, ClaimSettingsGuiService.MOB_RAIDER_SLOT)
        assertEquals(21, ClaimSettingsGuiService.MOB_PHANTOM_SLOT)
        assertEquals(22, ClaimSettingsGuiService.MOB_SLIME_SLOT)
        assertEquals(23, ClaimSettingsGuiService.MOB_AMBIENT_SLOT)
    }

    @Test
    fun `兩個開關群組的標籤跟內容彼此不衝突`() {
        val labelSlots = setOf(ClaimSettingsGuiService.ACCESS_GROUP_LABEL_SLOT, ClaimSettingsGuiService.MOB_GROUP_LABEL_SLOT)
        val toggleSlots = setOf(
            ClaimSettingsGuiService.TNT_SLOT, ClaimSettingsGuiService.PVP_SLOT, ClaimSettingsGuiService.SETHOME_SLOT,
            ClaimSettingsGuiService.MOB_HOSTILE_SLOT, ClaimSettingsGuiService.MOB_RAIDER_SLOT,
            ClaimSettingsGuiService.MOB_PHANTOM_SLOT, ClaimSettingsGuiService.MOB_SLIME_SLOT, ClaimSettingsGuiService.MOB_AMBIENT_SLOT,
        )
        assertEquals(emptySet<Int>(), labelSlots intersect toggleSlots, "群組標籤格不可以跟任何開關格重疊")
        assertEquals(2, labelSlots.size, "兩個群組標籤各自要有自己的格子")
    }

    @Test
    fun testAllSettingsRegistered() {
        val settings = ClaimSettingRegistry.ALL_SETTINGS
        assertEquals(8, settings.size)
        assertEquals("tnt", ClaimSettingRegistry.TNT.key)
        assertEquals("pvp", ClaimSettingRegistry.PVP.key)
        assertEquals("allow-sethome", ClaimSettingRegistry.ALLOW_SETHOME.key)
        assertEquals("no-hostile-spawn", ClaimSettingRegistry.NO_HOSTILE.key)
        assertEquals("no-raider-spawn", ClaimSettingRegistry.NO_RAIDER.key)
        assertEquals("no-phantom-spawn", ClaimSettingRegistry.NO_PHANTOM.key)
        assertEquals("no-slime-spawn", ClaimSettingRegistry.NO_SLIME.key)
        assertEquals("no-ambient-spawn", ClaimSettingRegistry.NO_AMBIENT.key)

        for (setting in settings) {
            assertNotNull(setting.iconId)
            assertNotNull(setting.fallbackMaterial)
        }
    }
}
