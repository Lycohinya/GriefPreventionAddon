package com.tinyyana.griefPreventionAddon.claim

import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
import me.ryanhamshire.GriefPrevention.events.ClaimCreatedEvent
import me.ryanhamshire.GriefPrevention.events.ClaimDeletedEvent
import me.ryanhamshire.GriefPrevention.events.ClaimExpirationEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

/**
 * 監聽 GriefPrevention 領地生命週期事件。
 * 領地刪除/放棄/過期時自動清除殘留的領地設定，領地建立時同步 TNT 等防護設定。
 */
class ClaimLifecycleListener(
    private val store: ClaimSettingsStore,
    private val bridge: GriefPreventionBridge,
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
        val claim = event.claim
        val claimId = claim.id ?: return
        val allowed = store.isTntAllowed(claimId)
        bridge.syncClaimExplosives(claim, allowed)
    }
}
