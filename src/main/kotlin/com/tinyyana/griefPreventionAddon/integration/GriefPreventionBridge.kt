package com.tinyyana.griefPreventionAddon.integration

import com.tinyyana.griefPreventionAddon.policy.ClaimFlags
import com.tinyyana.griefPreventionAddon.policy.ClaimPolicyResolver
import com.tinyyana.griefPreventionAddon.policy.ClaimScope
import com.tinyyana.griefPreventionAddon.sethome.SethomeRestrictionListener
import com.tinyyana.griefPreventionAddon.storage.ClaimIdKind
import me.ryanhamshire.GriefPrevention.Claim
import me.ryanhamshire.GriefPrevention.ClaimPermission
import me.ryanhamshire.GriefPrevention.GriefPrevention
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.UUID

/**
 * 一個精確範圍(主花域或子花域)的顯示資訊。
 *
 * v1 的這個型別一律回主花域的 id 與尺寸,`isSubdivision` 只是附註——所以站在子花域裡打開的畫面、
 * 改的其實是主花域。現在 [claimId]/[claim] 是精確範圍,[rootId]/[topClaim] 才是主花域。
 */
data class ClaimInfoResult(
    val claimId: Long,
    val rootId: Long,
    val ownerName: String?,
    val ownerUuid: UUID?,
    val width: Int,
    val height: Int,
    val area: Int,
    val isAdminClaim: Boolean,
    val isSubdivision: Boolean,
    val claim: Claim,
    val topClaim: Claim,
)

/** GP 的火焰相關設定(執行期讀 GP 實例欄位,`/gpreload` 之後跟著變)。 */
data class GpFireConfig(
    val fireSpreads: Boolean,
    val fireDestroys: Boolean,
    val claimsFireSpreads: Boolean,
    val claimsFireDamages: Boolean,
)

/** 玩家在某個範圍裡的身分(只讀 GP 的真實 trust 資料,不另存)。 */
enum class ClaimRole { OWNER, MANAGER, BUILDER, CONTAINER, ACCESSOR, VISITOR }

/**
 * 封裝 GriefPrevention API。
 * 執行期透過 softdepend 檢查防範 NoClassDefFoundError。
 */
class GriefPreventionBridge(val plugin: Plugin) {

    fun isAvailable(): Boolean =
        plugin.server.pluginManager.getPlugin("GriefPrevention")?.isEnabled == true

    /** 位置所在的**精確**範圍:在子花域裡就是子花域(GP `getClaimAt` 本來的語意)。 */
    fun getClaimAt(location: Location): Claim? {
        if (!isAvailable()) return null
        return runCatching {
            GriefPrevention.instance.dataStore.getClaimAt(location, false, null)
        }.getOrNull()
    }

    /**
     * 忽略高度取得精確範圍:只比對水平範圍,把領地視為一整根柱子。
     *
     * GP 的 [me.ryanhamshire.GriefPrevention.Claim.contains] 在 ignoreHeight=false 時會做 3D 判定，
     * 而地表領地的 lesserBoundaryCorner.y 只比建立當下的地面低數格(GP 的 extendIntoGroundDistance)。
     * 生怪過濾若沿用 3D 判定，領地底下洞穴／深板岩層的自然生怪一律查不到領地而放行
     * (史萊姆的 y<40 生成帶、地獄的岩漿立方怪都落在這個區間)，玩家看到的就是「開了還是會生」。
     * 生怪過濾一律走這條，其他功能(TNT、PVP、資訊查詢)維持原本的 3D 判定。
     * GP 16.18 的子花域沒有獨立的 3D 範圍(跟主花域一樣延伸到天空),忽略高度時同樣會回子花域。
     */
    fun getClaimAtIgnoringHeight(location: Location): Claim? {
        if (!isAvailable()) return null
        return runCatching {
            GriefPrevention.instance.dataStore.getClaimAt(location, true, null)
        }.getOrNull()
    }

    /** 主花域(別名、落腳點、傳送目的地這類「整塊地」的資料用它)。 */
    fun getTopClaim(location: Location): Claim? = getClaimAt(location)?.let(::root)

    /** 依 id 找現存範圍(含子花域);已刪除或不存在回 null——呼叫端不得改寫其他範圍。 */
    fun getClaim(claimId: Long): Claim? {
        if (!isAvailable()) return null
        return runCatching {
            GriefPrevention.instance.dataStore.getClaim(claimId)?.takeIf { it.inDataStore }
        }.getOrNull()
    }

    fun root(claim: Claim): Claim = claim.parent ?: claim

    fun scopeOf(claim: Claim): ClaimScope = ClaimScope(claim.getID(), claim.parent?.getID())

    /** 遷移用:這個 id 現在在 GP 是什麼。 */
    fun classify(claimId: Long): ClaimIdKind {
        val claim = getClaim(claimId) ?: return ClaimIdKind.UNKNOWN
        return if (claim.parent != null) ClaimIdKind.SUBDIVISION else ClaimIdKind.TOP_LEVEL
    }

    fun getClaimInfo(location: Location): ClaimInfoResult? {
        if (!isAvailable()) return null
        return runCatching {
            val claim = GriefPrevention.instance.dataStore.getClaimAt(location, false, null) ?: return@runCatching null
            getClaimInfo(claim)
        }.getOrNull()
    }

