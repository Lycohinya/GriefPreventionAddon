package com.tinyyana.griefPreventionAddon.trust

import com.tinyyana.griefPreventionAddon.audit.AuditLogger
import com.tinyyana.griefPreventionAddon.integration.GpCompat
import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import me.ryanhamshire.GriefPrevention.Claim
import me.ryanhamshire.GriefPrevention.ClaimPermission
import me.ryanhamshire.GriefPrevention.GriefPrevention
import me.ryanhamshire.GriefPrevention.events.TrustChangedEvent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

/** 一筆信任紀錄。[id] 是 GP 存的字串:玩家 UUID、`public` 或 `[權限節點]`。 */
data class TrustEntry(
    val id: String,
    val uuid: UUID?,
    val displayName: String,
    /** Build/Container/Access 其中之一;只有管理權時為 null */
    val level: ClaimPermission?,
    val manager: Boolean,
)

enum class TrustOutcome { OK, CLAIM_GONE, NO_PERMISSION, CANCELLED, INVALID }

/**
 * 信任管理。**資料只在 GP**:讀寫的都是 GP Claim 物件上的 trust,存檔用 GP 的 `saveClaim`,
 * 變更前照 GP 指令發 [TrustChangedEvent](其他插件可以取消)。權限規則逐條照 GP 16.18.7 的
 * `/trust`、`/untrust`、`/restrictsubclaim`:
 *
 * - 管理任何信任:需要該範圍的 Manage(地主、管理者、GP 管理員模式)。
 * - 給予某個等級:自己也要有那個等級;給予/撤銷「管理權」與「全部撤銷」:只有地主(Edit)。
 * - 移除成員 = GP `/untrust`:連同子花域裡同一人的信任一起撤(GP `dropPermission` 的語意)。
 * - 子花域「不繼承主花域信任」:只有主花域主人(管理員範圍需 adminclaims;GP 管理員模式可略過)。
 *
 * 「能改花域規則」是另一回事,在 [com.tinyyana.griefPreventionAddon.policy.ClaimSettingsService];
 * 有 Build/Container/Access 甚至 Manage 都不代表能改規則。
 */
