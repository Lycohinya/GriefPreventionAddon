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
        assertFalse(store.isMobSpawnBlocked(ClaimSettingsKeys.NO_HOSTILE_SPAWN, 100L))
        assertFalse(store.anyEnabled(ClaimSettingsKeys.TNT))
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
    }

    @Test
    fun `persists settings across reconnect`() {
        store.setBoolean(ClaimSettingsKeys.TNT, 100L, true)
        store.setBoolean(ClaimSettingsKeys.PVP, 200L, true)
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
}
