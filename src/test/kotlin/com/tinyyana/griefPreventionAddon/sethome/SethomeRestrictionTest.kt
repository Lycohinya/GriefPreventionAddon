package com.tinyyana.griefPreventionAddon.sethome

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SethomeRestrictionTest {

    @Test
    fun `identifies sethome command variants`() {
        assertTrue(SethomeRestrictionListener.isSethomeCommand("/sethome"))
        assertTrue(SethomeRestrictionListener.isSethomeCommand("/sethome bed"))
        assertTrue(SethomeRestrictionListener.isSethomeCommand("sethome home1"))
        assertTrue(SethomeRestrictionListener.isSethomeCommand("/esethome"))
        assertTrue(SethomeRestrictionListener.isSethomeCommand("/createhome"))
        assertTrue(SethomeRestrictionListener.isSethomeCommand("/ecreatehome"))
        assertTrue(SethomeRestrictionListener.isSethomeCommand("/essentials:sethome myhome"))
        assertTrue(SethomeRestrictionListener.isSethomeCommand("/essentials:esethome"))
        assertTrue(SethomeRestrictionListener.isSethomeCommand("/essentials:createhome"))
        assertTrue(SethomeRestrictionListener.isSethomeCommand("/essentials:ecreatehome"))

        assertFalse(SethomeRestrictionListener.isSethomeCommand("/home"))
        assertFalse(SethomeRestrictionListener.isSethomeCommand("/spawn"))
        assertFalse(SethomeRestrictionListener.isSethomeCommand("/ctp"))
        assertFalse(SethomeRestrictionListener.isSethomeCommand("/tp"))
        assertFalse(SethomeRestrictionListener.isSethomeCommand(""))
    }
}
