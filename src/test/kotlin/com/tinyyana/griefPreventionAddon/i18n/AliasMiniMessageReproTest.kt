package com.tinyyana.griefPreventionAddon.i18n

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.minimessage.MiniMessage
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private fun plainOf(component: Component): String {
    val sb = StringBuilder()
    fun walk(c: Component) {
        if (c is TextComponent) sb.append(c.content())
        c.children().forEach { walk(it) }
    }
    walk(component)
    return sb.toString()
}

private fun findClickEventString(component: Component): String? {
    component.clickEvent()?.let { return it.toString() }
    for (child in component.children()) {
        findClickEventString(child)?.let { return it }
    }
    return null
}

/**
 * /ctp 別名顯示異常根因回歸測試。
 *
 * 根因:舊的 `name.list-entry`(`ClaimTpCommand`/`ClaimsListCommand` 已不再使用)把
 * `{name}` 塞進 `<hover:show_text:'...'>` 這種帶單引號參數的標籤裡,取代時用純字串
 * String.replace、沒有跳脫。別名允許含單引號(ClaimNameCommand.INVALID_CHARS 沒禁 `'`,
 * `/cadmin name` 更完全不驗證),一旦別名裡有 `'`,MiniMessage 會把整段解析失敗、
 * 退回顯示原始標籤字串——玩家看到的是一整行沒被解析的 `<click:...><hover:...>...`,
 * 別名本身也認不出來。
 *
 * 這個測試檔驗證兩件事:
 * 1. (第一個測試)重現舊 key 結構的實際壞法,留著當「這個坑長怎樣」的證據。
 * 2. 新 key `name.claim-entry`/`name.alias-set-success` 不再把別名塞進任何引號參數,
 *    從結構上避開這個坑;[escapeForMiniMessageTemplate] 只跳脫 `< \` 兩個在任何位置都
 *    安全的字元,當 `/cadmin name`(無驗證)塞入完整標籤時的第二層防線。
 */
class AliasMiniMessageReproTest {

    private val mm = MiniMessage.miniMessage()

    // 舊版 name.list-entry 的結構(已不再由程式碼使用,只用來重現/證明根因)
    private val brokenTemplate =
        "  <click:run_command:/claimtp {target}><hover:show_text:'<gray>點擊傳送至 {name}</gray>'>" +
            "<color:#ffd166>花</color> <color:#ff8fc4><bold>{name}</bold></color> " +
            "<color:#a8a8a8>(#{claimId} · {width}×{height})</color> " +
            "<color:#6fd8e8><bold>[傳送]</bold></color></hover></click>"

    // 現行 name.claim-entry:hover 文字改成靜態、不再重複塞別名進單引號參數
    private val fixedTemplate =
        "  <click:run_command:/claimtp {target}><hover:show_text:'<gray>點擊傳送至此花域</gray>'>" +
            "<color:#ffd166>花</color> <color:#ff8fc4><bold>{name}</bold></color> " +
            "<color:#a8a8a8>(#{claimId} · {width}×{height})</color> " +
            "<color:#6fd8e8><bold>[傳送]</bold></color></hover></click>"

    private fun substitute(template: String, name: String): String = template
        .replace("{target}", "12")
        .replace("{name}", name)
        .replace("{claimId}", "12")
        .replace("{width}", "20")
        .replace("{height}", "20")

    @Test
    fun `舊模板結構下 alias 含單引號會讓整段標籤語法洩漏到聊天畫面`() {
        val alias = "Yana's" // 不含空白/斜線等 INVALID_CHARS,ClaimNameCommand 目前驗證允許此別名通過
        val plain = plainOf(mm.deserialize(substitute(brokenTemplate, alias)))

        assertTrue(plain.contains("<hover:show_text"), "舊結構下 hover 標籤應該沒被解析、原樣洩漏出來:$plain")
    }

    @Test
    fun `新的 name-claim-entry 結構不受 alias 裡的單引號影響`() {
        val alias = "Yana's"
        val component = mm.deserialize(substitute(fixedTemplate, alias))
        val plain = plainOf(component)

        assertTrue(!plain.contains("<hover:show_text"), "新結構不該再洩漏標籤語法:$plain")
        assertTrue(!plain.contains("<click:run_command"), "新結構不該再洩漏標籤語法:$plain")
        assertTrue(plain.contains(alias), "alias 本身要正確、完整顯示:$plain")

        // click:run_command 一律用領地編號,別名重複時傳送目標仍然明確
        val click = findClickEventString(component)
        assertNotNull(click)
        assertTrue(click.contains("/claimtp 12"), "click event 應該指向編號 12,不受別名影響:$click")
    }

    @Test
    fun `alias 不含特殊字元時新舊結構顯示結果一致`() {
        val alias = "主家"
        val before = plainOf(mm.deserialize(substitute(brokenTemplate, alias)))
        val after = plainOf(mm.deserialize(substitute(fixedTemplate, alias)))

        assertTrue(before.contains(alias))
        assertTrue(after.contains(alias))
    }

    @Test
    fun `escapeForMiniMessageTemplate 只跳脫任何位置都安全的字元`() {
        // `<`、`\` 在標籤外的一般文字裡一樣會被 MiniMessage 正確還原,適合當第二層防線,
        // 擋 /cadmin name(無驗證)塞入完整標籤,例如別名寫 "<red>...".
        assertEquals("\\<red>", escapeForMiniMessageTemplate("<red>"))
        assertEquals("back\\\\slash", escapeForMiniMessageTemplate("back\\slash"))

        // `'`、`\"`、`>` 故意不跳脫:它們只有在真的位於已開啟的標籤/引號參數裡才會被還原,
        // 別名通常插在標籤外的一般文字,加反斜線只會讓反斜線原封不動顯示出來。
        assertEquals("Yana's", escapeForMiniMessageTemplate("Yana's"))
        assertEquals("plain-name", escapeForMiniMessageTemplate("plain-name"))
        assertEquals("中文別名", escapeForMiniMessageTemplate("中文別名"))
    }

    @Test
    fun `escapeForMiniMessageTemplate 讓標籤字元在一般文字裡不會被還原成真標籤`() {
        val alias = escapeForMiniMessageTemplate("<red>PWNED</red>")
        val plain = plainOf(mm.deserialize("<bold>$alias</bold>"))

        assertTrue(plain.contains("<red>"), "跳脫後標籤字元要原樣顯示,不能被解析成真的顏色標籤:$plain")
        assertTrue(plain.contains("PWNED"), "別名文字本身仍要看得到:$plain")
    }
}
