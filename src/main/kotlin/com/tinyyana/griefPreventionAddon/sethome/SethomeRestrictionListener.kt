package com.tinyyana.griefPreventionAddon.sethome

import com.tinyyana.griefPreventionAddon.i18n.LanguageManager
import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.policy.ClaimFlags
import com.tinyyana.griefPreventionAddon.policy.ClaimPolicyResolver
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
    private val resolver: ClaimPolicyResolver,
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

        // 1. Claim owner is always allowed (子花域的主人就是主花域主人)
        val owner = claim.getOwnerID()
        if (owner != null && owner == player.uniqueId) return

        // 2. Players with admin or bypass permissions are allowed
        if (player.hasPermission("griefpreventionaddon.sethome.others") ||
            player.hasPermission("griefpreventionaddon.admin") ||
            player.hasPermission("griefprevention.ignoreclaims") ||
            player.isOp
        ) {
            return
        }

        // 3. 有 GP 信任(通行以上,含子花域繼承主花域的信任)的人看「成員設家」,其他人看「訪客設家」。
        //    兩項都是玩家所在精確範圍的有效值(子花域沒設定時跟主花域)。
        val trusted = claim.allowAccess(player) == null
        val flag = if (trusted) ClaimFlags.MEMBER_SETHOME else ClaimFlags.SETHOME
        if (!resolver.allows(flag, bridge.scopeOf(claim))) {
            event.isCancelled = true
            val ownerName = topClaim.ownerName ?: lang.raw(player, "gui.owner-admin") ?: "Owner"
            player.sendMessage(lang.get(player, if (trusted) "flag.member-sethome.blocked" else "sethome.blocked-in-claim", "owner" to ownerName))
        }
    }
}
