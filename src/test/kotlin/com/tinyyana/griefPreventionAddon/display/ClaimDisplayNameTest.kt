package com.tinyyana.griefPreventionAddon.display

import com.tinyyana.griefPreventionAddon.storage.ClaimSettingsStore
import com.tinyyana.griefPreventionAddon.storage.Db
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `/ctp` 列表、傳送成功訊息、GUI 卡片曾各自組一套「有別名用別名,沒別名退回什麼」的規則,
 * 其中一條退回規則甚至誤用了 GUI lore 專用的完整句子(`gui.card-status-no-alias`)當名字。
 * 這裡驗證統一後的 [ClaimDisplayName.resolve] 規則本身是對的,顯示層(LanguageManager)
 * 的跳脫另外在 [com.tinyyana.griefPreventionAddon.i18n.AliasMiniMessageReproTest] 驗證。
 */
class ClaimDisplayNameTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var db: Db
    private lateinit var store: ClaimSettingsStore

    @BeforeEach
    fun setUp() {
        db = Db(File(tempDir, "test.db"))
        store = ClaimSettingsStore(db)
        store.init()
    }

    @AfterEach
    fun tearDown() {
        db.close()
    }

    @Test
    fun `with a valid alias the alias is preferred everywhere`() {
        store.setAlias(10L, "主家")

        val display = ClaimDisplayName.resolve(store, 10L)

        assertTrue(display.hasAlias)
        assertEquals("主家", display.alias)
        assertEquals("主家", display.name)
        assertEquals("「主家」", display.decorated)
        // click 目標一律用編號,不用別名,別名重複也不會傳送到別人的花域
        assertEquals("10", display.targetParam)
    }

    @Test
    fun `without an alias it falls back to the claim id, never the GUI lore sentence`() {
        val display = ClaimDisplayName.resolve(store, 42L)

        assertFalse(display.hasAlias)
        assertEquals(null, display.alias)
        assertEquals("#42", display.name)
        assertEquals("#42", display.decorated)
        assertEquals("42", display.targetParam)
    }

    @Test
    fun `blank alias in storage is treated the same as no alias`() {
        // setAlias 本身會擋空白,但防禦性驗證:萬一底層資料是空字串也不能被當成「有別名」
        store.setAlias(5L, "  ")

        val display = ClaimDisplayName.resolve(store, 5L)

        assertFalse(display.hasAlias)
        assertEquals("#5", display.name)
    }

    @Test
    fun `alias with minimessage-significant characters is preserved raw on the model`() {
        // 別名驗證的邊界不是這個模型的責任(見 ClaimNameCommand),這裡只確認
        // resolve() 本身不會做任何字元過濾或跳脫——跳脫留給插入模板前那一刻
        // (LanguageManager.get / ClaimSettingsGuiService 各自呼叫 escapeForMiniMessageTemplate)。
        store.setAlias(7L, "Yana's")

        val display = ClaimDisplayName.resolve(store, 7L)

        assertEquals("Yana's", display.alias)
        assertEquals("「Yana's」", display.decorated)
    }

    @Test
    fun `subdivision id and its top claim id resolve independently`() {
        // 別名一律綁 GriefPrevention 的 top claim id(cname 走 bridge.getTopClaim);
        // 子領地(subdivision)有自己的 id,不會意外繼承或污染母領地的別名。
        val topClaimId = 100L
        val subdivisionId = 101L
        store.setAlias(topClaimId, "主家")

        val topDisplay = ClaimDisplayName.resolve(store, topClaimId)
        val subDisplay = ClaimDisplayName.resolve(store, subdivisionId)

        assertTrue(topDisplay.hasAlias)
        assertEquals("主家", topDisplay.name)
        assertFalse(subDisplay.hasAlias)
        assertEquals("#101", subDisplay.name)
    }

    @Test
    fun `completion offers one candidate per claim - alias when set, otherwise hash id`() {
        store.setAlias(108L, "測試")
        kotlin.test.assertEquals("測試", ClaimDisplayName.resolve(store, 108L).completion)
        kotlin.test.assertEquals("#96", ClaimDisplayName.resolve(store, 96L).completion)
    }
}
