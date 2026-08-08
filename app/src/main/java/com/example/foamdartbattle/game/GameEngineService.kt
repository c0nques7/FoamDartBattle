package com.example.foamdartbattle.game

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.example.foamdartbattle.geofence.GeofenceManager
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class GameEngineService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var timerJob: Job? = null
    private lateinit var geofenceManager: GeofenceManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val location = locationResult.lastLocation ?: return
            val playerLatLng = LatLng(location.latitude, location.longitude)
            GameStateHolder.updateState {
                it.copy(playerLocation = playerLatLng)
            }
        }
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "game_engine_channel"
        const val ACTION_START_GAME = "ACTION_START_GAME"
        const val ACTION_STOP_GAME = "ACTION_STOP_GAME"
    }

    override fun onCreate() {
        super.onCreate()
        geofenceManager = GeofenceManager(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_GAME -> {
                val centerLat = intent.getDoubleExtra("CENTER_LAT", 0.0)
                val centerLng = intent.getDoubleExtra("CENTER_LNG", 0.0)
                val safeRadius = intent.getFloatExtra("SAFE_RADIUS", 50.0f)
                val damageRadius = intent.getFloatExtra("DAMAGE_RADIUS", 60.0f)
                
                val maxRounds = intent.getIntExtra("MAX_ROUNDS", 5)
                val prepTime = intent.getIntExtra("PREP_TIME", 30)
                val shrinkTime = intent.getIntExtra("SHRINK_TIME", 15)
                val showNextRing = intent.getBooleanExtra("SHOW_NEXT_RING", true)
                val settings = GameSettings(maxRounds, prepTime, shrinkTime, showNextRing)

                if (centerLat != 0.0 && centerLng != 0.0) {
                    val center = LatLng(centerLat, centerLng)
                    startGame(center, safeRadius, damageRadius, settings)
                }
            }
            ACTION_STOP_GAME -> {
                stopGame()
            }
        }
        return START_NOT_STICKY
    }

    private fun startGame(center: LatLng, safeRadius: Float, damageRadius: Float, settings: GameSettings) {
        val nextRing = GameRules.generateNextRing(center, safeRadius)
        
        // Reset state
        GameStateHolder.updateState {
            GameState(
                isGameActive = true,
                totalTimeSeconds = 0,
                currentRound = 1,
                playerHealth = 100,
                isOutsideZone = false,
                zoneCenter = center,
                currentZoneRadius = damageRadius,
                isEliminated = false,
                currentPhase = GamePhase.PREP,
                phaseTimeLeftSeconds = settings.prepTimeSeconds,
                nextZoneCenter = center,
                nextZoneRadius = safeRadius,
                previewZoneCenter = nextRing.first,
                previewZoneRadius = nextRing.second,
                settings = settings,
                startZoneCenter = center,
                startZoneRadius = damageRadius,
                playerLocation = null
            )
        }

        // Add initial geofence
        geofenceManager.addGeofence(center, safeRadius)

        // Start location tracking
        startLocationUpdates()

        // Start Foreground Notification
        startForeground(NOTIFICATION_ID, createNotification(GameStateHolder.gameState.value))

        // Observe state changes to update notifications
        GameStateHolder.gameState
            .onEach { state ->
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, createNotification(state))
            }
            .launchIn(serviceScope)

        // Start timer loop
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                tickGame()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMinUpdateIntervalMillis(1000)
            .build()
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun tickGame() {
        val oldState = GameStateHolder.gameState.value
        if (!oldState.isGameActive || oldState.isEliminated) return

        val newState = GameRules.tickGame(oldState)

        // If radius or center changed (due to shrink or round transition), update the physical geofence
        if (newState.currentZoneRadius != oldState.currentZoneRadius || newState.zoneCenter != oldState.zoneCenter) {
            newState.zoneCenter?.let { center ->
                geofenceManager.addGeofence(center, newState.currentZoneRadius)
            }
        }

        GameStateHolder.updateState { newState }

        if (newState.isEliminated) {
            stopGame()
        }
    }

    private fun stopGame() {
        timerJob?.cancel()
        timerJob = null
        geofenceManager.removeGeofences()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        
        GameStateHolder.updateState {
            it.copy(isGameActive = false)
        }
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotification(state: GameState): Notification {
        val title = if (state.isEliminated) "Game Over - Eliminated" else "Foam Dart Battle Active"
        val text = "Round ${state.currentRound} | HP: ${state.playerHealth}% | Zone: ${state.currentZoneRadius.toInt()}m"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Game Engine Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        serviceScope.cancel()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        super.onDestroy()
    }
}
