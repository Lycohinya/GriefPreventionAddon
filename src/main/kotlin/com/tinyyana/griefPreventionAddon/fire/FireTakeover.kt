package com.tinyyana.griefPreventionAddon.fire

import com.tinyyana.griefPreventionAddon.integration.GriefPreventionBridge
import com.tinyyana.griefPreventionAddon.policy.ClaimFlags
import com.tinyyana.griefPreventionAddon.policy.ClaimPolicyResolver
import me.ryanhamshire.GriefPrevention.Claim
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBurnEvent
import org.bukkit.event.block.BlockIgniteEvent
import org.bukkit.event.block.BlockSpreadEvent
import org.bukkit.plugin.EventExecutor
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.RegisteredListener
import java.util.logging.Logger

/**
 * 讓「火焰蔓延」「火燒方塊」能按花域/子花域設定,而不改 GP 的全域設定、不在高優先序解除取消。
 *
 * ## 為什麼這樣接
 * GP 16.18.7 在 LOWEST 處理 Ignite/Spread/Burn,全域 `FireSpreads=false` 時對領地內也一律取消,
 * 而且直接把來源火改成空氣(Spread)、熄掉周圍的火(Burn)。在後面 `setCancelled(false)` 救不回已被
 * 改掉的方塊,也會把其他插件的取消一起解掉;改 GP 的 `config_fireSpreads` 則留下全域競態(`/gpreload`
 * 會改回、addon 被卸載時荒野突然能燒)。
 *
 * 所以這裡把 GP `BlockEventHandler` 在這三個事件上的 [RegisteredListener] 從 HandlerList 拿下來,
 * 換成同樣在 LOWEST 的 addon handler:
 * - **目標在範圍內**:用 [FirePolicy] + resolver 決定(只會取消、熄火,永遠不解除別人的取消)。
 * - **其他一切**(荒野、非火焰的 sculk 蔓延、打火石/閃電/火焰彈/箭等引燃):原封不動呼叫 GP 原本那個
 *   listener,行為與接管前逐位元相同。
 *
 * [release] 在 addon 停用時把 GP 的 listener 放回去;GP 自己被停用/重新啟用時由 [onGpDisabled]/
 * 重新 [takeOver] 處理。config `fire.takeover: false` 可以不接管(火焰兩項顯示不可用),這就是回退開關。
 */
