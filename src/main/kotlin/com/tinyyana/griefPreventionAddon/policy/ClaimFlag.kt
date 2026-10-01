package com.tinyyana.griefPreventionAddon.policy

import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsKeys
import org.bukkit.Material

/** 玩家在畫面與指令看到的三種選擇。 */
enum class TriState { INHERIT, ALLOW, DENY;

    companion object {
        /** 指令參數:on/allow → 允許、off/deny → 禁止、inherit/default → 繼承;認不得回 null。 */
        fun parse(arg: String?): TriState? = when (arg?.lowercase()) {
            "on", "enable", "allow", "true", "yes", "允許" -> ALLOW
            "off", "disable", "deny", "false", "no", "禁止" -> DENY
            "inherit", "default", "reset", "unset", "繼承", "預設" -> INHERIT
            else -> null
        }
    }
}

/** 畫面分組:環境與保護頁裡的帶。 */
enum class FlagGroup { SPAWN, COMBAT, FIRE, HOME }

/**
 * 一項可以按花域/子花域設定的布林規則。
 *
 * 儲存值沿用舊資料的正反(例如 `no-hostile-spawn=true` 是「禁止生成」),玩家看到的一律是
 * 「允許/禁止/繼承」——[allowStored] 是兩者的換算:儲存值等於它時代表「允許」。
 *
 * @param ownerPermission 地主本人改這一項還需要的權限(沒有就 null)。舊 `/ctnt`、`/csethome` 有這層。
 * @param othersPermission 改別人花域的這一項可用的權限;另外 `settings.others`/`admin` 永遠可以。
 * @param requiresFireTakeover 這一項要靠接管 GP 的火焰事件才會生效;沒接管時畫面顯示不可用,不假裝能改。
 * @param defaultAllow 內建的伺服器預設(config.yml `defaults.<key>` 沒寫時);火焰兩項跟 GP 走,不看這個。
 * @param commandDefault 一組指令只打狀態(例如舊的 `/csethome on`)時套用的那一項,讓舊用法照舊能用。
 */
data class ClaimFlag(
    val key: String,
    val group: FlagGroup,
    val allowStored: Boolean,
    val langKey: String,
    val iconId: String,
    val fallbackMaterial: Material,
    val ownerPermission: String? = null,
    val othersPermission: String? = null,
    val requiresFireTakeover: Boolean = false,
    /** 指令與管理介面用的短名稱(`/cmob hostile` 的 hostile)。 */
    val shortName: String,
    /** 對應的玩家指令(選單格子會附上,熟手不必點來點去);onEnable 依第一個字分組註冊,名稱要在 plugin.yml。 */
    val command: String,
    val defaultAllow: Boolean = false,
    val commandDefault: Boolean = false,
) {
    fun storedFor(state: TriState): Boolean? = when (state) {
        TriState.INHERIT -> null
        TriState.ALLOW -> allowStored
        TriState.DENY -> !allowStored
    }

    fun stateOf(stored: Boolean?): TriState = when (stored) {
        null -> TriState.INHERIT
        allowStored -> TriState.ALLOW
        else -> TriState.DENY
    }

    fun allows(stored: Boolean): Boolean = stored == allowStored
}

object ClaimFlags {
    val TNT = ClaimFlag(
        key = ClaimSettingsKeys.TNT, group = FlagGroup.COMBAT, allowStored = true,
        langKey = "tnt", iconId = "tnt", fallbackMaterial = Material.TNT,
        ownerPermission = "griefpreventionaddon.tnt", othersPermission = "griefpreventionaddon.tnt.others",
        shortName = "tnt", command = "/ctnt",
    )
    val PVP = ClaimFlag(
        key = ClaimSettingsKeys.PVP, group = FlagGroup.COMBAT, allowStored = true,
        langKey = "pvp", iconId = "pvp", fallbackMaterial = Material.IRON_SWORD,
        shortName = "pvp", command = "/pvp",
    )
    /** 有 GP 信任(通行以上)的人能不能在這裡 /sethome;預設允許(= GP 原本的 CommandsRequiringAccessTrust)。 */
    val MEMBER_SETHOME = ClaimFlag(
        key = ClaimSettingsKeys.MEMBER_SETHOME, group = FlagGroup.HOME, allowStored = true,
        langKey = "member-sethome", iconId = "home", fallbackMaterial = Material.WHITE_BED,
        ownerPermission = "griefpreventionaddon.sethome", othersPermission = "griefpreventionaddon.sethome.others",
        shortName = "trusted", command = "/csethome trusted", defaultAllow = true,
    )

    /** 沒有信任的訪客能不能在這裡 /sethome;預設禁止。舊 `/csethome on|off` 就是這一項。 */
    val SETHOME = ClaimFlag(
        key = ClaimSettingsKeys.ALLOW_SETHOME, group = FlagGroup.HOME, allowStored = true,
        langKey = "sethome", iconId = "bed", fallbackMaterial = Material.RED_BED,
        ownerPermission = "griefpreventionaddon.sethome", othersPermission = "griefpreventionaddon.sethome.others",
        shortName = "visitor", command = "/csethome visitor", commandDefault = true,
    )
    val HOSTILE = spawn(ClaimSettingsKeys.NO_HOSTILE_SPAWN, "hostile", "mob_hostile", Material.ZOMBIE_HEAD)
    val RAIDER = spawn(ClaimSettingsKeys.NO_RAIDER_SPAWN, "raider", "mob_raider", Material.CROSSBOW)
    val PHANTOM = spawn(ClaimSettingsKeys.NO_PHANTOM_SPAWN, "phantom", "mob_phantom", Material.PHANTOM_MEMBRANE)
    val SLIME = spawn(ClaimSettingsKeys.NO_SLIME_SPAWN, "slime", "mob_slime", Material.SLIME_BALL)
    val AMBIENT = spawn(ClaimSettingsKeys.NO_AMBIENT_SPAWN, "ambient", "mob_ambient", Material.FEATHER)
    val FIRE_SPREAD = ClaimFlag(
        key = ClaimSettingsKeys.FIRE_SPREAD, group = FlagGroup.FIRE, allowStored = true,
        langKey = "fire-spread", iconId = "fire_spread", fallbackMaterial = Material.CAMPFIRE,
        requiresFireTakeover = true, shortName = "spread", command = "/cfire spread",
    )
    val FIRE_BURN = ClaimFlag(
        key = ClaimSettingsKeys.FIRE_BURN, group = FlagGroup.FIRE, allowStored = true,
        langKey = "fire-burn", iconId = "fire_burn", fallbackMaterial = Material.CHARCOAL,
        requiresFireTakeover = true, shortName = "burn", command = "/cfire burn",
    )

    val SPAWN = listOf(HOSTILE, RAIDER, PHANTOM, SLIME, AMBIENT)

    /** 全部可在花域/子花域層級設定的規則。新增一項就是在這裡加一筆,指令、畫面、事件都吃同一份。 */
    val ALL: List<ClaimFlag> = listOf(TNT, PVP, MEMBER_SETHOME, SETHOME) + SPAWN + listOf(FIRE_SPREAD, FIRE_BURN)

    private val byKey = ALL.associateBy { it.key }

    fun byKey(key: String): ClaimFlag? = byKey[key.lowercase()]

    private fun spawn(key: String, shortName: String, icon: String, fallback: Material) = ClaimFlag(
        key = key, group = FlagGroup.SPAWN, allowStored = false,
        langKey = "mob-$shortName", iconId = icon, fallbackMaterial = fallback,
        shortName = shortName, command = "/cmob $shortName", defaultAllow = true,
    )
}
