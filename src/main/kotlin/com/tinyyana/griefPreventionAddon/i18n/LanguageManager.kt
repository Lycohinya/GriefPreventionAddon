package com.tinyyana.griefPreventionAddon.i18n

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Multi-language message and localization manager.
 *
 * Supports client-locale auto-detection with fallback to the configured default language (default zh_TW).
 * Uses Adventure MiniMessage for rich formatting and placeholders.
 */
class LanguageManager(
    private val plugin: JavaPlugin,
    val defaultLanguage: String = "zh_TW",
    val autoDetectClientLocale: Boolean = true,
) {
    private val mm = MiniMessage.miniMessage()

    // languageCode -> (key -> string)
    private val stringTables = ConcurrentHashMap<String, Map<String, String>>()

    // languageCode -> (key -> list of strings)
    private val listTables = ConcurrentHashMap<String, Map<String, List<String>>>()

    fun load() {
        val langDir = File(plugin.dataFolder, "languages")
        if (!langDir.exists()) {
            langDir.mkdirs()
        }

        val bundledLangs = listOf("messages_zh_TW.yml", "messages_en_US.yml")
        for (bundled in bundledLangs) {
            val targetFile = File(langDir, bundled)
            if (!targetFile.exists()) {
                plugin.getResource("languages/$bundled")?.use { stream ->
                    targetFile.outputStream().use { out ->
                        stream.copyTo(out)
                    }
                }
            } else {
                // Auto-upgrade missing keys from internal defaults
                runCatching {
                    plugin.getResource("languages/$bundled")?.use { stream ->
                        InputStreamReader(stream, StandardCharsets.UTF_8).use { reader ->
                            val defaultYaml = YamlConfiguration.loadConfiguration(reader)
                            val currentYaml = YamlConfiguration.loadConfiguration(targetFile)
                            currentYaml.setDefaults(defaultYaml)
                            currentYaml.options().copyDefaults(true)
                            currentYaml.save(targetFile)
                        }
                    }
                }
            }
        }

        // Also check if legacy messages.yml exists in root dataFolder for backwards compatibility
        val legacyMessages = File(plugin.dataFolder, "messages.yml")
        if (legacyMessages.exists()) {
            loadYamlFile("legacy", legacyMessages)
        }

        // Load all .yml files from languages folder
        val files = langDir.listFiles { f -> f.isFile && f.name.endsWith(".yml", ignoreCase = true) } ?: emptyArray()
        for (f in files) {
            val code = normalizeLangCode(f.nameWithoutExtension.removePrefix("messages_"))
            loadYamlFile(code, f)
        }
    }

    private fun loadYamlFile(code: String, file: File) {
        runCatching {
            val yaml = YamlConfiguration.loadConfiguration(file)
            val strings = mutableMapOf<String, String>()
            val lists = mutableMapOf<String, List<String>>()

            for (key in yaml.getKeys(true)) {
                if (yaml.isString(key)) {
                    yaml.getString(key)?.let { strings[key] = it }
                } else if (yaml.isList(key)) {
                    val list = yaml.getStringList(key).filter { it.isNotBlank() }
                    if (list.isNotEmpty()) {
                        lists[key] = list
                    }
                }
            }

            stringTables[code] = strings
            listTables[code] = lists
        }.onFailure {
            plugin.logger.warning("[GriefPreventionAddon] Failed to load language file '${file.name}': ${it.message}")
        }
    }

    /**
     * Resolves the best matching language code for a player.
     */
    fun resolveLanguage(player: Player?): String {
        if (player == null || !autoDetectClientLocale) {
            return normalizeLangCode(defaultLanguage)
        }
        val locale: Locale = runCatching { player.locale() }.getOrDefault(Locale.getDefault())
        val lang = locale.language.lowercase()
        val country = locale.country.uppercase()

        return when {
            lang == "zh" -> {
                if (country == "CN" && stringTables.containsKey("zh_CN")) {
                    "zh_CN"
                } else {
                    "zh_TW"
                }
            }
            lang == "en" -> "en_US"
            stringTables.containsKey("${lang}_$country") -> "${lang}_$country"
            stringTables.containsKey(lang) -> lang
            else -> normalizeLangCode(defaultLanguage)
        }
    }

    private fun normalizeLangCode(input: String): String {
        val trimmed = input.trim()
        return when {
            trimmed.equals("zh_TW", ignoreCase = true) || trimmed.equals("zh-TW", ignoreCase = true) || trimmed.equals("zh", ignoreCase = true) || trimmed.equals("tw", ignoreCase = true) -> "zh_TW"
            trimmed.equals("zh_CN", ignoreCase = true) || trimmed.equals("zh-CN", ignoreCase = true) || trimmed.equals("cn", ignoreCase = true) -> "zh_CN"
            trimmed.equals("en_US", ignoreCase = true) || trimmed.equals("en-US", ignoreCase = true) || trimmed.equals("en", ignoreCase = true) || trimmed.equals("us", ignoreCase = true) -> "en_US"
            else -> trimmed
        }
    }

    fun raw(player: Player?, key: String): String? {
        val lang = resolveLanguage(player)
        return rawForLang(lang, key)
    }

    fun raw(key: String): String? = rawForLang(normalizeLangCode(defaultLanguage), key)

    private fun rawForLang(lang: String, key: String): String? {
        // 1. Target lang
        stringTables[lang]?.get(key)?.let { return it }
        // 2. Legacy root messages.yml if present
        stringTables["legacy"]?.get(key)?.let { return it }
        // 3. Default lang
        val defCode = normalizeLangCode(defaultLanguage)
        if (lang != defCode) {
            stringTables[defCode]?.get(key)?.let { return it }
        }
        // 4. Fallback zh_TW or en_US
        stringTables["zh_TW"]?.get(key)?.let { return it }
        stringTables["en_US"]?.get(key)?.let { return it }
        return null
    }

    fun rawList(player: Player?, key: String): List<String> {
        val lang = resolveLanguage(player)
        return rawListForLang(lang, key)
    }

    fun rawList(key: String): List<String> = rawListForLang(normalizeLangCode(defaultLanguage), key)

    private fun rawListForLang(lang: String, key: String): List<String> {
        listTables[lang]?.get(key)?.let { return it }
        stringTables["legacy"]?.get(key)?.let { return listOf(it) }
        val defCode = normalizeLangCode(defaultLanguage)
        if (lang != defCode) {
            listTables[defCode]?.get(key)?.let { return it }
        }
        listTables["zh_TW"]?.get(key)?.let { return it }
        listTables["en_US"]?.get(key)?.let { return it }
        return emptyList()
    }

    fun template(player: Player?, key: String): String? {
        val rawStr = raw(player, key)
        if (rawStr != null) return rawStr
        val list = rawList(player, key)
        return if (list.isNotEmpty()) list.random(Random) else null
    }

    fun get(player: Player?, key: String, vararg placeholders: Pair<String, String>): Component {
        var text = template(player, key) ?: return missing(key)
        for ((k, v) in placeholders) {
            text = text.replace("{$k}", v)
        }
        val prefix = raw(player, "system.prefix") ?: ""
        return mm.deserialize(prefix + text)
    }

    fun get(key: String, vararg placeholders: Pair<String, String>): Component = get(null, key, *placeholders)

    fun getWithoutPrefix(player: Player?, key: String, vararg placeholders: Pair<String, String>): Component {
        var text = template(player, key) ?: return missing(key)
        for ((k, v) in placeholders) {
            text = text.replace("{$k}", v)
        }
        return mm.deserialize(text)
    }

    fun render(text: String, prefix: Boolean = false, player: Player? = null): Component {
        val p = if (prefix) (raw(player, "system.prefix") ?: "") else ""
        return mm.deserialize(p + text)
    }

    fun missing(key: String): Component = Component.text("[missing message: $key]")
}
