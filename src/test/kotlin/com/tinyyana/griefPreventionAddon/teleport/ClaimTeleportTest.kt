package com.tinyyana.griefPreventionAddon.teleport

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ClaimTeleportTest {

    @Test
    fun `test claim center calculation`() {
        val x1 = 100
        val x2 = 120
        val z1 = -50
        val z2 = -30

        val centerX = (x1 + x2) / 2
        val centerZ = (z1 + z2) / 2

        assertEquals(110, centerX)
        assertEquals(-40, centerZ)
    }

    @Test
    fun `test odd width claim center calculation`() {
        val x1 = 0
        val x2 = 15
        val z1 = 0
        val z2 = 15

        val centerX = (x1 + x2) / 2
        val centerZ = (z1 + z2) / 2

        assertEquals(7, centerX)
        assertEquals(7, centerZ)
    }
}
