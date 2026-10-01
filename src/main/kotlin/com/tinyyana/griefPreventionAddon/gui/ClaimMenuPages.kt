package com.tinyyana.griefPreventionAddon.gui

import com.tinyyana.griefPreventionAddon.display.ClaimDisplayName
import com.tinyyana.griefPreventionAddon.display.FlagText
import com.tinyyana.griefPreventionAddon.i18n.LanguageManager
import com.tinyyana.griefPreventionAddon.i18n.escapeForMiniMessageTemplate
import com.tinyyana.griefPreventionAddon.integration.ClaimRole
import com.tinyyana.griefPreventionAddon.integration.GpCompat
import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.policy.ClaimFlag
import com.tinyyana.griefPreventionAddon.policy.ClaimFlags
import com.tinyyana.griefPreventionAddon.policy.ClaimSettingsService
import com.tinyyana.griefPreventionAddon.policy.FlagGroup
import com.tinyyana.griefPreventionAddon.policy.TriState
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
import com.tinyyana.griefPreventionAddon.teleport.ClaimTeleportService
import com.tinyyana.griefPreventionAddon.trust.ClaimTrustService
import com.tinyyana.griefPreventionAddon.trust.TrustEntry
import me.ryanhamshire.GriefPrevention.Claim
import me.ryanhamshire.GriefPrevention.ClaimPermission
import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * 各頁內容。每頁只回傳 [PageModel](幾組、每格長什麼樣、點了做什麼),畫法交給 [ClaimMenuRenderer]。
 *
 * 文字全部在語系檔 `menu.*`;規則相關的字由 [FlagText] 從 `flag.*` 組出來。
 * 環境與保護頁直接走 [ClaimFlags.ALL] 依 [FlagGroup] 分組——新增一項規則不必改這裡。
 */
