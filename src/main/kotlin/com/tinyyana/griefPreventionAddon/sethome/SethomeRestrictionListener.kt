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
 * Listener for controlling /sethome execution inside claims based on per-claim settings.
 */
class SethomeRestrictionListener(
    private val bridge: GriefPreventionBridge,
    private val store: ClaimSettingsStore,
    private val lang: LanguageManager,
) : Listener {

    companion object {
        private val SETHOME_COMMANDS = setOf(
            "sethome",
            "esethome",
            "essentials:sethome",
            "essentials:esethome",
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
        val claim = bridge.getTopClaim(player.location) ?: return
        val claimId = claim.id ?: return

        // 1. Claim owner is always allowed
        val isOwner = claim.ownerID != null && claim.ownerID == player.uniqueId
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

        // 4. Admin claims: allow if not explicitly restricted or if admin permits
        if (claim.isAdminClaim()) {
            if (!store.isSethomeAllowed(claimId)) {
                // By default admin claims may follow server policy
                return
            }
        }

        // 5. Check per-claim setting for untrusted guests
        val allowed = store.isSethomeAllowed(claimId)
        if (!allowed) {
            event.isCancelled = true
            val ownerName = claim.ownerName ?: lang.raw(player, "gui.owner-admin") ?: "Owner"
            player.sendMessage(lang.get(player, "sethome.blocked-in-claim", "owner" to ownerName))
        }
    }
}
