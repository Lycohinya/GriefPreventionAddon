package com.tinyyana.griefPreventionAddon.claim

import com.tinyyana.griefPreventionAddon.fire.FireTakeover
import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.policy.ClaimPolicyResolver
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
import me.ryanhamshire.GriefPrevention.events.ClaimCreatedEvent
import me.ryanhamshire.GriefPrevention.events.ClaimDeletedEvent
import me.ryanhamshire.GriefPrevention.events.ClaimExpirationEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.event.server.PluginEnableEvent
import org.bukkit.plugin.Plugin

/**
 * 領地與 GP 本身的生命週期。
 *
 * - 刪除/過期:只清**那一個 id**。GP 刪主花域時會先逐一刪子花域、每個都發一次事件,所以刪父自然清到
 *   整棵樹;刪子花域不會碰到父或兄弟。
 * - 建立:新範圍沒有自己的設定(繼承),只需把 TNT 有效值寫進它在 GP 的原生欄位;
 *   新建子花域**不會**反過來改主花域(v1 會把整棵樹覆蓋成同一個值)。
 * - 縮放/移交:設定綁 claim id,id 不變所以資料不動;選單每次操作都重新檢查權限,舊視窗無法再寫。
 * - GP 停用/重新啟用:火焰接管與 TNT 原生欄位跟著重建(新 GP 實例的 claim 物件都是新的)。
 */
class ClaimLifecycleListener(
    private val plugin: Plugin,
    private val store: ClaimSettingsStore,
    private val bridge: GriefPreventionBridge,
    private val resolver: ClaimPolicyResolver,
    private val fire: FireTakeover?,
) : Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onClaimDeleted(event: ClaimDeletedEvent) {
        val claimId = event.claim.id ?: return
        store.purgeClaim(claimId)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onClaimExpired(event: ClaimExpirationEvent) {
        val claimId = event.claim.id ?: return
        store.purgeClaim(claimId)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onClaimCreated(event: ClaimCreatedEvent) {
        // GP 發事件時還沒把新範圍加進 dataStore(事件可能被取消);下一個 tick 確認真的加入了再同步
        val claim = event.claim
        val sync = Runnable { if (claim.inDataStore) bridge.syncExplosivesTree(claim, resolver) }
        val world = claim.lesserBoundaryCorner?.world
        runCatching {
            if (world != null) {
                plugin.server.regionScheduler.run(plugin, claim.lesserBoundaryCorner) { sync.run() }
            } else {
                plugin.server.globalRegionScheduler.run(plugin) { sync.run() }
            }
        }.onFailure { sync.run() }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPluginEnable(event: PluginEnableEvent) {
        if (event.plugin.name != "GriefPrevention") return
        plugin.server.globalRegionScheduler.run(plugin) {
            bridge.takeOverSethomeCommands()
            bridge.syncAllExplosives(resolver)
            fire?.takeOver()
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPluginDisable(event: PluginDisableEvent) {
        if (event.plugin.name != "GriefPrevention") return
        fire?.onGpDisabled()
    }
}
