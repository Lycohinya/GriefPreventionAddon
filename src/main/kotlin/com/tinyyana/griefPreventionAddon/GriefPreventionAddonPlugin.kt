package com.tinyyana.griefPreventionAddon

import com.tinyyana.griefPreventionAddon.audit.AuditLogger
import com.tinyyana.griefPreventionAddon.claim.ClaimLifecycleListener
import com.tinyyana.griefPreventionAddon.command.ClaimAdminCommand
import com.tinyyana.griefPreventionAddon.command.ClaimInfoCommand
import com.tinyyana.griefPreventionAddon.command.ClaimMobsCommand
import com.tinyyana.griefPreventionAddon.command.ClaimNameCommand
import com.tinyyana.griefPreventionAddon.command.ClaimPvpCommand
import com.tinyyana.griefPreventionAddon.command.ClaimSethomeCommand
import com.tinyyana.griefPreventionAddon.command.ClaimSettingsCommand
import com.tinyyana.griefPreventionAddon.command.ClaimSpawnCommand
import com.tinyyana.griefPreventionAddon.command.ClaimTntCommand
import com.tinyyana.griefPreventionAddon.command.ClaimTpCommand
import com.tinyyana.griefPreventionAddon.command.ClaimsListCommand
import com.tinyyana.griefPreventionAddon.gui.ClaimAdminGuiListener
import com.tinyyana.griefPreventionAddon.gui.ClaimAdminListGuiService
import com.tinyyana.griefPreventionAddon.gui.ClaimSettingsGuiListener
import com.tinyyana.griefPreventionAddon.gui.ClaimSettingsGuiService
import com.tinyyana.griefPreventionAddon.i18n.LanguageManager
import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.sethome.SethomeRestrictionListener
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
import com.tinyyana.griefPreventionAddon.storage.Db
import com.tinyyana.griefPreventionAddon.teleport.ClaimTeleportService
import com.tinyyana.griefPreventionAddon.tnt.TntExplosionListener
import com.tinyyana.griefPreventionAddon.toggle.ClaimToggleListener
import me.ryanhamshire.GriefPrevention.GriefPrevention
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class GriefPreventionAddonPlugin : JavaPlugin() {

    lateinit var db: Db
        private set
    lateinit var claimSettingsStore: ClaimSettingsStore
        private set
    lateinit var griefPreventionBridge: GriefPreventionBridge
        private set
    lateinit var claimTeleportService: ClaimTeleportService
        private set
    lateinit var lang: LanguageManager
        private set
    lateinit var auditLogger: AuditLogger
        private set
    lateinit var guiService: ClaimSettingsGuiService
        private set
    lateinit var adminGuiService: ClaimAdminListGuiService
        private set

    override fun onEnable() {
        saveDefaultConfig()

        val defaultLanguage = config.getString("locale.default-language", "zh_TW") ?: "zh_TW"
        val autoDetectLocale = config.getBoolean("locale.auto-detect-client-locale", true)
        lang = LanguageManager(this, defaultLanguage = defaultLanguage, autoDetectClientLocale = autoDetectLocale)
        lang.load()

        val logsDir = File(dataFolder, "logs")
        auditLogger = AuditLogger(logsDir, logger)

        val dbFile = File(dataFolder, "data.db")
        db = Db(dbFile)
        claimSettingsStore = ClaimSettingsStore(db, logger)
        claimSettingsStore.init()

        // Migrate legacy records if configured
        if (config.getBoolean("migration.auto-migrate-lst", true)) {
            val lstDbPath = config.getString("migration.lst-db-path", "../LycoServerTweaks/data.db") ?: "../LycoServerTweaks/data.db"
            val lstDbFile = File(dataFolder, lstDbPath)
            claimSettingsStore.migrateFromLycoServerTweaks(lstDbFile)
        }

        griefPreventionBridge = GriefPreventionBridge(this)
        claimTeleportService = ClaimTeleportService(griefPreventionBridge, claimSettingsStore, lang, auditLogger)
        guiService = ClaimSettingsGuiService(griefPreventionBridge, claimSettingsStore, claimTeleportService, lang)
        adminGuiService = ClaimAdminListGuiService(griefPreventionBridge, claimSettingsStore, lang)

        val spawnCmd = ClaimSpawnCommand(
            bridge = griefPreventionBridge,
            store = claimSettingsStore,
            teleportService = claimTeleportService,
            lang = lang,
            auditLogger = auditLogger,
        )

        // Intercept GriefPrevention's hardcoded CommandsRequiringAccessTrust if /sethome is in it
        takeOverGriefPreventionSethome()

        // Register Listeners
        val pm = server.pluginManager
        pm.registerEvents(TntExplosionListener(griefPreventionBridge, claimSettingsStore), this)
        pm.registerEvents(ClaimToggleListener(griefPreventionBridge, claimSettingsStore), this)
        pm.registerEvents(ClaimLifecycleListener(claimSettingsStore), this)
        pm.registerEvents(SethomeRestrictionListener(griefPreventionBridge, claimSettingsStore, lang), this)
        pm.registerEvents(
            ClaimSettingsGuiListener(
                guiService = guiService,
                adminGuiService = adminGuiService,
                bridge = griefPreventionBridge,
                store = claimSettingsStore,
                teleportService = claimTeleportService,
                spawnCommand = spawnCmd,
                lang = lang,
                auditLogger = auditLogger,
            ),
            this,
        )
        pm.registerEvents(
            ClaimAdminGuiListener(
                adminGuiService = adminGuiService,
                settingsGuiService = guiService,
                bridge = griefPreventionBridge,
                teleportService = claimTeleportService,
                lang = lang,
            ),
            this,
        )

        // Register Commands
        val tntCmd = ClaimTntCommand(griefPreventionBridge, claimSettingsStore, lang, auditLogger)
        getCommand("ctnt")?.let {
            it.setExecutor(tntCmd)
            it.tabCompleter = tntCmd
        }

        val settingsCmd = ClaimSettingsCommand(griefPreventionBridge, claimSettingsStore, guiService, lang)
        getCommand("csettings")?.let {
            it.setExecutor(settingsCmd)
            it.tabCompleter = settingsCmd
        }

        val pvpCmd = ClaimPvpCommand(griefPreventionBridge, claimSettingsStore, lang, auditLogger)
        getCommand("pvpinclaim")?.setExecutor(pvpCmd)

        val sethomeCmd = ClaimSethomeCommand(griefPreventionBridge, claimSettingsStore, lang, auditLogger)
        getCommand("csethome")?.let {
            it.setExecutor(sethomeCmd)
            it.tabCompleter = sethomeCmd
        }

        val mobsCmd = ClaimMobsCommand(griefPreventionBridge, claimSettingsStore, lang, auditLogger)
        getCommand("claimmobs")?.let {
            it.setExecutor(mobsCmd)
            it.tabCompleter = mobsCmd
        }

        val infoCmd = ClaimInfoCommand(griefPreventionBridge, claimSettingsStore, lang)
        getCommand("claiminfo")?.setExecutor(infoCmd)

        val tpCmd = ClaimTpCommand(griefPreventionBridge, claimSettingsStore, claimTeleportService, lang)
        getCommand("claimtp")?.let {
            it.setExecutor(tpCmd)
            it.tabCompleter = tpCmd
        }

        val nameCmd = ClaimNameCommand(griefPreventionBridge, claimSettingsStore, lang, auditLogger)
        getCommand("claimname")?.let {
            it.setExecutor(nameCmd)
            it.tabCompleter = nameCmd
        }

        getCommand("claimspawn")?.let {
            it.setExecutor(spawnCmd)
            it.tabCompleter = spawnCmd
        }

        val listCmd = ClaimsListCommand(griefPreventionBridge, claimSettingsStore, lang)
        getCommand("claimslist")?.setExecutor(listCmd)

        val adminCmd = ClaimAdminCommand(
            bridge = griefPreventionBridge,
            store = claimSettingsStore,
            adminGuiService = adminGuiService,
            settingsGuiService = guiService,
            teleportService = claimTeleportService,
            lang = lang,
            auditLogger = auditLogger,
        )
        getCommand("claimadmin")?.let {
            it.setExecutor(adminCmd)
            it.tabCompleter = adminCmd
        }

        logger.info("[GriefPreventionAddon] Plugin successfully enabled (version: ${description.version}).")
    }

    private fun takeOverGriefPreventionSethome() {
        runCatching {
            if (griefPreventionBridge.isAvailable()) {
                val gpInstance = GriefPrevention.instance
                val list = gpInstance.config_claims_commandsRequiringAccessTrust
                if (list != null) {
                    val removed = list.removeIf { cmd ->
                        cmd.equals("/sethome", ignoreCase = true) ||
                            cmd.equals("/esethome", ignoreCase = true) ||
                            cmd.equals("sethome", ignoreCase = true)
                    }
                    if (removed) {
                        logger.info("[GriefPreventionAddon] Took over '/sethome' access-trust enforcement from GriefPrevention.")
                    }
                }
            }
        }.onFailure {
            logger.fine("[GriefPreventionAddon] Could not inspect GriefPrevention commandsRequiringAccessTrust: ${it.message}")
        }
    }

    override fun onDisable() {
        if (::db.isInitialized) {
            db.close()
        }
        logger.info("[GriefPreventionAddon] Plugin disabled.")
    }
}
