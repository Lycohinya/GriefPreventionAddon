package com.tinyyana.griefPreventionAddon.toggle

import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsKeys
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
import me.ryanhamshire.GriefPrevention.events.PreventPvPEvent
import org.bukkit.Location
import org.bukkit.entity.Ambient
import org.bukkit.entity.Enemy
import org.bukkit.entity.Entity
import org.bukkit.entity.Phantom
import org.bukkit.entity.Player
import org.bukkit.entity.Raider
import org.bukkit.entity.Slime
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.CreatureSpawnEvent
import java.util.EnumSet

/** 純資料座標，供夜魅錨點計算脫離 Bukkit 型別、可離線單元測試。 */
data class Point3D(val x: Double, val y: Double, val z: Double) {
    fun distanceSquaredTo(o: Point3D): Double {
        val dx = x - o.x
        val dy = y - o.y
        val dz = z - o.z
        return dx * dx + dy * dy + dz * dz
    }
}

/**
 * 領地開關生效端(PVP 放行與生物生成攔截)。
 *
 * PVP: GP 要保護花域內玩家前會發 Cancellable 的 PreventPvPEvent，取消它=放行這次攻擊。
 * 生物: /cmob 一類一個開關。最具體優先，只擋「自然生成類」來源(BLOCKED_REASONS)。
 */
class ClaimToggleListener(
    private val bridge: GriefPreventionBridge,
    private val store: ClaimSettingsStore,
) : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onPreventPvp(event: PreventPvPEvent) {
        if (event.defender !is Player) return
        val claim = event.claim ?: return
        val top = claim.parent ?: claim
        val id = top.id ?: return
        if (store.isPvpAllowed(id)) {
            event.isCancelled = true
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onCreatureSpawn(event: CreatureSpawnEvent) {
        if (event.spawnReason !in BLOCKED_REASONS) return
        val key = categoryKey(event.entity) ?: return
        // 全服都沒有花域開這一類 -> 完全不查領地直接返回(高頻路徑保護)
        if (!store.anyEnabled(key)) return
        if (!bridge.isAvailable()) return

        val world = event.location.world ?: return
        val anchor = anchorLocation(event.entity, event.location, world.players)
        val claim = bridge.getTopClaim(anchor) ?: return
        val id = claim.id ?: return
        if (store.isMobSpawnBlocked(key, id)) {
            event.isCancelled = true
        }
    }

    private fun anchorLocation(entity: Entity, spawnLocation: Location, nearbyPlayers: Collection<Player>): Location {
        if (entity !is Phantom) return spawnLocation
        val spawn = Point3D(spawnLocation.x, spawnLocation.y, spawnLocation.z)
        val points = nearbyPlayers.map { Point3D(it.location.x, it.location.y, it.location.z) }
        val index = nearestWithinRadius(spawn, points, PHANTOM_ANCHOR_RADIUS) ?: return spawnLocation
        return nearbyPlayers.elementAt(index).location
    }

    companion object {
        private val BLOCKED_REASONS: Set<CreatureSpawnEvent.SpawnReason> = EnumSet.of(
            CreatureSpawnEvent.SpawnReason.NATURAL,
            CreatureSpawnEvent.SpawnReason.PATROL,
            CreatureSpawnEvent.SpawnReason.RAID,
            CreatureSpawnEvent.SpawnReason.REINFORCEMENTS,
            CreatureSpawnEvent.SpawnReason.JOCKEY,
            CreatureSpawnEvent.SpawnReason.SLIME_SPLIT,
        )

        const val PHANTOM_ANCHOR_RADIUS = 40.0

        fun nearestWithinRadius(spawn: Point3D, candidates: List<Point3D>, radius: Double): Int? {
            val radiusSq = radius * radius
            var bestIndex: Int? = null
            var bestDistSq = Double.MAX_VALUE
            candidates.forEachIndexed { i, p ->
                val d = spawn.distanceSquaredTo(p)
                if (d <= radiusSq && d < bestDistSq) {
                    bestDistSq = d
                    bestIndex = i
                }
            }
            return bestIndex
        }

        fun categoryKey(entity: Entity): String? = when (entity) {
            is Raider -> ClaimSettingsKeys.NO_RAIDER_SPAWN
            is Phantom -> ClaimSettingsKeys.NO_PHANTOM_SPAWN
            is Slime -> ClaimSettingsKeys.NO_SLIME_SPAWN
            is Ambient -> ClaimSettingsKeys.NO_AMBIENT_SPAWN
            is Enemy -> ClaimSettingsKeys.NO_HOSTILE_SPAWN
            else -> null
        }
    }
}
