package com.tinyyana.griefPreventionAddon.tnt

import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.policy.ClaimFlags
import com.tinyyana.griefPreventionAddon.policy.ClaimPolicyResolver
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.entity.Entity
import org.bukkit.entity.TNTPrimed
import org.bukkit.entity.minecart.ExplosiveMinecart
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.entity.EntityExplodeEvent

/**
 * 領地 TNT 爆炸方塊防護監聽器。
 *
 * 核心原則:
 * 1. 依方塊所在位置的**精確範圍**(子花域就是子花域)的 TNT 有效值判斷;子花域沒設定時跟主花域。
 *    GP 前段也是逐方塊查精確範圍的 `areExplosivesAllowed`,兩邊由 [GriefPreventionBridge.syncExplosives] 對齊。
 * 2. 領地內/外邊界保護:
 *    - TNT 位於領地外爆炸但範圍波及禁止領地: 僅禁止領地內的受影響方塊受到保護(從 blockList 移除)，外側荒野方塊正常破壞。
 *    - TNT 位於禁止領地內爆炸: 領地內所有方塊受到保護，若爆炸波及外側荒野或允許領地，外側方塊正常破壞。
 *    - 相鄰的允許領地 A 與禁止領地 B: 僅 B 內方塊受到保護，A 內方塊正常破壞。
 * 3. 非 TNT 爆炸源 (如 Creeper、Wither、Ghast 等) 一律保護所有領地方塊，避免因開啟 TNT 破壞而導致苦力怕等怪物破壞領地。
 * 4. 涵蓋 TNTPrimed、ExplosiveMinecart (TNT 礦車) 等相關 TNT 爆炸源。
 */
class TntExplosionListener(
    private val bridge: GriefPreventionBridge,
    private val resolver: ClaimPolicyResolver,
) : Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) {
        if (event.blockList().isEmpty()) return
        if (!bridge.isAvailable()) return

        val isTnt = isTntSource(event.entity)

        if (!isTnt) {
            // 非 TNT 實體爆炸 (例如 Creeper, Wither, Ghast, 終界水晶等)
            // 保護所有領地內的方塊，避免因領地開了 TNT 而遭苦力怕等破壞
            event.blockList().removeIf { block ->
                bridge.getClaimAt(block.location) != null
            }
            return
        }

        // TNT 爆炸源 (TNTPrimed, ExplosiveMinecart):
        // 依各方塊所在領地之 TNT 設定過濾；荒野方塊保留破壞，未允許 TNT 之領地方塊予以保護
        filterExplosionBlocks(event.blockList())
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockExplode(event: BlockExplodeEvent) {
        if (event.blockList().isEmpty()) return
        if (!bridge.isAvailable()) return
        filterExplosionBlocks(event.blockList())
    }

    private fun filterExplosionBlocks(blockList: MutableList<Block>) {
        blockList.removeIf { block ->
            val claim = bridge.getClaimAt(block.location) ?: return@removeIf false
            // 該範圍實際不允許 TNT(預設禁止),從爆炸清單中移除以保護方塊
            !resolver.allows(ClaimFlags.TNT, bridge.scopeOf(claim))
        }
    }

    companion object {
        /** 判斷是否為 TNT 相關的實體爆炸源 */
        fun isTntSource(entity: Entity?): Boolean {
            if (entity == null) return false
            return entity is TNTPrimed || entity is ExplosiveMinecart
        }

        /**
         * 純函式方塊清單過濾邏輯，供單元測試。
         * 回傳被保護(從清單中移除)的方塊數量。
         */
        fun <T> filterBlocks(
            blocks: MutableList<T>,
            locationExtractor: (T) -> Location,
            isAllowedAt: (Location) -> Boolean,
        ): Int {
            var protectedCount = 0
            val iterator = blocks.iterator()
            while (iterator.hasNext()) {
                val item = iterator.next()
                val loc = locationExtractor(item)
                if (!isAllowedAt(loc)) {
                    iterator.remove()
                    protectedCount++
                }
            }
            return protectedCount
        }

        /**
         * 純函式非 TNT 爆炸方塊清單過濾邏輯，供單元測試。
         * 移除所有位於領地內的方塊。
         */
        fun <T> filterNonTntBlocks(
            blocks: MutableList<T>,
            locationExtractor: (T) -> Location,
            isInAnyClaim: (Location) -> Boolean,
        ): Int {
            var protectedCount = 0
            val iterator = blocks.iterator()
            while (iterator.hasNext()) {
                val item = iterator.next()
                val loc = locationExtractor(item)
                if (isInAnyClaim(loc)) {
                    iterator.remove()
                    protectedCount++
                }
            }
            return protectedCount
        }
    }
}
