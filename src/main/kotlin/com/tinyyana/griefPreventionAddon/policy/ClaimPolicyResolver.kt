package com.tinyyana.griefPreventionAddon.policy

import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore

/**
 * 解析時需要的領地身分:自己的 id 與主花域 id(主花域本身為 null)。
 * GP 只有兩層(主花域 + 子花域),不會再往下巢狀。
 */
data class ClaimScope(val id: Long, val parentId: Long?) {
    val isSubdivision: Boolean get() = parentId != null
}

/** 有效值從哪裡來。 */
enum class ValueSource { LOCAL, PARENT, SERVER_DEFAULT }

/**
 * 一項規則在某個範圍的完整解析結果:畫面要同時顯示「這裡自己選了什麼」「實際生效什麼」「從哪來」。
 *
 * @param local 這個範圍自己的選擇
 * @param allowed 實際生效:是否允許
 * @param source 實際值的來源
 * @param inheritedAllowed 如果這裡選「繼承」會得到什麼(子花域 = 主花域的有效值;主花域 = 伺服器預設)
 */
data class Resolution(
    val flag: ClaimFlag,
    val scope: ClaimScope,
    val local: TriState,
    val allowed: Boolean,
    val source: ValueSource,
    val inheritedAllowed: Boolean,
)

/** 伺服器預設(儲存值)。火焰兩項跟著 GP 的實際設定走,其他來自 addon config。 */
fun interface FlagDefaults {
    fun defaultStored(flag: ClaimFlag): Boolean
}

/**
 * 單一解析語意:子花域明確值 → 主花域有效值 → 伺服器預設。
 *
 * 事件、指令、畫面、管理介面都只問這裡,所以「父值改變只影響仍在繼承的子花域」「恢復繼承不清父資料」
 * 是這個函式的性質,不是各呼叫端各自記得的規則。全程只讀 store 的記憶體快取。
 */
class ClaimPolicyResolver(
    private val store: ClaimSettingsStore,
    private val defaults: FlagDefaults,
) {
    /** 實際生效的儲存值。 */
    fun effectiveStored(flag: ClaimFlag, scope: ClaimScope): Boolean {
        store.getExplicit(flag.key, scope.id)?.let { return it }
        scope.parentId?.let { parent -> store.getExplicit(flag.key, parent)?.let { return it } }
        return defaults.defaultStored(flag)
    }

    fun allows(flag: ClaimFlag, scope: ClaimScope): Boolean = flag.allows(effectiveStored(flag, scope))

    fun resolve(flag: ClaimFlag, scope: ClaimScope): Resolution {
        val own = store.getExplicit(flag.key, scope.id)
        val parentStored = scope.parentId?.let { store.getExplicit(flag.key, it) }
        val defaultStored = defaults.defaultStored(flag)
        val inherited = parentStored ?: defaultStored
        val (effective, source) = when {
            own != null -> own to ValueSource.LOCAL
            parentStored != null -> parentStored to ValueSource.PARENT
            else -> defaultStored to ValueSource.SERVER_DEFAULT
        }
        return Resolution(
            flag = flag,
            scope = scope,
            local = flag.stateOf(own),
            allowed = flag.allows(effective),
            source = source,
            inheritedAllowed = flag.allows(inherited),
        )
    }

    /**
     * 高頻路徑快篩:全服有沒有任何範圍可能讓這一項「實際上是 [stored]」。
     * 預設就是 [stored] 時永遠 true;否則要有人明確設成 [stored](明確值在主花域時,繼承它的子花域也算在內)。
     */
    fun mayResolveTo(flag: ClaimFlag, stored: Boolean): Boolean =
        defaults.defaultStored(flag) == stored || store.hasAnyExplicit(flag.key, stored)
}
