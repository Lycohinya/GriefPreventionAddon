package com.tinyyana.griefPreventionAddon.policy

import com.tinyyana.griefPreventionAddon.integration.GpFireConfig
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfiguredFlagDefaultsTest {

    private fun yaml(text: String) = YamlConfiguration().apply { loadFromString(text) }

    private fun allows(defaults: ConfiguredFlagDefaults, flag: ClaimFlag): Boolean = flag.allows(defaults.defaultStored(flag))

    private fun gp(spreads: Boolean, destroys: Boolean, claimsSpread: Boolean, claimsDamage: Boolean) =
        GpFireConfig(spreads, destroys, claimsSpread, claimsDamage)

    @Test
    fun `沒有設定區段時退回內建值`() {
        val d = ConfiguredFlagDefaults(YamlConfiguration()) { gp(true, true, true, true) }

        assertFalse(allows(d, ClaimFlags.TNT))
        assertFalse(allows(d, ClaimFlags.PVP))
        assertFalse(allows(d, ClaimFlags.SETHOME))
        ClaimFlags.SPAWN.forEach { assertTrue(allows(d, it), "${it.key} 預設應允許生成") }
        // 火焰內建 gp
        assertTrue(d.followsGp(ClaimFlags.FIRE_SPREAD))
        assertTrue(d.followsGp(ClaimFlags.FIRE_BURN))
        assertFalse(d.followsGp(ClaimFlags.TNT))
    }

    @Test
    fun `defaults 區段的 allow 與 deny 生效`() {
        val d = ConfiguredFlagDefaults(
            yaml(
                """
                defaults:
                  tnt: allow
                  pvp: "on" # 不加引號的 on 會被 YAML 1.1 讀成布林 true,getString 得到 "true" 而不被認得
                  allow-sethome: allow
                  no-hostile-spawn: deny
                  no-phantom-spawn: DENY
                  no-slime-spawn: allow
                """.trimIndent(),
            ),
        ) { null }

        assertTrue(allows(d, ClaimFlags.TNT))
        assertTrue(allows(d, ClaimFlags.PVP))
        assertTrue(allows(d, ClaimFlags.SETHOME))
        assertFalse(allows(d, ClaimFlags.HOSTILE))
        assertFalse(allows(d, ClaimFlags.PHANTOM))
        assertTrue(allows(d, ClaimFlags.SLIME))
        assertTrue(allows(d, ClaimFlags.RAIDER)) // 沒寫 → 內建
    }

    @Test
    fun `defaults tnt deny 蓋過舊版 tnt default-allowed`() {
        val d = ConfiguredFlagDefaults(
            yaml(
                """
                defaults:
                  tnt: deny
                tnt:
                  default-allowed: true
                """.trimIndent(),
            ),
        ) { null }
        assertFalse(allows(d, ClaimFlags.TNT))
    }

    @Test
    fun `沒寫 defaults 時沿用舊版欄位`() {
        val d = ConfiguredFlagDefaults(
            yaml(
                """
                tnt:
                  default-allowed: true
                pvp:
                  default-allowed: true
                """.trimIndent(),
            ),
        ) { null }
        assertTrue(allows(d, ClaimFlags.TNT))
        assertTrue(allows(d, ClaimFlags.PVP))
        assertFalse(allows(d, ClaimFlags.SETHOME))
    }

    @Test
    fun `fire gp 蔓延要 GP 全域與領地開關都開`() {
        var cfg: GpFireConfig? = gp(spreads = true, destroys = false, claimsSpread = true, claimsDamage = false)
        val d = ConfiguredFlagDefaults(yaml("defaults:\n  fire-spread: gp\n  fire-burn: gp")) { cfg }

        assertTrue(allows(d, ClaimFlags.FIRE_SPREAD))

        cfg = gp(spreads = true, destroys = true, claimsSpread = false, claimsDamage = true)
        assertFalse(allows(d, ClaimFlags.FIRE_SPREAD)) // 領地內蔓延關
        cfg = gp(spreads = false, destroys = true, claimsSpread = true, claimsDamage = true)
        assertFalse(allows(d, ClaimFlags.FIRE_SPREAD)) // 全域蔓延關
    }

    @Test
    fun `fire gp 燒毀要 FireDestroys 與 FireDamagesInClaims 都開`() {
        var cfg: GpFireConfig? = gp(spreads = false, destroys = true, claimsSpread = false, claimsDamage = true)
        val d = ConfiguredFlagDefaults(yaml("defaults:\n  fire-spread: gp\n  fire-burn: gp")) { cfg }

        assertTrue(allows(d, ClaimFlags.FIRE_BURN))

        cfg = gp(spreads = true, destroys = false, claimsSpread = true, claimsDamage = true)
        assertFalse(allows(d, ClaimFlags.FIRE_BURN))
        cfg = gp(spreads = true, destroys = true, claimsSpread = true, claimsDamage = false)
        assertFalse(allows(d, ClaimFlags.FIRE_BURN))
    }

    @Test
    fun `GP 讀不到時 gp 預設為禁止 且每次呼叫都重新讀`() {
        var cfg: GpFireConfig? = null
        val d = ConfiguredFlagDefaults(YamlConfiguration()) { cfg }
        assertFalse(allows(d, ClaimFlags.FIRE_SPREAD))
        assertFalse(allows(d, ClaimFlags.FIRE_BURN))

        cfg = gp(true, true, true, true) // 模擬 /gpreload 之後
        assertTrue(allows(d, ClaimFlags.FIRE_SPREAD))
        assertTrue(allows(d, ClaimFlags.FIRE_BURN))
    }

    @Test
    fun `fire 可以固定成 allow 或 deny 不看 GP`() {
        val d = ConfiguredFlagDefaults(yaml("defaults:\n  fire-spread: allow\n  fire-burn: deny")) { gp(false, true, false, true) }
        assertTrue(allows(d, ClaimFlags.FIRE_SPREAD))
        assertFalse(allows(d, ClaimFlags.FIRE_BURN))
        assertFalse(d.followsGp(ClaimFlags.FIRE_SPREAD))
    }

    @Test
    fun `gp 用在非火焰規則時被忽略`() {
        val d = ConfiguredFlagDefaults(
            yaml("defaults:\n  tnt: gp\n  no-hostile-spawn: gp"),
        ) { gp(true, true, true, true) }

        assertFalse(d.followsGp(ClaimFlags.TNT))
        assertFalse(d.followsGp(ClaimFlags.HOSTILE))
        assertFalse(allows(d, ClaimFlags.TNT)) // 內建 deny
        assertTrue(allows(d, ClaimFlags.HOSTILE)) // 內建 allow
    }

    @Test
    fun `儲存值依規則的正反換算`() {
        val d = ConfiguredFlagDefaults(yaml("defaults:\n  tnt: allow\n  no-hostile-spawn: deny")) { null }
        // TNT allowStored = true:允許 → 儲存 true
        assertEquals(true, d.defaultStored(ClaimFlags.TNT))
        // 生怪 allowStored = false:禁止 → 儲存 true(no-hostile-spawn = true)
        assertEquals(true, d.defaultStored(ClaimFlags.HOSTILE))
        assertEquals(false, d.defaultStored(ClaimFlags.RAIDER))
    }
}
