package com.tinyyana.griefPreventionAddon.toggle

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhantomAnchorTest {

    @Test
    fun `finds closest player within radius`() {
        val spawn = Point3D(100.0, 80.0, 100.0)
        val players = listOf(
            Point3D(200.0, 64.0, 200.0), // 遠處
            Point3D(105.0, 64.0, 105.0), // 距離 ~17.5 格 (最近且在 40 格內)
            Point3D(120.0, 64.0, 120.0), // 距離 ~32.8 格
        )

        val index = ClaimToggleListener.nearestWithinRadius(spawn, players, ClaimToggleListener.PHANTOM_ANCHOR_RADIUS)
        assertEquals(1, index)
    }

    @Test
    fun `returns null if no players within radius`() {
        val spawn = Point3D(100.0, 80.0, 100.0)
        val players = listOf(
            Point3D(500.0, 64.0, 500.0),
            Point3D(300.0, 64.0, 300.0),
        )

        val index = ClaimToggleListener.nearestWithinRadius(spawn, players, ClaimToggleListener.PHANTOM_ANCHOR_RADIUS)
        assertNull(index)
    }
}
