package com.example.foamdartbattle.game

import com.google.android.gms.maps.model.LatLng

enum class GamePhase {
    PREP, SHRINK
}

data class GameState(
    val isGameActive: Boolean = false,
    val totalTimeSeconds: Int = 0,
    val currentRound: Int = 1,
    val playerHealth: Int = 100,
    val isOutsideZone: Boolean = false,
    val zoneCenter: LatLng? = null,
    val currentZoneRadius: Float = 0f,
    val isEliminated: Boolean = false,
    
    // Advanced Battle Royale & Dev Mode fields
    val currentPhase: GamePhase = GamePhase.PREP,
    val phaseTimeLeftSeconds: Int = 30,
    val nextZoneCenter: LatLng? = null,
    val nextZoneRadius: Float = 0f,
    val previewZoneCenter: LatLng? = null,
    val previewZoneRadius: Float = 0f,
    val settings: GameSettings = GameSettings(),
    val playerLocation: LatLng? = null,
    
    // Match termination voting state machine
    val endGameVoteActive: Boolean = false,
    val endGameVoteYesCount: Int = 0,
    val endGameVoteNoCount: Int = 0,
    val totalLobbyPlayers: Int = 3, // Defaults to 3 players for local testing
    val hasVotedInCurrentPoll: Boolean = false,
    
    // Interpolation helpers to avoid cumulative floating drift
    val startZoneCenter: LatLng? = null,
    val startZoneRadius: Float = 0f
)
