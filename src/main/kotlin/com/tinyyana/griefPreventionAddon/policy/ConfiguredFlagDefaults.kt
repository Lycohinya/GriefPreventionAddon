package com.tinyyana.griefPreventionAddon.policy

import com.tinyyana.griefPreventionAddon.integration.GpFireConfig
import org.bukkit.configuration.ConfigurationSection

/**
 * 伺服器預設 = 主花域沒有自己設定時的值。來源是 config.yml 的 `defaults.<規則 key>`:
 *
 * - `allow` / `deny`:固定值。
 * - `gp`:跟 GriefPrevention 的實際設定走(只給火焰兩項)。預設就是 `gp`,讓升級前後完全一樣:
 *   火焰蔓延 = `FireSpreads && FireSpreadsInClaims`,火燒方塊 = `FireDestroys && FireDamagesInClaims`
 *   (GP 原本就是全域開關先擋、再看領地開關)。
 *
 * 沒寫的 key 退回舊版的 `tnt.default-allowed` 這類欄位,再退回內建值。解析在建構時做一次,
 * 高頻路徑只查 map;`gp` 每次讀 GP 實例欄位(`/gpreload` 後立刻跟上)。
 */
class ConfiguredFlagDefaults(
    config: ConfigurationSection,
    private val gpFire: () -> GpFireConfig?,
) : FlagDefaults {

    private sealed interface Source {
        data class Fixed(val allow: Boolean) : Source
        data object Gp : Source
    }

    private val sources: Map<String, Source> = ClaimFlags.ALL.associate { flag ->
        val configured: Source? = parse(config.getString("defaults.${flag.key}"))
            ?.takeUnless { it == Source.Gp && !flag.requiresFireTakeover }
        flag.key to (configured ?: legacy(config, flag) ?: builtIn(flag))
    }

    override fun defaultStored(flag: ClaimFlag): Boolean = when (val source = sources[flag.key] ?: builtIn(flag)) {
        is Source.Fixed -> flag.storedFor(if (source.allow) TriState.ALLOW else TriState.DENY)!!
        Source.Gp -> {
            val gp = gpFire()
            val allow = when (flag) {
                ClaimFlags.FIRE_SPREAD -> gp != null && gp.fireSpreads && gp.claimsFireSpreads
                ClaimFlags.FIRE_BURN -> gp != null && gp.fireDestroys && gp.claimsFireDamages
                else -> false
            }
            flag.storedFor(if (allow) TriState.ALLOW else TriState.DENY)!!
        }
    }

    /** 給畫面說明預設從哪來。 */
    fun followsGp(flag: ClaimFlag): Boolean = sources[flag.key] == Source.Gp

    private fun parse(raw: String?): Source? = when (raw?.trim()?.lowercase()) {
        "allow", "on" -> Source.Fixed(true)
        "deny", "off" -> Source.Fixed(false)
        "gp" -> Source.Gp
        else -> null
    }

    private fun legacy(config: ConfigurationSection, flag: ClaimFlag): Source? {
        val path = when (flag) {
            ClaimFlags.TNT -> "tnt.default-allowed"
            ClaimFlags.PVP -> "pvp.default-allowed"
            ClaimFlags.SETHOME -> "sethome.default-allowed"
            else -> return null
        }
        return if (config.isSet(path)) Source.Fixed(config.getBoolean(path)) else null
    }

    private fun builtIn(flag: ClaimFlag): Source =
        if (flag.requiresFireTakeover) Source.Gp else Source.Fixed(flag.defaultAllow)
}
