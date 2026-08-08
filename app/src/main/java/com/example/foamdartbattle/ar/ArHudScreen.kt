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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.input.pointer.pointerInput
import com.example.foamdartbattle.data.HudPreferencesManager
import com.example.foamdartbattle.data.HudElementState

@Composable
fun ModularHudElement(
    modifier: Modifier = Modifier,
    state: HudElementState,
    isEditMode: Boolean,
    onStateChange: (HudElementState) -> Unit,
    onLongPress: () -> Unit,
    onReset: () -> Unit,
    content: @Composable () -> Unit
) {
    val currentState by rememberUpdatedState(state)
    val currentOnStateChange by rememberUpdatedState(onStateChange)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnReset by rememberUpdatedState(onReset)

    Box(
        modifier = modifier
            .offset { IntOffset(state.xOffset.roundToInt(), state.yOffset.roundToInt()) }
            .graphicsLayer(
                scaleX = state.scale,
                scaleY = state.scale
            )
            .pointerInput(isEditMode) {
                if (isEditMode) {
                    detectTapGestures(
                        onDoubleTap = { currentOnReset() }
                    )
                } else {
                    detectTapGestures(
                        onLongPress = { currentOnLongPress() }
                    )
                }
            }
            .pointerInput(isEditMode) {
                if (isEditMode) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (currentState.scale * zoom).coerceIn(0.5f, 3.0f)
                        val newX = currentState.xOffset + pan.x
                        val newY = currentState.yOffset + pan.y
                        currentOnStateChange(
                            HudElementState(
                                xOffset = newX,
                                yOffset = newY,
                                scale = newScale
                            )
                        )
                    }
                }
            }
            .then(
                if (isEditMode) {
                    Modifier
                        .border(
                            width = 1.5.dp,
                            color = Color.Red.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .background(Color.Red.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                } else {
                    Modifier
                }
            )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(4.dp)
        ) {
            content()
            if (isEditMode) {
                Box(
                    modifier = Modifier
                        .background(Color.Red.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Double-tap\nto reset",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 10.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

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

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasLocationPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        if (!hasLocationPermission) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
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

    val prefsManager = remember(context) { HudPreferencesManager(context) }
    var isEditMode by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (isEditMode) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures {
                            isEditMode = false
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        val screenWidth = constraints.maxWidth.toFloat()
        val screenHeight = constraints.maxHeight.toFloat()
        
        // Default positions in pixels
        val defaultRoundX = screenWidth * 0.12f
        val defaultHealthX = screenWidth * 0.42f
        val defaultTimeX = screenWidth * 0.72f
        val defaultMinimapX = screenWidth - 360f
        val defaultMinimapY = 250f
        val defaultY = 250f

        var healthLayout by remember(screenWidth) { mutableStateOf(prefsManager.getElementState("health", defaultHealthX, defaultY)) }
        var roundLayout by remember(screenWidth) { mutableStateOf(prefsManager.getElementState("round", defaultRoundX, defaultY)) }
        var timeLayout by remember(screenWidth) { mutableStateOf(prefsManager.getElementState("time", defaultTimeX, defaultY)) }
        var minimapLayout by remember(screenWidth) { mutableStateOf(prefsManager.getElementState("minimap", defaultMinimapX, defaultMinimapY)) }

        // Save layout whenever edit mode is disabled
        LaunchedEffect(isEditMode) {
            if (!isEditMode) {
                prefsManager.saveElementState("health", healthLayout)
                prefsManager.saveElementState("round", roundLayout)
                prefsManager.saveElementState("time", timeLayout)
                prefsManager.saveElementState("minimap", minimapLayout)
            }
        }

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

        // --- AR FORCEFIELD WALLS LAYER ---
        if (gameState.playerLocation != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val screenWidth = size.width
                val screenHeight = size.height

                fun drawForcefieldWall(
                    wallPoints: List<ArForcefieldUtils.WallPoint>,
                    color: Color,
                    drawGrid: Boolean = false
                ) {
                    if (wallPoints.size < 2) return

                    // Draw a continuous semi-transparent filled forcefield mesh polygon
                    val path = Path().apply {
                        moveTo(wallPoints[0].x, wallPoints[0].yTop)
                        for (i in 1 until wallPoints.size) {
                            lineTo(wallPoints[i].x, wallPoints[i].yTop)
                        }
                        for (i in wallPoints.indices.reversed()) {
                            lineTo(wallPoints[i].x, wallPoints[i].yBottom)
                        }
                        close()
                    }
                    drawPath(path = path, color = color)

                    // Draw top and bottom glowing border lines for the wall
                    for (i in 0 until wallPoints.size - 1) {
                        val pt1 = wallPoints[i]
                        val pt2 = wallPoints[i + 1]

                        // Top glowing line
                        drawLine(
                            color = color.copy(alpha = 0.8f),
                            start = Offset(pt1.x, pt1.yTop),
                            end = Offset(pt2.x, pt2.yTop),
                            strokeWidth = 3.5f
                        )
                        // Bottom glowing line
                        drawLine(
                            color = color.copy(alpha = 0.8f),
                            start = Offset(pt1.x, pt1.yBottom),
                            end = Offset(pt2.x, pt2.yBottom),
                            strokeWidth = 3.5f
                        )

                        // Technical cage grid for towering Damage/Danger Zone
                        if (drawGrid) {
                            // Vertical cage bar
                            drawLine(
                                color = color.copy(alpha = 0.4f),
                                start = Offset(pt1.x, pt1.yTop),
                                end = Offset(pt1.x, pt1.yBottom),
                                strokeWidth = 1.5f
                            )
                            // Middle horizontal divider line
                            val midY1 = (pt1.yTop + pt1.yBottom) / 2f
                            val midY2 = (pt2.yTop + pt2.yBottom) / 2f
                            drawLine(
                                color = color.copy(alpha = 0.5f),
                                start = Offset(pt1.x, midY1),
                                end = Offset(pt2.x, midY2),
                                strokeWidth = 1.5f
                            )
                        }
                    }

                    // Last vertical cage bar
                    if (drawGrid && wallPoints.isNotEmpty()) {
                        val last = wallPoints.last()
                        drawLine(
                            color = color.copy(alpha = 0.4f),
                            start = Offset(last.x, last.yTop),
                            end = Offset(last.x, last.yBottom),
                            strokeWidth = 1.5f
                        )
                    }
                }

                // 1. Draw Preview Zone (Green) - 12ft tall (3.66m)
                if (gameState.settings.showNextRing && gameState.previewZoneCenter != null && gameState.previewZoneRadius > 0f) {
                    val perimeter = ArForcefieldUtils.generateZonePerimeter(gameState.previewZoneCenter!!, gameState.previewZoneRadius)
                    val projected = ArForcefieldUtils.projectWallPoints(
                        playerLoc = gameState.playerLocation!!,
                        heading = headingDegrees,
                        perimeter = perimeter,
                        fovDegrees = 70f,
                        screenWidth = screenWidth,
                        screenHeight = screenHeight,
                        physicalHeightMeters = 3.66f // 12 feet tall
                    )
                    drawForcefieldWall(projected, Color.Green.copy(alpha = 0.12f), drawGrid = false)
                }

                // 2. Draw Next Safe Zone (White) - 20ft tall (6.10m)
                if (gameState.nextZoneCenter != null && gameState.nextZoneRadius > 0f) {
                    val perimeter = ArForcefieldUtils.generateZonePerimeter(gameState.nextZoneCenter!!, gameState.nextZoneRadius)
                    val projected = ArForcefieldUtils.projectWallPoints(
                        playerLoc = gameState.playerLocation!!,
                        heading = headingDegrees,
                        perimeter = perimeter,
                        fovDegrees = 70f,
                        screenWidth = screenWidth,
                        screenHeight = screenHeight,
                        physicalHeightMeters = 6.10f // 20 feet tall
                    )
                    drawForcefieldWall(projected, Color.White.copy(alpha = 0.18f), drawGrid = false)
                }

                // 3. Draw Current Danger Zone (Red) - 40ft tall (12.19m) with Technical Grid
                if (gameState.zoneCenter != null && gameState.currentZoneRadius > 0f) {
                    val perimeter = ArForcefieldUtils.generateZonePerimeter(gameState.zoneCenter!!, gameState.currentZoneRadius)
                    val projected = ArForcefieldUtils.projectWallPoints(
                        playerLoc = gameState.playerLocation!!,
                        heading = headingDegrees,
                        perimeter = perimeter,
                        fovDegrees = 70f,
                        screenWidth = screenWidth,
                        screenHeight = screenHeight,
                        physicalHeightMeters = 12.19f // 40 feet tall (precise!)
                    )
                    drawForcefieldWall(projected, Color.Red.copy(alpha = 0.28f), drawGrid = true)
                }
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
                    .height(78.dp)
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
                fontSize = 16.sp,
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
                    strokeWidth = 2.4f
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
                            marker.isCardinal -> 12f
                            marker.isMajor -> 8.4f
                            else -> 4.8f
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
                            strokeWidth = if (marker.isCardinal) 4.2f else 1.8f
                        )

                        // Draw label text above the tick
                        if (marker.label.isNotEmpty()) {
                            val textStyle = TextStyle(
                                color = if (marker.isCardinal) Color.White else primaryColor.copy(alpha = 0.8f),
                                fontSize = if (marker.isCardinal) 12.sp else 9.6.sp,
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
                    lineTo(centerX - 7.2f, scaleY - 14.4f)
                    lineTo(centerX + 7.2f, scaleY - 14.4f)
                    close()
                }
                
                // Draw a subtle vertical alignment line in the center of the scale
                drawLine(
                    color = Color.Red.copy(alpha = 0.5f),
                    start = Offset(centerX, scaleY),
                    end = Offset(centerX, scaleY - 16.8f),
                    strokeWidth = 2.4f
                )

                drawPath(
                    path = pointerPath,
                    color = Color.Red
                )
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

        // --- MODULAR HUD ELEMENTS (HEALTH, ROUND, TIME) ---
        ModularHudElement(
            state = healthLayout,
            isEditMode = isEditMode,
            onStateChange = { healthLayout = it },
            onLongPress = { isEditMode = true },
            onReset = {
                healthLayout = HudElementState(defaultHealthX, defaultY, 1.0f)
                prefsManager.saveElementState("health", healthLayout)
            }
        ) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (gameState.isEliminated) "ELIMINATED" else "HP",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (gameState.isEliminated) Color.Red else primaryColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${gameState.playerHealth}%",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (gameState.playerHealth < 30) Color.Red else primaryColor
                    )
                }
            }
        }

        ModularHudElement(
            state = roundLayout,
            isEditMode = isEditMode,
            onStateChange = { roundLayout = it },
            onLongPress = { isEditMode = true },
            onReset = {
                roundLayout = HudElementState(defaultRoundX, defaultY, 1.0f)
                prefsManager.saveElementState("round", roundLayout)
            }
        ) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "ROUND",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = onSurfaceColor.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${gameState.currentRound}/${gameState.settings.maxRounds}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = onSurfaceColor
                    )
                }
            }
        }

        ModularHudElement(
            state = timeLayout,
            isEditMode = isEditMode,
            onStateChange = { timeLayout = it },
            onLongPress = { isEditMode = true },
            onReset = {
                timeLayout = HudElementState(defaultTimeX, defaultY, 1.0f)
                prefsManager.saveElementState("time", timeLayout)
            }
        ) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val phaseLabel = if (gameState.currentPhase == GamePhase.PREP) "Prep" else "Shrink"
                        Text(
                            text = phaseLabel.uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = onSurfaceColor.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${gameState.phaseTimeLeftSeconds}s",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (gameState.currentPhase == GamePhase.SHRINK) Color.Red else primaryColor
                        )
                    }
                }
            }

        ModularHudElement(
            state = minimapLayout,
            isEditMode = isEditMode,
            onStateChange = { minimapLayout = it },
            onLongPress = { isEditMode = true },
            onReset = {
                minimapLayout = HudElementState(defaultMinimapX, defaultMinimapY, 1.0f)
                prefsManager.saveElementState("minimap", minimapLayout)
            }
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(2.dp)
            ) {
                val minimapCenter = gameState.playerLocation ?: gameState.zoneCenter ?: LatLng(37.4220, -122.0840)
                val minimapCameraPositionState = rememberCameraPositionState {
                    position = CameraPosition.fromLatLngZoom(minimapCenter, 16.5f)
                }

                // Smoothly center the minimap camera on the player or zone center
                LaunchedEffect(minimapCenter, gameState.currentZoneRadius, gameState.isGameActive) {
                    val zoom = if (gameState.isGameActive && gameState.currentZoneRadius > 0f) {
                        val radius = gameState.currentZoneRadius
                        (22.5f - (Math.log(radius.toDouble()) / Math.log(2.0))).toFloat().coerceIn(12f, 19f)
                    } else {
                        16.5f
                    }
                    minimapCameraPositionState.position = CameraPosition.fromLatLngZoom(minimapCenter, zoom)
                }

                GoogleMap(
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                    cameraPositionState = minimapCameraPositionState,
                    properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = false,
                        zoomGesturesEnabled = false,
                        scrollGesturesEnabled = false,
                        tiltGesturesEnabled = false,
                        rotationGesturesEnabled = false,
                        myLocationButtonEnabled = false
                    )
                ) {
                    if (gameState.isGameActive) {
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

        // --- HUD EDIT MODE BANNER OVERLAY ---
        if (isEditMode) {
            Box(
                modifier = Modifier
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                    .border(1.dp, Color.Red.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "HUD EDIT MODE ACTIVE",
                        color = Color.Red,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Drag elements to reposition\n• Pinch with two fingers to resize\n• Tap background or click 'Done' to save",
                        color = Color.White,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(
                            onClick = {
                                healthLayout = HudElementState(defaultHealthX, defaultY, 1.0f)
                                roundLayout = HudElementState(defaultRoundX, defaultY, 1.0f)
                                timeLayout = HudElementState(defaultTimeX, defaultY, 1.0f)
                                minimapLayout = HudElementState(defaultMinimapX, defaultMinimapY, 1.0f)
                                prefsManager.clearAll()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Reset Layout")
                        }
                        Button(
                            onClick = {
                                isEditMode = false
                                prefsManager.saveElementState("health", healthLayout)
                                prefsManager.saveElementState("round", roundLayout)
                                prefsManager.saveElementState("time", timeLayout)
                                prefsManager.saveElementState("minimap", minimapLayout)
                            }
                        ) {
                            Text("Done")
                        }
                    }
                }
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
