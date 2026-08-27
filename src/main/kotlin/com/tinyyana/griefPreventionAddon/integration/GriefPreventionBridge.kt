package com.tinyyana.griefPreventionAddon.integration

import com.tinyyana.griefPreventionAddon.sethome.SethomeRestrictionListener
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
import me.ryanhamshire.GriefPrevention.Claim
import me.ryanhamshire.GriefPrevention.GriefPrevention
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.UUID

data class ClaimOwnership(
    val claimId: Long,
    val isOwner: Boolean,
    val ownerUuid: UUID?,
    val ownerName: String?,
    val topClaim: Claim,
)

data class ClaimInfoResult(
    val claimId: Long,
    val ownerName: String?,
    val ownerUuid: UUID?,
    val width: Int,
    val height: Int,
    val area: Int,
    val isAdminClaim: Boolean,
    val isSubdivision: Boolean,
    val topClaim: Claim,
)

/**
 * 封裝 GriefPrevention API。
 * 執行期透過 softdepend 檢查防範 NoClassDefFoundError。
 */
class GriefPreventionBridge(val plugin: Plugin) {

    fun isAvailable(): Boolean =
        plugin.server.pluginManager.getPlugin("GriefPrevention")?.isEnabled == true

    fun getClaimAt(location: Location): Claim? {
        if (!isAvailable()) return null
        return runCatching {
            GriefPrevention.instance.dataStore.getClaimAt(location, false, null)
        }.getOrNull()
    }

    fun getTopClaim(location: Location): Claim? {
        val claim = getClaimAt(location) ?: return null
        return claim.parent ?: claim
    }

    fun getClaim(claimId: Long): Claim? {
        if (!isAvailable()) return null
        return runCatching {
            GriefPrevention.instance.dataStore.getClaim(claimId)
        }.getOrNull()
    }

    fun getTopClaimOwnership(player: Player): ClaimOwnership? {
        if (!isAvailable()) return null
        return runCatching {
            val claim = GriefPrevention.instance.dataStore.getClaimAt(player.location, false, null) ?: return@runCatching null
            val top = claim.parent ?: claim
            val id = top.id ?: return@runCatching null
            val ownerUuid = top.ownerID
            val isOwner = (ownerUuid != null && ownerUuid == player.uniqueId) || (claim.ownerID != null && claim.ownerID == player.uniqueId)
            ClaimOwnership(
                claimId = id,
                isOwner = isOwner,
                ownerUuid = ownerUuid,
                ownerName = if (top.isAdminClaim()) null else top.ownerName,
                topClaim = top,
            )
        }.getOrNull()
    }

    fun getClaimInfo(location: Location): ClaimInfoResult? {
        if (!isAvailable()) return null
        return runCatching {
            val claim = GriefPrevention.instance.dataStore.getClaimAt(location, false, null) ?: return@runCatching null
            getClaimInfo(claim)
        }.getOrNull()
    }

    fun getClaimInfo(claim: Claim): ClaimInfoResult {
        val top = claim.parent ?: claim
        return ClaimInfoResult(
            claimId = top.id ?: 0L,
            ownerName = if (top.isAdminClaim()) null else top.ownerName,
            ownerUuid = top.ownerID,
            width = top.width,
            height = top.height,
            area = top.area,
            isAdminClaim = top.isAdminClaim(),
            isSubdivision = claim.parent != null,
            topClaim = top,
        )
    }

    fun playersInsideClaim(claimId: Long): List<Player> {
        if (!isAvailable()) return emptyList()
        return runCatching {
            val claim = GriefPrevention.instance.dataStore.getClaim(claimId) ?: return@runCatching emptyList()
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

    /**
     * 同步單一領地及其子領地的 TNT 爆炸設定至 GriefPrevention Claim 物件。
     */
    fun syncClaimExplosives(claimId: Long, allowed: Boolean) {
        if (!isAvailable()) return
        runCatching {
            val claim = GriefPrevention.instance.dataStore.getClaim(claimId) ?: return@runCatching
            syncClaimExplosives(claim, allowed)
        }
    }

    /**
     * 同步指定 Claim 及其子領地的 TNT 爆炸設定至 GriefPrevention Claim 物件。
     */
    fun syncClaimExplosives(claim: Claim, allowed: Boolean) {
        val top = claim.parent ?: claim
        top.areExplosivesAllowed = allowed
        top.children?.forEach { child ->
            child.areExplosivesAllowed = allowed
        }
    }

    /**
     * 全服啟動/重載時，將所有已儲存的 TNT 設定同步至 GriefPrevention 的記憶體 Claim 物件。
     */
    fun syncAllClaimExplosives(store: ClaimSettingsStore) {
        if (!isAvailable()) return
        runCatching {
            val dataStore = GriefPrevention.instance.dataStore ?: return@runCatching
            val claims = dataStore.claims ?: return@runCatching
            for (claim in claims) {
                val id = claim.id ?: continue
                val allowed = store.isTntAllowed(id)
                syncClaimExplosives(claim, allowed)
            }
        }
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
        val top = claim.parent ?: claim
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
