package com.example.foamdartbattle.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {

    @Test
    fun gameTheme_hasRequiredPresets() {
        // Verify we have exactly the 3 requested theme presets
        val values = GameTheme.values()
        assertEquals(3, values.size)
        
        val names = values.map { it.name }
        assertTrue(names.contains("CYBERPUNK"))
        assertTrue(names.contains("TACTICAL"))
        assertTrue(names.contains("PLAYFUL"))
    }

    @Test
    fun colors_areCorrectlyConfigured() {
        // Verify our color constants are loaded and non-null
        assertNotNull(CyberpunkPrimary)
        assertNotNull(TacticalPrimary)
        assertNotNull(PlayfulPrimary)
    }
}
