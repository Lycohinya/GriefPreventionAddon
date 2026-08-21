package com.tinyyana.griefPreventionAddon

import com.tinyyana.griefPreventionAddon.claim.ClaimLifecycleListener
import com.tinyyana.griefPreventionAddon.command.ClaimInfoCommand
import com.tinyyana.griefPreventionAddon.command.ClaimMobsCommand
import com.tinyyana.griefPreventionAddon.command.ClaimPvpCommand
import com.tinyyana.griefPreventionAddon.command.ClaimSettingsCommand
import com.tinyyana.griefPreventionAddon.command.ClaimTntCommand
import com.tinyyana.griefPreventionAddon.command.ClaimTpCommand
import com.tinyyana.griefPreventionAddon.gui.ClaimSettingsGuiListener
import com.tinyyana.griefPreventionAddon.gui.ClaimSettingsGuiService
import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
import com.tinyyana.griefPreventionAddon.storage.Db
import com.tinyyana.griefPreventionAddon.teleport.ClaimTeleportService
import com.tinyyana.griefPreventionAddon.tnt.TntExplosionListener
import com.tinyyana.griefPreventionAddon.toggle.ClaimToggleListener
import com.tinyyana.lycoLib.config.Messages
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
    lateinit var messages: Messages
        private set
    lateinit var guiService: ClaimSettingsGuiService
        private set

    override fun onEnable() {
        saveDefaultConfig()
        messages = Messages.load(this)

        val dbFile = File(dataFolder, "data.db")
        db = Db(dbFile)
        claimSettingsStore = ClaimSettingsStore(db, logger)
        claimSettingsStore.init()

        // 遷移舊版 LycoServerTweaks 中的 claim_toggle 資料
        if (config.getBoolean("migration.auto-migrate-lst", true)) {
            val lstDbPath = config.getString("migration.lst-db-path", "../LycoServerTweaks/data.db") ?: "../LycoServerTweaks/data.db"
            val lstDbFile = File(dataFolder, lstDbPath)
            claimSettingsStore.migrateFromLycoServerTweaks(lstDbFile)
        }

        griefPreventionBridge = GriefPreventionBridge(this)
        claimTeleportService = ClaimTeleportService(griefPreventionBridge, messages)
        guiService = ClaimSettingsGuiService(griefPreventionBridge, claimSettingsStore, messages)

        // 註冊監聽器
        val pm = server.pluginManager
        pm.registerEvents(TntExplosionListener(griefPreventionBridge, claimSettingsStore), this)
        pm.registerEvents(ClaimToggleListener(griefPreventionBridge, claimSettingsStore), this)
        pm.registerEvents(ClaimLifecycleListener(claimSettingsStore), this)
        pm.registerEvents(
            ClaimSettingsGuiListener(
                guiService = guiService,
                bridge = griefPreventionBridge,
                store = claimSettingsStore,
                teleportService = claimTeleportService,
                messages = messages,
            ),
            this,
        )

        // 註冊指令
        val tntCmd = ClaimTntCommand(griefPreventionBridge, claimSettingsStore, messages)
        getCommand("ctnt")?.let {
            it.setExecutor(tntCmd)
            it.tabCompleter = tntCmd
        }

        val settingsCmd = ClaimSettingsCommand(griefPreventionBridge, guiService, messages)
        getCommand("csettings")?.let {
            it.setExecutor(settingsCmd)
            it.tabCompleter = settingsCmd
        }

        val pvpCmd = ClaimPvpCommand(griefPreventionBridge, claimSettingsStore, messages)
        getCommand("pvpinclaim")?.setExecutor(pvpCmd)

        val mobsCmd = ClaimMobsCommand(griefPreventionBridge, claimSettingsStore, messages)
        getCommand("claimmobs")?.let {
            it.setExecutor(mobsCmd)
            it.tabCompleter = mobsCmd
        }

        val infoCmd = ClaimInfoCommand(griefPreventionBridge, messages)
        getCommand("claiminfo")?.setExecutor(infoCmd)

        val tpCmd = ClaimTpCommand(griefPreventionBridge, claimTeleportService, messages)
        getCommand("claimtp")?.let {
            it.setExecutor(tpCmd)
            it.tabCompleter = tpCmd
        }

        logger.info("[GriefPreventionAddon] 插件已成功啟用 (版本: ${description.version})。")
    }

    override fun onDisable() {
        if (::db.isInitialized) {
            db.close()
        }
        logger.info("[GriefPreventionAddon] 插件已關閉。")
    }
}
