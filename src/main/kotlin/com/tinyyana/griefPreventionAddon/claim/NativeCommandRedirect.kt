package com.tinyyana.griefPreventionAddon.claim

import com.tinyyana.griefPreventionAddon.display.FlagText
import com.tinyyana.griefPreventionAddon.i18n.LanguageManager
import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.policy.ClaimFlags
import com.tinyyana.griefPreventionAddon.policy.ClaimSettingsService
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandPreprocessEvent

/**
 * GP 原生 `/claimexplosions` 會直接翻 GP 記憶體裡的 `areExplosivesAllowed`(不存檔、重啟就消失),
 * 跟 addon 保存的 TNT 規則各說各話。改成走同一個寫入口:翻轉腳下精確範圍的 TNT 規則並同步 GP。
 */
class NativeCommandRedirect(
    private val bridge: GriefPreventionBridge,
    private val settings: ClaimSettingsService,
    private val lang: LanguageManager,
) : Listener {

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onCommand(event: PlayerCommandPreprocessEvent) {
        val root = event.message.trimStart().removePrefix("/").substringBefore(' ').lowercase()
        if (root !in EXPLOSION_COMMANDS) return
        if (!bridge.isAvailable()) return
        event.isCancelled = true
        val player = event.player
        val claim = bridge.getClaimAt(player.location) ?: run {
            player.sendMessage(lang.get(player, "claim.not-in-claim"))
            return
        }
        FlagText.sendOutcome(lang, player, ClaimFlags.TNT, settings.toggle(player, claim.getID(), ClaimFlags.TNT))
    }

    companion object {
        val EXPLOSION_COMMANDS = setOf(
            "claimexplosions", "claimexplosion",
            "griefprevention:claimexplosions", "griefprevention:claimexplosion",
        )
    }
}
