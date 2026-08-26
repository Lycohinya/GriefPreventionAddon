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

        assertEquals(9, ClaimSettingsGuiService.TNT_SLOT)
        assertEquals(10, ClaimSettingsGuiService.PVP_SLOT)
        assertEquals(11, ClaimSettingsGuiService.SETHOME_SLOT)

        assertEquals(18, ClaimSettingsGuiService.MOB_HOSTILE_SLOT)
        assertEquals(19, ClaimSettingsGuiService.MOB_RAIDER_SLOT)
        assertEquals(20, ClaimSettingsGuiService.MOB_PHANTOM_SLOT)
        assertEquals(21, ClaimSettingsGuiService.MOB_SLIME_SLOT)
        assertEquals(22, ClaimSettingsGuiService.MOB_AMBIENT_SLOT)
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
