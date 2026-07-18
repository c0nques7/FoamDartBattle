package com.example.foamdartbattle.game

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.lifecycle.ViewModel
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.StateFlow

class GameViewModel : ViewModel() {
    val uiState: StateFlow<GameState> = GameStateHolder.gameState

    fun startGame(context: Context, center: LatLng, safeRadius: Float, damageRadius: Float, settings: GameSettings = GameSettings()) {
        val intent = Intent(context, GameEngineService::class.java).apply {
            action = GameEngineService.ACTION_START_GAME
            putExtra("CENTER_LAT", center.latitude)
            putExtra("CENTER_LNG", center.longitude)
            putExtra("SAFE_RADIUS", safeRadius)
            putExtra("DAMAGE_RADIUS", damageRadius)
            putExtra("MAX_ROUNDS", settings.maxRounds)
            putExtra("PREP_TIME", settings.prepTimeSeconds)
            putExtra("SHRINK_TIME", settings.shrinkTimeSeconds)
            putExtra("SHOW_NEXT_RING", settings.showNextRing)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopGame(context: Context) {
        val intent = Intent(context, GameEngineService::class.java).apply {
            action = GameEngineService.ACTION_STOP_GAME
        }
        context.startService(intent)
    }

    fun leaveMatch(context: Context) {
        stopGame(context)
        GameStateHolder.updateState {
            it.copy(
                isGameActive = false,
                endGameVoteActive = false,
                endGameVoteYesCount = 0,
                endGameVoteNoCount = 0,
                hasVotedInCurrentPoll = false
            )
        }
        Toast.makeText(context, "You left the match.", Toast.LENGTH_SHORT).show()
    }

    fun initiateEndGameVote(context: Context) {
        GameStateHolder.updateState {
            it.copy(
                endGameVoteActive = true,
                endGameVoteYesCount = 1,
                endGameVoteNoCount = 0,
                hasVotedInCurrentPoll = true
            )
        }
        Toast.makeText(context, "End Match vote initiated!", Toast.LENGTH_SHORT).show()
        checkVoteResults(context)
    }

    fun cancelEndGameVote(context: Context) {
        GameStateHolder.updateState {
            it.copy(
                endGameVoteActive = false,
                endGameVoteYesCount = 0,
                endGameVoteNoCount = 0,
                hasVotedInCurrentPoll = false
            )
        }
        Toast.makeText(context, "End match request cancelled. Returning to play!", Toast.LENGTH_SHORT).show()
    }

    fun castVote(context: Context, agree: Boolean) {
        GameStateHolder.updateState {
            val yes = if (agree) it.endGameVoteYesCount + 1 else it.endGameVoteYesCount
            val no = if (!agree) it.endGameVoteNoCount + 1 else it.endGameVoteNoCount
            it.copy(
                endGameVoteYesCount = yes,
                endGameVoteNoCount = no,
                hasVotedInCurrentPoll = true
            )
        }
        checkVoteResults(context)
    }

    private fun checkVoteResults(context: Context) {
        val state = GameStateHolder.gameState.value
        val majorityNeeded = (state.totalLobbyPlayers / 2) + 1
        
        if (state.endGameVoteYesCount >= majorityNeeded) {
            // Majority agreed! End the match!
            stopGame(context)
            GameStateHolder.updateState {
                it.copy(
                    endGameVoteActive = false,
                    endGameVoteYesCount = 0,
                    endGameVoteNoCount = 0,
                    hasVotedInCurrentPoll = false
                )
            }
            Toast.makeText(context, "Match ended by consensus!", Toast.LENGTH_LONG).show()
        } else if (state.endGameVoteNoCount >= majorityNeeded || (state.endGameVoteYesCount + state.endGameVoteNoCount == state.totalLobbyPlayers)) {
            // Majority disagreed or all votes cast and yes failed
            GameStateHolder.updateState {
                it.copy(
                    endGameVoteActive = false,
                    endGameVoteYesCount = 0,
                    endGameVoteNoCount = 0,
                    hasVotedInCurrentPoll = false
                )
            }
            Toast.makeText(context, "End Match request rejected by lobby.", Toast.LENGTH_LONG).show()
        }
    }
}
