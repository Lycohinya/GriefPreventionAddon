package com.tinyyana.griefPreventionAddon.tnt

import org.bukkit.Location
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TntExplosionFilterTest {

    data class MockBlock(val name: String, val location: Location)

    @Test
    fun `boundary case 1 - TNT in forbidden claim protects claim blocks while allowing outer wilderness blocks`() {
        val blocks = mutableListOf(
            MockBlock("inside_claim_1", Location(null, 10.0, 64.0, 10.0)),
            MockBlock("inside_claim_2", Location(null, 11.0, 64.0, 10.0)),
            MockBlock("wilderness_1", Location(null, 25.0, 64.0, 10.0)),
            MockBlock("wilderness_2", Location(null, 26.0, 64.0, 10.0)),
        )

        // 假定 x < 20 為禁止 TNT 領地 (回傳 false), x >= 20 為荒野 (允許破壞, 回傳 true)
        val isAllowed = { loc: Location -> loc.x >= 20 }

        val protectedCount = TntExplosionListener.filterBlocks(
            blocks,
            locationExtractor = { it.location },
            isAllowedAt = isAllowed,
        )

        assertEquals(2, protectedCount)
        assertEquals(2, blocks.size)
        assertEquals(listOf("wilderness_1", "wilderness_2"), blocks.map { it.name })
    }

    @Test
    fun `boundary case 2 - TNT in wilderness protects blocks when blast radius enters forbidden claim`() {
        val blocks = mutableListOf(
            MockBlock("wilderness_blast_origin", Location(null, 22.0, 64.0, 10.0)),
            MockBlock("wilderness_neighbor", Location(null, 21.0, 64.0, 10.0)),
            MockBlock("invaded_forbidden_claim_1", Location(null, 19.0, 64.0, 10.0)),
            MockBlock("invaded_forbidden_claim_2", Location(null, 18.0, 64.0, 10.0)),
        )

        // x < 20 為禁止 TNT 領地 (回傳 false)
        val isAllowed = { loc: Location -> loc.x >= 20 }

        val protectedCount = TntExplosionListener.filterBlocks(
            blocks,
            locationExtractor = { it.location },
            isAllowedAt = isAllowed,
        )

        assertEquals(2, protectedCount)
        assertEquals(listOf("wilderness_blast_origin", "wilderness_neighbor"), blocks.map { it.name })
    }

    @Test
    fun `boundary case 3 - adjacent allowed claim A and forbidden claim B correctly separates damage and protection`() {
        val blocks = mutableListOf(
            MockBlock("claimA_block_1", Location(null, 5.0, 64.0, 10.0)), // Claim A (Allowed)
            MockBlock("claimA_block_2", Location(null, 9.0, 64.0, 10.0)), // Claim A (Allowed)
            MockBlock("claimB_block_1", Location(null, 11.0, 64.0, 10.0)), // Claim B (Forbidden)
            MockBlock("claimB_block_2", Location(null, 15.0, 64.0, 10.0)), // Claim B (Forbidden)
        )

        // Claim A: x in [0, 10] (allowed = true)
        // Claim B: x in [11, 20] (forbidden = false)
        val isAllowed = { loc: Location -> loc.x <= 10 }

        val protectedCount = TntExplosionListener.filterBlocks(
            blocks,
            locationExtractor = { it.location },
            isAllowedAt = isAllowed,
        )

        assertEquals(2, protectedCount)
        assertEquals(listOf("claimA_block_1", "claimA_block_2"), blocks.map { it.name })
    }

    @Test
    fun `boundary case 4 - all allowed leaves block list untouched`() {
        val blocks = mutableListOf(
            MockBlock("b1", Location(null, 1.0, 64.0, 1.0)),
            MockBlock("b2", Location(null, 2.0, 64.0, 2.0)),
        )

        val protectedCount = TntExplosionListener.filterBlocks(
            blocks,
            locationExtractor = { it.location },
            isAllowedAt = { true },
        )

        assertEquals(0, protectedCount)
        assertEquals(2, blocks.size)
    }

    @Test
    fun `boundary case 5 - all forbidden removes all blocks`() {
        val blocks = mutableListOf(
            MockBlock("b1", Location(null, 1.0, 64.0, 1.0)),
            MockBlock("b2", Location(null, 2.0, 64.0, 2.0)),
        )

        val protectedCount = TntExplosionListener.filterBlocks(
            blocks,
            locationExtractor = { it.location },
            isAllowedAt = { false },
        )

        assertEquals(2, protectedCount)
        assertEquals(0, blocks.size)
    }

    @Test
    fun `non-TNT explosion removes all claim blocks but keeps wilderness blocks`() {
        val blocks = mutableListOf(
            MockBlock("claim_1", Location(null, 5.0, 64.0, 10.0)),
            MockBlock("claim_2", Location(null, 8.0, 64.0, 10.0)),
            MockBlock("wilderness_1", Location(null, 30.0, 64.0, 10.0)),
            MockBlock("wilderness_2", Location(null, 35.0, 64.0, 10.0)),
        )

        // x < 20 為領地 (回傳 true), x >= 20 為荒野 (回傳 false)
        val isInAnyClaim = { loc: Location -> loc.x < 20 }

        val protectedCount = TntExplosionListener.filterNonTntBlocks(
            blocks,
            locationExtractor = { it.location },
            isInAnyClaim = isInAnyClaim,
        )

        assertEquals(2, protectedCount)
        assertEquals(listOf("wilderness_1", "wilderness_2"), blocks.map { it.name })
    }

    @Test
    fun `isTntSource returns false for null entity`() {
        kotlin.test.assertFalse(TntExplosionListener.isTntSource(null))
    }
}
