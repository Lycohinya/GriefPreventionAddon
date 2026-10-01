package com.tinyyana.griefPreventionAddon.policy

import com.tinyyana.griefPreventionAddon.audit.AuditLogger
import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
import me.ryanhamshire.GriefPrevention.Claim
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/** 寫入結果;呼叫端照這個回訊息,不自己猜原因。 */
enum class SetOutcome { OK, CLAIM_GONE, NO_PERMISSION, UNAVAILABLE, UNCHANGED }

data class SetResult(val outcome: SetOutcome, val claim: Claim? = null, val resolution: Resolution? = null)

/**
 * 花域規則的**唯一寫入入口**:指令、選單、管理員指令全部走 [set]。
 *
 * 每次寫入都依 id 重新向 GP 取範圍、重新檢查權限——不信任開選單當下的快照(期間可能被刪、被移交、
 * 被縮放)。id 查不到就拒絕,絕不退回寫主花域。寫完負責連動:TNT 同步 GP 原生欄位、PVP 通知受影響的人。
 */
class ClaimSettingsService(
    private val bridge: GriefPreventionBridge,
    private val store: ClaimSettingsStore,
    val resolver: ClaimPolicyResolver,
    private val auditLogger: AuditLogger?,
    /** 火焰接管目前是否生效;沒接管時火焰兩項不可寫(寫了也不會生效)。 */
    private val fireActive: () -> Boolean,
    private val notify: (Player, ClaimFlag, Boolean) -> Unit = { _, _, _ -> },
) {
    fun isAvailable(flag: ClaimFlag): Boolean = !flag.requiresFireTakeover || fireActive()

    fun resolve(flag: ClaimFlag, claim: Claim): Resolution = resolver.resolve(flag, bridge.scopeOf(claim))

    /** 不分項目的「能不能改這塊地的規則」(畫面決定要不要顯示可操作提示用)。 */
    fun canEditAny(sender: CommandSender, claim: Claim): Boolean = isSettingsAdmin(sender) || isOwner(sender, claim)

    /**
     * 能不能改這個範圍的這一項規則。Build/Container/Access/Manage 信任**都不算**——
     * 規則屬於地主(主花域主人;子花域的主人就是主花域主人)與持有管理權限的管理員。
     */
    fun canEdit(sender: CommandSender, claim: Claim, flag: ClaimFlag): Boolean {
        if (isSettingsAdmin(sender)) return true
        if (flag.othersPermission != null && sender.hasPermission(flag.othersPermission)) return true
        if (!isOwner(sender, claim)) return false
        return flag.ownerPermission == null || sender.hasPermission(flag.ownerPermission)
    }

    fun isSettingsAdmin(sender: CommandSender): Boolean =
        sender.isOp || sender.hasPermission("griefpreventionaddon.admin") || sender.hasPermission("griefpreventionaddon.settings.others")

    private fun isOwner(sender: CommandSender, claim: Claim): Boolean {
        val player = sender as? Player ?: return false
        val owner = claim.getOwnerID() ?: return false
        return owner == player.uniqueId
    }

    fun set(sender: CommandSender, claimId: Long, flag: ClaimFlag, state: TriState): SetResult {
        val claim = bridge.getClaim(claimId) ?: return SetResult(SetOutcome.CLAIM_GONE)
        if (!isAvailable(flag)) return SetResult(SetOutcome.UNAVAILABLE, claim)
        if (!canEdit(sender, claim, flag)) return SetResult(SetOutcome.NO_PERMISSION, claim)

        val scope = bridge.scopeOf(claim)
        val stored = flag.storedFor(state)
        if (store.getExplicit(flag.key, scope.id) == stored) {
            return SetResult(SetOutcome.UNCHANGED, claim, resolver.resolve(flag, scope))
        }

        // 受影響的人:這個範圍內的玩家,各自所在的精確範圍改前改後的有效值
        val watchers = if (flag == ClaimFlags.PVP) bridge.playersInside(claim).map { player ->
            val at = bridge.getClaimAt(player.location) ?: claim
            Triple(player, at, resolver.allows(flag, bridge.scopeOf(at)))
        } else emptyList()

        store.setExplicit(flag.key, scope.id, stored)

        if (flag == ClaimFlags.TNT) bridge.syncExplosivesTree(claim, resolver)
        watchers.forEach { (player, at, before) ->
            val after = resolver.allows(flag, bridge.scopeOf(at))
            if (after != before) notify(player, flag, after)
        }
        auditLogger?.log(sender.name, "claim-flag.${flag.key}", "claim=${scope.id} parent=${scope.parentId ?: "-"} state=$state")
        return SetResult(SetOutcome.OK, claim, resolver.resolve(flag, scope))
    }

    /** 沒給參數的指令:把「目前實際值」翻過來,寫成這個範圍自己的明確值。 */
    fun toggle(sender: CommandSender, claimId: Long, flag: ClaimFlag): SetResult {
        val claim = bridge.getClaim(claimId) ?: return SetResult(SetOutcome.CLAIM_GONE)
        val allowed = resolver.allows(flag, bridge.scopeOf(claim))
        return set(sender, claimId, flag, if (allowed) TriState.DENY else TriState.ALLOW)
    }
}
