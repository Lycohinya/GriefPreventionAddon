package com.tinyyana.griefPreventionAddon.toggle

import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.policy.ClaimFlag
import com.tinyyana.griefPreventionAddon.policy.ClaimFlags
import com.tinyyana.griefPreventionAddon.policy.ClaimPolicyResolver
import com.tinyyana.griefPreventionAddon.policy.ClaimScope
import me.ryanhamshire.GriefPrevention.events.PreventPvPEvent
import org.bukkit.Location
import org.bukkit.entity.AbstractCubeMob
import org.bukkit.entity.Ambient
import org.bukkit.entity.Enemy
import org.bukkit.entity.Entity
import org.bukkit.entity.Phantom
import org.bukkit.entity.Player
import org.bukkit.entity.Raider
import org.bukkit.entity.SpawnCategory
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
 *      事件帶的 claim 是 GP 查到的精確範圍(子花域就是子花域),直接拿去解析。
 * 生物: /cmob 一類一個開關。最具體優先，只擋「自然生成類」來源(BLOCKED_REASONS)。
 *      範圍是生成點(夜魅用錨點)忽略高度的精確範圍;子花域沒設定時跟主花域。
 */
class ClaimToggleListener(
    private val bridge: GriefPreventionBridge,
    private val resolver: ClaimPolicyResolver,
) : Listener {

    @EventHandler(ignoreCancelled = true)
    fun onPreventPvp(event: PreventPvPEvent) {
        if (event.defender !is Player) return
        val claim = event.claim ?: return
        if (resolver.allows(ClaimFlags.PVP, bridge.scopeOf(claim))) {
            event.isCancelled = true
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onCreatureSpawn(event: CreatureSpawnEvent) {
        // GP 是否存在的檢查在 getClaimAtIgnoringHeight 內，只有真的要查領地時才付出成本
        val cancel = shouldCancelSpawn(
            reason = event.spawnReason,
            entity = event.entity,
            mayBlock = { flag -> resolver.mayResolveTo(flag, !flag.allowStored) },
            scopeAt = {
                val anchor = anchorLocation(event.entity, event.location)
                bridge.getClaimAtIgnoringHeight(anchor)?.let(bridge::scopeOf)
            },
            isBlocked = { flag, scope -> !resolver.allows(flag, scope) },
        )
        if (cancel) {
            event.isCancelled = true
        }
    }

    /**
     * 夜魅生在玩家頭頂高空，落點常在領地外；改用半徑內最近玩家的座標當判定錨點。
     *
     * Folia 注意事項：這裡只讀 [org.bukkit.World.getPlayers] 的快照與各玩家的座標欄位，
     * 不呼叫 `getNearbyEntities` / `getNearbyPlayers` 這類會做 region 所有權檢查的實體查詢 API，
     * 免得夜魅生在 region 邊界時整個生怪流程被例外打斷。仍整段包 runCatching，
     * 任何讀取失敗都退回生成點本身，過濾最多失準一次，不會影響生成。
     */
    private fun anchorLocation(entity: Entity, spawnLocation: Location): Location {
        if (entity !is Phantom) return spawnLocation
        return runCatching {
            val nearbyPlayers = spawnLocation.world?.players ?: return spawnLocation
            if (nearbyPlayers.isEmpty()) return spawnLocation
            val spawn = Point3D(spawnLocation.x, spawnLocation.y, spawnLocation.z)
            val points = nearbyPlayers.map { Point3D(it.location.x, it.location.y, it.location.z) }
            val index = nearestWithinRadius(spawn, points, PHANTOM_ANCHOR_RADIUS) ?: return spawnLocation
            nearbyPlayers[index].location
        }.getOrDefault(spawnLocation)
    }

    companion object {
        /**
         * 要攔的來源＝世界自己跑出來的生怪。判準：不是玩家蓋的設施、不是繁殖、也不是既有生物轉化。
         *
         * 明確放行(README 承諾或語意上不該被 /cmob 影響)：
         * - 玩家設施與指令：SPAWNER、TRIAL_SPAWNER、SPAWNER_EGG、EGG、DISPENSE_EGG、
         *   OMINOUS_ITEM_SPAWNER、COMMAND、CUSTOM、DEFAULT、SILVERFISH_BLOCK、BUILD_*、VILLAGE_DEFENSE
         * - 繁殖與農場產出：BREEDING、OCELOT_BABY、DUPLICATION、BEEHIVE、SHEARED、BUCKET、
         *   MOUNT、SHOULDER_ENTITY、ENDER_PEARL
         * - 既有生物轉化(擋了會讓原生物卡住，不是新增生怪)：INFECTION、CURED、DROWNED、FROZEN、
         *   PIGLIN_ZOMBIFIED、METAMORPHOSIS、REANIMATE、REHYDRATION、LIGHTNING、SPELL
         *
         * CHUNK_GEN 在 Paper 26.2 已標記 @Deprecated 且不再發事件(區塊生成的生物不走 CreatureSpawnEvent)，
         * 因此不列入；區塊載入時就存在的既有生物同樣沒有事件可攔，屬於 /cmob 涵蓋範圍外。
         */
        private val BLOCKED_REASONS: Set<CreatureSpawnEvent.SpawnReason> = EnumSet.of(
            CreatureSpawnEvent.SpawnReason.NATURAL,
            CreatureSpawnEvent.SpawnReason.PATROL,
            CreatureSpawnEvent.SpawnReason.RAID,
            CreatureSpawnEvent.SpawnReason.VILLAGE_INVASION,
            CreatureSpawnEvent.SpawnReason.REINFORCEMENTS,
            CreatureSpawnEvent.SpawnReason.JOCKEY,
            CreatureSpawnEvent.SpawnReason.SLIME_SPLIT,
            CreatureSpawnEvent.SpawnReason.NETHER_PORTAL,
            CreatureSpawnEvent.SpawnReason.TRAP,
        )

        const val PHANTOM_ANCHOR_RADIUS = 40.0

        /** 這個生成來源是否算「世界自己跑出來的生怪」，見 [BLOCKED_REASONS]。 */
        fun isNaturalSpawn(reason: CreatureSpawnEvent.SpawnReason): Boolean = reason in BLOCKED_REASONS

        /**
         * /cmob 的完整判斷：來源→類別→全服快篩→範圍→該範圍有效值。與 Bukkit 事件解耦以便測試。
         *
         * [scopeAt] 只有在真的需要查領地時才會被呼叫(夜魅錨點與 GP 查詢都在裡面)，
         * 全服沒有任何範圍可能禁止這一類時完全不查領地([mayBlock] 只看記憶體)。
         */
        fun shouldCancelSpawn(
            reason: CreatureSpawnEvent.SpawnReason,
            entity: Entity,
            mayBlock: (ClaimFlag) -> Boolean,
            scopeAt: () -> ClaimScope?,
            isBlocked: (ClaimFlag, ClaimScope) -> Boolean,
        ): Boolean {
            if (!isNaturalSpawn(reason)) return false
            val flag = categoryFlag(entity) ?: return false
            if (!mayBlock(flag)) return false
            val scope = scopeAt() ?: return false
            return isBlocked(flag, scope)
        }

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

        /**
         * 把生物歸到 /cmob 的五類；最具體優先，歸不到就回 null(不干涉)。
         *
         * 型別判定搭配伺服器端的 [Entity.getSpawnCategory]，不單靠介面繼承：
         * - Paper 26.2 起 [org.bukkit.entity.Slime] 與 [org.bukkit.entity.MagmaCube] 是
         *   [AbstractCubeMob] 的平行子介面，岩漿立方怪不再 is-a 史萊姆。改判 [AbstractCubeMob]
         *   才能一併涵蓋兩者與日後新增的立方生物。
         * - 部分敵對生物沒有實作 [Enemy] 標記介面(例如 26.2 從既有動物型別衍生的殭屍變種)，
         *   補上 [SpawnCategory.MONSTER] 才不會整類漏掉。
         *
         * 立方生物要同時是敵對的才歸「史萊姆」：可養殖的被動立方生物(硫磺方塊之類)
         * 跟牛羊一樣不受 /cmob 干涉。
         */
        fun categoryFlag(entity: Entity): ClaimFlag? {
            val spawnCategory = entity.spawnCategory
            val hostile = entity is Enemy || spawnCategory == SpawnCategory.MONSTER
            return when {
                entity is Raider -> ClaimFlags.RAIDER
                entity is Phantom -> ClaimFlags.PHANTOM
                entity is AbstractCubeMob && hostile -> ClaimFlags.SLIME
                entity is Ambient || spawnCategory == SpawnCategory.AMBIENT -> ClaimFlags.AMBIENT
                hostile -> ClaimFlags.HOSTILE
                else -> null
            }
        }

        /** 舊名稱:回傳儲存 key(診斷指令與測試用)。 */
        fun categoryKey(entity: Entity): String? = categoryFlag(entity)?.key
    }
}
