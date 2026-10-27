package com.tinyyana.griefPreventionAddon.claim

import me.ryanhamshire.GriefPrevention.Claim
import me.ryanhamshire.GriefPrevention.GriefPrevention
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.command.CommandSender

/**
 * 花域下界診斷。
 *
 * 花域到底靠 GP 自己:`ExtendIntoGroundDistance` 夠大(Lycohinya 400)時,新圈的花域直接到底;
 * 舊花域在地主用金鏟子調整一次邊界、或在下界以下放方塊時,GP 會把整棵樹(含子花域)延伸到底。
 * 這裡只負責看,不改 GP 資料。
 */
object ClaimDepthService {

    /** GP 判定子花域時還要求在主花域內,所以實際下界是自己與主花域兩者較高的那個。 */
    fun effectiveFloor(claim: Claim): Int {
        val own = claim.lesserBoundaryCorner.blockY
        return claim.parent?.let { maxOf(own, it.lesserBoundaryCorner.blockY) } ?: own
    }

    /**
     * `/cadmin depth <world> <x> <z>`:同一根 XZ 柱上每個整數 Y 做 GP 的 3D 與柱體查詢,
     * 印幾個代表高度並數兩者不一致的層數(0 = 整根柱子都是同一塊花域)。純記憶體查詢,不載入區塊。
     */
    fun probe(sender: CommandSender, world: World, x: Int, z: Int) {
        val store = GriefPrevention.instance.dataStore
        fun lookup(y: Int, ignoreHeight: Boolean): Claim? = store.getClaimAt(Location(world, x + 0.5, y.toDouble(), z + 0.5), ignoreHeight, null)
        fun label(c: Claim?): String = c?.let { "${it.getID()}${it.parent?.let { p -> "<${p.getID()}" } ?: ""}" } ?: "-"
        val column = lookup(world.maxHeight, true)
        val floor = column?.let { effectiveFloor(it) }
        sender.sendMessage("[claimdepth] probe world=${world.name} x=$x z=$z min=${world.minHeight} max=${world.maxHeight} claim=${label(column)} floor=${floor ?: "-"}")
        listOfNotNull(world.minHeight, floor?.minus(1), floor, 80, world.maxHeight)
            .filter { it in world.minHeight..world.maxHeight }.distinct().sorted()
            .forEach { y -> sender.sendMessage("[claimdepth] y=$y 3d=${label(lookup(y, false))} column=${label(lookup(y, true))}") }
        val mismatched = (world.minHeight..world.maxHeight).filter { y -> lookup(y, false)?.getID() != lookup(y, true)?.getID() }
        sender.sendMessage(
            "[claimdepth] checked=${world.maxHeight - world.minHeight + 1} mismatch=${mismatched.size}" +
                if (mismatched.isEmpty()) "" else " first=${mismatched.first()} last=${mismatched.last()}",
        )
    }
}
