package com.tinyyana.griefPreventionAddon.teleport

import org.bukkit.Bukkit
import org.bukkit.Location

/**
 * 花域自訂落腳點(玩家指定 `/ctp` 要傳到花域裡的哪個位置,而不是預設的中心地面)。
 *
 * 存進 `claim_settings` 時序列化成一行字串,格式:`world;x;y;z;yaw;pitch`。
 * 分隔符用 `;` 而不是 `,`——世界名稱不會有分號,但可能有逗號或空白。
 * 編解碼刻意寫成純函式(不碰 Bukkit),這樣單元測試不需要伺服器環境。
 */
data class ClaimSpawnPoint(
    val worldName: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float = 0f,
    val pitch: Float = 0f,
) {
    fun encode(): String = listOf(worldName, x, y, z, yaw, pitch).joinToString(";")

    /** 轉成 Bukkit 位置;世界已經不存在(被刪掉的維度)時回傳 null,由呼叫端退回中心 */
    fun toLocation(): Location? {
        val world = Bukkit.getWorld(worldName) ?: return null
        return Location(world, x, y, z, yaw, pitch)
    }

    companion object {
        /** 解析儲存字串;格式不對就回傳 null(不丟例外——壞掉的一筆資料不該讓整個花域傳不了) */
        fun decode(raw: String?): ClaimSpawnPoint? {
            val parts = raw?.trim()?.split(";") ?: return null
            if (parts.size < 4) return null
            val world = parts[0].takeIf { it.isNotBlank() } ?: return null
            val x = parts[1].toDoubleOrNull() ?: return null
            val y = parts[2].toDoubleOrNull() ?: return null
            val z = parts[3].toDoubleOrNull() ?: return null
            val yaw = parts.getOrNull(4)?.toFloatOrNull() ?: 0f
            val pitch = parts.getOrNull(5)?.toFloatOrNull() ?: 0f
            return ClaimSpawnPoint(world, x, y, z, yaw, pitch)
        }

        fun fromLocation(location: Location): ClaimSpawnPoint? {
            val world = location.world ?: return null
            return ClaimSpawnPoint(world.name, location.x, location.y, location.z, location.yaw, location.pitch)
        }
    }
}
