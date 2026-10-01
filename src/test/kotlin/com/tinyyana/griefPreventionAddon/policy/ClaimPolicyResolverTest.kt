package com.tinyyana.griefPreventionAddon.policy

import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
import com.tinyyana.griefPreventionAddon.storage.Db
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 三態解析矩陣:子花域明確值 → 主花域有效值 → 伺服器預設。用真的 store(暫存 SQLite)。 */
class ClaimPolicyResolverTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var db: Db
    private lateinit var store: ClaimSettingsStore

    /** 預設:生怪允許、其餘禁止(同內建值);測試可改。 */
    private var defaultAllow: (ClaimFlag) -> Boolean = { it.group == FlagGroup.SPAWN }
    private lateinit var resolver: ClaimPolicyResolver

    private val parent = ClaimScope(1L, null)
    private val childA = ClaimScope(2L, 1L)
    private val childB = ClaimScope(3L, 1L)
    private val hostile = ClaimFlags.HOSTILE

    @BeforeEach
    fun setUp() {
        db = Db(File(tempDir, "resolver.db"))
        store = ClaimSettingsStore(db)
        store.init()
        resolver = ClaimPolicyResolver(store) { flag ->
            flag.storedFor(if (defaultAllow(flag)) TriState.ALLOW else TriState.DENY)!!
        }
    }

    @AfterEach
    fun tearDown() {
        db.close()
    }

    private fun set(flag: ClaimFlag, scope: ClaimScope, state: TriState) =
        store.setExplicit(flag.key, scope.id, flag.storedFor(state))

    @Test
    fun `主花域禁止 子花域 A 允許 B 繼承`() {
        set(hostile, parent, TriState.DENY)
        set(hostile, childA, TriState.ALLOW)

        assertFalse(resolver.allows(hostile, parent))
        assertTrue(resolver.allows(hostile, childA))
        assertFalse(resolver.allows(hostile, childB))

        assertEquals(ValueSource.LOCAL, resolver.resolve(hostile, childA).source)
        assertEquals(ValueSource.PARENT, resolver.resolve(hostile, childB).source)
        assertEquals(ValueSource.LOCAL, resolver.resolve(hostile, parent).source)
    }

    @Test
    fun `反向組合 主花域允許 子花域 A 禁止 B 繼承`() {
        set(hostile, parent, TriState.ALLOW)
        set(hostile, childA, TriState.DENY)

        assertTrue(resolver.allows(hostile, parent))
        assertFalse(resolver.allows(hostile, childA))
        assertTrue(resolver.allows(hostile, childB))
        assertEquals(TriState.DENY, resolver.resolve(hostile, childA).local)
        assertEquals(TriState.INHERIT, resolver.resolve(hostile, childB).local)
    }

    @Test
    fun `改主花域的值只影響仍在繼承的 B`() {
        set(hostile, parent, TriState.DENY)
        set(hostile, childA, TriState.ALLOW)
        assertFalse(resolver.allows(hostile, childB))

        set(hostile, parent, TriState.ALLOW)

        assertTrue(resolver.allows(hostile, parent))
        assertTrue(resolver.allows(hostile, childA)) // A 自己的明確值不動
        assertTrue(resolver.allows(hostile, childB)) // B 跟著變

        set(hostile, parent, TriState.DENY)
        assertTrue(resolver.allows(hostile, childA))
        assertFalse(resolver.allows(hostile, childB))
    }

    @Test
    fun `子花域恢復繼承後跟主花域走 主花域的列還在`() {
        set(hostile, parent, TriState.DENY)
        set(hostile, childA, TriState.ALLOW)
        assertTrue(resolver.allows(hostile, childA))

        set(hostile, childA, TriState.INHERIT)

        assertFalse(resolver.allows(hostile, childA))
        assertEquals(ValueSource.PARENT, resolver.resolve(hostile, childA).source)
        assertNotNull(store.getExplicit(hostile.key, parent.id))
        assertEquals(TriState.DENY, resolver.resolve(hostile, parent).local)
    }

    @Test
    fun `沒有任何明確值時主花域與子花域都用伺服器預設`() {
        val root = resolver.resolve(hostile, parent)
        assertEquals(ValueSource.SERVER_DEFAULT, root.source)
        assertEquals(TriState.INHERIT, root.local)
        assertTrue(root.allowed)
        assertTrue(root.inheritedAllowed)

        val child = resolver.resolve(hostile, childA)
        assertEquals(ValueSource.SERVER_DEFAULT, child.source)
        assertTrue(child.allowed)

        // TNT 預設禁止
        val tnt = resolver.resolve(ClaimFlags.TNT, parent)
        assertFalse(tnt.allowed)
        assertEquals(ValueSource.SERVER_DEFAULT, tnt.source)
    }

    @Test
    fun `inheritedAllowed 是選繼承會得到的值`() {
        // 子花域:inherited = 主花域有效值;allowed = 自己的明確值
        set(hostile, parent, TriState.DENY)
        set(hostile, childA, TriState.ALLOW)
        val a = resolver.resolve(hostile, childA)
        assertTrue(a.allowed)
        assertFalse(a.inheritedAllowed)

        // 主花域:inherited = 伺服器預設(生怪預設允許)
        val p = resolver.resolve(hostile, parent)
        assertFalse(p.allowed)
        assertTrue(p.inheritedAllowed)

        // 子花域明確允許 TNT、主花域沒設:inherited = 伺服器預設(禁止)
        set(ClaimFlags.TNT, childB, TriState.ALLOW)
        val b = resolver.resolve(ClaimFlags.TNT, childB)
        assertTrue(b.allowed)
        assertEquals(ValueSource.LOCAL, b.source)
        assertFalse(b.inheritedAllowed)
    }

    @Test
    fun `子花域沒有明確值時不看兄弟`() {
        set(hostile, childA, TriState.DENY)
        assertFalse(resolver.allows(hostile, childA))
        assertTrue(resolver.allows(hostile, childB))
        assertTrue(resolver.allows(hostile, parent))
    }

    @Test
    fun `不同規則互不影響`() {
        set(ClaimFlags.PVP, parent, TriState.ALLOW)
        assertTrue(resolver.allows(ClaimFlags.PVP, childA))
        assertTrue(resolver.allows(hostile, childA)) // 預設
        assertFalse(resolver.allows(ClaimFlags.TNT, childA))
    }

    @Test
    fun `mayResolveTo 預設允許且沒人禁止時為 false`() {
        // 生怪預設允許;問「有沒有可能被擋(stored = true)」
        assertFalse(resolver.mayResolveTo(hostile, true))
        // 問「有沒有可能允許(stored = false)」永遠 true,因為預設就是
        assertTrue(resolver.mayResolveTo(hostile, false))
    }

    @Test
    fun `mayResolveTo 只要任何範圍有明確禁止就為 true 包含只有主花域`() {
        set(hostile, parent, TriState.DENY)
        assertTrue(resolver.mayResolveTo(hostile, true))

        // 恢復繼承後又變回 false
        set(hostile, parent, TriState.INHERIT)
        assertFalse(resolver.mayResolveTo(hostile, true))

        set(hostile, childA, TriState.DENY)
        assertTrue(resolver.mayResolveTo(hostile, true))
    }

    @Test
    fun `mayResolveTo 預設禁止的規則 明確允許才使允許成為可能`() {
        // TNT 預設禁止:問「可能允許(stored = true)」
        assertFalse(resolver.mayResolveTo(ClaimFlags.TNT, true))
        set(ClaimFlags.TNT, parent, TriState.ALLOW)
        assertTrue(resolver.mayResolveTo(ClaimFlags.TNT, true))
    }

    @Test
    fun `伺服器預設改為禁止後主花域與繼承的子花域跟著變`() {
        defaultAllow = { false }
        assertFalse(resolver.allows(hostile, parent))
        assertFalse(resolver.allows(hostile, childA))
        assertTrue(resolver.mayResolveTo(hostile, true))
    }
}
