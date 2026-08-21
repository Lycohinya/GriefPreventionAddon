package com.tinyyana.griefPreventionAddon.gui

import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsKeys
import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
import org.bukkit.Material

/**
 * 領地設定項目定義(可擴充架構)。
 * 新增設定項時只需在此註冊，無需改寫 GUI 版面與分派器。
 */
data class ClaimSettingDefinition(
    val key: String,
    val name: String,
    val description: String,
    val iconId: String,
    val fallbackMaterial: Material,
    val isEnabled: (ClaimSettingsStore, Long) -> Boolean,
    val toggle: (ClaimSettingsStore, Long) -> Boolean,
    val enabledLabel: String,
    val disabledLabel: String,
    val permissionRequired: String? = null,
)

object ClaimSettingRegistry {

    val TNT = ClaimSettingDefinition(
        key = ClaimSettingsKeys.TNT,
        name = "TNT 爆炸破壞",
        description = "控制 TNT 爆炸是否能破壞此花域內的方塊",
        iconId = "tnt",
        fallbackMaterial = Material.TNT,
        isEnabled = { store, id -> store.isTntAllowed(id) },
        toggle = { store, id -> store.toggle(ClaimSettingsKeys.TNT, id) },
        enabledLabel = "<color:#6fd8e8>[允許破壞]</color>",
        disabledLabel = "<color:#a8a8a8>[禁止破壞 (保護)]</color>",
    )

    val PVP = ClaimSettingDefinition(
        key = ClaimSettingsKeys.PVP,
        name = "領地 PVP 對戰",
        description = "控制玩家在此花域內是否能互相造成傷害",
        iconId = "pvp",
        fallbackMaterial = Material.DIAMOND_SWORD,
        isEnabled = { store, id -> store.isPvpAllowed(id) },
        toggle = { store, id -> store.toggle(ClaimSettingsKeys.PVP, id) },
        enabledLabel = "<color:#6fd8e8>[開放 PVP]</color>",
        disabledLabel = "<color:#a8a8a8>[禁止 PVP (保護)]</color>",
    )

    val NO_HOSTILE = ClaimSettingDefinition(
        key = ClaimSettingsKeys.NO_HOSTILE_SPAWN,
        name = "一般敵對生物",
        description = "禁止殭屍、骷髏、苦力怕等一般敵對生物自然生成",
        iconId = "mob_hostile",
        fallbackMaterial = Material.ZOMBIE_HEAD,
        isEnabled = { store, id -> store.isMobSpawnBlocked(ClaimSettingsKeys.NO_HOSTILE_SPAWN, id) },
        toggle = { store, id -> store.toggle(ClaimSettingsKeys.NO_HOSTILE_SPAWN, id) },
        enabledLabel = "<color:#6fd8e8>[已禁止生成]</color>",
        disabledLabel = "<color:#a8a8a8>[自然生成]</color>",
    )

    val NO_RAIDER = ClaimSettingDefinition(
        key = ClaimSettingsKeys.NO_RAIDER_SPAWN,
        name = "掠奪者巡邏隊",
        description = "禁止災厄巡邏隊與襲擊部隊自然生成",
        iconId = "mob_raider",
        fallbackMaterial = Material.CROSSBOW,
        isEnabled = { store, id -> store.isMobSpawnBlocked(ClaimSettingsKeys.NO_RAIDER_SPAWN, id) },
        toggle = { store, id -> store.toggle(ClaimSettingsKeys.NO_RAIDER_SPAWN, id) },
        enabledLabel = "<color:#6fd8e8>[已禁止生成]</color>",
        disabledLabel = "<color:#a8a8a8>[自然生成]</color>",
    )

    val NO_PHANTOM = ClaimSettingDefinition(
        key = ClaimSettingsKeys.NO_PHANTOM_SPAWN,
        name = "失眠夜魅",
        description = "禁止因失眠而產生的夜魅在花域上空生成",
        iconId = "mob_phantom",
        fallbackMaterial = Material.PHANTOM_MEMBRANE,
        isEnabled = { store, id -> store.isMobSpawnBlocked(ClaimSettingsKeys.NO_PHANTOM_SPAWN, id) },
        toggle = { store, id -> store.toggle(ClaimSettingsKeys.NO_PHANTOM_SPAWN, id) },
        enabledLabel = "<color:#6fd8e8>[已禁止生成]</color>",
        disabledLabel = "<color:#a8a8a8>[自然生成]</color>",
    )

    val NO_SLIME = ClaimSettingDefinition(
        key = ClaimSettingsKeys.NO_SLIME_SPAWN,
        name = "史萊姆與岩漿立方怪",
        description = "禁止史萊姆與岩漿立方怪自然生成與分裂",
        iconId = "mob_slime",
        fallbackMaterial = Material.SLIME_BALL,
        isEnabled = { store, id -> store.isMobSpawnBlocked(ClaimSettingsKeys.NO_SLIME_SPAWN, id) },
        toggle = { store, id -> store.toggle(ClaimSettingsKeys.NO_SLIME_SPAWN, id) },
        enabledLabel = "<color:#6fd8e8>[已禁止生成]</color>",
        disabledLabel = "<color:#a8a8a8>[自然生成]</color>",
    )

    val NO_AMBIENT = ClaimSettingDefinition(
        key = ClaimSettingsKeys.NO_AMBIENT_SPAWN,
        name = "環境生物 (蝙蝠)",
        description = "禁止蝙蝠等環境生物在花域內自然生成",
        iconId = "mob_ambient",
        fallbackMaterial = Material.FEATHER,
        isEnabled = { store, id -> store.isMobSpawnBlocked(ClaimSettingsKeys.NO_AMBIENT_SPAWN, id) },
        toggle = { store, id -> store.toggle(ClaimSettingsKeys.NO_AMBIENT_SPAWN, id) },
        enabledLabel = "<color:#6fd8e8>[已禁止生成]</color>",
        disabledLabel = "<color:#a8a8a8>[自然生成]</color>",
    )

    val ALL_SETTINGS = listOf(
        TNT,
        PVP,
        NO_HOSTILE,
        NO_RAIDER,
        NO_PHANTOM,
        NO_SLIME,
        NO_AMBIENT,
    )
}
