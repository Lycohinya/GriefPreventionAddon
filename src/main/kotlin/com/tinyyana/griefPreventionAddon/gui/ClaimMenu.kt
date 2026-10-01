package com.tinyyana.griefPreventionAddon.gui

import com.tinyyana.griefPreventionAddon.audit.AuditLogger
import com.tinyyana.griefPreventionAddon.command.ClaimSpawnCommand
import com.tinyyana.griefPreventionAddon.display.FlagText
import com.tinyyana.griefPreventionAddon.i18n.LanguageManager
import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.policy.ClaimFlags
import com.tinyyana.griefPreventionAddon.policy.ClaimSettingsService
import com.tinyyana.griefPreventionAddon.policy.SetOutcome
import com.tinyyana.griefPreventionAddon.policy.TriState
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
import com.tinyyana.griefPreventionAddon.teleport.ClaimTeleportService
import com.tinyyana.griefPreventionAddon.trust.ClaimTrustService
import com.tinyyana.griefPreventionAddon.trust.TrustOutcome
import me.ryanhamshire.GriefPrevention.Claim
import me.ryanhamshire.GriefPrevention.ClaimPermission
import me.ryanhamshire.GriefPrevention.GriefPrevention
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.plugin.Plugin

/**
 * 花域選單的控制器:開哪一頁、點了之後做什麼。
 *
 * - 開啟與每次點擊都依 id 重新向 GP 取範圍、重新檢查權限(視窗開著時範圍可能被刪、縮放、移交)。
 * - 實際寫入只經過 [ClaimSettingsService](規則)與 [ClaimTrustService](信任);
 *   放棄花域交給 GP 自己的 `/abandonclaim`(退格數、清流體等規則都在 GP)。
 * - 換頁在玩家自己的排程器上、下一個 tick 重開視窗(不在點擊事件裡直接換視窗;Folia 下也是玩家的執行緒)。
 */
