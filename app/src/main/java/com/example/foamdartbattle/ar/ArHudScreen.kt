package com.example.foamdartbattle.ar

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.foamdartbattle.game.GamePhase
import com.example.foamdartbattle.game.GameStateHolder
import com.example.foamdartbattle.game.GameViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import kotlin.math.roundToInt

@Composable
fun ArHudScreen(
    gameViewModel: GameViewModel = viewModel(),
    onToggleMapView: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val gameState by gameViewModel.uiState.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    var headingDegrees by remember { mutableStateOf(0f) }

    val compassMarkers = remember { CompassUtils.buildCompassMarkers() }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        val listener = object : SensorEventListener {
            private val rotationMatrix = FloatArray(9)
            private val remappedMatrix = FloatArray(9)
            private val orientationAngles = FloatArray(3)

            private val accelerometerReading = FloatArray(3)
            private val magnetometerReading = FloatArray(3)
            private var hasAcc = false
            private var hasMag = false

            override fun onSensorChanged(event: SensorEvent) {
                var updateHeading = false

                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    updateHeading = true
                } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    System.arraycopy(event.values, 0, accelerometerReading, 0, 3)
                    hasAcc = true
                    if (hasMag) {
                        updateHeading = SensorManager.getRotationMatrix(
                            rotationMatrix, null, accelerometerReading, magnetometerReading
                        )
                    }
                } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                    System.arraycopy(event.values, 0, magnetometerReading, 0, 3)
                    hasMag = true
                    if (hasAcc) {
                        updateHeading = SensorManager.getRotationMatrix(
                            rotationMatrix, null, accelerometerReading, magnetometerReading
                        )
                    }
                }

                if (updateHeading) {
                    val rotation = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        try {
                            context.display.rotation
                        } catch (e: Exception) {
                            Surface.ROTATION_90
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.rotation
                    }

                    when (rotation) {
                        Surface.ROTATION_90 -> SensorManager.remapCoordinateSystem(
                            rotationMatrix, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, remappedMatrix
                        )
                        Surface.ROTATION_270 -> SensorManager.remapCoordinateSystem(
                            rotationMatrix, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, remappedMatrix
                        )
                        Surface.ROTATION_180 -> SensorManager.remapCoordinateSystem(
                            rotationMatrix, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y, remappedMatrix
                        )
                        else -> System.arraycopy(rotationMatrix, 0, remappedMatrix, 0, 9)
                    }

                    SensorManager.getOrientation(remappedMatrix, orientationAngles)
                    val azimuthRadians = orientationAngles[0]
                    val azimuthDegrees = Math.toDegrees(azimuthRadians.toDouble()).toFloat()

                    headingDegrees = (azimuthDegrees + 360f) % 360f
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (rotationVectorSensor != null) {
            sensorManager.registerListener(listener, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val mag = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            if (accel != null && mag != null) {
                sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
                sensorManager.registerListener(listener, mag, SensorManager.SENSOR_DELAY_UI)
            }
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            try {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            // Live Camera Preview using CameraX
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().apply {
                            setSurfaceProvider(previewView.surfaceProvider)
                        }
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Fallback background if permission denied
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Text("Camera permission required for AR HUD", color = Color.White)
            }
        }

        val primaryColor = MaterialTheme.colorScheme.primary
        val onSurfaceColor = MaterialTheme.colorScheme.onSurface

        // --- TECHNICAL HUD CANVAS OVERLAY (CROSSHAIR & SENSORS) ---
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            
            // 1. Central Tech Crosshair Ring
            drawCircle(
                color = primaryColor,
                radius = 60f,
                center = center,
                style = Stroke(width = 3f)
            )
            drawCircle(
                color = primaryColor.copy(alpha = 0.3f),
                radius = 15f,
                center = center
            )
            
            // 2. Crosshair Tick Lines (Tech / Sight feel)
            drawLine(
                color = primaryColor,
                start = Offset(center.x - 90f, center.y),
                end = Offset(center.x - 40f, center.y),
                strokeWidth = 4f
            )
            drawLine(
                color = primaryColor,
                start = Offset(center.x + 40f, center.y),
                end = Offset(center.x + 90f, center.y),
                strokeWidth = 4f
            )
            drawLine(
                color = primaryColor,
                start = Offset(center.x, center.y - 90f),
                end = Offset(center.x, center.y - 40f),
                strokeWidth = 4f
            )
            drawLine(
                color = primaryColor,
                start = Offset(center.x, center.y + 40f),
                end = Offset(center.x, center.y + 90f),
                strokeWidth = 4f
            )
        }

        // --- TOP HUD CONTAINER (COMPASS, STATS, & MINIMAP) ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- ROTATIONAL HUD COMPASS (BATTLE ROYALE STYLE) ---
            Box(
                modifier = Modifier
                    .padding(top = 16.dp, start = 24.dp, end = 24.dp)
                    .fillMaxWidth()
                    .height(65.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = primaryColor.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp)
                    )
            ) {
            val textMeasurer = rememberTextMeasurer()
            
            val currentHeadingText = remember(headingDegrees) {
                val degreeInt = headingDegrees.roundToInt()
                val direction = when (degreeInt) {
                    in 338..360, in 0..22 -> "N"
                    in 23..67 -> "NE"
                    in 68..112 -> "E"
                    in 113..157 -> "SE"
                    in 158..202 -> "S"
                    in 203..247 -> "SW"
                    in 248..292 -> "W"
                    in 293..337 -> "NW"
                    else -> "N"
                }
                "$degreeInt° $direction"
            }
            
            Text(
                text = currentHeadingText,
                color = primaryColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp)
            )

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                val width = size.width
                val height = size.height
                val centerX = width / 2f
                val scaleY = height - 12f // line of scale
                
                // Draw horizontal base line
                drawLine(
                    color = primaryColor.copy(alpha = 0.4f),
                    start = Offset(15f, scaleY),
                    end = Offset(width - 15f, scaleY),
                    strokeWidth = 2f
                )

                val visibleDegrees = 120f // Panoramic Field of view of the compass scale (120 degrees total, 60 on each side)
                val halfFov = visibleDegrees / 2f

                compassMarkers.forEach { marker ->
                    val diff = CompassUtils.getAngularDifference(marker.angle, headingDegrees)
                    // If the marker is within our visible field of view
                    if (diff in -halfFov..halfFov) {
                        // Calculate X offset
                        val x = centerX + (diff / halfFov) * (centerX - 25f)
                        
                        // Determine tick height and color
                        val tickHeight = when {
                            marker.isCardinal -> 10f
                            marker.isMajor -> 7f
                            else -> 4f
                        }
                        
                        val tickColor = if (marker.isCardinal) {
                            primaryColor
                        } else {
                            primaryColor.copy(alpha = 0.5f)
                        }

                        // Draw tick line
                        drawLine(
                            color = tickColor,
                            start = Offset(x, scaleY),
                            end = Offset(x, scaleY - tickHeight),
                            strokeWidth = if (marker.isCardinal) 3.5f else 1.5f
                        )

                        // Draw label text above the tick
                        if (marker.label.isNotEmpty()) {
                            val textStyle = TextStyle(
                                color = if (marker.isCardinal) Color.White else primaryColor.copy(alpha = 0.8f),
                                fontSize = if (marker.isCardinal) 10.sp else 8.sp,
                                fontWeight = if (marker.isCardinal) FontWeight.Bold else FontWeight.Normal
                            )
                            val textLayoutResult = textMeasurer.measure(
                                text = marker.label,
                                style = textStyle
                            )
                            val textWidth = textLayoutResult.size.width
                            val textHeight = textLayoutResult.size.height
                            
                            // Place text right above the tick line
                            drawText(
                                textLayoutResult = textLayoutResult,
                                topLeft = Offset(x - textWidth / 2f, scaleY - tickHeight - textHeight - 2f)
                            )
                        }
                    }
                }

                // --- Center Pointer / Indicator ---
                val pointerPath = Path().apply {
                    moveTo(centerX, scaleY - 2f)
                    lineTo(centerX - 6f, scaleY - 12f)
                    lineTo(centerX + 6f, scaleY - 12f)
                    close()
                }
                
                // Draw a subtle vertical alignment line in the center of the scale
                drawLine(
                    color = Color.Red.copy(alpha = 0.5f),
                    start = Offset(centerX, scaleY),
                    end = Offset(centerX, scaleY - 14f),
                    strokeWidth = 2f
                )

                drawPath(
                    path = pointerPath,
                    color = Color.Red
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- TOP RESPONSIVE HEADER ROW (STATS & MINIMAP) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            if (gameState.isGameActive) {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (gameState.isEliminated) "ELIMINATED" else "SYSTEMS ACTIVE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (gameState.isEliminated) Color.Red else primaryColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Round", fontSize = 8.sp, color = onSurfaceColor.copy(alpha = 0.6f))
                                Text("${gameState.currentRound}/${gameState.settings.maxRounds}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = onSurfaceColor)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("HP", fontSize = 8.sp, color = onSurfaceColor.copy(alpha = 0.6f))
                                Text("${gameState.playerHealth}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (gameState.playerHealth < 30) Color.Red else primaryColor)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val phaseLabel = if (gameState.currentPhase == GamePhase.PREP) "Prep" else "Shrink"
                                Text(phaseLabel, fontSize = 8.sp, color = onSurfaceColor.copy(alpha = 0.6f))
                                Text("${gameState.phaseTimeLeftSeconds}s", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (gameState.currentPhase == GamePhase.SHRINK) Color.Red else primaryColor)
                            }
                        }
                    }
                }
            }

            if (gameState.isGameActive && gameState.zoneCenter != null) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(2.dp)
                ) {
                    val minimapCameraPositionState = rememberCameraPositionState {
                        position = CameraPosition.fromLatLngZoom(gameState.zoneCenter!!, 17f)
                    }

                    // Smoothly center and zoom the minimap camera onto the shifting active zone
                    LaunchedEffect(gameState.zoneCenter, gameState.currentZoneRadius) {
                        val radius = gameState.currentZoneRadius
                        // Custom scale zoom for minimap viewport size
                        val zoom = (22.5f - (Math.log(radius.toDouble()) / Math.log(2.0))).toFloat().coerceIn(12f, 19f)
                        minimapCameraPositionState.position = CameraPosition.fromLatLngZoom(gameState.zoneCenter!!, zoom)
                    }

                    GoogleMap(
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                        cameraPositionState = minimapCameraPositionState,
                        properties = MapProperties(isMyLocationEnabled = hasCameraPermission),
                        uiSettings = MapUiSettings(
                            zoomControlsEnabled = false,
                            zoomGesturesEnabled = false,
                            scrollGesturesEnabled = false,
                            tiltGesturesEnabled = false,
                            rotationGesturesEnabled = false,
                            myLocationButtonEnabled = false
                        )
                    ) {
                        // 1. Damage Zone (Red) - active shrinking storm border
                        if (gameState.zoneCenter != null) {
                            Circle(
                                center = gameState.zoneCenter!!,
                                radius = gameState.currentZoneRadius.toDouble(),
                                fillColor = Color(0x05FF0000),
                                strokeColor = Color.Red,
                                strokeWidth = 2f
                            )
                        }

                        // 2. Safe Zone (White) - current round's destination boundary
                        if (gameState.nextZoneCenter != null) {
                            Circle(
                                center = gameState.nextZoneCenter!!,
                                radius = gameState.nextZoneRadius.toDouble(),
                                fillColor = Color(0x0AFFFFFF),
                                strokeColor = Color.White,
                                strokeWidth = 1.5f
                            )
                        }

                        // 3. Next Zone (Green) - preview of the subsequent safe zone if enabled
                        if (gameState.settings.showNextRing && gameState.previewZoneCenter != null) {
                            Circle(
                                center = gameState.previewZoneCenter!!,
                                radius = gameState.previewZoneRadius.toDouble(),
                                fillColor = Color(0x0500FF00),
                                strokeColor = Color.Green,
                                strokeWidth = 1f
                            )
                        }
                    }
                }
            }
        }
    } // End of TOP HUD CONTAINER Column

        // --- BOTTOM NAVIGATION CONTROLS (Centering Map View Link) ---
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            Button(
                onClick = onToggleMapView,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Map View")
            }
        }

        // --- MATCH END CONSENSUS VOTE OVERLAY ---
        if (gameState.endGameVoteActive) {
            AlertDialog(
                onDismissRequest = { /* Cannot dismiss */ },
                title = { Text("Match End Consensus Required", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("A player has requested to end the match.")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Current Votes:", fontWeight = FontWeight.SemiBold)
                        Text("✅ Agree: ${gameState.endGameVoteYesCount}")
                        Text("❌ Disagree: ${gameState.endGameVoteNoCount}")
                        Text("Majority needed: ${gameState.totalLobbyPlayers / 2 + 1} of ${gameState.totalLobbyPlayers} players")
                    }
                },
                confirmButton = {
                    if (!gameState.hasVotedInCurrentPoll) {
                        Button(
                            onClick = { gameViewModel.castVote(context, true) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Agree to End")
                        }
                    }
                },
                dismissButton = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!gameState.hasVotedInCurrentPoll) {
                            Button(
                                onClick = { gameViewModel.castVote(context, false) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Text("Keep Playing")
                            }
                        } else {
                            Text("Waiting...", color = Color.Gray, fontSize = 12.sp)
                        }
                        
                        Button(
                            onClick = { gameViewModel.cancelEndGameVote(context) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text("Cancel Request")
                        }
                    }
                }
            )
        }
    }
}
