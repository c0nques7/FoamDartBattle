package com.example.foamdartbattle.ar

import org.junit.Assert.*
import org.junit.Test

class CompassUtilsTest {

    @Test
    fun testGetAngularDifference_basic() {
        // No difference
        assertEquals(0f, CompassUtils.getAngularDifference(90f, 90f), 0.01f)
        
        // Positive difference (clockwise)
        assertEquals(10f, CompassUtils.getAngularDifference(100f, 90f), 0.01f)
        
        // Negative difference (counter-clockwise)
        assertEquals(-10f, CompassUtils.getAngularDifference(80f, 90f), 0.01f)
    }

    @Test
    fun testGetAngularDifference_wrapAround() {
        // Crossing the 360/0 boundary
        // Target is 10, Center is 350. Angular diff should be +20 degrees (shortest path clockwise)
        assertEquals(20f, CompassUtils.getAngularDifference(10f, 350f), 0.01f)
        
        // Target is 350, Center is 10. Angular diff should be -20 degrees (shortest path counter-clockwise)
        assertEquals(-20f, CompassUtils.getAngularDifference(350f, 10f), 0.01f)
        
        // Precise wrap boundaries
        assertEquals(-2f, CompassUtils.getAngularDifference(359f, 1f), 0.01f)
        assertEquals(2f, CompassUtils.getAngularDifference(1f, 359f), 0.01f)
    }

    @Test
    fun testBuildCompassMarkers_structureAndCount() {
        val markers = CompassUtils.buildCompassMarkers()
        
        // 360 degrees divided by 5 steps = 72 markers
        assertEquals(72, markers.size)
        
        // Verify North (0 degrees)
        val northMarker = markers.first { it.angle == 0f }
        assertEquals("N", northMarker.label)
        assertTrue(northMarker.isCardinal)
        assertFalse(northMarker.isMajor)

        // Verify Northeast (45 degrees)
        val neMarker = markers.first { it.angle == 45f }
        assertEquals("NE", neMarker.label)
        assertTrue(neMarker.isCardinal)
        assertFalse(neMarker.isMajor)

        // Verify a major tick (e.g. 30 degrees)
        val thirtyMarker = markers.first { it.angle == 30f }
        assertEquals("30", thirtyMarker.label)
        assertFalse(thirtyMarker.isCardinal)
        assertTrue(thirtyMarker.isMajor)

        // Verify a minor tick (e.g. 15 degrees)
        val fifteenMarker = markers.first { it.angle == 15f }
        assertEquals("", fifteenMarker.label)
        assertFalse(fifteenMarker.isCardinal)
        assertFalse(fifteenMarker.isMajor)
    }
}
