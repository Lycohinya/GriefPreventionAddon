package com.tinyyana.griefPreventionAddon.storage

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClaimSettingsStoreTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var dbFile: File
    private lateinit var db: Db
    private lateinit var store: ClaimSettingsStore

    @BeforeEach
    fun setUp() {
        dbFile = File(tempDir, "test.db")
        db = Db(dbFile)
        store = ClaimSettingsStore(db)
        store.init()
    }

    @AfterEach
    fun tearDown() {
        db.close()
    }

    /** 關掉目前的 Db(close 會等寫入佇列清空)後,在同一個檔案上冷啟動一份新的。 */
    private fun coldRestart(): Pair<Db, ClaimSettingsStore> {
        db.close()
        val newDb = Db(dbFile)
        val newStore = ClaimSettingsStore(newDb)
        newStore.init()
        db = newDb
        store = newStore
        return newDb to newStore
    }

    /** 直接用 JDBC 讀 DB 內容(絕不經過 store 快取),key = "claimId:settingKey"。 */
    private fun dbRows(): Map<String, String> {
        db.flush()
        val out = LinkedHashMap<String, String>()
        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
            conn.createStatement().use { st ->
                st.executeQuery("SELECT claim_id, setting_key, setting_value FROM claim_settings").use { rs ->
                    while (rs.next()) out["${rs.getLong(1)}:${rs.getString(2)}"] = rs.getString(3)
                }
            }
        }
        return out
    }

    private fun createLstDb(vararg rows: Pair<Long, String>): File {
        val lstDbFile = File(tempDir, "lst.db")
        DriverManager.getConnection("jdbc:sqlite:${lstDbFile.absolutePath}").use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeUpdate("CREATE TABLE claim_toggle (claim_id INTEGER NOT NULL, key TEXT NOT NULL, PRIMARY KEY (claim_id, key))")
                rows.forEach { (id, key) -> stmt.executeUpdate("INSERT INTO claim_toggle VALUES ($id, '$key')") }
            }
        }
        return lstDbFile
    }

    // ---------------------------------------------------------------- 三態

    @Test
    fun `沒有列就是繼承 不是 false`() {
        assertNull(store.getExplicit(ClaimSettingsKeys.TNT, 100L))
        assertFalse(store.hasAnyExplicit(ClaimSettingsKeys.TNT, true))
        assertFalse(store.hasAnyExplicit(ClaimSettingsKeys.TNT, false))
        assertEquals(emptyMap(), store.explicitFor(100L))
    }

    @Test
    fun `明確 true 與明確 false 都落地並能冷啟動讀回`() {
        store.setExplicit(ClaimSettingsKeys.TNT, 100L, true)
        store.setExplicit(ClaimSettingsKeys.TNT, 200L, false)
        store.setExplicit(ClaimSettingsKeys.NO_HOSTILE_SPAWN, 100L, false)

        // 明確 false 是一列 'false',不是刪列(那會變成繼承)
        assertEquals("true", dbRows()["100:tnt"])
        assertEquals("false", dbRows()["200:tnt"])

        val (_, restarted) = coldRestart()
        assertEquals(true, restarted.getExplicit(ClaimSettingsKeys.TNT, 100L))
        assertEquals(false, restarted.getExplicit(ClaimSettingsKeys.TNT, 200L))
        assertEquals(false, restarted.getExplicit(ClaimSettingsKeys.NO_HOSTILE_SPAWN, 100L))
        assertNull(restarted.getExplicit(ClaimSettingsKeys.PVP, 100L))
        assertTrue(restarted.hasAnyExplicit(ClaimSettingsKeys.TNT, false))
        assertEquals(mapOf("tnt" to true, "no-hostile-spawn" to false), restarted.explicitFor(100L))
    }

    @Test
    fun `改寫明確值覆蓋舊值 setExplicit null 只刪那個範圍那一項`() {
        store.setExplicit(ClaimSettingsKeys.TNT, 100L, true)
        store.setExplicit(ClaimSettingsKeys.TNT, 200L, true)
        store.setExplicit(ClaimSettingsKeys.PVP, 100L, false)
        store.setExplicit(ClaimSettingsKeys.TNT, 100L, false)
        assertEquals(false, store.getExplicit(ClaimSettingsKeys.TNT, 100L))

        store.setExplicit(ClaimSettingsKeys.TNT, 100L, null)

        assertNull(store.getExplicit(ClaimSettingsKeys.TNT, 100L))
        assertEquals(true, store.getExplicit(ClaimSettingsKeys.TNT, 200L))
        assertEquals(false, store.getExplicit(ClaimSettingsKeys.PVP, 100L))
        val rows = dbRows()
        assertFalse("100:tnt" in rows)
        assertEquals("true", rows["200:tnt"])
        assertEquals("false", rows["100:pvp"])

        val (_, restarted) = coldRestart()
        assertNull(restarted.getExplicit(ClaimSettingsKeys.TNT, 100L))
        assertEquals(true, restarted.getExplicit(ClaimSettingsKeys.TNT, 200L))
    }

    @Test
    fun `purgeClaim 子花域不碰主花域與兄弟`() {
        val parent = 1L
        val childA = 2L
        val childB = 3L
        listOf(parent, childA, childB).forEach {
            store.setExplicit(ClaimSettingsKeys.TNT, it, true)
            store.setExplicit(ClaimSettingsKeys.NO_HOSTILE_SPAWN, it, false)
            store.setAlias(it, "claim$it")
            store.setSpawnRaw(it, "world;1.0;64.0;1.0;0.0;0.0")
        }

        store.purgeClaim(childA)

        assertNull(store.getExplicit(ClaimSettingsKeys.TNT, childA))
        assertNull(store.getExplicit(ClaimSettingsKeys.NO_HOSTILE_SPAWN, childA))
        assertNull(store.getAlias(childA))
        assertNull(store.getSpawnRaw(childA))
        listOf(parent, childB).forEach {
            assertEquals(true, store.getExplicit(ClaimSettingsKeys.TNT, it))
            assertEquals(false, store.getExplicit(ClaimSettingsKeys.NO_HOSTILE_SPAWN, it))
            assertEquals("claim$it", store.getAlias(it))
        }
        val rows = dbRows()
        assertTrue(rows.keys.none { it.startsWith("$childA:") })
        assertEquals(8, rows.size) // 兩個保留的範圍各 4 列
    }

    // ---------------------------------------------------------------- migrateToV2

    /** 建立 v1 形狀的 DB:只有 'true' 列,子花域 id 20 上有布林殘留與別名/落腳點。 */
    private fun createV1Db(): File {
        val f = File(tempDir, "v1.db")
        DriverManager.getConnection("jdbc:sqlite:${f.absolutePath}").use { conn ->
            conn.createStatement().use { st ->
                st.executeUpdate(
                    "CREATE TABLE claim_settings (claim_id INTEGER NOT NULL, setting_key TEXT NOT NULL, " +
                        "setting_value TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY (claim_id, setting_key))",
                )
                listOf(
                    "(10, 'tnt', 'true', 0)", // 主花域
                    "(10, 'no-hostile-spawn', 'true', 0)",
                    "(20, 'tnt', 'true', 0)", // 子花域殘留
                    "(20, 'pvp', 'true', 0)",
                    "(20, 'alias', '子花域別名', 0)",
                    "(20, 'tp-point', 'world;1.0;64.0;1.0;0.0;0.0', 0)",
                ).forEach { st.executeUpdate("INSERT INTO claim_settings VALUES $it") }
            }
        }
        return f
    }

    private val classifier: (Long) -> ClaimIdKind = { id ->
        when (id) {
            20L -> ClaimIdKind.SUBDIVISION
            10L -> ClaimIdKind.TOP_LEVEL
            else -> ClaimIdKind.UNKNOWN
        }
    }

    private fun openV1(): Pair<Db, ClaimSettingsStore> {
        db.close()
        dbFile = createV1Db()
        val v1Db = Db(dbFile)
        val v1Store = ClaimSettingsStore(v1Db)
        v1Store.init()
        db = v1Db
        store = v1Store
        return v1Db to v1Store
    }

    @Test
    fun `migrateToV2 刪子花域布林殘留 保留別名落腳點與主花域`() {
        val (_, s) = openV1()
        assertTrue(s.preExistingDatabase)

        val removed = s.migrateToV2(File(tempDir, "backups"), classifier)

        assertEquals(2, removed)
        val rows = dbRows()
        assertFalse("20:tnt" in rows)
        assertFalse("20:pvp" in rows)
        assertEquals("子花域別名", rows["20:alias"])
        assertNotNull(rows["20:tp-point"])
        assertEquals("true", rows["10:tnt"])
        assertEquals("true", rows["10:no-hostile-spawn"])
        // 快取同步
        assertNull(s.getExplicit(ClaimSettingsKeys.TNT, 20L))
        assertEquals(true, s.getExplicit(ClaimSettingsKeys.TNT, 10L))
        assertEquals("子花域別名", s.getAlias(20L))
        assertEquals("2", s.getMeta(ClaimSettingsStore.META_SCHEMA_VERSION))
        // DB 舊版建的 → 視為舊版早已匯入過 LST
        assertNotNull(s.getMeta(ClaimSettingsStore.META_LST_DONE))
        // 有資料 → 留一份備份
        assertTrue(File(tempDir, "backups").listFiles()?.any { it.name.startsWith("data.db.bak-v1-") } == true)
    }

    @Test
    fun `migrateToV2 重跑不改變任何東西`() {
        val (_, s) = openV1()
        s.migrateToV2(null, classifier)
        val rowsAfterFirst = dbRows()
        // 第二次就算分類器把更多 id 判成子花域也不會再清(旗標已設)
        val removed = s.migrateToV2(null) { ClaimIdKind.SUBDIVISION }

        assertEquals(-1, removed)
        assertEquals(rowsAfterFirst, dbRows())
        assertEquals("2", s.getMeta(ClaimSettingsStore.META_SCHEMA_VERSION))
    }

    @Test
    fun `migrateToV2 classifier 為 null 時跳過清理 之後有 classifier 才補做`() {
        val (_, s) = openV1()

        assertEquals(-1, s.migrateToV2(null, null))
        assertEquals("true", dbRows()["20:tnt"])
        assertNull(s.getMeta(ClaimSettingsStore.META_V2_SUBDIVISION_CLEANUP))
        // GP 還沒好時 LST 旗標仍照規則先設(DB 是舊版建的)
        assertNotNull(s.getMeta(ClaimSettingsStore.META_LST_DONE))

        // 之後 GP 可用了:同一份 DB 補做清理
        assertEquals(2, s.migrateToV2(null, classifier))
        assertFalse("20:tnt" in dbRows())
        assertNotNull(s.getMeta(ClaimSettingsStore.META_V2_SUBDIVISION_CLEANUP))
    }

    @Test
    fun `migrateToV2 空資料庫不備份 removed 為 0`() {
        // setUp 建的是全新 DB:沒有 pre-existing,也沒有列
        assertFalse(store.preExistingDatabase)
        val removed = store.migrateToV2(File(tempDir, "backups"), classifier)
        assertEquals(0, removed)
        assertFalse(File(tempDir, "backups").exists())
        // 全新 DB 沒有舊版匯入過 → 不標記 LST 完成
        assertNull(store.getMeta(ClaimSettingsStore.META_LST_DONE))
    }

    // ---------------------------------------------------------------- LST 匯入

    @Test
    fun `LST 匯入在全新 DB 只做一次且快取等於 DB`() {
        val lst = createLstDb(101L to "pvp", 101L to "no-hostile-spawn", 102L to "no-phantom-spawn")

        val migrated = store.migrateFromLycoServerTweaks(lst)

        assertEquals(3, migrated)
        assertEquals(true, store.getExplicit(ClaimSettingsKeys.PVP, 101L))
        assertEquals(true, store.getExplicit(ClaimSettingsKeys.NO_HOSTILE_SPAWN, 101L))
        assertEquals(true, store.getExplicit(ClaimSettingsKeys.NO_PHANTOM_SPAWN, 102L))
        assertNull(store.getExplicit(ClaimSettingsKeys.TNT, 101L))
        // 快取 = DB
        assertEquals(
            mapOf("101:pvp" to "true", "101:no-hostile-spawn" to "true", "102:no-phantom-spawn" to "true"),
            dbRows(),
        )
        assertNotNull(store.getMeta(ClaimSettingsStore.META_LST_DONE))

        // 第二次不再匯入(就算 LST 檔多了新列)
        DriverManager.getConnection("jdbc:sqlite:${lst.absolutePath}").use {
            it.createStatement().use { st -> st.executeUpdate("INSERT INTO claim_toggle VALUES (103, 'tnt')") }
        }
        assertEquals(0, store.migrateFromLycoServerTweaks(lst))
        assertNull(store.getExplicit(ClaimSettingsKeys.TNT, 103L))
    }

    @Test
    fun `LST 匯入不覆蓋既有的明確 false`() {
        store.setExplicit(ClaimSettingsKeys.PVP, 101L, false)
        db.flush()
        val lst = createLstDb(101L to "pvp", 102L to "pvp")

        val migrated = store.migrateFromLycoServerTweaks(lst)

        assertEquals(1, migrated) // 只有 102 是新的
        assertEquals(false, store.getExplicit(ClaimSettingsKeys.PVP, 101L))
        assertEquals(true, store.getExplicit(ClaimSettingsKeys.PVP, 102L))
        assertEquals("false", dbRows()["101:pvp"])
    }

    @Test
    fun `舊版 DB 遷移後 LST 匯入不做事 不復活玩家關掉的值`() {
        val (_, s) = openV1()
        s.migrateToV2(null, classifier)
        // 玩家在 v1 關掉過 pvp(v1 是刪列);LST 舊表裡還留著那一筆
        val lst = createLstDb(10L to "pvp", 30L to "tnt")

        val migrated = s.migrateFromLycoServerTweaks(lst)

        assertEquals(0, migrated)
        assertNull(s.getExplicit(ClaimSettingsKeys.PVP, 10L))
        assertNull(s.getExplicit(ClaimSettingsKeys.TNT, 30L))
        assertFalse("10:pvp" in dbRows())
    }

    @Test
    fun `LST 檔不存在或沒有舊表時不出錯`() {
        assertEquals(0, store.migrateFromLycoServerTweaks(File(tempDir, "missing.db")))
        val empty = File(tempDir, "empty-lst.db")
        DriverManager.getConnection("jdbc:sqlite:${empty.absolutePath}").use {
            it.createStatement().use { st -> st.executeUpdate("CREATE TABLE other (x INTEGER)") }
        }
        assertEquals(0, store.migrateFromLycoServerTweaks(empty))
    }

    // ---------------------------------------------------------------- 落腳點與別名

    @Test
    fun `spawn point CRUD survives reconnect and purge`() {
        assertEquals(null, store.getSpawnRaw(100L))

        store.setSpawnRaw(100L, "world;1.5;64.0;-2.5;90.0;0.0")
        store.setSpawnRaw(200L, "world_nether;10.0;70.0;10.0;0.0;0.0")
        assertEquals("world;1.5;64.0;-2.5;90.0;0.0", store.getSpawnRaw(100L))

        // 落腳點不是三態布林:不會出現在明確值裡
        assertEquals(emptyMap(), store.explicitFor(100L))

        val (_, newStore) = coldRestart()

        assertEquals("world;1.5;64.0;-2.5;90.0;0.0", newStore.getSpawnRaw(100L))
        assertEquals("world_nether;10.0;70.0;10.0;0.0;0.0", newStore.getSpawnRaw(200L))

        newStore.setSpawnRaw(100L, null)
        assertEquals(null, newStore.getSpawnRaw(100L))

        // 花域被刪掉時落腳點也要跟著清掉,否則同編號的新花域會繼承前一塊的落點
        newStore.purgeClaim(200L)
        assertEquals(null, newStore.getSpawnRaw(200L))
    }

    @Test
    fun `alias CRUD and findClaimId support`() {
        assertEquals(null, store.getAlias(100L))

        store.setAlias(100L, "主家")
        store.setAlias(200L, "MyFarm")

        assertEquals("主家", store.getAlias(100L))
        assertEquals("MyFarm", store.getAlias(200L))

        // 純數字與 # 編號查找
        assertEquals(100L, store.findClaimId("100", null, emptyList()))
        assertEquals(100L, store.findClaimId("#100", null, emptyList()))

        // 別名查找 (大小寫不拘)
        assertEquals(100L, store.findClaimId("主家", null, emptyList()))
        assertEquals(200L, store.findClaimId("myfarm", null, emptyList()))
        assertEquals(200L, store.findClaimId("MYFARM", null, emptyList()))

        val (_, newStore) = coldRestart()

        assertEquals("主家", newStore.getAlias(100L))
        assertEquals("MyFarm", newStore.getAlias(200L))

        newStore.setAlias(100L, null)
        assertEquals(null, newStore.getAlias(100L))
        assertEquals(null, newStore.findClaimId("主家", null, emptyList()))

        // purgeClaim 也會清除別名
        newStore.purgeClaim(200L)
        assertEquals(null, newStore.getAlias(200L))
    }
}
