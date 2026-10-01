package com.tinyyana.griefPreventionAddon.fire

/**
 * 火焰事件的判斷,與 Bukkit 解耦以便單元測試。
 *
 * ## 產品語意(兩項規則)
 * - **火焰蔓延** `fire-spread`:火往旁邊的空位擴散(BlockSpreadEvent 新狀態為火、BlockIgniteEvent SPREAD)、
 *   岩漿引燃旁邊的可燃物(BlockIgniteEvent LAVA)。也就是 `doFireTick` 管的那一套自然火勢。
 * - **火燒方塊** `fire-burn`:可燃方塊被火燒掉(BlockBurnEvent)。
 *
 * 不在這兩項裡、維持 GP 原樣的:打火石/火焰彈/閃電/箭/終界水晶/爆炸引燃(玩家主動點火歸 GP 的建築信任管),
 * 以及**目標在荒野**的一切火焰事件(GP 的全域 `FireSpreads`/`FireDestroys` 照舊)。
 *
 * ## 邊界(與 GP 16.18.7 一致)
 * - 目標與來源不同地主(或來源在荒野):一律禁止,並照 GP 熄掉來源的火(下方不是地獄岩時)。
 * - 同一地主:看**目標**精確範圍的有效值。兄弟子花域各自判斷。
 * - 禁止時才熄火,而且只熄「來源所在範圍自己也不允許蔓延」的那一團——
 *   允許蔓延的子花域裡的火,不會因為隔壁子花域禁止就被熄掉,只是過不去。
 */
object FirePolicy {

    enum class Verdict { DELEGATE, ALLOW, DENY, DENY_EXTINGUISH_SOURCE }

    /**
     * 擴散類事件(Spread 的火、Ignite 的 SPREAD/LAVA)。
     *
     * @param targetClaimed 目標方塊在任何範圍內
     * @param sameOwner 來源與目標同一地主(來源在荒野 = false)
     * @param targetAllows 目標精確範圍的火焰蔓延有效值
     * @param sourceAllows 來源精確範圍的火焰蔓延有效值(來源在荒野時傳 GP 全域 FireSpreads)
     * @param canExtinguish 這個事件有可熄的來源火(Spread 有;Ignite 不熄,GP 也只是取消)
     */
    fun spread(
        targetClaimed: Boolean,
        sameOwner: Boolean,
        targetAllows: Boolean,
        sourceAllows: Boolean,
        canExtinguish: Boolean,
    ): Verdict {
        if (!targetClaimed) return Verdict.DELEGATE
        if (!sameOwner) return if (canExtinguish) Verdict.DENY_EXTINGUISH_SOURCE else Verdict.DENY
        if (targetAllows) return Verdict.ALLOW
        return if (canExtinguish && !sourceAllows) Verdict.DENY_EXTINGUISH_SOURCE else Verdict.DENY
    }

    enum class BurnVerdict { DELEGATE, ALLOW, DENY, DENY_EXTINGUISH_ADJACENT }

    /**
     * 燒毀。禁止時,如果這裡也不允許蔓延,照 GP 原本的做法順手熄掉周圍的火(減少之後重複觸發);
     * 允許蔓延但禁止燒毀的範圍,火可以留著當裝飾,只是燒不掉方塊。
     */
    fun burn(targetClaimed: Boolean, burnAllows: Boolean, spreadAllows: Boolean): BurnVerdict = when {
        !targetClaimed -> BurnVerdict.DELEGATE
        burnAllows -> BurnVerdict.ALLOW
        spreadAllows -> BurnVerdict.DENY
        else -> BurnVerdict.DENY_EXTINGUISH_ADJACENT
    }
}
