package com.example.foamdartbattle.maps

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun MapScreen(
    onGeofenceDefined: (List<LatLng>) -> Unit
) {
    val context = LocalContext.current
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasLocationPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    val initialLocation = LatLng(37.4220, -122.0840) // Googleplex
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialLocation, 15f)
    }

    val polygonPoints = remember { mutableStateListOf<LatLng>() }

    Scaffold { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
                onMapClick = { latLng ->
                    polygonPoints.add(latLng)
                }
            ) {
                polygonPoints.forEach { point ->
                    Marker(
                        state = MarkerState(position = point),
                        title = "Geofence Point"
                    )
                }

                if (polygonPoints.size >= 3) {
                    Polygon(
                        points = polygonPoints.toList(),
                        fillColor = androidx.compose.ui.graphics.Color(0x33FF0000),
                        strokeColor = androidx.compose.ui.graphics.Color.Red
                    )
                }
            }
            
            if (polygonPoints.size >= 3) {
                Button(
                    onClick = { onGeofenceDefined(polygonPoints.toList()) },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
                ) {
                    Text("Set Geofence")
                }
            }
            
            Button(
                onClick = { polygonPoints.clear() },
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            ) {
                Text("Clear")
            }
        }
    }
}
