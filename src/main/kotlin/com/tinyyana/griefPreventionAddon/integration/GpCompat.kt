package com.tinyyana.griefPreventionAddon.integration

import me.ryanhamshire.GriefPrevention.ClaimPermission

/**
 * 編譯期與執行期的 GP 版本不同:CodeMC 只有 16.18.2-SNAPSHOT(容器信任叫 `Inventory`),
 * 正式服與 s01 跑 16.18.7(改名 `Container`,`Inventory` 留作 deprecated 別名)。
 * 用名字在執行期取,兩個版本都對。
 */
object GpCompat {
    val CONTAINER: ClaimPermission = runCatching { ClaimPermission.valueOf("Container") }.getOrElse { ClaimPermission.valueOf("Inventory") }

    /** 語系檔的權限 key:build/container/access/manage(舊名 Inventory 一律當 container)。 */
    fun levelKey(level: ClaimPermission): String = when (level.name) {
        "Inventory", "Container" -> "container"
        else -> level.name.lowercase()
    }
}
