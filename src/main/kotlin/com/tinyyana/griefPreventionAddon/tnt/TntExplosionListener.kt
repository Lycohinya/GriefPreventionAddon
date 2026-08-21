package com.tinyyana.griefPreventionAddon.tnt

import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
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
 * 1. 精確依據方塊所在位置的頂層花域判斷是否允許 TNT 破壞。
 * 2. 領地內/外邊界保護:
 *    - TNT 位於領地外爆炸但範圍波及禁止領地: 僅禁止領地內的受影響方塊受到保護(從 blockList 移除)，外側荒野方塊正常破壞。
 *    - TNT 位於禁止領地內爆炸: 領地內所有方塊受到保護，若爆炸波及外側荒野或允許領地，外側方塊正常破壞。
 *    - 相鄰的允許領地 A 與禁止領地 B: 僅 B 內方塊受到保護，A 內方塊正常破壞。
 * 3. 涵蓋 TNTPrimed、ExplosiveMinecart (TNT 礦車) 等相關爆炸源。
 */
class TntExplosionListener(
    private val bridge: GriefPreventionBridge,
    private val store: ClaimSettingsStore,
) : Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) {
        if (event.blockList().isEmpty()) return
        if (!isTntSource(event.entity)) return
        filterExplosionBlocks(event.blockList())
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockExplode(event: BlockExplodeEvent) {
        if (event.blockList().isEmpty()) return
        filterExplosionBlocks(event.blockList())
    }

    private fun filterExplosionBlocks(blockList: MutableList<Block>) {
        if (!bridge.isAvailable()) return
        blockList.removeIf { block ->
            val topClaim = bridge.getTopClaim(block.location) ?: return@removeIf false
            val claimId = topClaim.id ?: return@removeIf false
            // 若該領地未允許 TNT (預設 false)，則從爆炸清單中移除以保護方塊
            !store.isTntAllowed(claimId)
        }
    }

    companion object {
        /** 判斷是否為 TNT 相關的實體爆炸源 */
        fun isTntSource(entity: Entity?): Boolean {
            if (entity == null) return true // 無實體時防禦性處理
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
    }
}
