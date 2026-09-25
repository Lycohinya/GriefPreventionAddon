package com.tinyyana.griefPreventionAddon.teleport

import com.tinyyana.griefPreventionAddon.i18n.escapeForMiniMessageTemplate
import com.tinyyana.griefPreventionAddon.audit.AuditLogger
import com.tinyyana.griefPreventionAddon.display.ClaimDisplayName
import com.tinyyana.griefPreventionAddon.i18n.LanguageManager
import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
import me.ryanhamshire.GriefPrevention.Claim
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.entity.Player

class ClaimTeleportService(
    private val bridge: GriefPreventionBridge,
    private val store: ClaimSettingsStore,
    private val lang: LanguageManager,
    private val auditLogger: AuditLogger? = null,
) {

    fun canTeleport(player: Player, claim: Claim): Boolean {
        val top = claim.parent ?: claim
        val isOwner = top.ownerID != null && top.ownerID == player.uniqueId
        if (isOwner) return true

        if (player.hasPermission("griefpreventionaddon.admin") ||
            player.hasPermission("griefpreventionaddon.tp.others") ||
            player.isOp
        ) {
            return true
        }

        if (top.isAdminClaim()) {
            return player.hasPermission("griefpreventionaddon.tp.admin")
        }

        // Access trusted players can also teleport
        return top.allowAccess(player) == null
    }

    fun teleport(player: Player, claim: Claim, closeInventory: Boolean = true) {
        val top = claim.parent ?: claim
        if (!canTeleport(player, top)) {
            player.sendMessage(lang.get(player, "teleport.no-permission"))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f)
            return
        }

        val claimId = top.id ?: run {
            player.sendMessage(lang.get(player, "teleport.failed"))
            return
        }

        val lesser = top.lesserBoundaryCorner ?: run {
            player.sendMessage(lang.get(player, "teleport.failed"))
            return
        }
        val greater = top.greaterBoundaryCorner ?: run {
            player.sendMessage(lang.get(player, "teleport.failed"))
            return
        }
        val world = lesser.world ?: run {
            player.sendMessage(lang.get(player, "teleport.failed"))
            return
        }

        val centerX = (lesser.blockX + greater.blockX) / 2
        val centerZ = (lesser.blockZ + greater.blockZ) / 2

        val customSpawn = resolveCustomSpawn(top)
        val chunkX = (customSpawn?.blockX ?: centerX) shr 4
        val chunkZ = (customSpawn?.blockZ ?: centerZ) shr 4

        if (closeInventory) {
            player.closeInventory()
        }

        val plugin = bridge.plugin

        val doTeleport = Runnable {
            val targetLoc = customSpawn ?: run {
                val safeY = resolveSafeGroundY(world, centerX, centerZ, lesser.blockY)
                Location(
                    world,
                    centerX + 0.5,
                    safeY.toDouble(),
                    centerZ + 0.5,
                    player.location.yaw,
                    player.location.pitch,
                )
            }

            player.teleportAsync(targetLoc).thenAccept { success ->
                if (success) {
                    player.playSound(targetLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f)
                    val display = ClaimDisplayName.resolve(store, claimId)
                    player.sendMessage(
                        lang.get(
                            player,
                            if (customSpawn != null) "teleport.success-custom" else "teleport.success",
                            "name" to escapeForMiniMessageTemplate(display.decorated),
                            "claimId" to claimId.toString(),
                            "x" to targetLoc.blockX.toString(),
                            "y" to targetLoc.blockY.toString(),
                            "z" to targetLoc.blockZ.toString(),
                        ),
                    )
                    auditLogger?.log(
                        player.name,
                        "claim-tp",
                        "claim=$claimId dest=(${targetLoc.blockX},${targetLoc.blockY},${targetLoc.blockZ})",
                    )
                } else {
                    player.sendMessage(lang.get(player, "teleport.failed"))
                }
            }
        }

        try {
            plugin.server.regionScheduler.execute(plugin, world, chunkX, chunkZ, doTeleport)
        } catch (t: Throwable) {
            // Non-Folia or test environment fallback
            doTeleport.run()
        }
    }

    fun resolveCustomSpawn(claim: Claim): Location? {
        val claimId = claim.id ?: return null
        val point = ClaimSpawnPoint.decode(store.getSpawnRaw(claimId)) ?: return null
        val loc = point.toLocation() ?: return null
        return if (isInsideClaim(claim, loc)) loc else null
    }

    fun isInsideClaim(claim: Claim, location: Location): Boolean {
        val claimWorld = claim.lesserBoundaryCorner?.world ?: return false
        if (location.world?.name != claimWorld.name) return false
        return claim.contains(location, true, false)
    }

    private fun resolveSafeGroundY(world: World, x: Int, z: Int, fallbackY: Int): Int {
        return try {
            val highest = world.getHighestBlockYAt(x, z)
            if (highest <= world.minHeight) {
                if (fallbackY > world.minHeight) fallbackY else 64
            } else {
                highest + 1
            }
        } catch (e: Throwable) {
            if (fallbackY > world.minHeight) fallbackY else 64
        }
    }
}
