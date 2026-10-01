package com.tinyyana.griefPreventionAddon.toggle

import com.tinyyana.griefPreventionAddon.policy.ClaimFlag
import com.tinyyana.griefPreventionAddon.policy.ClaimFlags
import com.tinyyana.griefPreventionAddon.policy.ClaimScope
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsKeys
import org.bukkit.entity.Bat
import org.bukkit.entity.CamelHusk
import org.bukkit.entity.Cow
import org.bukkit.entity.Entity
import org.bukkit.entity.MagmaCube
import org.bukkit.entity.Phantom
import org.bukkit.entity.Pillager
import org.bukkit.entity.Slime
import org.bukkit.entity.SpawnCategory
import org.bukkit.entity.SulfurCube
import org.bukkit.entity.Zombie
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * /cmob 自然生怪過濾的分類與決策回歸測試。
 *
 * 生物用動態代理實作 Paper 的實體介面：分類只看介面繼承與 getSpawnCategory()，
 * 兩者代理都能忠實提供，不需要真的伺服器。
 */
class MobSpawnFilterTest {

    private fun mob(type: Class<out Entity>, spawnCategory: SpawnCategory): Entity =
        Proxy.newProxyInstance(type.classLoader, arrayOf<Class<*>>(type)) { proxy, method, args ->
            when (method.name) {
                "getSpawnCategory" -> spawnCategory
                "toString" -> type.simpleName
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.get(0)
                else -> throw UnsupportedOperationException("測試代理未預期呼叫 ${method.name}")
            }
        } as Entity

    // --- 類別對應 ---

    @Test
    fun `史萊姆與岩漿立方怪都歸到 slime 類`() {
        // 迴歸重點:Paper 26.2 起 MagmaCube 不再繼承 Slime,舊的 is Slime 判定會把它漏給一般敵對類
        assertEquals(
            ClaimSettingsKeys.NO_SLIME_SPAWN,
            ClaimToggleListener.categoryKey(mob(Slime::class.java, SpawnCategory.MONSTER)),
        )
        assertEquals(
            ClaimSettingsKeys.NO_SLIME_SPAWN,
            ClaimToggleListener.categoryKey(mob(MagmaCube::class.java, SpawnCategory.MONSTER)),
        )
    }

    @Test
    fun `被動立方生物不受 cmob 干涉`() {
        // 可養殖的立方生物跟牛羊同級,不該因為「也是立方體」被 slime 開關掃掉
        assertNull(ClaimToggleListener.categoryKey(mob(SulfurCube::class.java, SpawnCategory.ANIMAL)))
    }

    @Test
    fun `沒有 Enemy 標記但屬於 MONSTER 生成類別的仍歸一般敵對`() {
        // CamelHusk 在 API 上繼承 Camel(Animals),只靠 is Enemy 會整類漏掉
        assertEquals(
            ClaimSettingsKeys.NO_HOSTILE_SPAWN,
            ClaimToggleListener.categoryKey(mob(CamelHusk::class.java, SpawnCategory.MONSTER)),
        )
    }

    @Test
    fun `其他四類維持原本對應`() {
        assertEquals(
            ClaimSettingsKeys.NO_HOSTILE_SPAWN,
            ClaimToggleListener.categoryKey(mob(Zombie::class.java, SpawnCategory.MONSTER)),
        )
        assertEquals(
            ClaimSettingsKeys.NO_RAIDER_SPAWN,
            ClaimToggleListener.categoryKey(mob(Pillager::class.java, SpawnCategory.MONSTER)),
        )
        assertEquals(
            ClaimSettingsKeys.NO_PHANTOM_SPAWN,
            ClaimToggleListener.categoryKey(mob(Phantom::class.java, SpawnCategory.MONSTER)),
        )
        assertEquals(
            ClaimSettingsKeys.NO_AMBIENT_SPAWN,
            ClaimToggleListener.categoryKey(mob(Bat::class.java, SpawnCategory.AMBIENT)),
        )
    }

    @Test
    fun `動物不歸任何類別`() {
        assertNull(ClaimToggleListener.categoryKey(mob(Cow::class.java, SpawnCategory.ANIMAL)))
    }

