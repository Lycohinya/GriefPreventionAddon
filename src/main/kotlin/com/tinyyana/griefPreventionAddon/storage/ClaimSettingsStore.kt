package com.tinyyana.griefPreventionAddon.storage

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

object ClaimSettingsKeys {
    /** 領地 TNT 方塊破壞:true = 允許 TNT 爆炸破壞方塊 */
    const val TNT = "tnt"

    /** 領地 PVP:true = 開放 PVP */
    const val PVP = "pvp"

    /** 訪客設家權限:true = 允許未信任訪客在此花域使用 /sethome */
    const val ALLOW_SETHOME = "allow-sethome"

    /** 成員設家權限:true = 有 GP 信任(通行以上)的人可以在此使用 /sethome */
    const val MEMBER_SETHOME = "member-sethome"

    /** 生物生成開關:true = 該範圍禁止該類生物自然生成(值的正反沿用舊資料,不翻轉) */
    const val NO_HOSTILE_SPAWN = "no-hostile-spawn"
    const val NO_RAIDER_SPAWN = "no-raider-spawn"
    const val NO_PHANTOM_SPAWN = "no-phantom-spawn"
    const val NO_SLIME_SPAWN = "no-slime-spawn"
    const val NO_AMBIENT_SPAWN = "no-ambient-spawn"

    /** 火焰蔓延(火往旁邊擴散、岩漿引燃):true = 允許 */
    const val FIRE_SPREAD = "fire-spread"

    /** 火燒方塊(可燃方塊被火燒掉):true = 允許 */
    const val FIRE_BURN = "fire-burn"

    /** 領地別名(自訂名稱，支援中文與英數字) */
    const val ALIAS = "alias"

    /** 自訂落腳點:`/ctp` 的目的地,序列化格式見 [com.tinyyana.griefPreventionAddon.teleport.ClaimSpawnPoint] */
    const val TP_POINT = "tp-point"

    /** 三態(繼承/允許/禁止)的布林設定。別名與落腳點是有型別的資料,不在這裡。 */
    val BOOLEAN_KEYS = listOf(
        TNT,
        PVP,
        ALLOW_SETHOME,
        MEMBER_SETHOME,
        NO_HOSTILE_SPAWN,
        NO_RAIDER_SPAWN,
        NO_PHANTOM_SPAWN,
        NO_SLIME_SPAWN,
        NO_AMBIENT_SPAWN,
        FIRE_SPREAD,
        FIRE_BURN,
    )

    val ALL_KEYS = BOOLEAN_KEYS + listOf(ALIAS, TP_POINT)
}

/** 遷移時對 claim id 的分類(由 GP 的實際資料回答;store 本身不依賴 GP)。 */
enum class ClaimIdKind { TOP_LEVEL, SUBDIVISION, UNKNOWN }

/**
 * 領地設定儲存庫(SQLite + 記憶體快取)。
 *
 * ## 三態語意(schema v2 起)
 * 布林設定一列一個 **明確值**:`'true'` 或 `'false'`。**沒有列 = 沒有設定 = 繼承**
 * (子花域繼承主花域、主花域繼承伺服器預設,解析見 `ClaimPolicyResolver`)。
 * v1 之前「關閉」是刪列,沒辦法同時表示「子花域明確關閉」與「子花域沒設定、跟主花域走」。
 *
 * 爆炸、生怪、火焰等高頻路徑只讀 [explicit] 快取,永遠不打資料庫。寫入先改快取再交給
 * [Db.write] 依序落地。
 */