class ClaimTrustService(
    private val bridge: GriefPreventionBridge,
    private val auditLogger: AuditLogger?,
) {
    fun entries(claim: Claim): List<TrustEntry> {
        val builders = ArrayList<String>()
        val containers = ArrayList<String>()
        val accessors = ArrayList<String>()
        val managers = ArrayList<String>()
        claim.getPermissions(builders, containers, accessors, managers)
        val levels = LinkedHashMap<String, ClaimPermission?>()
        builders.forEach { levels[it] = ClaimPermission.Build }
        containers.forEach { levels[it] = GpCompat.CONTAINER }
        accessors.forEach { levels[it] = ClaimPermission.Access }
        managers.forEach { levels.putIfAbsent(it, null) }
        return levels.map { (id, level) -> entry(id, level, managers.contains(id)) }
            .sortedWith(compareBy<TrustEntry>({ !it.manager }, { it.level?.ordinal ?: 99 }, { it.displayName.lowercase() }))
    }

    fun entry(claim: Claim, id: String): TrustEntry {
        val match = entries(claim).firstOrNull { it.id.equals(id, ignoreCase = true) }
        return match ?: entry(id, null, false)
    }

    private fun entry(id: String, level: ClaimPermission?, manager: Boolean): TrustEntry {
        val uuid = runCatching { UUID.fromString(id) }.getOrNull()
        val name = when {
            uuid != null -> runCatching { Bukkit.getOfflinePlayer(uuid).name }.getOrNull() ?: id
            else -> id
        }
        return TrustEntry(id, uuid, name, level, manager)
    }

    fun canManage(player: Player, claim: Claim): Boolean = claim.checkPermission(player, ClaimPermission.Manage, null) == null

    fun isOwnerLevel(player: Player, claim: Claim): Boolean = claim.checkPermission(player, ClaimPermission.Edit, null) == null

    fun canGrant(player: Player, claim: Claim, level: ClaimPermission): Boolean =
        canManage(player, claim) && claim.checkPermission(player, level, null) == null

    fun canChangeSubRestriction(player: Player, claim: Claim): Boolean {
        val parent = claim.parent ?: return false
        val ignoring = runCatching { GriefPrevention.instance.dataStore.getPlayerData(player.uniqueId).ignoreClaims }.getOrDefault(false)
        if (ignoring) return true
        return if (claim.isAdminClaim()) player.hasPermission("griefprevention.adminclaims") else player.uniqueId == parent.ownerID
    }

    /** 設定 Build/Container/Access 等級(覆蓋原等級,不動管理權,不動子花域)。 */
    fun setLevel(player: Player, claimId: Long, id: String, level: ClaimPermission): TrustOutcome {
        if (level == ClaimPermission.Manage || level == ClaimPermission.Edit) return TrustOutcome.INVALID
        val claim = bridge.getClaim(claimId) ?: return TrustOutcome.CLAIM_GONE
        if (!canGrant(player, claim, level)) return TrustOutcome.NO_PERMISSION
        if (!fire(player, claim, level, true, id)) return TrustOutcome.CANCELLED
        claim.setPermission(id, level)
        return save(player, claim, "trust.level", "id=$id level=$level")
    }

    /** 給予或撤銷管理權。兩個方向 GP 都只讓地主做。 */
    fun setManager(player: Player, claimId: Long, id: String, grant: Boolean): TrustOutcome {
        val claim = bridge.getClaim(claimId) ?: return TrustOutcome.CLAIM_GONE
        if (!canManage(player, claim) || !isOwnerLevel(player, claim)) return TrustOutcome.NO_PERMISSION
        // GP 的 /permissiontrust 在事件裡用 null 代表管理權
        if (!fire(player, claim, null, grant, id)) return TrustOutcome.CANCELLED
        val normalized = id.lowercase()
        if (grant) {
            if (claim.managers.none { it.equals(normalized, ignoreCase = true) }) claim.managers.add(normalized)
        } else {
            claim.managers.removeIf { it.equals(normalized, ignoreCase = true) }
        }
        return save(player, claim, "trust.manager", "id=$id grant=$grant")
    }

    /** 移除成員(GP `/untrust` 語意:連同子花域)。 */
    fun remove(player: Player, claimId: Long, id: String): TrustOutcome {
        val claim = bridge.getClaim(claimId) ?: return TrustOutcome.CLAIM_GONE
        if (!canManage(player, claim)) return TrustOutcome.NO_PERMISSION
        val targetIsManager = claim.managers.any { it.equals(id, ignoreCase = true) }
        if (targetIsManager && !isOwnerLevel(player, claim)) return TrustOutcome.NO_PERMISSION
        if (!fire(player, claim, null, false, id)) return TrustOutcome.CANCELLED
        claim.dropPermission(id)
        claim.managers.removeIf { it.equals(id, ignoreCase = true) }
        return save(player, claim, "trust.remove", "id=$id")
    }

    /** 全部撤銷(GP `/untrust all`):只有地主。 */
    fun clearAll(player: Player, claimId: Long): TrustOutcome {
        val claim = bridge.getClaim(claimId) ?: return TrustOutcome.CLAIM_GONE
        if (!isOwnerLevel(player, claim)) return TrustOutcome.NO_PERMISSION
        if (!fire(player, claim, null, false, "all")) return TrustOutcome.CANCELLED
        claim.clearPermissions()
        return save(player, claim, "trust.clear", "")
    }

    fun setSubRestriction(player: Player, claimId: Long, restrict: Boolean): TrustOutcome {
        val claim = bridge.getClaim(claimId) ?: return TrustOutcome.CLAIM_GONE
        if (claim.parent == null) return TrustOutcome.INVALID
        if (!canChangeSubRestriction(player, claim)) return TrustOutcome.NO_PERMISSION
        claim.setSubclaimRestrictions(restrict)
        return save(player, claim, "trust.restrict-subclaim", "restrict=$restrict")
    }

    private fun fire(player: Player, claim: Claim, level: ClaimPermission?, given: Boolean, id: String): Boolean {
        val event = TrustChangedEvent(player, claim, level, given, id)
        Bukkit.getPluginManager().callEvent(event)
        return !event.isCancelled
    }

    private fun save(player: Player, claim: Claim, action: String, detail: String): TrustOutcome {
        GriefPrevention.instance.dataStore.saveClaim(claim)
        auditLogger?.log(player.name, action, "claim=${claim.getID()} $detail")
        return TrustOutcome.OK
    }
}
