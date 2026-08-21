package com.tinyyana.griefPreventionAddon.claim

import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
import me.ryanhamshire.GriefPrevention.events.ClaimDeletedEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

/**
 * 監聽 GriefPrevention 領地生命週期事件。
 * 領地刪除/放棄/過期時自動清除殘留的領地設定。
 */
class ClaimLifecycleListener(private val store: ClaimSettingsStore) : Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onClaimDeleted(event: ClaimDeletedEvent) {
        val claimId = event.claim.id ?: return
        store.purgeClaim(claimId)
    }
}
