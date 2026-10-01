package com.tinyyana.griefPreventionAddon.command

import com.tinyyana.griefPreventionAddon.display.FlagText
import com.tinyyana.griefPreventionAddon.i18n.LanguageManager
import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.policy.ClaimFlag
import com.tinyyana.griefPreventionAddon.policy.ClaimSettingsService
import com.tinyyana.griefPreventionAddon.policy.TriState
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * 所有規則指令共用的實作。`/ctnt`、`/pvp`、`/csethome` 管一項,`/cmob`、`/cfire` 管一組:
 *
 * - 一項:`/<cmd> [on|off|inherit|status]`,不帶參數 = 把目前實際值翻過來
 * - 一組:`/<cmd> <項目> [on|off|inherit]`,只打項目 = 翻轉;什麼都不打 = 列出這一組的狀態
 *
 * 範圍一律是玩家**腳下的精確範圍**(站在子花域就是子花域)。新增一個規則指令只要在
 * plugin.yml 登記、在 onEnable 用對應的 [ClaimFlag] 清單建一個實例。
 */
class FlagCommand(
    private val bridge: GriefPreventionBridge,
    private val service: ClaimSettingsService,
    private val lang: LanguageManager,
    private val flags: List<ClaimFlag>,
) : CommandExecutor, TabCompleter {

    private val single = flags.size == 1

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: run {
            sender.sendMessage(lang.get("system.player-only"))
            return true
        }
        if (!bridge.isAvailable()) {
            player.sendMessage(lang.get(player, "claim.gp-missing"))
            return true
        }
        val claim = bridge.getClaimAt(player.location) ?: run {
            player.sendMessage(lang.get(player, "claim.not-in-claim"))
            return true
        }

        val (flag, stateArg) = if (single) {
            flags.first() to args.getOrNull(0)
        } else {
            val first = args.getOrNull(0)
            val picked = first?.let { a -> flags.firstOrNull { it.shortName.equals(a, ignoreCase = true) } }
            val legacy = flags.firstOrNull { it.commandDefault }
            when {
                picked != null -> picked to args.getOrNull(1)
                // 舊用法:`/csethome on|off|status` 直接作用在 commandDefault 那一項
                legacy != null && first != null && (TriState.parse(first) != null || first.equals("status", true) || first.equals("info", true)) ->
                    legacy to first
                else -> {
                    sendGroupStatus(player, claim, label)
                    return true
                }
            }
        }

        if (stateArg.equals("status", ignoreCase = true) || stateArg.equals("info", ignoreCase = true)) {
            player.sendMessage(FlagText.statusLine(lang, player, service.resolve(flag, claim)))
            return true
        }

        val result = if (stateArg == null) {
            service.toggle(player, claim.getID(), flag)
        } else {
            val state = TriState.parse(stateArg) ?: run {
                player.sendMessage(lang.get(player, "flag.bad-state", "input" to stateArg.replace("<", "")))
                return true
            }
            service.set(player, claim.getID(), flag, state)
        }
        FlagText.sendOutcome(lang, player, flag, result)
        return true
    }

    private fun sendGroupStatus(player: Player, claim: me.ryanhamshire.GriefPrevention.Claim, label: String) {
        player.sendMessage(lang.get(player, "flag.group-header", "scope" to FlagText.scopeLabel(lang, player, claim)))
        for (flag in flags) {
            val resolution = service.resolve(flag, claim)
            player.sendMessage(
                lang.getWithoutPrefix(
                    player, "flag.group-line",
                    "cmd" to "/$label ${flag.shortName}",
                    "name" to FlagText.name(lang, player, flag),
                    "state" to FlagText.effectiveWord(lang, player, resolution),
                    "source" to FlagText.sourceWord(lang, player, resolution),
                ),
            )
        }
        if (flags.none { service.isAvailable(it) }) player.sendMessage(lang.get(player, "flag.unavailable"))
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        val states = listOf("on", "off", "inherit")
        return when {
            single && args.size == 1 -> (states + "status").filter { it.startsWith(args[0], ignoreCase = true) }
            !single && args.size == 1 -> flags.map { it.shortName }.filter { it.startsWith(args[0], ignoreCase = true) }
            !single && args.size == 2 -> (states + "status").filter { it.startsWith(args[1], ignoreCase = true) }
            else -> emptyList()
        }
    }
}