class ClaimMenuPages(
    private val bridge: GriefPreventionBridge,
    private val store: ClaimSettingsStore,
    private val settings: ClaimSettingsService,
    private val trust: ClaimTrustService,
    private val teleport: ClaimTeleportService,
    private val lang: LanguageManager,
    /** 世界規則是否讓火能自己蔓延;false 時火焰兩項在畫面上註明。 */
    private val worldFireTicks: (Claim) -> Boolean,
) {
    /** 一次建頁需要的東西:誰在看、看哪個範圍、他能做什麼。 */
    inner class Ctx(val player: Player, val claim: Claim, val state: MenuState) {
        val root: Claim = bridge.root(claim)
        val isSub: Boolean = claim.parent != null
        val admin: Boolean = settings.isSettingsAdmin(player)
        val canEditRules: Boolean = settings.canEditAny(player, claim)
        val canManageTrust: Boolean = trust.canManage(player, claim)
        fun t(key: String, vararg pairs: Pair<String, Any?>): String {
            var s = lang.raw(player, key) ?: key
            for ((k, v) in pairs) s = s.replace("{$k}", v?.toString() ?: "")
            return s
        }
        fun tl(key: String, vararg pairs: Pair<String, Any?>): List<String> =
            lang.rawList(player, key).map { line -> pairs.fold(line) { acc, (k, v) -> acc.replace("{$k}", v?.toString() ?: "") } }
        val scopeLabel: String get() = FlagText.scopeLabel(lang, player, claim)

        /** 這一格對應的短指令(熟手直接打);模板在語系檔 `menu.cmd.*`。 */
        fun cmd(key: String, vararg pairs: Pair<String, Any?>): String = t("menu.cmd-line", "cmd" to t("menu.cmd.$key", *pairs))
        fun cmdRaw(command: String): String = t("menu.cmd-line", "cmd" to command)
    }

    fun ctx(player: Player, claim: Claim, state: MenuState) = Ctx(player, claim, state)

    fun build(c: Ctx): PageModel = when (c.state.page) {
        MenuPage.OVERVIEW -> overview(c)
        MenuPage.RULES -> rules(c)
        MenuPage.MEMBERS -> members(c)
        MenuPage.MANAGE -> manage(c)
        MenuPage.SCOPES -> scopes(c)
        MenuPage.MY_CLAIMS -> myClaims(c)
        MenuPage.MEMBER_DETAIL -> memberDetail(c)
        MenuPage.ADD_MEMBER -> addMember(c)
        MenuPage.CONFIRM -> confirm(c)
    }

    /** 左欄分頁:四顆,目前這一頁亮起。 */
    fun tabs(player: Player, highlighted: MenuPage?, scopeId: Long): List<Cell> = TAB_SPECS.map { spec ->
        val on = spec.page == highlighted
        val name = (lang.raw(player, "menu.tab.${spec.key}") ?: spec.key)
        Cell(
            spec.icon, spec.fallback,
            if (on) "<color:#ff8fc4><bold>▶ $name</bold></color>" else "<color:#f5f5f5><bold>$name</bold></color>",
            glint = on,
            action = if (on) null else MenuAction.Go(spec.page, scopeId),
        )
    }

    // ------------------------------------------------------------------ 總覽

    private fun overview(c: Ctx): PageModel {
        val info = bridge.getClaimInfo(c.claim)
        val display = ClaimDisplayName.resolve(store, c.root.getID())
        val center = bridge.getSafeTeleportLocation(c.root)
        val role = if (c.admin && bridge.role(c.player, c.claim) == ClaimRole.VISITOR) c.t("menu.role.admin") else c.t("menu.role.${bridge.role(c.player, c.claim).name.lowercase()}")
        val typeLine = when {
            info.isAdminClaim && c.isSub -> c.t("menu.type.admin-sub", "root" to c.root.getID())
            info.isAdminClaim -> c.t("menu.type.admin")
            c.isSub -> c.t("menu.type.sub", "root" to c.root.getID())
            else -> c.t("menu.type.root")
        }
        val infoCell = Cell(
            "claim", Material.GOLDEN_SHOVEL,
            c.t("menu.overview.info-name", "scope" to c.scopeLabel, "name" to esc(display.decorated)),
            listOf(
                c.t("menu.line.type", "value" to typeLine),
                c.t("menu.line.owner", "value" to esc(info.ownerName ?: c.t("menu.owner-admin"))),
                c.t("menu.line.size", "w" to info.width, "h" to info.height, "area" to info.area),
                c.t("menu.line.center", "world" to (center.world?.name ?: "?"), "x" to center.blockX, "z" to center.blockZ),
                c.t("menu.line.role", "value" to role),
                "",
                c.cmd("info"),
            ),
        )
        val children = c.root.children.filter { it.inDataStore }
        val scopeCell = Cell(
            "move", Material.COMPASS,
            c.t("menu.overview.scope-name"),
            listOf(
                c.t("menu.line.current", "value" to c.scopeLabel),
                c.t("menu.line.sub-count", "count" to children.size),
                "",
                c.t("menu.hint.left", "action" to c.t("menu.overview.scope-action")),
                c.cmd("open", "id" to c.claim.getID()),
            ),
            action = MenuAction.Go(MenuPage.SCOPES, c.claim.getID()),
        )
        val teleportCell = teleportCell(c)
        val ownClaims = bridge.getClaimsForPlayer(c.player.uniqueId)
        val thisClaim = mutableListOf(infoCell, scopeCell, teleportCell)
        if (ownClaims.size > 1) {
            thisClaim += Cell(
                "home", Material.OAK_DOOR, c.t("menu.overview.mine-name"),
                listOf(c.t("menu.line.claim-count", "count" to ownClaims.size), "", c.t("menu.hint.left", "action" to c.t("menu.overview.mine-action")), c.cmd("list")),
                action = MenuAction.Go(MenuPage.MY_CLAIMS, c.claim.getID()),
            )
        }

        // 子花域:在主花域看 = 它的子花域;在子花域看 = 回主花域 + 兄弟
        val subCells = ArrayList<Cell>()
        if (c.isSub) subCells += scopeJumpCell(c, c.root)
        children.filter { it.getID() != c.claim.getID() }.forEach { subCells += scopeJumpCell(c, it) }
        val cap = RailLayout.CAPACITY - RailLayout.ITEMS_PER_ROW
        val shown = if (subCells.size > cap) subCells.take(cap - 1) + Cell(
            "next_step", Material.ARROW, c.t("menu.overview.all-scopes"), listOf(c.t("menu.line.sub-count", "count" to children.size)),
            action = MenuAction.Go(MenuPage.SCOPES, c.claim.getID()),
        ) else subCells
        val subBand = if (shown.isEmpty()) {
            listOf(
                Cell(
                    "facility_create", Material.SHEARS, c.t("menu.manage.subdivide-name"),
                    c.tl("menu.manage.subdivide-lore") + listOf("", c.t("menu.hint.left", "action" to c.t("menu.manage.subdivide-action")), c.cmd("subdivide")),
                    action = MenuAction.SubdivideMode,
                ),
            )
        } else shown

        return PageModel(
            title = c.t("menu.title", "scope" to c.scopeLabel, "page" to c.t("menu.tab.overview")),
            bands = listOf(
                Band("this", c.t("menu.band.this"), thisClaim),
                Band("subs", c.t(if (c.isSub) "menu.band.other-scopes" else "menu.band.subs"), subBand),
            ),
            footer = footer(c),
        )
    }

    private fun scopeJumpCell(c: Ctx, target: Claim): Cell {
        val isRoot = target.parent == null
        val overrides = store.explicitFor(target.getID()).size
        return Cell(
            if (isRoot) "claim" else "home_point", if (isRoot) Material.GRASS_BLOCK else Material.MOSS_BLOCK,
            FlagText.scopeLabel(lang, c.player, target).let { "<color:#ffb7d5><bold>$it</bold></color>" },
            listOf(
                c.t("menu.line.size", "w" to target.getWidth(), "h" to target.getHeight(), "area" to target.getArea()),
                c.t("menu.line.overrides", "count" to overrides),
                "",
                c.t("menu.hint.left", "action" to c.t("menu.overview.jump-action")),
                c.cmd("open", "id" to target.getID()),
            ),
            glint = target.getID() == c.claim.getID(),
            action = MenuAction.Go(c.state.page.highlightedTab ?: MenuPage.OVERVIEW, target.getID()),
        )
    }

    private fun teleportCell(c: Ctx): Cell {
        val custom = teleport.resolveCustomSpawn(c.root)
        val dest = custom ?: bridge.getSafeTeleportLocation(c.root)
        return Cell(
            "travel", Material.ENDER_PEARL,
            c.t(if (custom != null) "menu.teleport.name-spawn" else "menu.teleport.name-center"),
            listOf(
                c.t("menu.line.target", "value" to FlagText.scopeLabel(lang, c.player, c.root)),
                c.t("menu.line.coords", "world" to (dest.world?.name ?: "?"), "x" to dest.blockX, "y" to dest.blockY, "z" to dest.blockZ),
                "",
                c.t("menu.hint.left", "action" to c.t("menu.teleport.action")),
                c.cmd("teleport", "target" to esc(ClaimDisplayName.resolve(store, c.root.getID()).alias ?: "#${c.root.getID()}")),
            ),
            action = MenuAction.Teleport,
        )
    }

    // ------------------------------------------------------------------ 環境與保護

    private fun rules(c: Ctx): PageModel {
        val bands = FlagGroup.entries.mapNotNull { group ->
            val flags = ClaimFlags.ALL.filter { it.group == group }
            if (flags.isEmpty()) null else Band(group.name.lowercase(), c.t("menu.band.${group.name.lowercase()}"), flags.map { flagCell(c, it) })
        }.toMutableList()
        val overrides = store.explicitFor(c.claim.getID()).keys.count { ClaimFlags.byKey(it) != null }
        if (c.canEditRules && RailLayout.plan((bands.map { it.id to it.cells.size }) + ("reset" to 1)) != null) {
            bands += Band(
                "reset", c.t("menu.band.reset"),
                listOf(
                    Cell(
                        "cancel", Material.BRUSH,
                        c.t(if (c.isSub) "menu.rules.reset-sub-name" else "menu.rules.reset-root-name"),
                        listOf(c.t("menu.line.overrides", "count" to overrides), "", c.t("menu.hint.left", "action" to c.t("menu.rules.reset-action"))),
                        action = if (overrides > 0) MenuAction.AskConfirm(ConfirmKind.RESET_RULES) else null,
                    ),
                ),
            )
        }
        return PageModel(c.t("menu.title", "scope" to c.scopeLabel, "page" to c.t("menu.tab.rules")), bands, footer(c))
    }

    fun flagCell(c: Ctx, flag: ClaimFlag): Cell {
        val r = settings.resolve(flag, c.claim)
        val available = settings.isAvailable(flag)
        val canEdit = available && settings.canEdit(c.player, c.claim, flag)
        val name = FlagText.name(lang, c.player, flag)
        val effective = FlagText.effectiveWord(lang, c.player, r)
        val inheritWord = c.t(if (c.isSub) "menu.rules.inherit-parent" else "menu.rules.inherit-default")
        val localWord = if (r.local == TriState.INHERIT) inheritWord else FlagText.localWord(lang, c.player, r.local)
        val lore = mutableListOf(
            c.t("menu.line.local", "value" to localWord),
            c.t("menu.line.effective", "value" to effective, "source" to FlagText.sourceWord(lang, c.player, r)),
        )
        if (r.local != TriState.INHERIT) {
            lore += c.t("menu.line.if-inherit", "value" to FlagText.allowWord(lang, c.player, flag, r.inheritedAllowed))
        }
        if (flag.requiresFireTakeover && !worldFireTicks(c.claim)) lore += c.t("menu.rules.world-no-fire")
        lore += ""
        when {
            !available -> lore += c.t("menu.rules.unavailable")
            canEdit -> {
                lore += c.t("menu.hint.flag")
                lore += c.t("menu.hint.flag-inherit", "inherit" to inheritWord)
                lore += c.cmdRaw("${flag.command} on|off|inherit")
            }
            else -> lore += c.t("menu.rules.owner-only")
        }
        return Cell(
            flag.iconId, flag.fallbackMaterial,
            c.t("menu.rules.cell-name", "name" to name, "state" to effective),
            lore,
            glint = r.local != TriState.INHERIT,
            action = if (canEdit) MenuAction.Flag(flag) else null,
        )
    }

    // ------------------------------------------------------------------ 信任與成員

    private fun members(c: Ctx): PageModel {
        val entries = trust.entries(c.claim)
        val perPage = RailLayout.CAPACITY - 2 * RailLayout.ITEMS_PER_ROW
        val pages = maxOf(1, (entries.size + perPage - 1) / perPage)
        val index = c.state.pageIndex.coerceIn(0, pages - 1)
        val slice = entries.drop(index * perPage).take(perPage)
        val memberCells = if (slice.isEmpty()) {
            listOf(Cell("empty", Material.LIGHT_GRAY_DYE, c.t("menu.members.none"), listOf(c.t("menu.members.none-lore"))))
        } else slice.map { memberCell(c, it) }

        val actions = ArrayList<Cell>()
        actions += Cell(
            "invite", Material.WRITABLE_BOOK, c.t("menu.members.add-name"),
            if (c.canManageTrust) listOf(c.t("menu.hint.left", "action" to c.t("menu.members.add-action")), c.cmd("trust-build", "name" to c.t("menu.cmd.player"))) else listOf(c.t("menu.members.manage-only")),
            action = if (c.canManageTrust) MenuAction.Go(MenuPage.ADD_MEMBER, c.claim.getID()) else null,
        )
        if (c.isSub) {
            val restricted = c.claim.getSubclaimRestrictions()
            val can = trust.canChangeSubRestriction(c.player, c.claim)
            val parentCount = trust.entries(c.root).size
            actions += Cell(
                "bond", Material.LEAD,
                c.t(if (restricted) "menu.members.inherit-off-name" else "menu.members.inherit-on-name"),
                listOf(
                    c.t(if (restricted) "menu.members.inherit-off-lore" else "menu.members.inherit-on-lore", "count" to parentCount, "root" to c.root.getID()),
                    "",
                    if (can) c.t("menu.hint.left", "action" to c.t(if (restricted) "menu.members.inherit-enable" else "menu.members.inherit-disable"))
                    else c.t("menu.members.owner-only"),
                    c.cmd("restrict"),
                ),
                glint = restricted,
                action = if (can) MenuAction.SubRestriction(!restricted) else null,
            )
        }
        if (entries.isNotEmpty()) {
            val canClear = trust.isOwnerLevel(c.player, c.claim)
            actions += Cell(
                "delete", Material.LAVA_BUCKET, c.t("menu.members.clear-name"),
                listOf(c.t("menu.members.clear-lore", "count" to entries.size), "", if (canClear) c.t("menu.hint.left", "action" to c.t("menu.members.clear-action")) else c.t("menu.members.owner-only"), c.cmd("untrust", "name" to "all")),
                action = if (canClear) MenuAction.AskConfirm(ConfirmKind.CLEAR_TRUST) else null,
            )
        }
        return PageModel(
            c.t("menu.title", "scope" to c.scopeLabel, "page" to c.t("menu.tab.members")),
            listOf(Band("members", c.t("menu.band.members"), memberCells), Band("member-actions", c.t("menu.band.member-actions"), actions)),
            footer(c, index, pages),
        )
    }

    private fun memberCell(c: Ctx, e: TrustEntry): Cell {
        val levelWord = levelWord(c, e)
        return Cell(
            iconFor(e), Material.PAPER,
            c.t("menu.members.cell-name", "name" to esc(nameOf(c, e)), "level" to levelWord),
            listOf(
                c.t("menu.line.level", "value" to (e.level?.let { c.t("menu.level.${GpCompat.levelKey(it)}") } ?: c.t("menu.level.none"))),
                c.t("menu.line.manager", "value" to c.t(if (e.manager) "menu.yes-word" else "menu.no-word")),
                "",
                c.t("menu.hint.left", "action" to c.t("menu.members.open-action")),
            ),
            action = MenuAction.Go(MenuPage.MEMBER_DETAIL, c.claim.getID(), memberId = e.id),
            head = e.uuid,
        )
    }

    private fun memberDetail(c: Ctx): PageModel {
        val id = c.state.memberId ?: return members(c)
        val e = trust.entry(c.claim, id)
        val trusted = e.level != null || e.manager
        val head = Cell(
            iconFor(e), Material.PAPER,
            "<color:#ffb7d5><bold>${esc(nameOf(c, e))}</bold></color>",
            listOf(
                c.t("menu.line.level", "value" to (e.level?.let { c.t("menu.level.${GpCompat.levelKey(it)}") } ?: c.t("menu.level.none"))),
                c.t("menu.line.manager", "value" to c.t(if (e.manager) "menu.yes-word" else "menu.no-word")),
                c.t("menu.line.scope", "value" to c.scopeLabel),
            ),
            head = e.uuid,
        )
        val levels = listOf(ClaimPermission.Build, GpCompat.CONTAINER, ClaimPermission.Access).map { level ->
            val current = e.level == level
            val can = trust.canGrant(c.player, c.claim, level)
            Cell(
                LEVEL_ICONS.getValue(level).first, LEVEL_ICONS.getValue(level).second,
                c.t("menu.members.level-name", "level" to c.t("menu.level.${GpCompat.levelKey(level)}"), "mark" to if (current) c.t("menu.current-mark") else ""),
                c.tl("menu.level.${GpCompat.levelKey(level)}-lore") + listOf(
                    "",
                    when {
                        current -> c.t("menu.members.level-current")
                        can -> c.t("menu.hint.left", "action" to c.t("menu.members.level-action"))
                        else -> c.t("menu.members.level-cannot")
                    },
                    c.cmd("trust-${GpCompat.levelKey(level)}", "name" to cmdName(e)),
                ),
                glint = current,
                action = if (!current && can) MenuAction.MemberLevel(id, level) else null,
            )
        }
        val ownerLevel = trust.isOwnerLevel(c.player, c.claim)
        val canManage = trust.canManage(c.player, c.claim)
        val managerCell = Cell(
            "settings", Material.GOLDEN_HELMET,
            c.t(if (e.manager) "menu.members.manager-revoke-name" else "menu.members.manager-grant-name"),
            c.tl("menu.level.manage-lore") + listOf("", if (ownerLevel) c.t("menu.hint.left", "action" to c.t(if (e.manager) "menu.members.manager-revoke" else "menu.members.manager-grant")) else c.t("menu.members.owner-only"), c.cmd("trust-manage", "name" to cmdName(e))),
            glint = e.manager,
            action = if (ownerLevel && e.id != "public") MenuAction.MemberManager(id, !e.manager) else null,
        )
        val bands = mutableListOf(
            Band("who", c.t("menu.band.member"), listOf(head)),
            Band("level", c.t("menu.band.level"), levels),
        )
        if (e.id != "public") bands += Band("manager", c.t("menu.band.manager"), listOf(managerCell))
        if (trusted) {
            val canRemove = canManage && (!e.manager || ownerLevel)
            bands += Band(
                "remove", c.t("menu.band.remove"),
                listOf(
                    Cell(
                        "delete", Material.BARRIER, c.t("menu.members.remove-name"),
                        listOf(c.t(if (c.isSub) "menu.members.remove-lore-sub" else "menu.members.remove-lore"), "", if (canRemove) c.t("menu.hint.left", "action" to c.t("menu.members.remove-action")) else c.t("menu.members.manage-only"), c.cmd("untrust", "name" to cmdName(e))),
                        action = if (canRemove) MenuAction.AskConfirm(ConfirmKind.REMOVE_MEMBER) else null,
                    ),
                ),
            )
        }
        return PageModel(
            c.t("menu.title", "scope" to c.scopeLabel, "page" to esc(nameOf(c, e))),
            bands,
            footer(c, back = MenuAction.Go(MenuPage.MEMBERS, c.claim.getID())),
        )
    }

    private fun addMember(c: Ctx): PageModel {
        val existing = trust.entries(c.claim).map { it.id.lowercase() }.toSet()
        val ownerId = c.claim.getOwnerID()
        val candidates = c.player.server.onlinePlayers
            .filter { it.uniqueId != ownerId && it.uniqueId.toString() !in existing }
            .sortedBy { it.name.lowercase() }
            .map { p ->
                Cell(
                    "visit", Material.PLAYER_HEAD, "<color:#ffb7d5><bold>${esc(p.name)}</bold></color>",
                    listOf(c.t("menu.hint.left", "action" to c.t("menu.members.pick-action"))),
                    action = MenuAction.Go(MenuPage.MEMBER_DETAIL, c.claim.getID(), memberId = p.uniqueId.toString()),
                    head = p.uniqueId,
                )
            }
        val all = buildList {
            if ("public" !in existing) add(
                Cell(
                    "visit", Material.OAK_SIGN, c.t("menu.members.public-name"), listOf(c.t("menu.members.public-lore"), "", c.t("menu.hint.left", "action" to c.t("menu.members.pick-action"))),
                    action = MenuAction.Go(MenuPage.MEMBER_DETAIL, c.claim.getID(), memberId = "public"),
                ),
            )
            addAll(candidates)
        }
        val perPage = RailLayout.CAPACITY
        val pages = maxOf(1, (all.size + perPage - 1) / perPage)
        val index = c.state.pageIndex.coerceIn(0, pages - 1)
        val slice = all.drop(index * perPage).take(perPage).ifEmpty {
            listOf(Cell("empty", Material.LIGHT_GRAY_DYE, c.t("menu.members.no-candidates"), listOf(c.t("menu.members.no-candidates-lore"))))
        }
        return PageModel(
            c.t("menu.title", "scope" to c.scopeLabel, "page" to c.t("menu.members.add-name")),
            listOf(Band("candidates", c.t("menu.band.candidates"), slice)),
            footer(c, index, pages, back = MenuAction.Go(MenuPage.MEMBERS, c.claim.getID())),
        )
    }

    // ------------------------------------------------------------------ 花域管理

    private fun manage(c: Ctx): PageModel {
        val rootId = c.root.getID()
        val display = ClaimDisplayName.resolve(store, rootId)
        val ownsRoot = c.root.getOwnerID() == c.player.uniqueId
        val canIdentity = ownsRoot || c.admin
        val spawn = teleport.resolveCustomSpawn(c.root)
        val spawnRaw = store.getSpawnRaw(rootId) != null
        val rootTag = if (c.isSub) c.t("menu.manage.belongs-root", "root" to rootId) else ""
        val identity = listOf(
            Cell(
                "rename", Material.NAME_TAG, c.t("menu.manage.rename-name"),
                listOfNotNull(
                    c.t("menu.line.current", "value" to esc(display.alias ?: c.t("menu.none"))),
                    rootTag.takeIf { it.isNotEmpty() },
                    "",
                    if (canIdentity) c.t("menu.hint.left", "action" to c.t("menu.manage.rename-action")) else c.t("menu.members.owner-only"),
                    c.cmd("rename", "id" to rootId),
                ),
                action = if (canIdentity) MenuAction.Rename else null,
            ),
            Cell(
                "warp_point", Material.LODESTONE, c.t("menu.manage.spawn-name"),
                listOfNotNull(
                    when {
                        spawn != null -> c.t("menu.line.coords", "world" to (spawn.world?.name ?: "?"), "x" to spawn.blockX, "y" to spawn.blockY, "z" to spawn.blockZ)
                        spawnRaw -> c.t("menu.manage.spawn-outside")
                        else -> c.t("menu.manage.spawn-none")
                    },
                    rootTag.takeIf { it.isNotEmpty() },
                    "",
                    if (canIdentity) c.t("menu.hint.left", "action" to c.t("menu.manage.spawn-set")) else c.t("menu.members.owner-only"),
                    if (canIdentity && spawnRaw) c.t("menu.hint.right", "action" to c.t("menu.manage.spawn-clear")) else null,
                    c.cmd("spawn"),
                ),
                action = if (canIdentity) MenuAction.Spawn else null,
            ),
            teleportCell(c),
        )
        val shape = listOf(
            Cell(
                "facility_create", Material.SHEARS, c.t("menu.manage.subdivide-name"),
                c.tl("menu.manage.subdivide-lore") + listOf("", c.t("menu.hint.left", "action" to c.t("menu.manage.subdivide-action")), c.cmd("subdivide")),
                action = MenuAction.SubdivideMode,
            ),
            Cell(
                "boundary_lining", Material.GOLDEN_SHOVEL, c.t("menu.manage.resize-name"),
                c.tl("menu.manage.resize-lore") + listOf("", c.t("menu.hint.left", "action" to c.t("menu.manage.resize-action")), c.cmd("basic")),
                action = MenuAction.BasicMode,
            ),
        )
        val danger = ArrayList<Cell>()
        val canAbandon = c.claim.checkPermission(c.player, ClaimPermission.Edit, null) == null
        danger += Cell(
            "delete", Material.LAVA_BUCKET,
            c.t(if (c.isSub) "menu.manage.abandon-sub-name" else "menu.manage.abandon-name"),
            c.tl(if (c.isSub) "menu.manage.abandon-sub-lore" else "menu.manage.abandon-lore", "count" to c.claim.children.size) +
                listOf(
                    "",
                    if (canAbandon) c.t("menu.hint.left", "action" to c.t("menu.manage.abandon-action")) else c.t("menu.members.owner-only"),
                    c.cmd(if (!c.isSub && c.claim.children.isNotEmpty()) "abandon-top" else "abandon"),
                ),
            action = if (canAbandon) MenuAction.AskConfirm(ConfirmKind.ABANDON) else null,
        )
        if (c.admin) {
            danger += Cell(
                "delete", Material.TNT, c.t("menu.manage.admin-delete-name"),
                listOf(c.t("menu.manage.admin-delete-lore"), "", c.t("menu.hint.left", "action" to c.t("menu.manage.admin-delete-action")), c.cmd("admin-delete", "id" to c.claim.getID())),
                action = MenuAction.AskConfirm(ConfirmKind.ADMIN_DELETE),
            )
        }
        return PageModel(
            c.t("menu.title", "scope" to c.scopeLabel, "page" to c.t("menu.tab.manage")),
            listOf(
                Band("identity", c.t("menu.band.identity"), identity),
                Band("shape", c.t("menu.band.shape"), shape),
                Band("danger", c.t("menu.band.danger"), danger),
            ),
            footer(c),
        )
    }

    // ------------------------------------------------------------------ 選擇範圍/我的花域

    private fun scopes(c: Ctx): PageModel {
        val all = listOf(c.root) + c.root.children.filter { it.inDataStore }
        return paged(c, "scopes", c.t("menu.band.scopes"), all.map { scopeJumpCell(c, it).copy(action = MenuAction.Go(MenuPage.OVERVIEW, it.getID())) }, c.t("menu.overview.scope-name"))
    }

    private fun myClaims(c: Ctx): PageModel {
        val claims = bridge.getClaimsForPlayer(c.player.uniqueId).filter { it.inDataStore }
        val cells = claims.map { claim ->
            val display = ClaimDisplayName.resolve(store, claim.getID())
            Cell(
                "claim", Material.GRASS_BLOCK,
                "<color:#ffb7d5><bold>${esc(display.decorated)}</bold></color> <color:#a8a8a8>#${claim.getID()}</color>",
                listOf(
                    c.t("menu.line.size", "w" to claim.getWidth(), "h" to claim.getHeight(), "area" to claim.getArea()),
                    c.t("menu.line.sub-count", "count" to claim.children.size),
                    "",
                    c.t("menu.hint.left", "action" to c.t("menu.overview.jump-action")),
                ),
                glint = claim.getID() == c.root.getID(),
                action = MenuAction.Go(MenuPage.OVERVIEW, claim.getID()),
            )
        }
        return paged(c, "mine", c.t("menu.band.mine"), cells, c.t("menu.overview.mine-name"))
    }

    private fun paged(c: Ctx, id: String, heading: String, cells: List<Cell>, pageName: String): PageModel {
        val perPage = RailLayout.CAPACITY
        val pages = maxOf(1, (cells.size + perPage - 1) / perPage)
        val index = c.state.pageIndex.coerceIn(0, pages - 1)
        return PageModel(
            c.t("menu.title", "scope" to c.scopeLabel, "page" to pageName),
            listOf(Band(id, heading, cells.drop(index * perPage).take(perPage))),
            footer(c, index, pages, back = MenuAction.Go(MenuPage.OVERVIEW, c.claim.getID())),
        )
    }

    // ------------------------------------------------------------------ 確認

    private fun confirm(c: Ctx): PageModel {
        val kind = c.state.confirm ?: return overview(c)
        val key = kind.name.lowercase().replace('_', '-')
        val member = c.state.memberId?.let { trust.entry(c.claim, it) }
        val pairs = arrayOf<Pair<String, Any?>>(
            "scope" to c.scopeLabel,
            "count" to c.claim.children.size,
            "name" to (member?.let { esc(nameOf(c, it)) } ?: ""),
        )
        val back = MenuAction.Go(kind.returnPage, c.claim.getID(), memberId = c.state.memberId)
        return PageModel(
            c.t("menu.title", "scope" to c.scopeLabel, "page" to c.t("menu.confirm.page")),
            listOf(
                Band(
                    "confirm", c.t("menu.band.confirm"),
                    listOf(
                        Cell("warning", Material.PAPER, c.t("menu.confirm.$key-name", *pairs), c.tl("menu.confirm.$key-lore", *pairs)),
                        Cell("confirm", Material.LIME_CONCRETE, c.t("menu.confirm.accept"), listOf(c.t("menu.hint.left", "action" to c.t("menu.confirm.$key-action", *pairs))), action = MenuAction.Execute(kind)),
                        Cell("cancel", Material.RED_CONCRETE, c.t("menu.confirm.decline"), listOf(c.t("menu.hint.left", "action" to c.t("menu.confirm.no-action"))), action = back),
                    ),
                ),
            ),
            footer(c, back = back),
        )
    }

    // ------------------------------------------------------------------ 共用

    private fun footer(c: Ctx, index: Int = 0, pages: Int = 1, back: MenuAction? = null): Map<FooterSlot, Cell> {
        val out = LinkedHashMap<FooterSlot, Cell>()
        if (back != null) out[FooterSlot.BACK] = Cell("back", Material.ARROW, c.t("menu.footer.back"), action = back)
        if (c.admin) out[FooterSlot.HOME] = Cell("admin", Material.COMMAND_BLOCK, c.t("menu.footer.admin"), listOf(c.t("menu.hint.left", "action" to c.t("menu.footer.admin-action"))), action = MenuAction.AdminPanel)
        if (pages > 1) {
            if (index > 0) out[FooterSlot.PREVIOUS] = Cell("previous_page", Material.ARROW, c.t("menu.footer.previous"), action = MenuAction.Turn(-1))
            out[FooterSlot.INDICATOR] = Cell("page_indicator", Material.PAPER, c.t("menu.footer.page", "page" to index + 1, "pages" to pages))
            if (index < pages - 1) out[FooterSlot.NEXT] = Cell("next_page", Material.ARROW, c.t("menu.footer.next"), action = MenuAction.Turn(1))
        }
        out[FooterSlot.HELP] = Cell("help", Material.KNOWLEDGE_BOOK, c.t("menu.footer.help"), c.tl("menu.footer.help-lore"))
        out[FooterSlot.CLOSE] = Cell("close", Material.BARRIER, c.t("menu.footer.close"), action = MenuAction.Close)
        return out
    }

    private fun levelWord(c: Ctx, e: TrustEntry): String {
        val parts = ArrayList<String>()
        e.level?.let { parts += c.t("menu.level.${GpCompat.levelKey(it)}") }
        if (e.manager) parts += c.t("menu.level.manage")
        return parts.joinToString(" · ").ifEmpty { c.t("menu.level.none") }
    }

    private fun nameOf(c: Ctx, e: TrustEntry): String = when {
        e.id == "public" -> c.t("menu.members.public-name-plain")
        else -> e.displayName
    }

    /** 指令裡的對象:玩家名稱、public,或 [權限節點]。 */
    private fun cmdName(e: TrustEntry): String = esc(if (e.uuid != null) e.displayName else e.id)

    private fun iconFor(e: TrustEntry): String = if (e.uuid != null) "visit" else if (e.id == "public") "visit" else "settings"

    private fun esc(text: String): String = escapeForMiniMessageTemplate(text)

    private data class TabSpec(val page: MenuPage, val key: String, val icon: String, val fallback: Material)

    companion object {
        private val TAB_SPECS = listOf(
            TabSpec(MenuPage.OVERVIEW, "overview", "claim", Material.GRASS_BLOCK),
            TabSpec(MenuPage.RULES, "rules", "settings", Material.COMPARATOR),
            TabSpec(MenuPage.MEMBERS, "members", "invite", Material.PLAYER_HEAD),
            TabSpec(MenuPage.MANAGE, "manage", "section_home_land", Material.GOLDEN_SHOVEL),
        )

        private val LEVEL_ICONS: Map<ClaimPermission, Pair<String, Material>> = mapOf(
            ClaimPermission.Build to ("tool_material_hammer" to Material.BRICKS),
            GpCompat.CONTAINER to ("warehouse" to Material.CHEST),
            ClaimPermission.Access to ("visit" to Material.OAK_DOOR),
        )
    }
}