class FireTakeover(
    private val plugin: Plugin,
    private val bridge: GriefPreventionBridge,
    private val resolver: ClaimPolicyResolver,
    private val logger: Logger,
) {
    private class Taken(val handlers: HandlerList, val gp: RegisteredListener)

    private val taken = ArrayList<Taken>()
    private val ours = object : Listener {}

    @Volatile
    var active: Boolean = false
        private set

    /** 接管;三個事件缺任何一個就全部放棄(不留半套),回傳是否成功。 */
    @Synchronized
    fun takeOver(): Boolean {
        if (active) return true
        val gpPlugin = plugin.server.pluginManager.getPlugin("GriefPrevention")?.takeIf { it.isEnabled } ?: return false

        val targets = listOf(
            BlockIgniteEvent.getHandlerList() to BlockIgniteEvent::class.java,
            BlockSpreadEvent.getHandlerList() to BlockSpreadEvent::class.java,
            BlockBurnEvent.getHandlerList() to BlockBurnEvent::class.java,
        )
        val found = targets.map { (handlers, type) ->
            val matches = handlers.registeredListeners.filter {
                it.plugin === gpPlugin && it.listener.javaClass.name == GP_BLOCK_HANDLER && it.priority == EventPriority.LOWEST
            }
            if (matches.size != 1) {
                logger.warning(
                    "[GriefPreventionAddon] 火焰接管放棄:${type.simpleName} 找到 ${matches.size} 個 GP BlockEventHandler(預期 1),維持 GP 原樣",
                )
                return false
            }
            Triple(handlers, type, matches.single())
        }

        for ((handlers, type, gp) in found) {
            handlers.unregister(gp)
            taken += Taken(handlers, gp)
            val executor = EventExecutor { _, event -> dispatch(event, gp) }
            plugin.server.pluginManager.registerEvent(type, ours, EventPriority.LOWEST, executor, plugin, false)
        }
        active = true
        logger.info("[GriefPreventionAddon] 已接管 GP 的 Ignite/Spread/Burn(範圍內的火焰依花域規則,其餘照 GP)")
        return true
    }

    /** addon 停用:拿掉自己、把 GP 的 listener 放回原處。 */
    @Synchronized
    fun release() {
        HandlerList.unregisterAll(ours)
        val gpPlugin = plugin.server.pluginManager.getPlugin("GriefPrevention")
        for (t in taken) {
            // GP 已經被停用/換成新實例時不放回舊的 listener(新實例自己註冊過了)
            if (gpPlugin != null && gpPlugin.isEnabled && t.gp.plugin === gpPlugin &&
                t.handlers.registeredListeners.none { it === t.gp }
            ) {
                t.handlers.register(t.gp)
            }
        }
        taken.clear()
        if (active) logger.info("[GriefPreventionAddon] 已歸還 GP 的火焰事件處理")
        active = false
    }

    /** GP 被停用:它的 listener 已經被 Bukkit 拿掉,手上的參照作廢;也不能再委派給它。 */
    @Synchronized
    fun onGpDisabled() {
        HandlerList.unregisterAll(ours)
        taken.clear()
        active = false
    }

    private fun dispatch(event: Event, gp: RegisteredListener) {
        val handled = runCatching {
            when (event) {
                is BlockSpreadEvent -> onSpread(event)
                is BlockIgniteEvent -> onIgnite(event)
                is BlockBurnEvent -> onBurn(event)
                else -> false
            }
        }.getOrElse { e ->
            logger.warning("[GriefPreventionAddon] 火焰判斷失敗,這次交給 GP 處理: ${e.message}")
            false
        }
        if (!handled) gp.callEvent(event)
    }

    /** @return true = addon 已處理;false = 交給 GP */
    private fun onSpread(event: BlockSpreadEvent): Boolean {
        if (event.newState.type != Material.FIRE) return false
        if (!bridge.claimsEnabledForWorld(event.block.world)) return false
        val target = bridge.getClaimAt(event.block.location) ?: return false
        val source = event.source
        val sourceClaim = bridge.getClaimAt(source.location)
        val verdict = FirePolicy.spread(
            targetClaimed = true,
            sameOwner = sameOwner(sourceClaim, target),
            targetAllows = spreadAllows(target),
            sourceAllows = sourceClaim?.let(::spreadAllows) ?: (bridge.fireConfig()?.fireSpreads == true),
            canExtinguish = true,
        )
        apply(verdict, event, source)
        return true
    }

    private fun onIgnite(event: BlockIgniteEvent): Boolean {
        val cause = event.cause
        if (cause != BlockIgniteEvent.IgniteCause.SPREAD && cause != BlockIgniteEvent.IgniteCause.LAVA) return false
        if (!bridge.claimsEnabledForWorld(event.block.world)) return false
        val target = bridge.getClaimAt(event.block.location) ?: return false
        val sourceClaim = event.ignitingBlock?.let { bridge.getClaimAt(it.location) }
        val verdict = FirePolicy.spread(
            targetClaimed = true,
            sameOwner = sameOwner(sourceClaim, target),
            targetAllows = spreadAllows(target),
            sourceAllows = true,
            canExtinguish = false,
        )
        apply(verdict, event, null)
        return true
    }

    private fun onBurn(event: BlockBurnEvent): Boolean {
        if (!bridge.claimsEnabledForWorld(event.block.world)) return false
        val target = bridge.getClaimAt(event.block.location) ?: return false
        val scope = bridge.scopeOf(target)
        when (FirePolicy.burn(true, resolver.allows(ClaimFlags.FIRE_BURN, scope), resolver.allows(ClaimFlags.FIRE_SPREAD, scope))) {
            FirePolicy.BurnVerdict.DELEGATE, FirePolicy.BurnVerdict.ALLOW -> Unit
            FirePolicy.BurnVerdict.DENY -> event.isCancelled = true
            FirePolicy.BurnVerdict.DENY_EXTINGUISH_ADJACENT -> {
                event.isCancelled = true
                extinguishAround(event.block)
            }
        }
        return true
    }

    private fun apply(verdict: FirePolicy.Verdict, event: org.bukkit.event.Cancellable, source: Block?) {
        when (verdict) {
            FirePolicy.Verdict.DELEGATE, FirePolicy.Verdict.ALLOW -> Unit
            FirePolicy.Verdict.DENY -> event.isCancelled = true
            FirePolicy.Verdict.DENY_EXTINGUISH_SOURCE -> {
                event.isCancelled = true
                if (source != null && source.getRelative(BlockFace.DOWN).type != Material.NETHERRACK) source.type = Material.AIR
            }
        }
    }

    private fun spreadAllows(claim: Claim): Boolean = resolver.allows(ClaimFlags.FIRE_SPREAD, bridge.scopeOf(claim))

    private fun sameOwner(a: Claim?, b: Claim): Boolean = a != null && a.getOwnerID() == b.getOwnerID()

    /** 與 GP 16.18.7 onBlockBurn 相同:熄掉六面相鄰(下方不是地獄岩)的火與正上方的火。 */
    private fun extinguishAround(block: Block) {
        for (face in ADJACENT) {
            val adjacent = block.getRelative(face)
            if (adjacent.type == Material.FIRE && adjacent.getRelative(BlockFace.DOWN).type != Material.NETHERRACK) {
                adjacent.type = Material.AIR
            }
        }
        val above = block.getRelative(BlockFace.UP)
        if (above.type == Material.FIRE) above.type = Material.AIR
    }

    companion object {
        const val GP_BLOCK_HANDLER = "me.ryanhamshire.GriefPrevention.BlockEventHandler"
        private val ADJACENT = listOf(BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)
    }
}
