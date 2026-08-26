package com.tinyyana.griefPreventionAddon.command

import com.tinyyana.griefPreventionAddon.audit.AuditLogger
import com.tinyyana.griefPreventionAddon.i18n.LanguageManager
import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsKeys
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class ClaimMobsCommand(
    private val bridge: GriefPreventionBridge,
    private val store: ClaimSettingsStore,
    private val lang: LanguageManager,
    private val auditLogger: AuditLogger? = null,
) : CommandExecutor, TabCompleter {

    private val categories = listOf(
        Category("hostile", ClaimSettingsKeys.NO_HOSTILE_SPAWN, "mobs.cat-hostile"),
        Category("raider", ClaimSettingsKeys.NO_RAIDER_SPAWN, "mobs.cat-raider"),
        Category("phantom", ClaimSettingsKeys.NO_PHANTOM_SPAWN, "mobs.cat-phantom"),
        Category("slime", ClaimSettingsKeys.NO_SLIME_SPAWN, "mobs.cat-slime"),
        Category("ambient", ClaimSettingsKeys.NO_AMBIENT_SPAWN, "mobs.cat-ambient"),
    )

    private data class Category(val arg: String, val key: String, val labelKey: String)

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: run {
            sender.sendMessage(lang.get("system.player-only"))
            return true
        }

        if (!bridge.isAvailable()) {
            player.sendMessage(lang.get(player, "claim.gp-missing"))
            return true
        }

        val ownership = bridge.getTopClaimOwnership(player)
        if (ownership == null) {
            player.sendMessage(lang.get(player, "claim.not-in-claim"))
            return true
        }

        val canModify = ownership.isOwner ||
            player.hasPermission("griefpreventionaddon.settings.others") ||
            player.hasPermission("griefpreventionaddon.admin") ||
            player.isOp

        if (!canModify) {
            player.sendMessage(lang.get(player, "claim.not-owner"))
            return true
        }

        val arg = args.firstOrNull()?.lowercase()
        val category = arg?.let { a -> categories.firstOrNull { it.arg == a } }

        if (category != null) {
            val state = runCatching { store.toggle(category.key, ownership.claimId) }.getOrElse {
                player.sendMessage(lang.get(player, "claim.save-failed"))
                return true
            }
            val catLabel = lang.raw(player, category.labelKey) ?: category.arg
            player.sendMessage(
                lang.get(player, if (state) "mobs.category-on" else "mobs.category-off", "label" to catLabel),
            )
            auditLogger?.log(player.name, "claim-toggle.${category.key}", "claim=${ownership.claimId} state=$state")
        }

        openMobsMenu(player, ownership.claimId)
        return true
    }

    private fun openMobsMenu(player: Player, claimId: Long) {
        val sb = StringBuilder(lang.raw(player, "mobs.menu-header") ?: "")
        for (category in categories) {
            val blocked = store.isMobSpawnBlocked(category.key, claimId)
            val state = lang.raw(player, if (blocked) "mobs.state-on" else "mobs.state-off") ?: ""
            val catLabel = lang.raw(player, category.labelKey) ?: category.arg
            val line = (lang.raw(player, "mobs.menu-line") ?: "")
                .replace("{cmd}", category.arg)
                .replace("{state}", state)
                .replace("{label}", catLabel)
            sb.append("<newline>").append(line)
        }
        lang.raw(player, "mobs.menu-hint")?.let { sb.append("<newline>").append(it) }
        player.sendMessage(lang.render(sb.toString(), false, player))
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (args.size == 1) {
            return categories.map { it.arg }.filter { it.startsWith(args[0], ignoreCase = true) }
        }
        return emptyList()
    }
}