    fun getClaimInfo(claim: Claim): ClaimInfoResult {
        val top = root(claim)
        return ClaimInfoResult(
            claimId = claim.getID(),
            rootId = top.getID(),
            ownerName = if (top.isAdminClaim()) null else top.getOwnerName(),
            ownerUuid = top.getOwnerID(),
            width = claim.getWidth(),
            height = claim.getHeight(),
            area = claim.getArea(),
            isAdminClaim = top.isAdminClaim(),
            isSubdivision = claim.parent != null,
            claim = claim,
            topClaim = top,
        )
    }

    /** 站在 [claim](精確範圍,含子花域範圍判定)裡的線上玩家。 */
    fun playersInside(claim: Claim): List<Player> {
        if (!isAvailable()) return emptyList()
        return runCatching {
            plugin.server.onlinePlayers.filter { claim.contains(it.location, true, false) }
        }.getOrDefault(emptyList())
    }

    fun getClaimsForPlayer(playerUuid: UUID): List<Claim> {
        if (!isAvailable()) return emptyList()
        return runCatching {
            val playerData = GriefPrevention.instance.dataStore.getPlayerData(playerUuid) ?: return@runCatching emptyList()
            playerData.claims?.toList() ?: emptyList()
        }.getOrDefault(emptyList())
    }

    fun allClaims(): List<Claim> {
        if (!isAvailable()) return emptyList()
        return runCatching { GriefPrevention.instance.dataStore.claims?.toList() ?: emptyList() }.getOrDefault(emptyList())
    }

    fun role(player: Player, claim: Claim): ClaimRole {
        val owner = claim.getOwnerID()
        if (owner != null && owner == player.uniqueId) return ClaimRole.OWNER
        fun has(p: ClaimPermission) = runCatching { claim.hasExplicitPermission(player, p) }.getOrDefault(false)
        return when {
            has(ClaimPermission.Manage) -> ClaimRole.MANAGER
            has(ClaimPermission.Build) -> ClaimRole.BUILDER
            has(GpCompat.CONTAINER) -> ClaimRole.CONTAINER
            has(ClaimPermission.Access) -> ClaimRole.ACCESSOR
            else -> ClaimRole.VISITOR
        }
    }

    fun fireConfig(): GpFireConfig? {
        if (!isAvailable()) return null
        return runCatching {
            val gp = GriefPrevention.instance
            GpFireConfig(gp.config_fireSpreads, gp.config_fireDestroys, gp.config_claims_firespreads, gp.config_claims_firedamages)
        }.getOrNull()
    }

    fun claimsEnabledForWorld(world: org.bukkit.World): Boolean =
        runCatching { GriefPrevention.instance.claimsEnabledForWorld(world) }.getOrDefault(false)

    /**
     * 把一個範圍的 TNT 有效值寫進 GP 的 `areExplosivesAllowed`。
     *
     * GP 在 LOWEST 優先處理爆炸時逐方塊查**精確範圍**的這個欄位,不允許就先把方塊移出爆炸清單;
     * addon 後段的過濾只能再移除、不能補回。所以每個範圍各自寫自己的有效值,不能像 v1 那樣
     * 用一個值覆蓋整棵樹(那會讓子花域的「禁止」被主花域的「允許」蓋掉,反之亦然)。
     */
    fun syncExplosives(claim: Claim, resolver: ClaimPolicyResolver) {
        runCatching { claim.areExplosivesAllowed = resolver.allows(ClaimFlags.TNT, scopeOf(claim)) }
    }

    /** 主花域改了值:它自己與所有子花域都要重算(繼承中的子花域會跟著變,有自己值的不變)。 */
    fun syncExplosivesTree(claim: Claim, resolver: ClaimPolicyResolver) {
        syncExplosives(claim, resolver)
        if (claim.parent == null) claim.children?.toList()?.forEach { syncExplosives(it, resolver) }
    }

    /** 全服啟動/GP 重新啟用時,把所有範圍的 TNT 有效值寫回 GP 的記憶體物件。 */
    fun syncAllExplosives(resolver: ClaimPolicyResolver) {
        if (!isAvailable()) return
        allClaims().forEach { syncExplosivesTree(it, resolver) }
    }

    /**
     * 自行接管 /sethome 權限檢查，從 GriefPrevention 的 commandsRequiringAccessTrust 移除 sethome 相關指令。
     */
    fun takeOverSethomeCommands(): Boolean {
        if (!isAvailable()) return false
        return runCatching {
            val list = GriefPrevention.instance.config_claims_commandsRequiringAccessTrust ?: return@runCatching false
            list.removeIf { cmd ->
                val clean = cmd.trim().removePrefix("/").lowercase()
                clean in SethomeRestrictionListener.SETHOME_COMMANDS
            }
        }.getOrDefault(false)
    }

    /**
     * 計算花域中心點座標（純數學計算，不存取方塊資料，跨 region / GUI 顯示 100% 安全）。
     */
    fun getSafeTeleportLocation(claim: Claim): Location {
        val top = root(claim)
        val lesser = top.lesserBoundaryCorner
        val greater = top.greaterBoundaryCorner
        val world = lesser?.world ?: plugin.server.worlds.firstOrNull()
        val minX = lesser?.blockX ?: 0
        val maxX = greater?.blockX ?: 0
        val minZ = lesser?.blockZ ?: 0
        val maxZ = greater?.blockZ ?: 0
        val centerX = (minX + maxX) / 2
        val centerZ = (minZ + maxZ) / 2
        val targetY = if ((lesser?.blockY ?: 64) > (world?.minHeight ?: 0)) lesser?.blockY ?: 64 else 64
        return Location(world, centerX + 0.5, targetY.toDouble(), centerZ + 0.5)
    }
}
