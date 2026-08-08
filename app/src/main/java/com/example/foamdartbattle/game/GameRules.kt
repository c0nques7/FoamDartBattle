package com.example.foamdartbattle.game

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import kotlin.random.Random

object GameRules {
    const val MIN_RADIUS = 5.0f
    const val SHRINK_FACTOR = 0.60f // Shrink by 40% each round
    const val HEALTH_DRAIN_RATE = 5 // HP lost per second outside zone

    fun generateNextRing(currentCenter: LatLng, currentRadius: Float): Pair<LatLng, Float> {
        val nextRadius = (currentRadius * SHRINK_FACTOR).coerceAtLeast(MIN_RADIUS)
        if (currentRadius <= MIN_RADIUS || currentRadius <= nextRadius) {
            return Pair(currentCenter, currentRadius)
        }

        val maxOffset = (currentRadius - nextRadius).toDouble()
        val offsetDist = Random.nextDouble(0.0, maxOffset)
        val heading = Random.nextDouble(0.0, 360.0)
        
        val nextCenter = SphericalUtil.computeOffset(currentCenter, offsetDist, heading)
        return Pair(nextCenter, nextRadius)
    }

    fun tickGame(state: GameState): GameState {
        if (!state.isGameActive || state.isEliminated) return state

        val newTotalTime = state.totalTimeSeconds + 1
        var newPhaseTime = state.phaseTimeLeftSeconds - 1
        var newPhase = state.currentPhase
        var newRound = state.currentRound
        var newHealth = state.playerHealth
        var eliminated = state.isEliminated
        var gameActive = state.isGameActive
        
        var currentRadius = state.currentZoneRadius
        var currentCenter = state.zoneCenter
        var startRadius = state.startZoneRadius
        var startCenter = state.startZoneCenter
        var nextRadius = state.nextZoneRadius
        var nextCenter = state.nextZoneCenter
        var previewRadius = state.previewZoneRadius
        var previewCenter = state.previewZoneCenter

        // 1. Calculate outside zone dynamically if we have player and zone locations
        var outsideZone = state.isOutsideZone
        if (currentCenter != null && state.playerLocation != null) {
            val distance = SphericalUtil.computeDistanceBetween(currentCenter, state.playerLocation)
            outsideZone = distance > currentRadius
        }

        // 2. Outside zone damage check
        if (outsideZone) {
            newHealth -= HEALTH_DRAIN_RATE
            if (newHealth <= 0) {
                newHealth = 0
                eliminated = true
                gameActive = false
            }
        }

        if (eliminated) {
            return state.copy(
                totalTimeSeconds = newTotalTime,
                playerHealth = newHealth,
                isEliminated = true,
                isGameActive = false,
                isOutsideZone = outsideZone
            )
        }

        // 3. Handle Phase Progression
        when (state.currentPhase) {
            GamePhase.PREP -> {
                if (newPhaseTime <= 0) {
                    // Transition to SHRINK
                    newPhase = GamePhase.SHRINK
                    newPhaseTime = state.settings.shrinkTimeSeconds
                    startCenter = state.zoneCenter
                    startRadius = state.currentZoneRadius
                }
            }
            GamePhase.SHRINK -> {
                if (newPhaseTime <= 0) {
                    // Shrink finished
                    currentCenter = state.nextZoneCenter
                    currentRadius = state.nextZoneRadius
                    
                    if (newRound >= state.settings.maxRounds) {
                        // All rounds completed!
                        gameActive = false
                    } else {
                        // Transition to next round PREP
                        newRound++
                        newPhase = GamePhase.PREP
                        newPhaseTime = state.settings.prepTimeSeconds
                        
                        // Shift zones down for the three-tiered system:
                        // Next Safe Zone (nextZone) becomes the previous Preview Zone (previewZone)
                        nextCenter = state.previewZoneCenter
                        nextRadius = state.previewZoneRadius
                        
                        // Generate a new Preview Zone inside the new Safe Zone
                        nextCenter?.let { nc ->
                            val nextRing = generateNextRing(nc, nextRadius)
                            previewCenter = nextRing.first
                            previewRadius = nextRing.second
                        }
                    }
                } else {
                    // Interpolate current zone towards next zone
                    if (startCenter != null && nextCenter != null) {
                        val elapsed = state.settings.shrinkTimeSeconds - newPhaseTime
                        val t = elapsed.toDouble() / state.settings.shrinkTimeSeconds.toDouble()
                        
                        currentRadius = startRadius + (t * (nextRadius - startRadius)).toFloat()
                        currentCenter = SphericalUtil.interpolate(startCenter, nextCenter, t)
                    }
                }
            }
        }

        return state.copy(
            totalTimeSeconds = newTotalTime,
            currentRound = newRound,
            playerHealth = newHealth,
            isEliminated = eliminated,
            isGameActive = gameActive,
            currentPhase = newPhase,
            phaseTimeLeftSeconds = newPhaseTime,
            zoneCenter = currentCenter,
            currentZoneRadius = currentRadius,
            startZoneCenter = startCenter,
            startZoneRadius = startRadius,
            nextZoneCenter = nextCenter,
            nextZoneRadius = nextRadius,
            previewZoneCenter = previewCenter,
            previewZoneRadius = previewRadius,
            isOutsideZone = outsideZone
        )
    }
}
