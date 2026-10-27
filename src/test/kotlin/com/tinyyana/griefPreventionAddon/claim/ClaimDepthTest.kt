package com.tinyyana.griefPreventionAddon.claim

import me.ryanhamshire.GriefPrevention.Claim
import org.bukkit.Location
import org.bukkit.World
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.util.UUID

/**
 * 判定用的是真的 GP `Claim.contains`(預設 CodeMC 16.18.2-SNAPSHOT;`-PgpJar=<GriefPrevention.jar>` 換成部署中的 16.18.7)。
 * 「每個整數 Y 的 3D 查詢與柱體查詢一致」就是玩家感受到的「整根柱子都是同一塊花域」。
 */
class ClaimDepthTest {

    private val world: World = fakeWorld(minHeight = -64, maxHeight = 320)

    private fun fakeWorld(minHeight: Int, maxHeight: Int): World {
        val uid = UUID.randomUUID()
        lateinit var self: World
        self = Proxy.newProxyInstance(World::class.java.classLoader, arrayOf(World::class.java)) { proxy, method, args ->
            when (method.name) {
                "getMinHeight" -> minHeight
                "getMaxHeight" -> maxHeight
                "getUID" -> uid
                "getName" -> "fixture"
                "equals" -> proxy === args?.get(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "FakeWorld"
                else -> throw UnsupportedOperationException(method.name)
            }
        } as World
        return self
    }

    private fun claim(id: Long, x1: Int, z1: Int, x2: Int, z2: Int, lesserY: Int, parent: Claim? = null): Claim {
        val ctor = Claim::class.java.getDeclaredConstructor(
            Location::class.java, Location::class.java, UUID::class.java,
            List::class.java, List::class.java, List::class.java, List::class.java, java.lang.Long::class.java,
        ).apply { isAccessible = true }
        val c = ctor.newInstance(
            Location(world, x1.toDouble(), lesserY.toDouble(), z1.toDouble()),
            Location(world, x2.toDouble(), world.maxHeight.toDouble(), z2.toDouble()),
            if (parent == null) UUID.randomUUID() else null,
            emptyList<String>(), emptyList<String>(), emptyList<String>(), emptyList<String>(), id,
        )
        if (parent != null) {
            c.parent = parent
            parent.children.add(c)
        }
        return c
    }

    /** 模擬 GP setNewDepth 的結果(金鏟調整/下方放方塊時 GP 做的事;真的流程要 DataStore,留給 runtime 驗)。 */
    private fun setLesserY(c: Claim, y: Int) {
        val f = Claim::class.java.getDeclaredField("lesserBoundaryCorner").apply { isAccessible = true }
        (f.get(c) as Location).y = y.toDouble()
    }

    private fun at(x: Int, y: Int, z: Int) = Location(world, x + 0.5, y.toDouble(), z + 0.5)

    /** 固定 XZ,[minHeight, maxHeight] 每個整數 Y 上 3D 與柱體查詢不一致的層。 */
    private fun mismatches(c: Claim, x: Int, z: Int): List<Int> =
        (world.minHeight..world.maxHeight).filter { y -> c.contains(at(x, y, z), false, false) != c.contains(at(x, y, z), true, false) }

    @Test
    fun `shallow claim loses every layer below its floor and keeps all of them once at world minimum`() {
        val c = claim(1, 0, 0, 20, 20, lesserY = 60)
        val gap = mismatches(c, 10, 10)
        assertEquals((-64..59).toList(), gap, "Y80 查得到、往下飛到 Y59 就變野外")

        setLesserY(c, world.minHeight)
        assertEquals(emptyList<Int>(), mismatches(c, 10, 10))
        assertTrue(c.contains(at(10, -64, 10), false, false))
        assertTrue(c.contains(at(10, 320, 10), false, false), "GP 上界是 maxHeight(含),站在頂層方塊上的腳部")
        assertFalse(c.contains(at(10, 321, 10), false, false), "高於 maxHeight 不在保證範圍")
        assertFalse(c.contains(at(10, -65, 10), false, false), "低於 minHeight 的虛空不在保證範圍")
        assertFalse(c.contains(at(21, 0, 10), false, false), "XZ 外不變")
    }

    @Test
    fun `deep subdivision under a shallow parent is still cut off at the parent floor`() {
        val parent = claim(10, 0, 0, 30, 30, lesserY = 60)
        val child = claim(11, 5, 5, 10, 10, lesserY = -64, parent = parent)
        assertFalse(child.contains(at(7, 0, 7), false, false), "GP 要求子花域同時在主花域內")
        assertEquals(124, mismatches(child, 7, 7).size)

        assertEquals(60, ClaimDepthService.effectiveFloor(child), "子花域實際下界跟著主花域")

        setLesserY(parent, -64)
        assertEquals(emptyList<Int>(), mismatches(child, 7, 7))
        assertEquals(emptyList<Int>(), mismatches(parent, 20, 20))
    }
}
