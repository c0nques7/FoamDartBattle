package com.example.foamdartbattle.game

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import org.junit.Assert.*
import org.junit.Test

class GameRulesTest {

    @Test
    fun tickGame_doesNothingIfGameInactive() {
        val state = GameState(isGameActive = false, totalTimeSeconds = 0)
        val result = GameRules.tickGame(state)
        assertEquals(state, result)
    }

    @Test
    fun tickGame_doesNothingIfPlayerEliminated() {
        val state = GameState(isGameActive = true, isEliminated = true)
        val result = GameRules.tickGame(state)
        assertEquals(state, result)
    }

    @Test
    fun tickGame_incrementsTotalTimeAndDecrementsPhaseTime() {
        val state = GameState(
            isGameActive = true,
            totalTimeSeconds = 10,
            currentPhase = GamePhase.PREP,
            phaseTimeLeftSeconds = 25,
            playerHealth = 100
        )
        val result = GameRules.tickGame(state)
        assertEquals(11, result.totalTimeSeconds)
        assertEquals(24, result.phaseTimeLeftSeconds)
        assertEquals(GamePhase.PREP, result.currentPhase)
        assertEquals(100, result.playerHealth)
    }

    @Test
    fun tickGame_decrementsHealthWhenOutsideZone() {
        val state = GameState(
            isGameActive = true,
            totalTimeSeconds = 10,
            currentPhase = GamePhase.PREP,
            phaseTimeLeftSeconds = 25,
            playerHealth = 100,
            isOutsideZone = true
        )
        val result = GameRules.tickGame(state)
        assertEquals(100 - GameRules.HEALTH_DRAIN_RATE, result.playerHealth)
        assertFalse(result.isEliminated)
    }

    @Test
    fun tickGame_eliminatesPlayerWhenHealthReachesZero() {
        val state = GameState(
            isGameActive = true,
            totalTimeSeconds = 10,
            currentPhase = GamePhase.PREP,
            phaseTimeLeftSeconds = 25,
            playerHealth = GameRules.HEALTH_DRAIN_RATE,
            isOutsideZone = true
        )
        val result = GameRules.tickGame(state)
        assertEquals(0, result.playerHealth)
        assertTrue(result.isEliminated)
        assertFalse(result.isGameActive)
    }

    @Test
    fun tickGame_transitionsFromPrepToShrink() {
        val settings = GameSettings(prepTimeSeconds = 10, shrinkTimeSeconds = 5)
        val state = GameState(
            isGameActive = true,
            totalTimeSeconds = 9,
            currentPhase = GamePhase.PREP,
            phaseTimeLeftSeconds = 1, // Hits 0 on next tick
            zoneCenter = LatLng(37.4, -122.0),
            currentZoneRadius = 50.0f,
            settings = settings
        )
        val result = GameRules.tickGame(state)
        assertEquals(GamePhase.SHRINK, result.currentPhase)
        assertEquals(5, result.phaseTimeLeftSeconds)
        assertEquals(LatLng(37.4, -122.0), result.startZoneCenter)
        assertEquals(50.0f, result.startZoneRadius)
    }

    @Test
    fun tickGame_interpolatesDuringShrinkPhase() {
        val settings = GameSettings(prepTimeSeconds = 10, shrinkTimeSeconds = 10)
        val state = GameState(
            isGameActive = true,
            totalTimeSeconds = 15,
            currentPhase = GamePhase.SHRINK,
            phaseTimeLeftSeconds = 5, // 5 seconds left out of 10 (halfway)
            startZoneCenter = LatLng(0.0, 0.0),
            startZoneRadius = 100.0f,
            nextZoneCenter = LatLng(0.0, 1.0),
            nextZoneRadius = 50.0f,
            zoneCenter = LatLng(0.0, 0.0),
            currentZoneRadius = 100.0f,
            settings = settings
        )
        val result = GameRules.tickGame(state)
        
        // At half-way (5s left before tick -> 4s left after tick), radius should be 70.0f (60% shrunken)
        assertEquals(70.0f, result.currentZoneRadius, 0.1f)
        
        // Center should have moved 60% of the distance towards next zone
        val currentDist = SphericalUtil.computeDistanceBetween(LatLng(0.0, 0.0), result.zoneCenter!!)
        val totalDist = SphericalUtil.computeDistanceBetween(LatLng(0.0, 0.0), LatLng(0.0, 1.0))
        assertEquals(0.6, currentDist / totalDist, 0.01)
    }

    @Test
    fun tickGame_completesRoundAndStartsNextRoundPrep() {
        val settings = GameSettings(maxRounds = 3, prepTimeSeconds = 10, shrinkTimeSeconds = 5)
        val state = GameState(
            isGameActive = true,
            totalTimeSeconds = 20,
            currentRound = 1,
            currentPhase = GamePhase.SHRINK,
            phaseTimeLeftSeconds = 1, // Shrink is ending
            zoneCenter = LatLng(0.0, 0.0),
            currentZoneRadius = 50.0f,
            nextZoneCenter = LatLng(0.0, 0.01),
            nextZoneRadius = 30.0f,
            previewZoneCenter = LatLng(0.0, 0.012),
            previewZoneRadius = 18.0f,
            settings = settings
        )
        val result = GameRules.tickGame(state)
        assertEquals(2, result.currentRound)
        assertEquals(GamePhase.PREP, result.currentPhase)
        assertEquals(10, result.phaseTimeLeftSeconds)
        // Next center and radius become the new current zone
        assertEquals(LatLng(0.0, 0.01), result.zoneCenter)
        assertEquals(30.0f, result.currentZoneRadius)
        // Safe zone (nextZone) becomes the previous previewZone
        assertEquals(LatLng(0.0, 0.012), result.nextZoneCenter)
        assertEquals(18.0f, result.nextZoneRadius)
        // A new next ring should have been generated as preview
        assertNotNull(result.previewZoneCenter)
        assertTrue(result.previewZoneRadius < 18.0f)
    }

    @Test
    fun generateNextRing_fitsCompletelyInsideCurrentRing() {
        val currentCenter = LatLng(37.4220, -122.0840)
        val currentRadius = 100.0f
        
        for (i in 1..100) {
            val (nextCenter, nextRadius) = GameRules.generateNextRing(currentCenter, currentRadius)
            
            // The distance between centers
            val distance = SphericalUtil.computeDistanceBetween(currentCenter, nextCenter)
            
            // For next ring to fit inside current ring:
            // distance + nextRadius must be <= currentRadius
            assertTrue(
                "Ring does not fit: distance($distance) + nextRadius($nextRadius) = ${distance + nextRadius} must be <= currentRadius($currentRadius)",
                distance + nextRadius <= currentRadius + 0.01 // margin for float precision
            )
        }
    }

    @Test
    fun tickGame_calculatesOutsideZoneFromPlayerLocationAndAppliesDamage() {
        val state = GameState(
            isGameActive = true,
            totalTimeSeconds = 10,
            currentPhase = GamePhase.PREP,
            phaseTimeLeftSeconds = 25,
            playerHealth = 100,
            zoneCenter = LatLng(37.4220, -122.0840),
            currentZoneRadius = 10.0f, // 10 meters radius
            playerLocation = LatLng(37.4230, -122.0840), // ~111 meters away, definitely outside!
            isOutsideZone = false // initially false, should become true dynamically
        )
        val result = GameRules.tickGame(state)
        assertTrue(result.isOutsideZone)
        assertEquals(100 - GameRules.HEALTH_DRAIN_RATE, result.playerHealth)
    }
}
