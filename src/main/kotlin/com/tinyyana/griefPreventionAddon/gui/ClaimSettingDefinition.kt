package com.tinyyana.griefPreventionAddon.gui

import com.tinyyana.griefPreventionAddon.i18n.LanguageManager
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsKeys
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * Claim setting item definition with multi-language i18n support.
 */
data class ClaimSettingDefinition(
    val key: String,
    val nameKey: String,
    val descriptionKey: String,
    val iconId: String,
    val fallbackMaterial: Material,
    val isEnabled: (ClaimSettingsStore, Long) -> Boolean,
    val toggle: (ClaimSettingsStore, Long) -> Boolean,
    val enabledLabelKey: String,
    val disabledLabelKey: String,
    val actionEnableKey: String,
    val actionDisableKey: String,
    val permissionRequired: String? = null,
) {
    fun getName(lang: LanguageManager, player: Player?): String =
        lang.raw(player, nameKey) ?: key

    fun getDescription(lang: LanguageManager, player: Player?): String =
        lang.raw(player, descriptionKey) ?: ""

    fun getStatusLabel(lang: LanguageManager, player: Player?, enabled: Boolean): String =
        lang.raw(player, if (enabled) enabledLabelKey else disabledLabelKey) ?: if (enabled) "[ON]" else "[OFF]"

    fun getActionHint(lang: LanguageManager, player: Player?, enabled: Boolean): String =
        lang.raw(player, if (enabled) actionDisableKey else actionEnableKey) ?: ""
}

object ClaimSettingRegistry {

    val TNT = ClaimSettingDefinition(
        key = ClaimSettingsKeys.TNT,
        nameKey = "settings.tnt.name",
        descriptionKey = "settings.tnt.desc",
        iconId = "tnt",
        fallbackMaterial = Material.TNT,
        isEnabled = { store, id -> store.isTntAllowed(id) },
        toggle = { store, id -> store.toggle(ClaimSettingsKeys.TNT, id) },
        enabledLabelKey = "settings.tnt.enabled",
        disabledLabelKey = "settings.tnt.disabled",
        actionEnableKey = "settings.tnt.action-enable",
        actionDisableKey = "settings.tnt.action-disable",
    )

    val PVP = ClaimSettingDefinition(
        key = ClaimSettingsKeys.PVP,
        nameKey = "settings.pvp.name",
        descriptionKey = "settings.pvp.desc",
        iconId = "pvp",
        fallbackMaterial = Material.DIAMOND_SWORD,
        isEnabled = { store, id -> store.isPvpAllowed(id) },
        toggle = { store, id -> store.toggle(ClaimSettingsKeys.PVP, id) },
        enabledLabelKey = "settings.pvp.enabled",
        disabledLabelKey = "settings.pvp.disabled",
        actionEnableKey = "settings.pvp.action-enable",
        actionDisableKey = "settings.pvp.action-disable",
    )

    val ALLOW_SETHOME = ClaimSettingDefinition(
        key = ClaimSettingsKeys.ALLOW_SETHOME,
        nameKey = "settings.sethome.name",
        descriptionKey = "settings.sethome.desc",
        iconId = "bed",
        fallbackMaterial = Material.RED_BED,
        isEnabled = { store, id -> store.isSethomeAllowed(id) },
        toggle = { store, id -> store.toggle(ClaimSettingsKeys.ALLOW_SETHOME, id) },
        enabledLabelKey = "settings.sethome.enabled",
        disabledLabelKey = "settings.sethome.disabled",
        actionEnableKey = "settings.sethome.action-enable",
        actionDisableKey = "settings.sethome.action-disable",
    )

    val NO_HOSTILE = ClaimSettingDefinition(
        key = ClaimSettingsKeys.NO_HOSTILE_SPAWN,
        nameKey = "settings.mob-hostile.name",
        descriptionKey = "settings.mob-hostile.desc",
        iconId = "mob_hostile",
        fallbackMaterial = Material.ZOMBIE_HEAD,
        isEnabled = { store, id -> store.isMobSpawnBlocked(ClaimSettingsKeys.NO_HOSTILE_SPAWN, id) },
        toggle = { store, id -> store.toggle(ClaimSettingsKeys.NO_HOSTILE_SPAWN, id) },
        enabledLabelKey = "settings.mob-hostile.enabled",
        disabledLabelKey = "settings.mob-hostile.disabled",
        actionEnableKey = "settings.mob-hostile.action-enable",
        actionDisableKey = "settings.mob-hostile.action-disable",
    )