class ClaimSettingsStore(
    private val db: Db,
    private val logger: Logger? = null,
) {
    /** key -> (claimId -> 明確值) */
    private val explicit = ConcurrentHashMap<String, ConcurrentHashMap<Long, Boolean>>()

    /** claimId -> 自訂別名 (支援中英文) */
    private val aliasCache = ConcurrentHashMap<Long, String>()

    /** claimId -> 自訂落腳點(序列化字串;解析交給 ClaimSpawnPoint,store 不依賴 Bukkit) */
    private val spawnCache = ConcurrentHashMap<Long, String>()

    /** 這個 DB 在本次啟動前是否已經被舊版建過(用來判斷舊版是否早就重複匯入過 LycoServerTweaks)。 */
    var preExistingDatabase: Boolean = false
        private set

    fun init() {
        db.tx { conn ->
            preExistingDatabase = tableExists(conn, "claim_settings")
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
                stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS addon_meta (meta_key TEXT PRIMARY KEY, meta_value TEXT NOT NULL)",
                )
            }
        }
        reloadCache()
    }

    /** 從 DB 重建全部快取。啟動與遷移後呼叫,保證快取 = DB。 */
    fun reloadCache() {
        val freshExplicit = HashMap<String, ConcurrentHashMap<Long, Boolean>>()
        val freshAlias = HashMap<Long, String>()
        val freshSpawn = HashMap<Long, String>()
        db.tx { conn ->
            conn.prepareStatement("SELECT claim_id, setting_key, setting_value FROM claim_settings").use { stmt ->
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val claimId = rs.getLong("claim_id")
                        val key = rs.getString("setting_key")
                        val value = rs.getString("setting_value")
                        when (key) {
                            ClaimSettingsKeys.ALIAS -> if (value.isNotBlank()) freshAlias[claimId] = value
                            ClaimSettingsKeys.TP_POINT -> if (value.isNotBlank()) freshSpawn[claimId] = value
                            else -> parseBoolean(value)?.let { freshExplicit.getOrPut(key) { ConcurrentHashMap() }[claimId] = it }
                        }
                    }
                }
            }
        }
        explicit.clear()
        explicit.putAll(freshExplicit)
        aliasCache.clear()
        aliasCache.putAll(freshAlias)
        spawnCache.clear()
        spawnCache.putAll(freshSpawn)
    }

    // ---------------------------------------------------------------- 三態布林

    /** 這個範圍自己的明確值;null = 沒有設定(繼承)。 */
    fun getExplicit(key: String, claimId: Long): Boolean? = explicit[key]?.get(claimId)

    /** 全服是否有任何範圍把 [key] 明確設成 [value](生怪等高頻路徑的快篩,O(n) 只在快取上)。 */
    fun hasAnyExplicit(key: String, value: Boolean): Boolean = explicit[key]?.containsValue(value) == true

    /** 這個範圍所有明確設定(畫面/管理介面用)。 */
    fun explicitFor(claimId: Long): Map<String, Boolean> =
        explicit.entries.mapNotNull { (key, map) -> map[claimId]?.let { key to it } }.toMap()

    /** 有明確設定的 claim id(管理用)。 */
    fun claimIdsWithExplicit(key: String): Set<Long> = explicit[key]?.keys?.toSet() ?: emptySet()

    /**
     * 設定明確值;[value] 為 null 代表「恢復繼承」——只刪這個範圍自己的那一列,不碰主花域。
     */
    fun setExplicit(key: String, claimId: Long, value: Boolean?) {
        val map = explicit.getOrPut(key) { ConcurrentHashMap() }
        val now = System.currentTimeMillis()
        if (value == null) {
            map.remove(claimId)
            db.write("unset $key#$claimId") { conn ->
                conn.prepareStatement("DELETE FROM claim_settings WHERE claim_id = ? AND setting_key = ?").use { stmt ->
                    stmt.setLong(1, claimId)
                    stmt.setString(2, key)
                    stmt.executeUpdate()
                }
            }
        } else {
            map[claimId] = value
            db.write("set $key#$claimId") { conn -> upsert(conn, claimId, key, value.toString(), now) }
        }
    }

    // ---------------------------------------------------------------- 別名與落腳點

    /** 取得領地自訂別名 */
    fun getAlias(claimId: Long): String? = aliasCache[claimId]

    /** 設定或清除領地自訂別名(null 或空白代表清除) */
    fun setAlias(claimId: Long, alias: String?) {
        val cleanAlias = alias?.trim()?.takeIf { it.isNotBlank() }
        val now = System.currentTimeMillis()
        if (cleanAlias != null) {
            aliasCache[claimId] = cleanAlias
            db.write("alias#$claimId") { conn -> upsert(conn, claimId, ClaimSettingsKeys.ALIAS, cleanAlias, now) }
        } else {
            aliasCache.remove(claimId)
            db.write("alias-clear#$claimId") { conn -> deleteKey(conn, claimId, ClaimSettingsKeys.ALIAS) }
        }
    }

    /** 取得領地自訂落腳點的原始字串(null = 未設定,傳送時退回花域中心) */
    fun getSpawnRaw(claimId: Long): String? = spawnCache[claimId]

    /** 設定或清除領地自訂落腳點(null 或空白代表清除) */
    fun setSpawnRaw(claimId: Long, encoded: String?) {
        val clean = encoded?.trim()?.takeIf { it.isNotBlank() }
        val now = System.currentTimeMillis()
        if (clean != null) {
            spawnCache[claimId] = clean
            db.write("spawn#$claimId") { conn -> upsert(conn, claimId, ClaimSettingsKeys.TP_POINT, clean, now) }
        } else {
            spawnCache.remove(claimId)
            db.write("spawn-clear#$claimId") { conn -> deleteKey(conn, claimId, ClaimSettingsKeys.TP_POINT) }
        }
    }

    /**
     * 尋找領地 ID：優先解析編號（如 `123` 或 `#123`），次之比對玩家名下領地之自訂別名（不分大小寫）。
     * 編號是否真的存在由呼叫端向 GP 查;這裡不會把查不到的編號換成別的領地。
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

    // ---------------------------------------------------------------- 生命週期

    /** 領地被刪除時清理這個 id 的所有設定記錄(子花域各自觸發,不會波及父或兄弟)。 */
    fun purgeClaim(claimId: Long) {
        explicit.values.forEach { it.remove(claimId) }
        aliasCache.remove(claimId)
        spawnCache.remove(claimId)
        db.write("purge#$claimId") { conn ->
            conn.prepareStatement("DELETE FROM claim_settings WHERE claim_id = ?").use { stmt ->
                stmt.setLong(1, claimId)
                stmt.executeUpdate()
            }
        }
    }

    // ---------------------------------------------------------------- 遷移

    fun getMeta(key: String): String? = db.tx { conn ->
        conn.prepareStatement("SELECT meta_value FROM addon_meta WHERE meta_key = ?").use { stmt ->
            stmt.setString(1, key)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }
    }

    fun setMeta(key: String, value: String) {
        db.tx { conn ->
            conn.prepareStatement(
                "INSERT INTO addon_meta(meta_key, meta_value) VALUES (?, ?) ON CONFLICT(meta_key) DO UPDATE SET meta_value=excluded.meta_value",
            ).use { stmt ->
                stmt.setString(1, key)
                stmt.setString(2, value)
                stmt.executeUpdate()
            }
        }
    }

    /**
     * schema v1 → v2。可重跑:每一步各自有 meta 旗標,做過就跳過。
     *
     * 1. 備份:有資料才用 `VACUUM INTO` 留一份 `data.db.bak-v1-<時間>`(回退只需換回舊 jar,見 README)。
     * 2. LycoServerTweaks 匯入旗標:v1 每次啟動都 `INSERT OR IGNORE` 舊表、還把舊值無條件塞回快取,
     *    玩家關掉的設定(v1 是刪列)下次重啟就復活。DB 是舊版建的,代表舊版早就匯入過——直接標記完成,
     *    不再匯入。
     * 3. 子花域殘留:v1 的事件路徑一律把子花域換成主花域,掛在子花域 id 上的布林列從來沒有生效過
     *    (只有 `/cadmin set <子id>` 這類路徑寫得出來)。v2 起它們會生效,所以必須刪掉才能保持行為不變。
     *    需要 GP 回答哪些 id 是子花域;[classify] 為 null(GP 不可用)時這一步留到下次啟動。
     *
     * @return 刪掉的子花域殘留列數(-1 = 這次沒做)
     */
    fun migrateToV2(backupDir: File?, classify: ((Long) -> ClaimIdKind)?): Int {
        if (getMeta(META_LST_DONE) == null && preExistingDatabase) {
            setMeta(META_LST_DONE, "v1-imported-every-start")
        }
        var removed = -1
        if (getMeta(META_V2_SUBDIVISION_CLEANUP) == null && classify != null) {
            val rows = db.tx { conn -> countRows(conn) }
            if (rows > 0 && backupDir != null) backup(backupDir, "v1")
            removed = 0
            val candidates = db.tx { conn ->
                val out = ArrayList<Pair<Long, String>>()
                conn.prepareStatement("SELECT claim_id, setting_key FROM claim_settings").use { stmt ->
                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            val key = rs.getString(2)
                            if (key == ClaimSettingsKeys.ALIAS || key == ClaimSettingsKeys.TP_POINT) continue
                            out += rs.getLong(1) to key
                        }
                    }
                }
                out
            }
            val stale = candidates.filter { (id, _) -> classify(id) == ClaimIdKind.SUBDIVISION }
            if (stale.isNotEmpty()) {
                db.tx { conn ->
                    conn.prepareStatement("DELETE FROM claim_settings WHERE claim_id = ? AND setting_key = ?").use { stmt ->
                        for ((id, key) in stale) {
                            stmt.setLong(1, id)
                            stmt.setString(2, key)
                            stmt.addBatch()
                        }
                        stmt.executeBatch()
                    }
                }
                removed = stale.size
                logger?.info(
                    "[GriefPreventionAddon] schema v2:移除 ${stale.size} 筆從未生效的子花域殘留設定 " +
                        stale.joinToString(limit = 20) { "${it.second}#${it.first}" },
                )
            }
            setMeta(META_V2_SUBDIVISION_CLEANUP, System.currentTimeMillis().toString())
        }
        setMeta(META_SCHEMA_VERSION, "2")
        reloadCache()
        return removed
    }

    /**
     * 從 LycoServerTweaks 的 data.db 匯入舊版 claim_toggle 表。**一輩子只做一次**(meta 旗標),
     * 而且 `INSERT OR IGNORE`:新表已有的明確值(包含明確 false)不會被舊資料覆蓋。
     * 匯入後重建快取,快取一定等於 DB。
     */
    fun migrateFromLycoServerTweaks(lstDbFile: File): Int {
        if (getMeta(META_LST_DONE) != null) return 0
        if (!lstDbFile.exists()) return 0
        var migratedCount = 0
        runCatching {
            DriverManager.getConnection("jdbc:sqlite:${lstDbFile.absolutePath}").use { lstConn ->
                val tableExists = tableExists(lstConn, "claim_toggle")
                if (tableExists) {
                    val rows = ArrayList<Pair<Long, String>>()
                    lstConn.prepareStatement("SELECT claim_id, key FROM claim_toggle").use { stmt ->
                        stmt.executeQuery().use { rs ->
                            while (rs.next()) rows += rs.getLong("claim_id") to rs.getString("key")
                        }
                    }
                    val now = System.currentTimeMillis()
                    db.tx { conn ->
                        conn.prepareStatement(
                            "INSERT OR IGNORE INTO claim_settings(claim_id, setting_key, setting_value, updated_at) VALUES (?, ?, 'true', ?)",
                        ).use { insertStmt ->
                            for ((claimId, key) in rows) {
                                insertStmt.setLong(1, claimId)
                                insertStmt.setString(2, key)
                                insertStmt.setLong(3, now)
                                insertStmt.addBatch()
                            }
                            migratedCount = insertStmt.executeBatch().count { it > 0 }
                        }
                    }
                }
            }
            setMeta(META_LST_DONE, System.currentTimeMillis().toString())
            reloadCache()
            if (migratedCount > 0) {
                logger?.info("[GriefPreventionAddon] 成功從 LycoServerTweaks 遷移 $migratedCount 筆領地開關設定。")
            }
        }.onFailure { e ->
            logger?.warning("[GriefPreventionAddon] 從 LycoServerTweaks 遷移資料時發生錯誤: ${e.message}")
        }
        return migratedCount
    }

    private fun backup(dir: File, tag: String) {
        runCatching {
            dir.mkdirs()
            val target = File(dir, "data.db.bak-$tag-${System.currentTimeMillis()}")
            db.tx { conn ->
                conn.createStatement().use { it.executeUpdate("VACUUM INTO '${target.absolutePath.replace("'", "''")}'") }
            }
            logger?.info("[GriefPreventionAddon] 遷移前備份:${target.name}")
        }.onFailure { logger?.warning("[GriefPreventionAddon] 遷移前備份失敗(遷移照常進行): ${it.message}") }
    }

    private fun countRows(conn: Connection): Int =
        conn.createStatement().use { st -> st.executeQuery("SELECT COUNT(*) FROM claim_settings").use { if (it.next()) it.getInt(1) else 0 } }

    private fun tableExists(conn: Connection, table: String): Boolean =
        conn.prepareStatement("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?").use { stmt ->
            stmt.setString(1, table)
            stmt.executeQuery().use { it.next() }
        }

    private fun upsert(conn: Connection, claimId: Long, key: String, value: String, now: Long) {
        val sql = "INSERT INTO claim_settings(claim_id, setting_key, setting_value, updated_at) VALUES (?, ?, ?, ?) " +
            "ON CONFLICT(claim_id, setting_key) DO UPDATE SET setting_value=excluded.setting_value, updated_at=excluded.updated_at"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, claimId)
            stmt.setString(2, key)
            stmt.setString(3, value)
            stmt.setLong(4, now)
            stmt.executeUpdate()
        }
    }

    private fun deleteKey(conn: Connection, claimId: Long, key: String) {
        conn.prepareStatement("DELETE FROM claim_settings WHERE claim_id = ? AND setting_key = ?").use { stmt ->
            stmt.setLong(1, claimId)
            stmt.setString(2, key)
            stmt.executeUpdate()
        }
    }

    companion object {
        const val META_SCHEMA_VERSION = "schema_version"
        const val META_LST_DONE = "lst_import_done"
        const val META_V2_SUBDIVISION_CLEANUP = "v2_subdivision_cleanup"

        fun parseBoolean(value: String?): Boolean? = when {
            value.equals("true", ignoreCase = true) -> true
            value.equals("false", ignoreCase = true) -> false
            else -> null
        }
    }
}