    // --- 生成來源政策 ---

    @Test
    fun `世界自發的生怪來源要攔`() {
        listOf(
            SpawnReason.NATURAL,
            SpawnReason.SLIME_SPLIT,
            SpawnReason.PATROL,
            SpawnReason.RAID,
            SpawnReason.VILLAGE_INVASION,
            SpawnReason.REINFORCEMENTS,
            SpawnReason.JOCKEY,
            SpawnReason.NETHER_PORTAL,
            SpawnReason.TRAP,
        ).forEach { assertTrue(ClaimToggleListener.isNaturalSpawn(it), "$it 應該要攔") }
    }

    @Test
    fun `玩家設施 繁殖與轉化來源要放行`() {
        listOf(
            SpawnReason.SPAWNER,
            SpawnReason.TRIAL_SPAWNER,
            SpawnReason.SPAWNER_EGG,
            SpawnReason.DISPENSE_EGG,
            SpawnReason.BREEDING,
            SpawnReason.OCELOT_BABY,
            SpawnReason.SILVERFISH_BLOCK,
            SpawnReason.INFECTION,
            SpawnReason.DROWNED,
            SpawnReason.PIGLIN_ZOMBIFIED,
            SpawnReason.COMMAND,
            SpawnReason.CUSTOM,
            SpawnReason.DEFAULT,
        ).forEach { assertFalse(ClaimToggleListener.isNaturalSpawn(it), "$it 應該放行") }
    }

    // --- 完整決策 ---

    private val scope1 = ClaimScope(1L, null)
    private val childScope = ClaimScope(5L, 1L)

    private fun decide(
        entity: Entity,
        reason: SpawnReason = SpawnReason.NATURAL,
        mayBlockFlags: Set<ClaimFlag> = setOf(ClaimFlags.SLIME),
        scope: ClaimScope? = scope1,
        blocked: Set<Pair<ClaimFlag, ClaimScope>> = setOf(ClaimFlags.SLIME to scope1),
    ): Boolean = ClaimToggleListener.shouldCancelSpawn(
        reason = reason,
        entity = entity,
        mayBlock = { it in mayBlockFlags },
        scopeAt = { scope },
        isBlocked = { flag, s -> (flag to s) in blocked },
    )

    @Test
    fun `categoryFlag 與 categoryKey 一致`() {
        assertEquals(ClaimFlags.SLIME, ClaimToggleListener.categoryFlag(mob(MagmaCube::class.java, SpawnCategory.MONSTER)))
        assertEquals(ClaimFlags.HOSTILE, ClaimToggleListener.categoryFlag(mob(Zombie::class.java, SpawnCategory.MONSTER)))
        assertEquals(ClaimFlags.RAIDER, ClaimToggleListener.categoryFlag(mob(Pillager::class.java, SpawnCategory.MONSTER)))
        assertEquals(ClaimFlags.PHANTOM, ClaimToggleListener.categoryFlag(mob(Phantom::class.java, SpawnCategory.MONSTER)))
        assertEquals(ClaimFlags.AMBIENT, ClaimToggleListener.categoryFlag(mob(Bat::class.java, SpawnCategory.AMBIENT)))
        assertNull(ClaimToggleListener.categoryFlag(mob(Cow::class.java, SpawnCategory.ANIMAL)))
    }

    @Test
    fun `關閉史萊姆生成後領地內的史萊姆與岩漿立方怪都被擋`() {
        assertTrue(decide(mob(Slime::class.java, SpawnCategory.MONSTER)))
        assertTrue(decide(mob(MagmaCube::class.java, SpawnCategory.MONSTER)))
        assertTrue(decide(mob(Slime::class.java, SpawnCategory.MONSTER), reason = SpawnReason.SLIME_SPLIT))
        assertTrue(decide(mob(MagmaCube::class.java, SpawnCategory.MONSTER), reason = SpawnReason.SLIME_SPLIT))
    }

