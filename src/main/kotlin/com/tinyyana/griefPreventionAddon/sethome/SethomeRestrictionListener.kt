package com.tinyyana.griefPreventionAddon.sethome

import com.tinyyana.griefPreventionAddon.i18n.LanguageManager
import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandPreprocessEvent

/**
 * 監聽玩家指令，依據領地 per-claim 設定控制 /sethome 在領地內的執行權限。
 */
class SethomeRestrictionListener(
    private val bridge: GriefPreventionBridge,
    private val store: ClaimSettingsStore,
    private val lang: LanguageManager,
) : Listener {

    companion object {
        val SETHOME_COMMANDS = setOf(
            "sethome",
            "esethome",
            "createhome",
            "ecreatehome",
            "essentials:sethome",
            "essentials:esethome",
            "essentials:createhome",
            "essentials:ecreatehome",
        )

        fun isSethomeCommand(rawMessage: String): Boolean {
            val trimmed = rawMessage.trimStart().removePrefix("/").trim()
            val cmdRoot = trimmed.split("\\s+".toRegex(), limit = 2).firstOrNull()?.lowercase() ?: return false
            return cmdRoot in SETHOME_COMMANDS
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onPlayerCommandPreprocess(event: PlayerCommandPreprocessEvent) {
        if (!isSethomeCommand(event.message)) return
        if (!bridge.isAvailable()) return

        val player = event.player
        val claim = bridge.getClaimAt(player.location) ?: return
        val topClaim = claim.parent ?: claim
        val claimId = topClaim.id ?: return

        // 1. Claim owner is always allowed (both top claim owner and subdivision owner)
        val isOwner = (topClaim.ownerID != null && topClaim.ownerID == player.uniqueId) ||
            (claim.ownerID != null && claim.ownerID == player.uniqueId)
        if (isOwner) return

        // 2. Players with Access Trust in this claim are allowed
        if (claim.allowAccess(player) == null) return

        // 3. Players with admin or bypass permissions are allowed
        if (player.hasPermission("griefpreventionaddon.sethome.others") ||
            player.hasPermission("griefpreventionaddon.admin") ||
            player.hasPermission("griefprevention.ignoreclaims") ||
            player.isOp
        ) {
            return
        }

        // 4. Check per-claim setting for untrusted guests (both player claims and admin claims)
        val allowed = store.isSethomeAllowed(claimId)
        if (!allowed) {
            event.isCancelled = true
            val ownerName = topClaim.ownerName ?: lang.raw(player, "gui.owner-admin") ?: "Owner"
            player.sendMessage(lang.get(player, "sethome.blocked-in-claim", "owner" to ownerName))
        }
    }
}
