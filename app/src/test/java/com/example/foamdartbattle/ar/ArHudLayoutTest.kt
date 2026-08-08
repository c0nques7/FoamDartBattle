package com.example.foamdartbattle.ar

import com.example.foamdartbattle.data.HudElementState
import org.junit.Assert.*
import org.junit.Test

class ArHudLayoutTest {

    @Test
    fun testHudElementState_defaults() {
        val state = HudElementState()
        assertEquals(0f, state.xOffset, 0.001f)
        assertEquals(0f, state.yOffset, 0.001f)
        assertEquals(1.0f, state.scale, 0.001f)
    }

    @Test
    fun testHudElementState_customValues() {
        val state = HudElementState(xOffset = 150.5f, yOffset = -22.3f, scale = 1.75f)
        assertEquals(150.5f, state.xOffset, 0.001f)
        assertEquals(-22.3f, state.yOffset, 0.001f)
        assertEquals(1.75f, state.scale, 0.001f)
    }

    @Test
    fun testLayoutBoundsCoercion_logic() {
        // Enforce bounds similar to what we do in gestures
        val screenWidth = 1080f
        val screenHeight = 2400f
        
        // Simulating gesture movement drag and scale pinch
        val currentScale = 1.2f
        val zoomFactor = 0.8f
        val newScale = (currentScale * zoomFactor).coerceIn(0.5f, 3.0f)
        assertEquals(0.96f, newScale, 0.001f)

        // Simulating off-screen drag
        val draggedX = 1200f // Off screen (screenWidth is 1080)
        val draggedY = -50f  // Off screen

        val boundedX = draggedX.coerceIn(0f, screenWidth - 80f)
        val boundedY = draggedY.coerceIn(0f, screenHeight - 80f)

        assertEquals(1000f, boundedX, 0.001f) // 1080 - 80
        assertEquals(0f, boundedY, 0.001f)    // clamped to 0f
    }
}