    @Test
    fun `非自然來源永遠不攔 而且不查快篩與範圍`() {
        var mayBlockCalls = 0
        var scopeCalls = 0
        val cancel = ClaimToggleListener.shouldCancelSpawn(
            reason = SpawnReason.SPAWNER,
            entity = mob(Slime::class.java, SpawnCategory.MONSTER),
            mayBlock = { mayBlockCalls++; true },
            scopeAt = { scopeCalls++; scope1 },
            isBlocked = { _, _ -> true },
        )
        assertFalse(cancel)
        assertEquals(0, mayBlockCalls)
        assertEquals(0, scopeCalls)
    }

    @Test
    fun `全服快篩為 false 時完全不查領地範圍`() {
        var scopeCalls = 0
        val cancel = ClaimToggleListener.shouldCancelSpawn(
            reason = SpawnReason.NATURAL,
            entity = mob(Zombie::class.java, SpawnCategory.MONSTER),
            mayBlock = { false },
            scopeAt = { scopeCalls++; scope1 },
            isBlocked = { _, _ -> true },
        )
        assertFalse(cancel)
        assertEquals(0, scopeCalls, "mayBlock=false 時不該呼叫 scopeAt")
    }

    @Test
    fun `歸不到類別的生物不查快篩也不查範圍`() {
        var mayBlockCalls = 0
        var scopeCalls = 0
        val cancel = ClaimToggleListener.shouldCancelSpawn(
            reason = SpawnReason.NATURAL,
            entity = mob(Cow::class.java, SpawnCategory.ANIMAL),
            mayBlock = { mayBlockCalls++; true },
            scopeAt = { scopeCalls++; scope1 },
            isBlocked = { _, _ -> true },
        )
        assertFalse(cancel)
        assertEquals(0, mayBlockCalls)
        assertEquals(0, scopeCalls)
    }

    @Test
    fun `快篩通過後交給 isBlocked 用該範圍判定`() {
        val slime = mob(Slime::class.java, SpawnCategory.MONSTER)
        val blocked = setOf(ClaimFlags.SLIME to childScope)
        // 子花域被禁、主花域沒禁:各自判斷
        assertTrue(decide(slime, scope = childScope, blocked = blocked))
        assertFalse(decide(slime, scope = scope1, blocked = blocked))
    }

    @Test
    fun `isBlocked 收到的是對應類別的 flag 與 scopeAt 回傳的範圍`() {
        var seen: Pair<ClaimFlag, ClaimScope>? = null
        ClaimToggleListener.shouldCancelSpawn(
            reason = SpawnReason.PATROL,
            entity = mob(Pillager::class.java, SpawnCategory.MONSTER),
            mayBlock = { true },
            scopeAt = { childScope },
            isBlocked = { f, s -> seen = f to s; false },
        )
        assertEquals(ClaimFlags.RAIDER to childScope, seen)
    }

    @Test
    fun `領地外不受影響`() {
        assertFalse(decide(mob(MagmaCube::class.java, SpawnCategory.MONSTER), scope = null))
    }

    @Test
    fun `其他領地開了不影響本領地`() {
        assertFalse(decide(mob(Slime::class.java, SpawnCategory.MONSTER), scope = ClaimScope(2L, null)))
    }

    @Test
    fun `重新開啟後恢復生成`() {
        assertFalse(
            decide(
                mob(Slime::class.java, SpawnCategory.MONSTER),
                mayBlockFlags = emptySet(),
                blocked = emptySet(),
            ),
        )
    }

    @Test
    fun `生怪磚來源即使開關開著也放行`() {
        assertTrue(decide(mob(Slime::class.java, SpawnCategory.MONSTER)))
        assertFalse(decide(mob(Slime::class.java, SpawnCategory.MONSTER), reason = SpawnReason.SPAWNER))
        assertFalse(decide(mob(Slime::class.java, SpawnCategory.MONSTER), reason = SpawnReason.TRIAL_SPAWNER))
        assertFalse(decide(mob(Slime::class.java, SpawnCategory.MONSTER), reason = SpawnReason.SPAWNER_EGG))
    }

    @Test
    fun `關閉史萊姆不影響其他類別`() {
        assertFalse(decide(mob(Zombie::class.java, SpawnCategory.MONSTER)))
        assertFalse(decide(mob(Bat::class.java, SpawnCategory.AMBIENT)))
    }
}
