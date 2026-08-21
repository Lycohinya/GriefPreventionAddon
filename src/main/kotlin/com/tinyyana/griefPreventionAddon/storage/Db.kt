package com.tinyyana.griefPreventionAddon.storage

import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * 全插件共用的單一 SQLite 連線(WAL mode + busy_timeout)。
 */
class Db(file: File) : AutoCloseable {

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

    @Synchronized
    fun <T> tx(block: (Connection) -> T): T = block(conn)

    @Synchronized
    override fun close() {
        runCatching { conn.close() }
    }
}