    val NO_RAIDER = ClaimSettingDefinition(
        key = ClaimSettingsKeys.NO_RAIDER_SPAWN,
        nameKey = "settings.mob-raider.name",
        descriptionKey = "settings.mob-raider.desc",
        iconId = "mob_raider",
        fallbackMaterial = Material.CROSSBOW,
        isEnabled = { store, id -> store.isMobSpawnBlocked(ClaimSettingsKeys.NO_RAIDER_SPAWN, id) },
        toggle = { store, id -> store.toggle(ClaimSettingsKeys.NO_RAIDER_SPAWN, id) },
        enabledLabelKey = "settings.mob-raider.enabled",
        disabledLabelKey = "settings.mob-raider.disabled",
        actionEnableKey = "settings.mob-raider.action-enable",
        actionDisableKey = "settings.mob-raider.action-disable",
    )

    val NO_PHANTOM = ClaimSettingDefinition(
        key = ClaimSettingsKeys.NO_PHANTOM_SPAWN,
        nameKey = "settings.mob-phantom.name",
        descriptionKey = "settings.mob-phantom.desc",
        iconId = "mob_phantom",
        fallbackMaterial = Material.PHANTOM_MEMBRANE,
        isEnabled = { store, id -> store.isMobSpawnBlocked(ClaimSettingsKeys.NO_PHANTOM_SPAWN, id) },
        toggle = { store, id -> store.toggle(ClaimSettingsKeys.NO_PHANTOM_SPAWN, id) },
        enabledLabelKey = "settings.mob-phantom.enabled",
        disabledLabelKey = "settings.mob-phantom.disabled",
        actionEnableKey = "settings.mob-phantom.action-enable",
        actionDisableKey = "settings.mob-phantom.action-disable",
    )

    val NO_SLIME = ClaimSettingDefinition(
        key = ClaimSettingsKeys.NO_SLIME_SPAWN,
        nameKey = "settings.mob-slime.name",
        descriptionKey = "settings.mob-slime.desc",
        iconId = "mob_slime",
        fallbackMaterial = Material.SLIME_BALL,
        isEnabled = { store, id -> store.isMobSpawnBlocked(ClaimSettingsKeys.NO_SLIME_SPAWN, id) },
        toggle = { store, id -> store.toggle(ClaimSettingsKeys.NO_SLIME_SPAWN, id) },
        enabledLabelKey = "settings.mob-slime.enabled",
        disabledLabelKey = "settings.mob-slime.disabled",
        actionEnableKey = "settings.mob-slime.action-enable",
        actionDisableKey = "settings.mob-slime.action-disable",
    )

    val NO_AMBIENT = ClaimSettingDefinition(
        key = ClaimSettingsKeys.NO_AMBIENT_SPAWN,
        nameKey = "settings.mob-ambient.name",
        descriptionKey = "settings.mob-ambient.desc",
        iconId = "mob_ambient",
        fallbackMaterial = Material.FEATHER,
        isEnabled = { store, id -> store.isMobSpawnBlocked(ClaimSettingsKeys.NO_AMBIENT_SPAWN, id) },
        toggle = { store, id -> store.toggle(ClaimSettingsKeys.NO_AMBIENT_SPAWN, id) },
        enabledLabelKey = "settings.mob-ambient.enabled",
        disabledLabelKey = "settings.mob-ambient.disabled",
        actionEnableKey = "settings.mob-ambient.action-enable",
        actionDisableKey = "settings.mob-ambient.action-disable",
    )

    val ALL_SETTINGS = listOf(
        TNT,
        PVP,
        ALLOW_SETHOME,
        NO_HOSTILE,
        NO_RAIDER,
        NO_PHANTOM,
        NO_SLIME,
        NO_AMBIENT,
    )
}
