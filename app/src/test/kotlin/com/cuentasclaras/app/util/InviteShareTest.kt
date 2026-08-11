package com.cuentasclaras.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteShareTest {
    @Test
    fun normalizeCode_stripsSpacesAndDashes() {
        assertEquals("ABC123", InviteShare.normalizeCode(" abc-123 "))
    }

    @Test
    fun deepLinkUri_usesCustomScheme() {
        assertEquals("cuentasclaras://join/XY12", InviteShare.deepLinkUri("xy-12"))
    }

    @Test
    fun shareText_includesStepsAndDeepLink() {
        val text = InviteShare.shareText("Casa", "xy12")
        assertTrue(text.contains("Casa"))
        assertTrue(text.contains("XY12"))
        assertTrue(text.contains("Unirme a un grupo"))
        assertTrue(text.contains("cuentasclaras://join/XY12"))
    }
}
