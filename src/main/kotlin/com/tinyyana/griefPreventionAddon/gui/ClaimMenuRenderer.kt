package com.tinyyana.griefPreventionAddon.gui

import com.tinyyana.lycoLib.icon.LycoIcons
import com.tinyyana.lycoLib.menu.MenuBuilder
import com.tinyyana.lycoLib.menu.MenuHeadings
import com.tinyyana.lycoLib.menu.MenuShell
import com.tinyyana.lycoLib.menu.MenuSize
import com.tinyyana.lycoLib.menu.NavigationSlots
import com.tinyyana.lycoLib.style.LycoText
import com.tinyyana.lycoLib.style.deserializeUpright
import com.tinyyana.lycoLib.ui.PanelText
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta

/**
 * 把一份 [PageModel] 畫成視窗。所有頁面共用這一支,頁面本身只產生資料。
 *
 * 有資源包:`shell_railgroup_6_*` 背景(左欄分類、每組一塊底色)+ 組名牌上的字;
 * 沒有資源包(或這一版的包缺 glyph):純文字標題、分類欄邊填玻璃、組名改成一顆物品,
 * 空格補玻璃——同樣的格位、同樣的操作。
 */
class ClaimMenuRenderer(
    private val tabs: (Player, ClaimMenuHolder) -> List<Cell>,
    private val builder: MenuBuilder = MenuBuilder(),
) {
    private val mm = MiniMessage.miniMessage()
    private val size = MenuSize.LARGE
    private val nav = NavigationSlots.resolve(size)

    fun open(player: Player, holder: ClaimMenuHolder, model: PageModel) {
        val plan = RailLayout.plan(model.bands.map { it.id to it.cells.size })
            ?: error("page ${holder.state.page} 放不下 ${RailLayout.CONTENT_ROWS} 列:${model.bands.map { it.id to it.cells.size }}")
        val glyph = MenuShell.banded(size, plan.groupStartRows, rail = true)
        holder.decorated = MenuShell.decorated(player, glyph)

        val headingTexts = model.bands.filter { it.cells.isNotEmpty() }.map { it.heading }
        val headed = holder.decorated && MenuHeadings.available(player) && headingTexts.all { PanelText.supports(it) }
        val headings = if (headed) plan.bands.zip(model.bands.filter { it.cells.isNotEmpty() }).map { (bp, band) ->
            MenuHeadings.Heading(bp.labelSlot, RailLayout.LABEL_CELLS, MenuHeadings.fit(band.heading, RailLayout.LABEL_CELLS), band.heading)
        } else emptyList()

        val title = if (headed) {
            MenuShell.title(player, glyph, LycoText.upright(model.title), headings)
        } else {
            MenuShell.title(player, glyph, model.title)
        }
        val inv = builder.build(holder, size, title)
        holder.setInventory(inv)
        draw(inv, holder, model, plan, headed, player)
        player.openInventory(inv)
    }

    /** 同一頁內重畫(標題不變,例如切換一項規則之後)。 */
    fun redraw(player: Player, holder: ClaimMenuHolder, model: PageModel) {
        val plan = RailLayout.plan(model.bands.map { it.id to it.cells.size }) ?: return open(player, holder, model)
        val headed = holder.decorated && MenuHeadings.available(player) &&
            model.bands.filter { it.cells.isNotEmpty() }.all { PanelText.supports(it.heading) }
        draw(holder.inventory, holder, model, plan, headed, player)
    }

    private fun draw(inv: Inventory, holder: ClaimMenuHolder, model: PageModel, plan: RailLayout.Plan, headed: Boolean, player: Player) {
        inv.clear()
        holder.actions.clear()

        tabs(player, holder).forEachIndexed { index, cell -> put(inv, holder, RailLayout.tabSlot(index), cell) }
        if (!holder.decorated) {
            RailLayout.separatorSlots().forEach { inv.setItem(it, LycoIcons.itemOr("filler", Material.GRAY_STAINED_GLASS_PANE)) }
        }

        val bands = model.bands.filter { it.cells.isNotEmpty() }
        plan.bands.zip(bands).forEach { (bp, band) ->
            if (headed) {
                // 字已經畫在標題背景的牌子上;格子放透明物件,滑過去看得到完整組名
                (0 until RailLayout.LABEL_CELLS).forEach { offset ->
                    inv.setItem(bp.labelSlot + offset, labelHitbox(band.heading))
                }
            } else {
                builder.place(inv, bp.labelSlot, Material.OAK_HANGING_SIGN, "<color:#ffd166><bold>${band.heading}</bold></color>")
            }
            band.cells.forEachIndexed { i, cell -> put(inv, holder, bp.itemSlots[i], cell) }
        }

        model.footer.forEach { (slot, cell) -> put(inv, holder, footerSlot(slot), cell) }
        builder.finish(inv, holder.decorated)
    }

    private fun put(inv: Inventory, holder: ClaimMenuHolder, slot: Int, cell: Cell) {
        if (cell.head != null) {
            // 玩家平面頭像:與 Lycovelia 主選單同一份契約(PlayerPortraitContractTest)——
            // 頭顱掛 `lycohinya:menu_portrait`(資源包畫成正面平面頭,沒標記的頭顱不受影響),
            // 名稱前接 `<head:UUID>` 文字物件(原版就會畫 2D 頭,沒有資源包也看得到)。
            val stack = ItemStack(Material.PLAYER_HEAD)
            stack.editMeta { meta ->
                (meta as? SkullMeta)?.owningPlayer = Bukkit.getOfflinePlayer(cell.head)
                val cmd = meta.customModelDataComponent
                cmd.strings = listOf(PORTRAIT_MODEL)
                meta.setCustomModelDataComponent(cmd)
                meta.displayName(mm.deserializeUpright("<white><head:${cell.head}></white> ${cell.name}"))
                if (cell.lore.isNotEmpty()) meta.lore(cell.lore.map { mm.deserializeUpright(it) })
                if (cell.glint) meta.setEnchantmentGlintOverride(true)
            }
            inv.setItem(slot, stack)
        } else {
            builder.placeIcon(inv, slot, cell.iconId, cell.name, cell.lore, cell.fallback, cell.glint)
        }
        cell.action?.let { holder.actions[slot] = it }
    }

    private fun labelHitbox(label: String): ItemStack =
        LycoIcons.itemOr("works_hitbox", Material.GRAY_STAINED_GLASS_PANE).also { stack ->
            stack.editMeta { meta -> meta.displayName(mm.deserializeUpright("<color:#ffd166><bold>$label</bold></color>")) }
        }

    companion object {
        const val PORTRAIT_MODEL = "lycohinya:menu_portrait"
    }

    private fun footerSlot(slot: FooterSlot): Int = when (slot) {
        FooterSlot.BACK -> nav.back
        FooterSlot.HOME -> nav.home
        FooterSlot.PREVIOUS -> nav.previousPage
        FooterSlot.INDICATOR -> nav.pageIndicator
        FooterSlot.NEXT -> nav.nextPage
        FooterSlot.HELP -> nav.help
        FooterSlot.CLOSE -> nav.rightClose
    }
}
