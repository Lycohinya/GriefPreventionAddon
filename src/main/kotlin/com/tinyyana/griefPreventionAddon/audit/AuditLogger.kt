package com.tinyyana.griefPreventionAddon.audit

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.logging.Logger

/**
 * Lightweight standalone audit logger for GriefPreventionAddon.
 * Writes formatted actions to `logs/audit-YYYY-MM-DD.log`.
 */
class AuditLogger(
    private val logDirectory: File,
    private val hostLogger: Logger? = null,
) {
    private var writeFailed = false

    init {
        if (!logDirectory.exists()) {
            logDirectory.mkdirs()
        }
    }

    @Synchronized
    fun log(player: String?, action: String, detail: String = "") {
        val time = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val line = "$time\t[GriefPreventionAddon]\t${player ?: "-"}\t$action\t$detail\n"
        runCatching {
            val file = File(logDirectory, "audit-${LocalDate.now()}.log")
            Files.write(
                file.toPath(),
                line.toByteArray(Charsets.UTF_8),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
            )
        }.onFailure { e ->
            if (!writeFailed) {
                writeFailed = true
                hostLogger?.warning("[GriefPreventionAddon] Audit log write failed: ${e.message}")
            }
        }
    }
}
