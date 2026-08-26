package com.tinyyana.griefPreventionAddon.command

import com.tinyyana.griefPreventionAddon.audit.AuditLogger
import com.tinyyana.griefPreventionAddon.i18n.LanguageManager
import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
import com.tinyyana.griefPreventionAddon.teleport.ClaimSpawnPoint
import com.tinyyana.griefPreventionAddon.teleport.ClaimTeleportService
import me.ryanhamshire.GriefPrevention.Claim
import org.bukkit.Sound
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * `/cspawn` - Sets current location as custom claim spawn point for `/ctp`.
 */
class ClaimSpawnCommand(
    private val bridge: GriefPreventionBridge,
    private val store: ClaimSettingsStore,
    private val teleportService: ClaimTeleportService,
    private val lang: LanguageManager,
    private val auditLogger: AuditLogger? = null,
) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: run {
            sender.sendMessage(lang.get("system.player-only"))
            return true
        }

        if (!bridge.isAvailable()) {
            player.sendMessage(lang.get(player, "claim.gp-missing"))
            return true
        }

        val sub = args.firstOrNull()?.lowercase()

        if (sub == "help" || sub == "?") {
            sendHelp(player)
            return true
        }

        val claim = bridge.getTopClaim(player.location) ?: run {
            player.sendMessage(lang.get(player, "spawn.not-in-claim"))
            sendHelp(player)
            return true
        }
        val claimId = claim.id ?: run {
            player.sendMessage(lang.get(player, "teleport.failed"))
            return true
        }

        if (!canEdit(player, claim)) {
            player.sendMessage(lang.get(player, "claim.not-owner"))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f)
            return true
        }

        if (sub == "clear" || sub == "reset") {
            if (store.getSpawnRaw(claimId) == null) {
                player.sendMessage(lang.get(player, "spawn.already-default", "claimId" to claimId.toString()))
                return true
            }
            store.setSpawnRaw(claimId, null)
            auditLogger?.log(player.name, "claim-spawn.clear", "claim=$claimId")
            player.sendMessage(lang.get(player, "spawn.cleared", "claimId" to claimId.toString()))
            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.7f, 1.0f)
            return true
        }

        if (sub != null && sub != "set") {
            sendHelp(player)
            return true
        }

        setSpawnHere(player, claim, claimId)
        return true
    }

    fun setSpawnHere(player: Player, claim: Claim, claimId: Long) {
        val loc = player.location
        if (!teleportService.isInsideClaim(claim, loc)) {
            player.sendMessage(lang.get(player, "spawn.outside-claim"))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f)
            return
        }

        val point = ClaimSpawnPoint.fromLocation(loc) ?: run {
            player.sendMessage(lang.get(player, "teleport.failed"))
            return
        }

        store.setSpawnRaw(claimId, point.encode())
        auditLogger?.log(
            player.name,
            "claim-spawn.set",
            "claim=$claimId point=(${loc.blockX},${loc.blockY},${loc.blockZ})",
        )
        player.sendMessage(
            lang.get(
                player,
                "spawn.set",
                "claimId" to claimId.toString(),
                "x" to loc.blockX.toString(),
                "y" to loc.blockY.toString(),
                "z" to loc.blockZ.toString(),
            ),
        )
        player.playSound(player.location, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.4f)
    }

    fun canEdit(player: Player, claim: Claim): Boolean {
        val top = claim.parent ?: claim
        if (top.ownerID != null && top.ownerID == player.uniqueId) return true
        return player.hasPermission("griefpreventionaddon.settings.others") ||
            player.hasPermission("griefpreventionaddon.admin") ||
            player.isOp
    }

    private fun sendHelp(player: Player) {
        player.sendMessage(lang.get(player, "spawn.help-header"))
        player.sendMessage(lang.get(player, "spawn.help-set"))
        player.sendMessage(lang.get(player, "spawn.help-clear"))
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (args.size == 1) {
            return listOf("set", "clear").filter { it.startsWith(args[0], ignoreCase = true) }
        }
        return emptyList()
    }
}
