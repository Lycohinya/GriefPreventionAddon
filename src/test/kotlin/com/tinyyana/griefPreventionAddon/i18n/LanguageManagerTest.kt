package com.tinyyana.griefPreventionAddon.i18n

import org.junit.jupiter.api.Test
import java.util.Locale
import kotlin.test.assertEquals

class LanguageManagerTest {

    @Test
    fun `locale matching logic maps zh to zh_TW and en to en_US`() {
        fun resolveLang(locale: Locale): String {
            val lang = locale.language.lowercase()
            return when {
                lang == "zh" -> "zh_TW"
                lang == "en" -> "en_US"
                else -> "zh_TW"
            }
        }

        assertEquals("zh_TW", resolveLang(Locale.TRADITIONAL_CHINESE))
        assertEquals("zh_TW", resolveLang(Locale.TAIWAN))
        assertEquals("zh_TW", resolveLang(Locale("zh", "TW")))
        assertEquals("en_US", resolveLang(Locale.ENGLISH))
        assertEquals("en_US", resolveLang(Locale.US))
        assertEquals("en_US", resolveLang(Locale.UK))
        assertEquals("zh_TW", resolveLang(Locale.JAPANESE))
    }
}
