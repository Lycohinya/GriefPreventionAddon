package com.tinyyana.griefPreventionAddon.storage

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test
    fun `defaults to false for all settings`() {
        assertFalse(store.isTntAllowed(100L))
        assertFalse(store.isPvpAllowed(100L))
        assertFalse(store.isSethomeAllowed(100L))
        assertFalse(store.isMobSpawnBlocked(ClaimSettingsKeys.NO_HOSTILE_SPAWN, 100L))
        assertFalse(store.anyEnabled(ClaimSettingsKeys.TNT))
        assertFalse(store.anyEnabled(ClaimSettingsKeys.ALLOW_SETHOME))
    }

    @Test
    fun `toggle changes state and updates cache`() {
        val newState = store.toggle(ClaimSettingsKeys.TNT, 100L)
        assertTrue(newState)
        assertTrue(store.isTntAllowed(100L))
        assertTrue(store.anyEnabled(ClaimSettingsKeys.TNT))

        val turnedOff = store.toggle(ClaimSettingsKeys.TNT, 100L)
        assertFalse(turnedOff)
        assertFalse(store.isTntAllowed(100L))
        assertFalse(store.anyEnabled(ClaimSettingsKeys.TNT))

        val sethomeState = store.toggle(ClaimSettingsKeys.ALLOW_SETHOME, 100L)
        assertTrue(sethomeState)
        assertTrue(store.isSethomeAllowed(100L))
    }

    @Test
    fun `persists settings across reconnect`() {
        store.setBoolean(ClaimSettingsKeys.TNT, 100L, true)
        store.setBoolean(ClaimSettingsKeys.PVP, 200L, true)
        store.setBoolean(ClaimSettingsKeys.ALLOW_SETHOME, 300L, true)
        store.setBoolean(ClaimSettingsKeys.NO_HOSTILE_SPAWN, 100L, true)

        db.close()

        val newDb = Db(dbFile)
        val newStore = ClaimSettingsStore(newDb)
        newStore.init()

        assertTrue(newStore.isTntAllowed(100L))
        assertFalse(newStore.isTntAllowed(200L))
        assertTrue(newStore.isPvpAllowed(200L))
        assertTrue(newStore.isMobSpawnBlocked(ClaimSettingsKeys.NO_HOSTILE_SPAWN, 100L))

        newDb.close()
    }

    @Test
    fun `purgeClaim cleans up all settings for deleted claim`() {
        store.setBoolean(ClaimSettingsKeys.TNT, 100L, true)
        store.setBoolean(ClaimSettingsKeys.PVP, 100L, true)
        store.setBoolean(ClaimSettingsKeys.NO_HOSTILE_SPAWN, 100L, true)
        store.setBoolean(ClaimSettingsKeys.TNT, 200L, true)

        store.purgeClaim(100L)

        assertFalse(store.isTntAllowed(100L))
        assertFalse(store.isPvpAllowed(100L))
        assertFalse(store.isMobSpawnBlocked(ClaimSettingsKeys.NO_HOSTILE_SPAWN, 100L))
        assertTrue(store.isTntAllowed(200L))
    }

    @Test
    fun `migrates old claim_toggle records from LycoServerTweaks`() {
        val lstDbFile = File(tempDir, "lst.db")
        DriverManager.getConnection("jdbc:sqlite:${lstDbFile.absolutePath}").use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeUpdate("CREATE TABLE claim_toggle (claim_id INTEGER NOT NULL, key TEXT NOT NULL, PRIMARY KEY (claim_id, key))")
                stmt.executeUpdate("INSERT INTO claim_toggle VALUES (101, 'pvp')")
                stmt.executeUpdate("INSERT INTO claim_toggle VALUES (101, 'no-hostile-spawn')")
                stmt.executeUpdate("INSERT INTO claim_toggle VALUES (102, 'no-phantom-spawn')")
            }
        }

        val migrated = store.migrateFromLycoServerTweaks(lstDbFile)
        assertEquals(3, migrated)

        assertTrue(store.isPvpAllowed(101L))
        assertTrue(store.isMobSpawnBlocked(ClaimSettingsKeys.NO_HOSTILE_SPAWN, 101L))
        assertTrue(store.isMobSpawnBlocked(ClaimSettingsKeys.NO_PHANTOM_SPAWN, 102L))
        assertFalse(store.isTntAllowed(101L)) // TNT 仍維持預設安全值 false
    }

    @Test
    fun `spawn point CRUD survives reconnect and purge`() {
        assertEquals(null, store.getSpawnRaw(100L))

        store.setSpawnRaw(100L, "world;1.5;64.0;-2.5;90.0;0.0")
        store.setSpawnRaw(200L, "world_nether;10.0;70.0;10.0;0.0;0.0")
        assertEquals("world;1.5;64.0;-2.5;90.0;0.0", store.getSpawnRaw(100L))

        // 落腳點不能被誤認成布林開關(值不是 "true",載入時走的是另一條分支)
        assertFalse(store.getBoolean(ClaimSettingsKeys.TP_POINT, 100L))

        db.close()
        val newDb = Db(dbFile)
        val newStore = ClaimSettingsStore(newDb)
        newStore.init()

        assertEquals("world;1.5;64.0;-2.5;90.0;0.0", newStore.getSpawnRaw(100L))
        assertEquals("world_nether;10.0;70.0;10.0;0.0;0.0", newStore.getSpawnRaw(200L))

        newStore.setSpawnRaw(100L, null)
        assertEquals(null, newStore.getSpawnRaw(100L))

        // 花域被刪掉時落腳點也要跟著清掉,否則同編號的新花域會繼承前一塊的落點
        newStore.purgeClaim(200L)
        assertEquals(null, newStore.getSpawnRaw(200L))

        newDb.close()
    }

    @Test
    fun `alias CRUD and findClaimId support`() {
        // 初始狀態無別名
        assertEquals(null, store.getAlias(100L))

        // 設定別名
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

        // 重新連線後別名依然存在
        db.close()
        val newDb = Db(dbFile)
        val newStore = ClaimSettingsStore(newDb)
        newStore.init()

        assertEquals("主家", newStore.getAlias(100L))
        assertEquals("MyFarm", newStore.getAlias(200L))

        // 清除別名
        newStore.setAlias(100L, null)
        assertEquals(null, newStore.getAlias(100L))
        assertEquals(null, newStore.findClaimId("主家", null, emptyList()))

        // purgeClaim 也會清除別名
        newStore.purgeClaim(200L)
        assertEquals(null, newStore.getAlias(200L))

        newDb.close()
    }
}
