package com.example.foamdartbattle.game

data class GameSettings(
    val maxRounds: Int = 5,
    val prepTimeSeconds: Int = 30,
    val shrinkTimeSeconds: Int = 15,
    val showNextRing: Boolean = true
)
