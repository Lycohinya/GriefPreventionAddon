package com.tinyyana.griefPreventionAddon.teleport

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClaimSpawnPointTest {

    @Test
    fun `encode then decode round trips`() {
        val point = ClaimSpawnPoint("world", 123.5, 64.0, -88.25, 90f, -12.5f)
        val decoded = ClaimSpawnPoint.decode(point.encode())
        assertEquals(point, decoded)
    }

    @Test
    fun `decode tolerates missing yaw and pitch`() {
        val decoded = ClaimSpawnPoint.decode("world_nether;10.0;70.0;-5.0")
        assertEquals(ClaimSpawnPoint("world_nether", 10.0, 70.0, -5.0, 0f, 0f), decoded)
    }

    @Test
    fun `decode rejects garbage instead of throwing`() {
        // 壞掉的一筆資料不該讓整塊花域傳不了,一律回 null 讓呼叫端退回中心
        assertNull(ClaimSpawnPoint.decode(null))
        assertNull(ClaimSpawnPoint.decode(""))
        assertNull(ClaimSpawnPoint.decode("world;10;70"))
        assertNull(ClaimSpawnPoint.decode("world;abc;70;-5"))
        assertNull(ClaimSpawnPoint.decode(";10;70;-5"))
    }

    @Test
    fun `world names with spaces and commas survive the round trip`() {
        // 分隔符刻意用分號:世界名稱可能有逗號或空白,但不會有分號
        val point = ClaimSpawnPoint("my world, copy", 1.0, 2.0, 3.0)
        assertEquals(point, ClaimSpawnPoint.decode(point.encode()))
    }
}
