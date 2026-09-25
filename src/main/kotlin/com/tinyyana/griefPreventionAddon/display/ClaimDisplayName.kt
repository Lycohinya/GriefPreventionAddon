package com.tinyyana.griefPreventionAddon.display

import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore

/**
 * 單一領地在畫面上該怎麼呈現的共用模型。
 *
 * 修復前 `/ctp` 列表、`/claimslist`、傳送成功訊息、GUI 卡片各自組一套顯示規則
 * (有的用「別名」、有的用原始別名、有的把 `gui.card-status-no-alias`
 * 這行本來是給 lore 用的完整「別名：未設定」提示句誤用成無別名時的名字),
 * 造成有別名時也顯示不一致、沒別名時顯示出一整句不像名字的文字。
 * 統一由這裡決定:有效別名一律優先,沒有才退回 `#claimId`。
 *
 * `targetParam` 固定用領地編號:別名可能重複(不同地主可以取一樣的別名,
 * [ClaimSettingsStore.findClaimId] 的全域反查在重複時只會抓到其中一個),
 * 用編號做 `/claimtp` 的 click 目標才不會傳送到別人的領地。
 */
data class ClaimDisplayName(
    val claimId: Long,
    /** 原始別名(未跳脫);null 或空白代表沒有設定 */
    val alias: String?,
    val hasAlias: Boolean,
    /** 純名字:別名或 `#編號` */
    val name: String,
    /** 「別名」形式的裝飾名,沒別名時同樣退回 `#編號` */
    val decorated: String,
    /** 用於 `/claimtp`、`/csettings` 等 click 指令的目標參數,一律是編號 */
    val targetParam: String,
) {
    /** 指令補全的候選字:有別名就是別名,沒有就是 `#編號`。一塊花域只給一個,不會別名跟編號各列一次 */
    val completion: String get() = alias ?: "#$claimId"

    companion object {
        fun resolve(store: ClaimSettingsStore, claimId: Long): ClaimDisplayName {
            val alias = store.getAlias(claimId)?.takeIf { it.isNotBlank() }
            val fallback = "#$claimId"
            return ClaimDisplayName(
                claimId = claimId,
                alias = alias,
                hasAlias = alias != null,
                name = alias ?: fallback,
                decorated = if (alias != null) "「$alias」" else fallback,
                targetParam = claimId.toString(),
            )
        }
    }
}