class ClaimMenu(
    private val plugin: Plugin,
    private val bridge: GriefPreventionBridge,
    private val store: ClaimSettingsStore,
    private val settings: ClaimSettingsService,
    private val trust: ClaimTrustService,
    private val teleport: ClaimTeleportService,
    private val spawnCommand: ClaimSpawnCommand,
    private val lang: LanguageManager,
    private val auditLogger: AuditLogger?,
    private val openAdminPanel: (Player) -> Unit,
    worldFireTicks: (Claim) -> Boolean,
) {
    val pages = ClaimMenuPages(bridge, store, settings, trust, teleport, lang, worldFireTicks)
    private val renderer = ClaimMenuRenderer({ player, holder -> pages.tabs(player, holder.state.page.highlightedTab, holder.state.scopeId) })

    fun canView(player: Player, claim: Claim): Boolean =
        settings.isSettingsAdmin(player) ||
            claim.getOwnerID() == player.uniqueId ||
            claim.checkPermission(player, ClaimPermission.Access, null) == null

    /** `/csettings` 不帶參數:腳下的精確範圍 → 只有一塊地就開那塊 → 有好幾塊就開「我的花域」。 */
    fun openHere(player: Player): Boolean {
        val here = bridge.getClaimAt(player.location)
        if (here != null) return open(player, MenuState(MenuPage.OVERVIEW, here.getID()))
        val mine = bridge.getClaimsForPlayer(player.uniqueId).filter { it.inDataStore }
        return when (mine.size) {
            0 -> { player.sendMessage(lang.get(player, "claim.not-in-claim")); false }
            1 -> open(player, MenuState(MenuPage.OVERVIEW, mine.first().getID()))
            else -> open(player, MenuState(MenuPage.MY_CLAIMS, mine.first().getID()))
        }
    }

    fun open(player: Player, state: MenuState): Boolean {
        val claim = bridge.getClaim(state.scopeId) ?: run {
            player.sendMessage(lang.get(player, "flag.claim-gone"))
            player.closeInventory()
            return false
        }
        if (!canView(player, claim)) {
            player.sendMessage(lang.get(player, "system.no-permission"))
            player.closeInventory()
            return false
        }
        val holder = ClaimMenuHolder(player.uniqueId, state)
        renderer.open(player, holder, pages.build(pages.ctx(player, claim, state)))
        return true
    }

    private fun reopenLater(player: Player, state: MenuState) {
        val task = Runnable { if (player.isOnline) open(player, state) }
        runCatching { player.scheduler.run(plugin, { task.run() }, null) }.onFailure { task.run() }
    }

    private fun redraw(player: Player, holder: ClaimMenuHolder) {
        val claim = bridge.getClaim(holder.state.scopeId) ?: run {
            player.sendMessage(lang.get(player, "flag.claim-gone"))
            player.closeInventory()
            return
        }
        if (!canView(player, claim)) {
            player.closeInventory()
            return
        }
        renderer.redraw(player, holder, pages.build(pages.ctx(player, claim, holder.state)))
    }

    fun handle(player: Player, holder: ClaimMenuHolder, slot: Int, click: ClickType) {
        val action = holder.actions[slot] ?: return
        val state = holder.state
        val claim = bridge.getClaim(state.scopeId)
        if (claim == null || !canView(player, claim)) {
            player.sendMessage(lang.get(player, if (claim == null) "flag.claim-gone" else "system.no-permission"))
            player.closeInventory()
            return
        }
        when (action) {
            is MenuAction.Go -> {
                click(player)
                reopenLater(player, MenuState(action.page, action.scopeId ?: state.scopeId, action.pageIndex, action.memberId))
            }
            is MenuAction.Turn -> {
                click(player)
                reopenLater(player, state.copy(pageIndex = (state.pageIndex + action.delta).coerceAtLeast(0)))
            }
            is MenuAction.Flag -> {
                val target = when (click) {
                    ClickType.LEFT -> TriState.ALLOW
                    ClickType.RIGHT -> TriState.DENY
                    ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT -> TriState.INHERIT
                    else -> return
                }
                val result = settings.set(player, claim.getID(), action.flag, target)
                when (result.outcome) {
                    SetOutcome.OK -> player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.7f, 1.2f)
                    SetOutcome.UNCHANGED -> click(player)
                    else -> { deny(player); FlagText.sendOutcome(lang, player, action.flag, result) }
                }
                redraw(player, holder)
            }
            is MenuAction.MemberLevel -> trustResult(player, trust.setLevel(player, claim.getID(), action.memberId, action.level)) {
                reopenLater(player, state)
            }
            is MenuAction.MemberManager -> trustResult(player, trust.setManager(player, claim.getID(), action.memberId, action.grant)) {
                reopenLater(player, state)
            }
            is MenuAction.SubRestriction -> trustResult(player, trust.setSubRestriction(player, claim.getID(), action.restrict)) {
                redraw(player, holder)
            }
            is MenuAction.AskConfirm -> {
                click(player)
                reopenLater(player, MenuState(MenuPage.CONFIRM, claim.getID(), memberId = state.memberId, confirm = action.kind))
            }
            is MenuAction.Execute -> execute(player, claim, state, action.kind)
            MenuAction.Teleport -> teleport.teleport(player, bridge.root(claim), closeInventory = true)
            MenuAction.Rename -> {
                click(player)
                player.closeInventory()
                val rootId = bridge.root(claim).getID()
                val current = store.getAlias(rootId)?.let { com.tinyyana.griefPreventionAddon.i18n.escapeForMiniMessageTemplate(it) }
                    ?: (lang.raw(player, "menu.none") ?: "-")
                // 帶編號的 /cname <id> <別名>:不管玩家現在站在哪,改的都是這個選單的主花域
                player.sendMessage(lang.get(player, "name.gui-prompt-header"))
                player.sendMessage(lang.getWithoutPrefix(player, "menu.manage.rename-prompt", "claimId" to rootId.toString(), "current" to current))
            }
            MenuAction.Spawn -> {
                val root = bridge.root(claim)
                if (!spawnCommand.canEdit(player, root)) {
                    deny(player)
                    player.sendMessage(lang.get(player, "claim.not-owner"))
                    return
                }
                if (click.isRightClick) {
                    if (store.getSpawnRaw(root.getID()) != null) {
                        store.setSpawnRaw(root.getID(), null)
                        auditLogger?.log(player.name, "claim-spawn.clear", "claim=${root.getID()}")
                        player.sendMessage(lang.get(player, "spawn.cleared", "claimId" to root.getID().toString()))
                    }
                } else {
                    spawnCommand.setSpawnHere(player, root, root.getID())
                }
                redraw(player, holder)
            }
            MenuAction.SubdivideMode -> modeSwitch(player, "subdivideclaims", "menu.manage.subdivide-guide")
            MenuAction.BasicMode -> modeSwitch(player, "basicclaims", "menu.manage.resize-guide")
            MenuAction.AdminPanel -> {
                if (!settings.isSettingsAdmin(player)) return deny(player)
                click(player)
                val task = Runnable { openAdminPanel(player) }
                runCatching { player.scheduler.run(plugin, { task.run() }, null) }.onFailure { task.run() }
            }
            MenuAction.Close -> {
                click(player)
                player.closeInventory()
            }
        }
    }

    private fun execute(player: Player, claim: Claim, state: MenuState, kind: ConfirmKind) {
        when (kind) {
            ConfirmKind.ABANDON -> {
                // 交給 GP 的指令,它只認玩家腳下(忽略高度)的範圍——站錯地方就請玩家走過去,不偷換目標
                val here = bridge.getClaimAtIgnoringHeight(player.location)
                if (here?.getID() != claim.getID()) {
                    deny(player)
                    player.sendMessage(lang.get(player, "menu.manage.abandon-stand-inside", "scope" to FlagText.scopeLabel(lang, player, claim)))
                    return
                }
                player.closeInventory()
                val command = if (claim.parent == null && claim.children.isNotEmpty()) "abandontoplevelclaim" else "abandonclaim"
                auditLogger?.log(player.name, "claim-abandon.menu", "claim=${claim.getID()} via=/$command")
                player.performCommand(command)
            }
            ConfirmKind.ADMIN_DELETE -> {
                if (!settings.isSettingsAdmin(player)) return deny(player)
                player.closeInventory()
                val id = claim.getID()
                GriefPrevention.instance.dataStore.deleteClaim(claim, true)
                auditLogger?.log(player.name, "admin-delete-claim.menu", "claim=$id")
                player.sendMessage(lang.get(player, "admin.claim-deleted", "claimId" to id.toString()))
            }
            ConfirmKind.REMOVE_MEMBER -> {
                val memberId = state.memberId ?: return
                trustResult(player, trust.remove(player, claim.getID(), memberId)) {
                    reopenLater(player, MenuState(MenuPage.MEMBERS, claim.getID()))
                }
            }
            ConfirmKind.CLEAR_TRUST -> trustResult(player, trust.clearAll(player, claim.getID())) {
                reopenLater(player, MenuState(MenuPage.MEMBERS, claim.getID()))
            }
            ConfirmKind.RESET_RULES -> {
                var denied = false
                ClaimFlags.ALL.filter { store.getExplicit(it.key, claim.getID()) != null }.forEach { flag ->
                    val r = settings.set(player, claim.getID(), flag, TriState.INHERIT)
                    if (r.outcome == SetOutcome.NO_PERMISSION || r.outcome == SetOutcome.CLAIM_GONE) denied = true
                }
                if (denied) deny(player) else click(player)
                reopenLater(player, MenuState(MenuPage.RULES, claim.getID()))
            }
        }
    }

    private fun modeSwitch(player: Player, command: String, guideKey: String) {
        click(player)
        player.closeInventory()
        player.performCommand(command)
        lang.rawList(player, guideKey).forEach { player.sendMessage(lang.render(it, true, player)) }
    }

    private inline fun trustResult(player: Player, outcome: TrustOutcome, onOk: () -> Unit) {
        if (outcome == TrustOutcome.OK) {
            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.7f, 1.2f)
            onOk()
        } else {
            deny(player)
            player.sendMessage(lang.get(player, "menu.trust-result.${outcome.name.lowercase()}"))
        }
    }

    private fun click(player: Player) = player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.7f, 1.0f)

    private fun deny(player: Player) = player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f)
}
