package com.example.foamdartbattle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import android.widget.Toast
import com.example.foamdartbattle.ar.ArHudScreen
import com.example.foamdartbattle.geofence.GeofenceManager
import com.example.foamdartbattle.maps.MapScreen
import com.example.foamdartbattle.theme.FoamDartBattleTheme
import com.example.foamdartbattle.theme.GameTheme

class MainActivity : ComponentActivity() {
  private lateinit var geofenceManager: GeofenceManager

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    geofenceManager = GeofenceManager(this)

    enableEdgeToEdge()
    setContent {
      var isArView by rememberSaveable { mutableStateOf(false) }
      var currentThemeName by rememberSaveable { mutableStateOf(GameTheme.CYBERPUNK.name) }
      val currentTheme = remember(currentThemeName) { GameTheme.valueOf(currentThemeName) }

      FoamDartBattleTheme(theme = currentTheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (isArView) {
                ArHudScreen(
                    onToggleMapView = { isArView = false }
                )
            } else {
                MapScreen(
                    currentTheme = currentTheme,
                    onThemeChanged = { newTheme -> currentThemeName = newTheme.name },
                    onToggleArView = { isArView = true },
                    onGeofenceDefined = { points ->
                        val center = geofenceManager.calculateCenter(points)
                        val radius = geofenceManager.calculateRadius(center, points)
                        geofenceManager.addGeofence(center, radius)
                        Toast.makeText(this@MainActivity, "Geofence created!", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
      }
    }
  }
}
