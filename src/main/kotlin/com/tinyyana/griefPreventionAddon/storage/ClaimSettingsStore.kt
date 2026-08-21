package com.tinyyana.griefPreventionAddon.storage

import java.io.File
import java.sql.DriverManager
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

object ClaimSettingsKeys {
    /** 領地 TNT 方塊破壞:存在且為 true = 允許 TNT 爆炸破壞方塊;預設 false (禁止破壞) */
    const val TNT = "tnt"

    /** 領地 PVP:存在且為 true = 開放 PVP;預設 false (GP 領地保護) */
    const val PVP = "pvp"

    /** 生物生成開關:存在且為 true = 該領地禁止該類生物自然生成 */
    const val NO_HOSTILE_SPAWN = "no-hostile-spawn"
    const val NO_RAIDER_SPAWN = "no-raider-spawn"
    const val NO_PHANTOM_SPAWN = "no-phantom-spawn"
    const val NO_SLIME_SPAWN = "no-slime-spawn"
    const val NO_AMBIENT_SPAWN = "no-ambient-spawn"

    /** 領地別名(自訂名稱，支援中文與英數字) */
    const val ALIAS = "alias"

    val ALL_KEYS = listOf(
        TNT,
        PVP,
        NO_HOSTILE_SPAWN,
        NO_RAIDER_SPAWN,
        NO_PHANTOM_SPAWN,
        NO_SLIME_SPAWN,
        NO_AMBIENT_SPAWN,
        ALIAS,
    )
}

/**
 * 領地設定儲存庫(SQLite + 記憶體快取)。
 * 爆炸、生怪等高頻路徑 100% 走快取 O(1) 查詢，不打資料庫。
 */
