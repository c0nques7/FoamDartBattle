package com.example.foamdartbattle.game

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object GameStateHolder {
    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    fun updateState(transform: (GameState) -> GameState) {
        _gameState.update(transform)
    }

    fun resetState() {
        _gameState.value = GameState()
    }
}
