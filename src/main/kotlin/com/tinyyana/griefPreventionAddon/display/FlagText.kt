package com.tinyyana.griefPreventionAddon.display

import com.tinyyana.griefPreventionAddon.i18n.LanguageManager
import com.tinyyana.griefPreventionAddon.policy.ClaimFlag
import com.tinyyana.griefPreventionAddon.policy.Resolution
import com.tinyyana.griefPreventionAddon.policy.SetOutcome
import com.tinyyana.griefPreventionAddon.policy.SetResult
import com.tinyyana.griefPreventionAddon.policy.TriState
import com.tinyyana.griefPreventionAddon.policy.ValueSource
import me.ryanhamshire.GriefPrevention.Claim
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

/**
 * 規則的玩家文字。全部由語系檔的固定結構組出來,新增一項規則只要補 `flag.<langKey>.*` 三行:
 *
 * ```
 * flag.<langKey>.name    名稱
 * flag.<langKey>.allow   實際允許時怎麼說(例:會自然生成)
 * flag.<langKey>.deny    實際禁止時怎麼說(例:不會自然生成)
 * ```
 * 畫面與指令共用這裡,兩邊的說法不會分岔。
 */
object FlagText {

    fun name(lang: LanguageManager, player: Player?, flag: ClaimFlag): String =
        lang.raw(player, "flag.${flag.langKey}.name") ?: flag.key

    /** 實際生效的說法(例:「不會自然生成」)。 */
    fun effectiveWord(lang: LanguageManager, player: Player?, resolution: Resolution): String =
        allowWord(lang, player, resolution.flag, resolution.allowed)

    fun allowWord(lang: LanguageManager, player: Player?, flag: ClaimFlag, allowed: Boolean): String {
        val suffix = if (allowed) "allow" else "deny"
        return lang.raw(player, "flag.${flag.langKey}.$suffix") ?: lang.raw(player, "flag.state.$suffix") ?: suffix
    }

    /** 這個範圍自己的選擇(繼承/允許/禁止)。 */
    fun localWord(lang: LanguageManager, player: Player?, state: TriState): String =
        lang.raw(player, "flag.state.${state.name.lowercase()}") ?: state.name

    fun sourceWord(lang: LanguageManager, player: Player?, resolution: Resolution): String = when (resolution.source) {
        ValueSource.LOCAL -> lang.raw(player, "flag.source.local") ?: "local"
        ValueSource.PARENT -> (lang.raw(player, "flag.source.parent") ?: "parent #{id}").replace("{id}", resolution.scope.parentId.toString())
        ValueSource.SERVER_DEFAULT -> lang.raw(player, "flag.source.default") ?: "default"
    }

    fun scopeLabel(lang: LanguageManager, player: Player?, claim: Claim): String {
        val key = if (claim.parent != null) "flag.scope.sub" else "flag.scope.root"
        return (lang.raw(player, key) ?: "#{id}").replace("{id}", claim.getID().toString())
    }

    fun statusLine(lang: LanguageManager, player: Player, resolution: Resolution): Component =
        lang.get(
            player, "flag.status-line",
            "name" to name(lang, player, resolution.flag),
            "local" to localWord(lang, player, resolution.local),
            "state" to effectiveWord(lang, player, resolution),
            "source" to sourceWord(lang, player, resolution),
        )

    fun sendOutcome(lang: LanguageManager, player: Player, flag: ClaimFlag, result: SetResult) {
        val message = when (result.outcome) {
            SetOutcome.OK, SetOutcome.UNCHANGED -> {
                val resolution = result.resolution ?: return
                lang.get(
                    player, if (result.outcome == SetOutcome.OK) "flag.changed" else "flag.unchanged",
                    "scope" to (result.claim?.let { scopeLabel(lang, player, it) } ?: ""),
                    "name" to name(lang, player, flag),
                    "local" to localWord(lang, player, resolution.local),
                    "state" to effectiveWord(lang, player, resolution),
                    "source" to sourceWord(lang, player, resolution),
                )
            }
            SetOutcome.CLAIM_GONE -> lang.get(player, "flag.claim-gone")
            SetOutcome.NO_PERMISSION -> lang.get(player, "claim.not-owner")
            SetOutcome.UNAVAILABLE -> lang.get(player, "flag.unavailable")
        }
        player.sendMessage(message)
    }
}
