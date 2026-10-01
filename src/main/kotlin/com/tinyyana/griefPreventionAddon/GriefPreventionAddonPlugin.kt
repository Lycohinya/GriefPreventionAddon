package com.tinyyana.griefPreventionAddon

import com.tinyyana.griefPreventionAddon.audit.AuditLogger
import com.tinyyana.griefPreventionAddon.claim.ClaimLifecycleListener
import com.tinyyana.griefPreventionAddon.claim.NativeCommandRedirect
import com.tinyyana.griefPreventionAddon.command.ClaimAdminCommand
import com.tinyyana.griefPreventionAddon.command.ClaimInfoCommand
import com.tinyyana.griefPreventionAddon.command.ClaimNameCommand
import com.tinyyana.griefPreventionAddon.command.ClaimSettingsCommand
import com.tinyyana.griefPreventionAddon.command.ClaimSpawnCommand
import com.tinyyana.griefPreventionAddon.command.ClaimTpCommand
import com.tinyyana.griefPreventionAddon.command.ClaimsListCommand
import com.tinyyana.griefPreventionAddon.command.FlagCommand
import com.tinyyana.griefPreventionAddon.fire.FireTakeover
import com.tinyyana.griefPreventionAddon.gui.ClaimAdminGuiListener
import com.tinyyana.griefPreventionAddon.gui.ClaimAdminListGuiService
import com.tinyyana.griefPreventionAddon.gui.ClaimMenu
import com.tinyyana.griefPreventionAddon.gui.ClaimMenuListener
import com.tinyyana.griefPreventionAddon.i18n.LanguageManager
import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.policy.ClaimFlag
import com.tinyyana.griefPreventionAddon.policy.ClaimFlags
import com.tinyyana.griefPreventionAddon.policy.ClaimPolicyResolver
import com.tinyyana.griefPreventionAddon.policy.ClaimSettingsService
import com.tinyyana.griefPreventionAddon.policy.ConfiguredFlagDefaults
import com.tinyyana.griefPreventionAddon.sethome.SethomeRestrictionListener
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
import com.tinyyana.griefPreventionAddon.storage.Db
import com.tinyyana.griefPreventionAddon.teleport.ClaimTeleportService
import com.tinyyana.griefPreventionAddon.tnt.TntExplosionListener
import com.tinyyana.griefPreventionAddon.toggle.ClaimToggleListener
import com.tinyyana.griefPreventionAddon.trust.ClaimTrustService
import me.ryanhamshire.GriefPrevention.Claim
import org.bukkit.GameRules
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

/**
 * 組裝點。資料流:
 * ```
 * ClaimFlags(登記) ─┬─ ClaimSettingsStore(SQLite + 快取,三態明確值)
 *                  ├─ ClaimPolicyResolver(子 → 父 → 預設)── 事件:生怪/PVP/TNT/設家/火焰
 *                  └─ ClaimSettingsService(唯一寫入口)──── 指令 FlagCommand / 花域選單 / /cadmin set
 * ClaimTrustService(只讀寫 GP 的 trust)──── 花域選單的成員頁
 * ```
 * 新增一項規則的步驟見 README「維護地圖」。
 */
class GriefPreventionAddonPlugin : JavaPlugin() {

    lateinit var db: Db
        private set
    lateinit var claimSettingsStore: ClaimSettingsStore
        private set
    lateinit var griefPreventionBridge: GriefPreventionBridge
        private set
    lateinit var resolver: ClaimPolicyResolver
        private set
    lateinit var settingsService: ClaimSettingsService
        private set
    lateinit var lang: LanguageManager
        private set
    private var fireTakeover: FireTakeover? = null

