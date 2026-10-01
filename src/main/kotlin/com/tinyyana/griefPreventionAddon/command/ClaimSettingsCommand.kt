package com.tinyyana.griefPreventionAddon.command

import com.tinyyana.griefPreventionAddon.display.ClaimDisplayName
import com.tinyyana.griefPreventionAddon.gui.ClaimMenu
import com.tinyyana.griefPreventionAddon.gui.MenuPage
import com.tinyyana.griefPreventionAddon.gui.MenuState
import com.tinyyana.griefPreventionAddon.i18n.LanguageManager
import com.tinyyana.griefPreventionAddon.i18n.escapeForMiniMessageTemplate
import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/** `/csettings [編號|別名]`:打開花域選單。不帶參數 = 腳下的精確範圍(站在子花域就是子花域)。 */
class ClaimSettingsCommand(
    private val bridge: GriefPreventionBridge,
    private val store: ClaimSettingsStore,
    private val menu: ClaimMenu,
    private val lang: LanguageManager,
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
        if (args.isEmpty()) {
            menu.openHere(player)
            return true
        }
        val input = args[0]
        val claimId = store.findClaimId(input, player.uniqueId, bridge.getClaimsForPlayer(player.uniqueId))
        if (claimId == null) {
            player.sendMessage(lang.get(player, "teleport.invalid-target", "target" to escapeForMiniMessageTemplate(input)))
            return true
        }
        if (bridge.getClaim(claimId) == null) {
            player.sendMessage(lang.get(player, "teleport.claim-not-found", "claimId" to claimId.toString()))
            return true
        }
        menu.open(player, MenuState(MenuPage.OVERVIEW, claimId))
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (args.size == 1 && sender is Player) {
            // 一塊花域一個候選:有別名就只給別名,沒有才給 #編號(兩種輸入 findClaimId 都認)
            return bridge.getClaimsForPlayer(sender.uniqueId).mapNotNull { c -> c.id?.let { ClaimDisplayName.resolve(store, it).completion } }
                .filter { it.startsWith(args[0], ignoreCase = true) }
        }
        return emptyList()
    }
}
