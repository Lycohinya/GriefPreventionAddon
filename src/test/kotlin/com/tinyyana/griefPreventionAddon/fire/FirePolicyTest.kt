package com.tinyyana.griefPreventionAddon.fire

import com.tinyyana.griefPreventionAddon.fire.FirePolicy.BurnVerdict
import com.tinyyana.griefPreventionAddon.fire.FirePolicy.Verdict
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class FirePolicyTest {

    private fun spread(
        targetClaimed: Boolean = true,
        sameOwner: Boolean = true,
        targetAllows: Boolean = false,
        sourceAllows: Boolean = false,
        canExtinguish: Boolean = true,
    ) = FirePolicy.spread(targetClaimed, sameOwner, targetAllows, sourceAllows, canExtinguish)

    @Test
    fun `目標在荒野一律交還 GP`() {
        val bools = listOf(true, false)
        for (same in bools) for (t in bools) for (s in bools) for (e in bools) {
            assertEquals(
                Verdict.DELEGATE,
                spread(targetClaimed = false, sameOwner = same, targetAllows = t, sourceAllows = s, canExtinguish = e),
            )
        }
    }

    @Test
    fun `不同地主 蔓延事件禁止並熄來源 點燃事件只禁止`() {
        assertEquals(Verdict.DENY_EXTINGUISH_SOURCE, spread(sameOwner = false, targetAllows = true, sourceAllows = true, canExtinguish = true))
        assertEquals(Verdict.DENY, spread(sameOwner = false, targetAllows = true, sourceAllows = true, canExtinguish = false))
    }

    @Test
    fun `同地主 目標允許就放行 不看來源`() {
        assertEquals(Verdict.ALLOW, spread(targetAllows = true, sourceAllows = false))
        assertEquals(Verdict.ALLOW, spread(targetAllows = true, sourceAllows = true))
        assertEquals(Verdict.ALLOW, spread(targetAllows = true, canExtinguish = false))
    }

    @Test
    fun `同地主 目標禁止 來源允許 只禁止不熄火`() {
        assertEquals(Verdict.DENY, spread(targetAllows = false, sourceAllows = true))
    }

    @Test
    fun `同地主 目標禁止 來源也禁止 熄掉來源`() {
        assertEquals(Verdict.DENY_EXTINGUISH_SOURCE, spread(targetAllows = false, sourceAllows = false))
    }

    @Test
    fun `同地主 目標與來源都禁止但事件沒有可熄的來源 只禁止`() {
        assertEquals(Verdict.DENY, spread(targetAllows = false, sourceAllows = false, canExtinguish = false))
    }

    @Test
    fun `燒毀判定表`() {
        // 荒野:交還 GP
        for (b in listOf(true, false)) for (s in listOf(true, false)) {
            assertEquals(BurnVerdict.DELEGATE, FirePolicy.burn(false, b, s))
        }
        assertEquals(BurnVerdict.ALLOW, FirePolicy.burn(true, burnAllows = true, spreadAllows = true))
        assertEquals(BurnVerdict.ALLOW, FirePolicy.burn(true, burnAllows = true, spreadAllows = false))
        // 禁止燒毀但允許蔓延:火留著當裝飾,不熄
        assertEquals(BurnVerdict.DENY, FirePolicy.burn(true, burnAllows = false, spreadAllows = true))
        // 兩者都禁止:順手熄周圍的火
        assertEquals(BurnVerdict.DENY_EXTINGUISH_ADJACENT, FirePolicy.burn(true, burnAllows = false, spreadAllows = false))
    }
}