    override fun onEnable() {
        saveDefaultConfig()

        val defaultLanguage = config.getString("locale.default-language", "zh_TW") ?: "zh_TW"
        val autoDetectLocale = config.getBoolean("locale.auto-detect-client-locale", true)
        lang = LanguageManager(this, defaultLanguage = defaultLanguage, autoDetectClientLocale = autoDetectLocale)
        lang.load()

        val auditLogger = AuditLogger(File(dataFolder, "logs"), logger)
        griefPreventionBridge = GriefPreventionBridge(this)
        val bridge = griefPreventionBridge

        db = Db(File(dataFolder, "data.db"), logger)
        claimSettingsStore = ClaimSettingsStore(db, logger)
        val store = claimSettingsStore
        store.init()
        // schema v2(三態)。子花域殘留清理需要 GP 回答 id 是什麼;GP 不在時留到下次啟動。
        store.migrateToV2(File(dataFolder, "backups"), if (bridge.isAvailable()) bridge::classify else null)
        if (config.getBoolean("migration.auto-migrate-lst", true)) {
            val lstDbPath = config.getString("migration.lst-db-path", "../LycoServerTweaks/data.db") ?: "../LycoServerTweaks/data.db"
            store.migrateFromLycoServerTweaks(File(dataFolder, lstDbPath))
        }

        val defaults = ConfiguredFlagDefaults(config, bridge::fireConfig)
        resolver = ClaimPolicyResolver(store, defaults)
        val fire = if (config.getBoolean("fire.takeover", true)) FireTakeover(this, bridge, resolver, logger) else null
        fireTakeover = fire
        settingsService = ClaimSettingsService(
            bridge, store, resolver, auditLogger,
            fireActive = { fire?.active == true },
            notify = { player, flag, allowed -> notifyChange(player, flag, allowed) },
        )
        val settings = settingsService
        val trust = ClaimTrustService(bridge, auditLogger)
        val teleport = ClaimTeleportService(bridge, store, lang, auditLogger)
        val spawnCmd = ClaimSpawnCommand(bridge, store, teleport, lang, auditLogger)

        lateinit var adminGui: ClaimAdminListGuiService
        val menu = ClaimMenu(
            this, bridge, store, settings, trust, teleport, spawnCmd, lang, auditLogger,
            openAdminPanel = { adminGui.open(it) },
            worldFireTicks = ::worldFireTicks,
        )
        adminGui = ClaimAdminListGuiService(bridge, store, settings, lang)

        // GP 的 /sethome 存取信任檢查由 addon 接手;TNT 原生欄位與火焰事件同步成 addon 的規則
        bridge.takeOverSethomeCommands()
        bridge.syncAllExplosives(resolver)
        fire?.takeOver()

        val pm = server.pluginManager
        // 選單用殼層背景、空格是真的空的——拖曳要由程式擋(見 docs/ux/PLAYER_SHELL.md §3)。一個插件只需註冊一次。
        pm.registerEvents(com.tinyyana.lycoLib.menu.MenuGuardListener(), this)
        pm.registerEvents(TntExplosionListener(bridge, resolver), this)
        pm.registerEvents(ClaimToggleListener(bridge, resolver), this)
        pm.registerEvents(ClaimLifecycleListener(this, store, bridge, resolver, fire), this)
        pm.registerEvents(SethomeRestrictionListener(bridge, resolver, lang), this)
        pm.registerEvents(NativeCommandRedirect(bridge, settings, lang), this)
        pm.registerEvents(ClaimMenuListener(menu), this)
        pm.registerEvents(ClaimAdminGuiListener(adminGui, menu, bridge, teleport, lang), this)

        // 規則指令:同一個實作,由 ClaimFlag.command 分組(/cmob hostile → cmob 管全部生怪項)。
        // 名稱要在 plugin.yml 登記(本名或別名都可以)。
        val flagCommands: Map<String, List<ClaimFlag>> = ClaimFlags.ALL.groupBy { it.command.removePrefix("/").substringBefore(' ') }
        flagCommands.forEach { (name, flags) ->
            val executor = FlagCommand(bridge, settings, lang, flags)
            getCommand(name)?.let {
                it.setExecutor(executor)
                it.tabCompleter = executor
            }
        }

        val settingsCmd = ClaimSettingsCommand(bridge, store, menu, lang)
        getCommand("csettings")?.let {
            it.setExecutor(settingsCmd)
            it.tabCompleter = settingsCmd
        }
        getCommand("claiminfo")?.setExecutor(ClaimInfoCommand(bridge, store, lang))
        val tpCmd = ClaimTpCommand(bridge, store, teleport, lang)
        getCommand("claimtp")?.let {
            it.setExecutor(tpCmd)
            it.tabCompleter = tpCmd
        }
        val nameCmd = ClaimNameCommand(bridge, store, lang, auditLogger)
        getCommand("claimname")?.let {
            it.setExecutor(nameCmd)
            it.tabCompleter = nameCmd
        }
        getCommand("claimspawn")?.let {
            it.setExecutor(spawnCmd)
            it.tabCompleter = spawnCmd
        }
        getCommand("claimslist")?.setExecutor(ClaimsListCommand(bridge, store, lang))
        val adminCmd = ClaimAdminCommand(
            bridge = bridge,
            store = store,
            adminGuiService = adminGui,
            menu = menu,
            settings = settings,
            trust = trust,
            fireStatus = { if (fire == null) "takeover=disabled-by-config" else "takeover=${fire.active}" },
            teleportService = teleport,
            lang = lang,
            auditLogger = auditLogger,
        )
        getCommand("claimadmin")?.let {
            it.setExecutor(adminCmd)
            it.tabCompleter = adminCmd
        }

        logger.info("[GriefPreventionAddon] Plugin successfully enabled (version: ${description.version}, fire takeover: ${fire?.active == true}).")
    }

    /** 規則改變後,站在受影響範圍裡、實際值有變的玩家收到通知(目前只有 PVP)。 */
    private fun notifyChange(player: Player, flag: ClaimFlag, allowed: Boolean) {
        if (flag == ClaimFlags.PVP) player.sendMessage(lang.get(player, if (allowed) "pvp.allowed" else "pvp.blocked"))
    }

    /** 世界規則允許火自己燒嗎(關掉時火焰兩項在畫面上註明;讀 world 的 gamerule 不碰方塊)。 */
    private fun worldFireTicks(claim: Claim): Boolean {
        val world = claim.lesserBoundaryCorner?.world ?: return true
        return runCatching { (world.getGameRuleValue(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER) ?: 128) != 0 }.getOrDefault(true)
    }

    override fun onDisable() {
        fireTakeover?.release()
        if (::db.isInitialized) {
            db.close()
        }
        logger.info("[GriefPreventionAddon] Plugin disabled.")
    }
}