class ClaimSettingsStore(
    private val db: Db,
    private val logger: Logger? = null,
) {
    /** key -> 啟用(true)該設定的 claimId 集合 */
    private val cache = ConcurrentHashMap<String, MutableSet<Long>>()

    /** claimId -> 自訂別名 (支援中英文) */
    private val aliasCache = ConcurrentHashMap<Long, String>()

    fun init() {
        db.tx { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS claim_settings (
                        claim_id INTEGER NOT NULL,
                        setting_key TEXT NOT NULL,
                        setting_value TEXT NOT NULL,
                        updated_at INTEGER NOT NULL,
                        PRIMARY KEY (claim_id, setting_key)
                    )
                    """.trimIndent(),
                )
            }
            conn.prepareStatement("SELECT claim_id, setting_key, setting_value FROM claim_settings").use { stmt ->
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val claimId = rs.getLong("claim_id")
                        val key = rs.getString("setting_key")
                        val value = rs.getString("setting_value")
                        if (key == ClaimSettingsKeys.ALIAS) {
                            if (value.isNotBlank()) {
                                aliasCache[claimId] = value
                            }
                        } else if (value.equals("true", ignoreCase = true)) {
                            cache.getOrPut(key) { ConcurrentHashMap.newKeySet() }.add(claimId)
                        }
                    }
                }
            }
        }
    }

    /** 取得領地自訂別名 */
    fun getAlias(claimId: Long): String? = aliasCache[claimId]

    /** 設定或清除領地自訂別名(null 或空白代表清除) */
    fun setAlias(claimId: Long, alias: String?) {
        val cleanAlias = alias?.trim()?.takeIf { it.isNotBlank() }
        val now = System.currentTimeMillis()
        db.tx { conn ->
            if (cleanAlias != null) {
                val sql = "INSERT INTO claim_settings(claim_id, setting_key, setting_value, updated_at) VALUES (?, 'alias', ?, ?) " +
                    "ON CONFLICT(claim_id, setting_key) DO UPDATE SET setting_value=excluded.setting_value, updated_at=excluded.updated_at"
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setLong(1, claimId)
                    stmt.setString(2, cleanAlias)
                    stmt.setLong(3, now)
                    stmt.executeUpdate()
                }
                aliasCache[claimId] = cleanAlias
            } else {
                conn.prepareStatement("DELETE FROM claim_settings WHERE claim_id = ? AND setting_key = 'alias'").use { stmt ->
                    stmt.setLong(1, claimId)
                    stmt.executeUpdate()
                }
                aliasCache.remove(claimId)
            }
        }
    }

    /**
     * 尋找領地 ID：優先解析編號（如 `123` 或 `#123`），次之比對玩家名下領地之自訂別名（不分大小寫）。
     */
    fun findClaimId(input: String, ownerUuid: java.util.UUID?, playerClaims: List<me.ryanhamshire.GriefPrevention.Claim>): Long? {
        val trimmed = input.trim()
        val numId = trimmed.removePrefix("#").toLongOrNull()
        if (numId != null) {
            return numId
        }

        // 1. 從玩家自身領地中比對別名
        for (claim in playerClaims) {
            val id = claim.id ?: continue
            val alias = aliasCache[id]
            if (alias != null && alias.equals(trimmed, ignoreCase = true)) {
                return id
            }
        }

        // 2. 全域別名反查(例如管理員操作或唯一別名)
        val globalMatch = aliasCache.entries.firstOrNull { it.value.equals(trimmed, ignoreCase = true) }
        return globalMatch?.key
    }

    /** 領地是否允許 TNT 破壞方塊(預設 false) */
    fun isTntAllowed(claimId: Long): Boolean = cache[ClaimSettingsKeys.TNT]?.contains(claimId) == true

    /** 領地是否開放 PVP(預設 false) */
    fun isPvpAllowed(claimId: Long): Boolean = cache[ClaimSettingsKeys.PVP]?.contains(claimId) == true

    /** 領地是否禁止指定類別的生物生成 */
    fun isMobSpawnBlocked(key: String, claimId: Long): Boolean = cache[key]?.contains(claimId) == true

    /** 全服是否有任何領地啟用此開關(生怪事件快速過濾) */
    fun anyEnabled(key: String): Boolean = !cache[key].isNullOrEmpty()

    /** 取得指定領地的指定布林開關狀態 */
    fun getBoolean(key: String, claimId: Long): Boolean = cache[key]?.contains(claimId) == true

    /** 切換布林設定並持久化。回傳切換後的新狀態 */
    fun toggle(key: String, claimId: Long): Boolean {
        val set = cache.getOrPut(key) { ConcurrentHashMap.newKeySet() }
        val turnOn = claimId !in set
        val now = System.currentTimeMillis()
        db.tx { conn ->
            val sql = if (turnOn) {
                "INSERT INTO claim_settings(claim_id, setting_key, setting_value, updated_at) VALUES (?, ?, 'true', ?) " +
                    "ON CONFLICT(claim_id, setting_key) DO UPDATE SET setting_value='true', updated_at=excluded.updated_at"
            } else {
                "DELETE FROM claim_settings WHERE claim_id = ? AND setting_key = ?"
            }
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, claimId)
                stmt.setString(2, key)
                if (turnOn) {
                    stmt.setLong(3, now)
                }
                stmt.executeUpdate()
            }
        }
        if (turnOn) set.add(claimId) else set.remove(claimId)
        return turnOn
    }

    /** 設定指定布林值 */
    fun setBoolean(key: String, claimId: Long, value: Boolean) {
        val set = cache.getOrPut(key) { ConcurrentHashMap.newKeySet() }
        val now = System.currentTimeMillis()
        db.tx { conn ->
            val sql = if (value) {
                "INSERT INTO claim_settings(claim_id, setting_key, setting_value, updated_at) VALUES (?, ?, 'true', ?) " +
                    "ON CONFLICT(claim_id, setting_key) DO UPDATE SET setting_value='true', updated_at=excluded.updated_at"
            } else {
                "DELETE FROM claim_settings WHERE claim_id = ? AND setting_key = ?"
            }
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, claimId)
                stmt.setString(2, key)
                if (value) {
                    stmt.setLong(3, now)
                }
                stmt.executeUpdate()
            }
        }
        if (value) set.add(claimId) else set.remove(claimId)
    }

    /** 領地被刪除時清理所有設定記錄 */
    fun purgeClaim(claimId: Long) {
        db.tx { conn ->
            conn.prepareStatement("DELETE FROM claim_settings WHERE claim_id = ?").use { stmt ->
                stmt.setLong(1, claimId)
                stmt.executeUpdate()
            }
        }
        cache.values.forEach { it.remove(claimId) }
        aliasCache.remove(claimId)
    }

    /**
     * 從 LycoServerTweaks 的 data.db 自動遷移舊版 claim_toggle 表
     */
    fun migrateFromLycoServerTweaks(lstDbFile: File): Int {
        if (!lstDbFile.exists()) return 0
        var migratedCount = 0
        runCatching {
            DriverManager.getConnection("jdbc:sqlite:${lstDbFile.absolutePath}").use { lstConn ->
                // 檢查 claim_toggle 表是否存在
                val tableExists = lstConn.metaData.getTables(null, null, "claim_toggle", null).use { rs ->
                    rs.next()
                }
                if (!tableExists) return 0

                val now = System.currentTimeMillis()
                lstConn.prepareStatement("SELECT claim_id, key FROM claim_toggle").use { stmt ->
                    stmt.executeQuery().use { rs ->
                        db.tx { conn ->
                            conn.prepareStatement(
                                "INSERT OR IGNORE INTO claim_settings(claim_id, setting_key, setting_value, updated_at) VALUES (?, ?, 'true', ?)",
                            ).use { insertStmt ->
                                while (rs.next()) {
                                    val claimId = rs.getLong("claim_id")
                                    val key = rs.getString("key")
                                    insertStmt.setLong(1, claimId)
                                    insertStmt.setString(2, key)
                                    insertStmt.setLong(3, now)
                                    insertStmt.addBatch()
                                    cache.getOrPut(key) { ConcurrentHashMap.newKeySet() }.add(claimId)
                                    migratedCount++
                                }
                                insertStmt.executeBatch()
                            }
                        }
                    }
                }
            }
            if (migratedCount > 0) {
                logger?.info("[GriefPreventionAddon] 成功從 LycoServerTweaks 遷移 $migratedCount 筆領地開關設定。")
            }
        }.onFailure { e ->
            logger?.warning("[GriefPreventionAddon] 從 LycoServerTweaks 遷移資料時發生錯誤: ${e.message}")
        }
        return migratedCount
    }
}
