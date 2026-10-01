package com.tinyyana.griefPreventionAddon.storage

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger

/**
 * 全插件共用的單一 SQLite 連線(WAL mode + busy_timeout)。
 *
 * 寫入有兩條路:
 * - [tx]:同步,給啟動時的建表/遷移與單元測試用。
 * - [write]:丟到單一寫入執行緒依序執行。玩家點選單、打指令改設定時走這條——快取已經先更新,
 *   tick/region 執行緒不必等 SQLite(s01 的資料在 HDD 上,一次 fsync 就是好幾 ms,Folia 下等於讓那個
 *   region 停住)。單一執行緒保證同一筆設定的多次寫入照順序落地。
 */
class Db(file: File, private val logger: Logger? = null) : AutoCloseable {

    private val conn: Connection = run {
        file.parentFile?.mkdirs()
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}")
    }.apply {
        createStatement().use {
            it.execute("PRAGMA journal_mode=WAL")
            it.execute("PRAGMA synchronous=NORMAL")
            it.execute("PRAGMA busy_timeout=3000")
        }
    }

    private val writer: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "GriefPreventionAddon-db").apply { isDaemon = true }
    }

    @Synchronized
    fun <T> tx(block: (Connection) -> T): T = block(conn)

    /** 依序在寫入執行緒上執行;失敗只記 log(快取已是新值,下次寫入或重啟會以 DB 為準)。 */
    fun write(label: String, block: (Connection) -> Unit) {
        if (writer.isShutdown) {
            // 關閉流程中仍有人寫(例如 onDisable 前最後一次點擊):直接同步落地,不丟資料
            runCatching { tx(block) }.onFailure { logger?.log(Level.WARNING, "[GriefPreventionAddon] DB write '$label' failed", it) }
            return
        }
        writer.execute {
            runCatching { tx(block) }.onFailure { logger?.log(Level.WARNING, "[GriefPreventionAddon] DB write '$label' failed", it) }
        }
    }

    /** 等寫入佇列清空(測試與關閉用)。 */
    fun flush(timeoutSeconds: Long = 10) {
        if (writer.isShutdown) return
        val done = java.util.concurrent.CountDownLatch(1)
        writer.execute { done.countDown() }
        done.await(timeoutSeconds, TimeUnit.SECONDS)
    }

    override fun close() {
        writer.shutdown()
        runCatching { writer.awaitTermination(10, TimeUnit.SECONDS) }
        synchronized(this) {
            runCatching { conn.close() }
        }
    }
}
