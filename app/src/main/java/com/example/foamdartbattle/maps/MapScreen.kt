package com.example.foamdartbattle.maps

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import com.example.foamdartbattle.game.GamePhase
import com.example.foamdartbattle.game.GameSettings
import com.example.foamdartbattle.game.GameState
import com.example.foamdartbattle.game.GameViewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    gameViewModel: GameViewModel = viewModel(),
    currentTheme: com.example.foamdartbattle.theme.GameTheme = com.example.foamdartbattle.theme.GameTheme.CYBERPUNK,
    onThemeChanged: (com.example.foamdartbattle.theme.GameTheme) -> Unit = {},
    onToggleArView: () -> Unit = {},
    onGeofenceDefined: (List<LatLng>) -> Unit = {}
) {
    val context = LocalContext.current
    val gameState by gameViewModel.uiState.collectAsState()

    var showSettingsSheet by remember { mutableStateOf(false) }
    var currentSettings by remember { mutableStateOf(GameSettings()) }
    var savedProfiles by remember { mutableStateOf(listOf<SavedBattleProfile>()) }
    var profileNameInput by remember { mutableStateOf("") }
    var activeProfileId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        savedProfiles = SavedProfilesManager.loadProfiles(context)
    }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasLocationPermission = isGranted
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val initialLocation = LatLng(37.4220, -122.0840) // Googleplex
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialLocation, 15f)
    }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val coroutineScope = rememberCoroutineScope()

    // Auto-zoom to player location on startup before points are set
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            try {
                // Try lastLocation first for instant cache retrieval
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        val playerLatLng = LatLng(location.latitude, location.longitude)
                        cameraPositionState.position = CameraPosition.fromLatLngZoom(playerLatLng, 17f)
                    }
                }
                
                // Actively query current location with a 2-second timeout to prevent indefinite hangs
                val cts = CancellationTokenSource()
                fusedLocationClient.getCurrentLocation(
                    com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                    cts.token
                ).addOnSuccessListener { location ->
                    if (location != null) {
                        val playerLatLng = LatLng(location.latitude, location.longitude)
                        cameraPositionState.position = CameraPosition.fromLatLngZoom(playerLatLng, 17f)
                    }
                }
                
                coroutineScope.launch {
                    delay(2000)
                    cts.cancel()
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }

    var isMapLocked by remember { mutableStateOf(true) }
    var isMenuExpanded by rememberSaveable { mutableStateOf(true) }
    var menuEdge by rememberSaveable { mutableStateOf(MenuEdge.LEFT) }
    var activeDrawMode by rememberSaveable { mutableStateOf(DrawMode.TAP_POINTS) }
    var boxStartPoint by remember { mutableStateOf<Offset?>(null) }
    var boxEndPoint by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(gameState.isGameActive, gameState.zoneCenter, gameState.currentZoneRadius, isMapLocked) {
        if (gameState.isGameActive && isMapLocked && gameState.zoneCenter != null) {
            val radius = gameState.currentZoneRadius
            val zoom = (23f - (Math.log(radius.toDouble()) / Math.log(2.0))).toFloat().coerceIn(10f, 21f)
            cameraPositionState.animate(
                com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(
                    gameState.zoneCenter!!,
                    zoom
                )
            )
        }
    }

    var polygonPoints by remember { mutableStateOf(listOf<LatLng>()) }

    Scaffold { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
                onMapClick = { latLng ->
                    android.util.Log.d("MapScreen", "onMapClick triggered: $latLng")
                    if (!gameState.isGameActive && activeDrawMode == DrawMode.TAP_POINTS) {
                        if (polygonPoints.size >= 4) {
                            Toast.makeText(context, "Geofence is limited to 4 points! Drag them to modify, or tap Clear.", Toast.LENGTH_SHORT).show()
                        } else {
                            val updatedPoints = polygonPoints + latLng
                            if (updatedPoints.size == 4) {
                                // Find centroid of the 4 tapped points
                                val avgLat = updatedPoints.map { it.latitude }.average()
                                val avgLng = updatedPoints.map { it.longitude }.average()

                                // Sort the 4 points by their polar angle relative to the centroid
                                // This aligns the geofence perimeter perfectly around the exact taps without changing their locations!
                                polygonPoints = updatedPoints.sortedBy { point ->
                                    Math.atan2(point.latitude - avgLat, point.longitude - avgLng)
                                }
                                Toast.makeText(context, "Aligned geofence around perimeter!", Toast.LENGTH_SHORT).show()
                            } else {
                                polygonPoints = updatedPoints
                                Toast.makeText(context, "Boundary point added!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            ) {
                // If game is active, show the three-tiered zone circles
                if (gameState.isGameActive) {
                    // 1. Damage Zone (Red) - active shrinking storm border
                    if (gameState.zoneCenter != null) {
                        Circle(
                            center = gameState.zoneCenter!!,
                            radius = gameState.currentZoneRadius.toDouble(),
                            fillColor = Color(0x05FF0000), // Transparent inside to see map details
                            strokeColor = Color.Red,
                            strokeWidth = 4f
                        )
                    }

                    // 2. Safe Zone (White) - current round's destination boundary
                    if (gameState.nextZoneCenter != null) {
                        Circle(
                            center = gameState.nextZoneCenter!!,
                            radius = gameState.nextZoneRadius.toDouble(),
                            fillColor = Color(0x0AFFFFFF),
                            strokeColor = Color.White,
                            strokeWidth = 3f
                        )
                    }

                    // 3. Next Zone (Green) - preview of the subsequent safe zone if enabled
                    if (gameState.settings.showNextRing && gameState.previewZoneCenter != null) {
                        Circle(
                            center = gameState.previewZoneCenter!!,
                            radius = gameState.previewZoneRadius.toDouble(),
                            fillColor = Color(0x0500FF00),
                            strokeColor = Color.Green,
                            strokeWidth = 2f
                        )
                    }
                }

                // If game is not active, show the points placed by user
                if (!gameState.isGameActive) {
                    polygonPoints.forEachIndexed { index, point ->
                        val markerState = rememberMarkerState(key = index.toString(), position = point)
                        
                        var wasDragging by remember { mutableStateOf(false) }
                        LaunchedEffect(markerState.isDragging) {
                            if (wasDragging && !markerState.isDragging) {
                                val newPoints = polygonPoints.toMutableList()
                                val newPos = markerState.position
                                if (activeDrawMode == DrawMode.BOX_DRAW && polygonPoints.size == 4) {
                                    // Axis-aligned rectangular constraints: dragging any corner automatically
                                    // adjusts the adjacent corners to maintain a perfect box/rectangle
                                    when (index) {
                                        0 -> { // Bottom-Left (minLat, minLng)
                                            newPoints[0] = newPos
                                            newPoints[1] = LatLng(newPoints[1].latitude, newPos.longitude)
                                            newPoints[3] = LatLng(newPos.latitude, newPoints[3].longitude)
                                        }
                                        1 -> { // Top-Left (maxLat, minLng)
                                            newPoints[1] = newPos
                                            newPoints[0] = LatLng(newPoints[0].latitude, newPos.longitude)
                                            newPoints[2] = LatLng(newPos.latitude, newPoints[2].longitude)
                                        }
                                        2 -> { // Top-Right (maxLat, maxLng)
                                            newPoints[2] = newPos
                                            newPoints[3] = LatLng(newPoints[3].latitude, newPos.longitude)
                                            newPoints[1] = LatLng(newPos.latitude, newPoints[1].longitude)
                                        }
                                        3 -> { // Bottom-Right (minLat, maxLng)
                                            newPoints[3] = newPos
                                            newPoints[2] = LatLng(newPoints[2].latitude, newPos.longitude)
                                            newPoints[0] = LatLng(newPos.latitude, newPoints[0].longitude)
                                        }
                                    }
                                } else {
                                    // Individual points can be dragged independently
                                    newPoints[index] = newPos
                                    if (newPoints.size == 4) {
                                        // Automatically re-sort points relative to their centroid upon dragging
                                        // to keep the perimeter clean and prevent crossed lines
                                        val avgLat = newPoints.map { it.latitude }.average()
                                        val avgLng = newPoints.map { it.longitude }.average()
                                        newPoints.sortBy { point ->
                                            Math.atan2(point.latitude - avgLat, point.longitude - avgLng)
                                        }
                                    }
                                }
                                polygonPoints = newPoints
                            }
                            wasDragging = markerState.isDragging
                        }

                        Marker(
                            state = markerState,
                            title = "Geofence Point ${index + 1}",
                            draggable = true
                        )
                    }

                    if (polygonPoints.size >= 3) {
                        Polygon(
                            points = polygonPoints.toList(),
                            fillColor = Color(0x330000FF), // Semi-transparent blue for setup
                            strokeColor = Color.Blue
                        )
                    }
                }
            }

            // --- BOX DRAW GESTURE OVERLAY ---
            if (!gameState.isGameActive && activeDrawMode == DrawMode.BOX_DRAW && polygonPoints.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(cameraPositionState) {
                            detectDragGestures(
                                onDragStart = { startOffset ->
                                    boxStartPoint = startOffset
                                    boxEndPoint = startOffset
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    boxEndPoint = (boxEndPoint ?: change.position) + dragAmount
                                },
                                onDragEnd = {
                                    val start = boxStartPoint
                                    val end = boxEndPoint
                                    if (start != null && end != null) {
                                        val proj = cameraPositionState.projection
                                        if (proj != null) {
                                            val p1 = proj.fromScreenLocation(android.graphics.Point(start.x.toInt(), start.y.toInt()))
                                            val p2 = proj.fromScreenLocation(android.graphics.Point(end.x.toInt(), end.y.toInt()))
                                            if (p1 != null && p2 != null) {
                                                val left = minOf(p1.latitude, p2.latitude)
                                                val right = maxOf(p1.latitude, p2.latitude)
                                                val bottom = minOf(p1.longitude, p2.longitude)
                                                val top = maxOf(p1.longitude, p2.longitude)
                                                
                                                polygonPoints = listOf(
                                                    LatLng(left, bottom),
                                                    LatLng(right, bottom),
                                                    LatLng(right, top),
                                                    LatLng(left, top)
                                                )
                                            }
                                        }
                                    }
                                    boxStartPoint = null
                                    boxEndPoint = null
                                },
                                onDragCancel = {
                                    boxStartPoint = null
                                    boxEndPoint = null
                                }
                            )
                        }
                ) {
                    if (boxStartPoint != null && boxEndPoint != null) {
                        val start = boxStartPoint!!
                        val end = boxEndPoint!!
                        
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            val left = minOf(start.x, end.x)
                            val top = minOf(start.y, end.y)
                            val right = maxOf(start.x, end.x)
                            val bottom = maxOf(start.y, end.y)
                            
                            drawRect(
                                color = Color.Blue.copy(alpha = 0.2f),
                                topLeft = Offset(left, top),
                                size = androidx.compose.ui.geometry.Size(right - left, bottom - top)
                            )
                            drawRect(
                                color = Color.Blue,
                                topLeft = Offset(left, top),
                                size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
                            )
                        }
                    }
                }
            }
            
            // --- TOP GAME STATUS BAR & LEFT CONTROL PANEL ---
            if (gameState.isGameActive) {
                val phaseColor = when (gameState.currentPhase) {
                    GamePhase.PREP -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    GamePhase.SHRINK -> MaterialTheme.colorScheme.error
                }
                val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                val valueColor = MaterialTheme.colorScheme.onSurfaceVariant

                // 1. TOP STATUS BAR CARD (Game Data across the top)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                        .align(Alignment.TopCenter),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("STATUS", fontSize = 8.sp, color = labelColor, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(
                                text = if (gameState.isEliminated) "ELIMINATED" else "ACTIVE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (gameState.isEliminated) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        }
                        
                        Box(modifier = Modifier.height(20.dp).width(1.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)))

                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("ROUND", fontSize = 8.sp, color = labelColor, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text("${gameState.currentRound}/${gameState.settings.maxRounds}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1)
                        }

                        Box(modifier = Modifier.height(20.dp).width(1.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)))

                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("HP STATUS", fontSize = 8.sp, color = labelColor, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(
                                "${gameState.playerHealth}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (gameState.playerHealth < 30) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        }

                        Box(modifier = Modifier.height(20.dp).width(1.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)))

                        Column(modifier = Modifier.weight(1.2f), horizontalAlignment = Alignment.CenterHorizontally) {
                            val phaseLabel = if (gameState.currentPhase == GamePhase.PREP) "ZONE STEADY" else "SHRINKING"
                            Text(phaseLabel, fontSize = 8.sp, color = labelColor, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text("${gameState.phaseTimeLeftSeconds}s", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = phaseColor, maxLines = 1)
                        }

                        Box(modifier = Modifier.height(20.dp).width(1.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)))

                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("TOTAL TIME", fontSize = 8.sp, color = labelColor, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text("${gameState.totalTimeSeconds}s", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1)
                        }
                    }
                }
            }

            val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

            // --- EDGE-POSITIONABLE SIDEBAR MENU (COLLAPSED STATE) ---
            if (!isMenuExpanded) {
                val alignment = when (menuEdge) {
                    MenuEdge.LEFT -> Alignment.CenterStart
                    MenuEdge.RIGHT -> Alignment.CenterEnd
                }
                val padding = Modifier.padding(16.dp)

                FloatingActionButton(
                    onClick = { isMenuExpanded = true },
                    modifier = padding.align(alignment),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    val iconText = when (menuEdge) {
                        MenuEdge.LEFT -> "▶ Controls"
                        MenuEdge.RIGHT -> "Controls ◀"
                    }
                    Text(iconText, modifier = Modifier.padding(horizontal = 12.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // --- EDGE-POSITIONABLE SIDEBAR MENU (EXPANDED STATE) ---
            if (isMenuExpanded) {
                // Adjust alignment and sizing to dock flat against screen boundary edges
                val modifier = when (menuEdge) {
                    MenuEdge.LEFT -> Modifier
                        .width(150.dp)
                        .fillMaxHeight()
                        .padding(top = if (gameState.isGameActive) 80.dp else 0.dp)
                        .align(Alignment.CenterStart)
                    MenuEdge.RIGHT -> Modifier
                        .width(150.dp)
                        .fillMaxHeight()
                        .padding(top = if (gameState.isGameActive) 80.dp else 0.dp)
                        .align(Alignment.CenterEnd)
                }

                val shape = when (menuEdge) {
                    MenuEdge.LEFT -> RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                    MenuEdge.RIGHT -> RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                }

                Card(
                    modifier = modifier,
                    shape = shape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    // Scrollable vertical layout
                    Column(
                        modifier = Modifier
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("MENU", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = labelColor)
                            IconButton(
                                onClick = { isMenuExpanded = false },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Text("✖", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // Edge selector
                        EdgeSelector(
                            activeEdge = menuEdge,
                            onEdgeSelected = { menuEdge = it }
                        )

                        if (!gameState.isGameActive) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                            DrawModeSelector(
                                activeMode = activeDrawMode,
                                onModeSelected = { activeDrawMode = it }
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                            
                            Text("SAVED MAPS", fontSize = 8.sp, color = labelColor, fontWeight = FontWeight.Bold)
                            
                            if (savedProfiles.isEmpty()) {
                                Text("No saved maps", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    savedProfiles.forEach { profile ->
                                        val isActive = activeProfileId == profile.id
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    // Load profile points and settings, and mark as active edit profile!
                                                    polygonPoints = profile.points.map { LatLng(it.lat, it.lng) }
                                                    currentSettings = GameSettings(
                                                        maxRounds = profile.maxRounds,
                                                        prepTimeSeconds = profile.prepTimeSeconds,
                                                        shrinkTimeSeconds = profile.shrinkTimeSeconds,
                                                        showNextRing = profile.showNextRing
                                                    )
                                                    activeProfileId = profile.id
                                                    profileNameInput = profile.name
                                                    Toast.makeText(context, "Loaded: ${profile.name}", Toast.LENGTH_SHORT).show()
                                                },
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                            ),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = profile.name,
                                                    fontSize = 10.sp,
                                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.weight(1f),
                                                    maxLines = 1
                                                )
                                                IconButton(
                                                    onClick = {
                                                        val updated = savedProfiles.filter { it.id != profile.id }
                                                        SavedProfilesManager.saveProfiles(context, updated)
                                                        savedProfiles = updated
                                                        if (activeProfileId == profile.id) {
                                                            activeProfileId = null
                                                            profileNameInput = ""
                                                        }
                                                        Toast.makeText(context, "Deleted map", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(16.dp)
                                                ) {
                                                    Text("🗑️", fontSize = 9.sp, color = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            // Profile Saving / Editing Panel
                            OutlinedTextField(
                                value = profileNameInput,
                                onValueChange = { profileNameInput = it },
                                label = { Text(if (activeProfileId != null) "Edit Name" else "Map Name", fontSize = 8.sp) },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontSize = 10.sp)
                            )

                            if (activeProfileId != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            val name = profileNameInput.trim()
                                            if (name.isNotEmpty()) {
                                                val updated = savedProfiles.map {
                                                    if (it.id == activeProfileId) {
                                                        it.copy(
                                                            name = name,
                                                            points = polygonPoints.map { PointDouble(it.latitude, it.longitude) },
                                                            maxRounds = currentSettings.maxRounds,
                                                            prepTimeSeconds = currentSettings.prepTimeSeconds,
                                                            shrinkTimeSeconds = currentSettings.shrinkTimeSeconds,
                                                            showNextRing = currentSettings.showNextRing
                                                        )
                                                    } else it
                                                }
                                                SavedProfilesManager.saveProfiles(context, updated)
                                                savedProfiles = updated
                                                profileNameInput = ""
                                                activeProfileId = null
                                                Toast.makeText(context, "Map updated!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.weight(1.2f).height(28.dp),
                                        contentPadding = PaddingValues(horizontal = 2.dp)
                                    ) {
                                        Text("Update", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            activeProfileId = null
                                            profileNameInput = ""
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                                        modifier = Modifier.weight(0.8f).height(28.dp),
                                        contentPadding = PaddingValues(horizontal = 2.dp)
                                    ) {
                                        Text("Cancel", fontSize = 8.sp)
                                    }
                                }
                            } else {
                                Button(
                                    onClick = {
                                        val name = profileNameInput.trim()
                                        if (name.isNotEmpty()) {
                                            val newProfile = SavedBattleProfile(
                                                id = java.util.UUID.randomUUID().toString(),
                                                name = name,
                                                points = polygonPoints.map { PointDouble(it.latitude, it.longitude) },
                                                maxRounds = currentSettings.maxRounds,
                                                prepTimeSeconds = currentSettings.prepTimeSeconds,
                                                shrinkTimeSeconds = currentSettings.shrinkTimeSeconds,
                                                showNextRing = currentSettings.showNextRing
                                            )
                                            val updated = savedProfiles + newProfile
                                            SavedProfilesManager.saveProfiles(context, updated)
                                            savedProfiles = updated
                                            profileNameInput = ""
                                            Toast.makeText(context, "Saved as new!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Enter map name", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(28.dp),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    Text("Save Current", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))

                        // Controls
                        MenuControls(
                            isGameActive = gameState.isGameActive,
                            isMapLocked = isMapLocked,
                            onToggleMapLock = { isMapLocked = !isMapLocked },
                            onToggleArView = onToggleArView,
                            onInitiateEndGameVote = { gameViewModel.initiateEndGameVote(context) },
                            onLeaveMatch = { gameViewModel.leaveMatch(context) },
                            onShowSettings = { showSettingsSheet = true },
                            onClearPoints = { polygonPoints = emptyList() },
                            hasPoints = polygonPoints.size >= 3,
                            onStartGame = {
                                val dummyGeofenceManager = com.example.foamdartbattle.geofence.GeofenceManager(context)
                                val center = dummyGeofenceManager.calculateCenter(polygonPoints)
                                val safeRadius = dummyGeofenceManager.calculateRadius(center, polygonPoints)
                                val damageRadius = dummyGeofenceManager.calculateCircumscribedRadius(center, polygonPoints)
                                gameViewModel.startGame(context, center, safeRadius, damageRadius, currentSettings)
                                polygonPoints = emptyList()
                                Toast.makeText(context, "Battle Royale Started!", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            // Settings Bottom Sheet
            if (showSettingsSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showSettingsSheet = false }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                    ) {
                        Text(
                            text = "Settings",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // 1. Max Rounds
                        Text("Max Rounds: ${currentSettings.maxRounds}", fontWeight = FontWeight.SemiBold)
                        Slider(
                            value = currentSettings.maxRounds.toFloat(),
                            onValueChange = { currentSettings = currentSettings.copy(maxRounds = it.toInt()) },
                            valueRange = 1f..10f,
                            steps = 8
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // 2. Prep Time
                        Text("Prep Phase Time: ${currentSettings.prepTimeSeconds}s", fontWeight = FontWeight.SemiBold)
                        Slider(
                            value = currentSettings.prepTimeSeconds.toFloat(),
                            onValueChange = { currentSettings = currentSettings.copy(prepTimeSeconds = it.toInt()) },
                            valueRange = 5f..120f,
                            steps = 23
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // 3. Shrink Time
                        Text("Shrink Phase Time: ${currentSettings.shrinkTimeSeconds}s", fontWeight = FontWeight.SemiBold)
                        Slider(
                            value = currentSettings.shrinkTimeSeconds.toFloat(),
                            onValueChange = { currentSettings = currentSettings.copy(shrinkTimeSeconds = it.toInt()) },
                            valueRange = 5f..60f,
                            steps = 11
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // 4. Show Next Ring Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Visualize Next Ring Location", fontWeight = FontWeight.SemiBold)
                            Switch(
                                checked = currentSettings.showNextRing,
                                onCheckedChange = { currentSettings = currentSettings.copy(showNextRing = it) }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 5. Theme Selection Customizer Framework
                        Text("Active Visual Theme Preset", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            com.example.foamdartbattle.theme.GameTheme.values().forEach { themeOption ->
                                val isSelected = currentTheme == themeOption
                                Button(
                                    onClick = { onThemeChanged(themeOption) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = themeOption.name.substring(0, 1) + themeOption.name.substring(1).lowercase(),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { showSettingsSheet = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Apply Settings")
                        }
                    }
                }
            }

            // --- END GAME CONSENSUS VOTE OVERLAY ---
            EndGameVoteOverlay(
                gameState = gameState,
                onVote = { agree -> gameViewModel.castVote(context, agree) },
                onCancelVote = { gameViewModel.cancelEndGameVote(context) }
            )
        }
    }
}

@Composable
fun StatRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
fun LongPressButton(
    text: String,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color.Red,
    holdTimeMs: Long = 1500L
) {
    var isPressing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    
    LaunchedEffect(isPressing) {
        if (isPressing) {
            val startTime = System.currentTimeMillis()
            while (isPressing && progress < 1f) {
                val elapsed = System.currentTimeMillis() - startTime
                progress = (elapsed.toFloat() / holdTimeMs).coerceAtMost(1f)
                delay(30)
            }
            if (progress >= 1f) {
                onComplete()
                isPressing = false
                progress = 0f
            }
        } else {
            progress = 0f
        }
    }
    
    Box(
        modifier = modifier
            .height(40.dp) // Matched to regular buttons height in vertical pane
            .clip(RoundedCornerShape(20.dp)) // Matched to Material 3 rounded buttons
            .background(color.copy(alpha = 0.15f)) // Translucent red when idle
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressing = true
                        try {
                            awaitRelease()
                        } finally {
                            isPressing = false
                        }
                    }
                )
            }
    ) {
        // Progress background slide-fill
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .background(color.copy(alpha = 0.5f))
        )
        
        // Border boundary outlining button shape
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(2.dp, color, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isPressing) "HOLDING... ${(progress * 100).toInt()}%" else text,
                color = if (isPressing) Color.White else color,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp // Fitted perfectly for longer text
            )
        }
    }
}

@Composable
fun EndGameVoteOverlay(
    gameState: GameState,
    onVote: (Boolean) -> Unit,
    onCancelVote: () -> Unit
) {
    if (gameState.endGameVoteActive) {
        AlertDialog(
            onDismissRequest = { /* Cannot dismiss, must vote! */ },
            title = { Text("Match End Consensus Required", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("A player has requested to prematurely end this match.")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Current Votes:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("✅ Agree (End): ${gameState.endGameVoteYesCount}", fontSize = 14.sp)
                    Text("❌ Disagree (Keep Playing): ${gameState.endGameVoteNoCount}", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Majority needed: ${gameState.totalLobbyPlayers / 2 + 1} of ${gameState.totalLobbyPlayers} players", 
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                if (!gameState.hasVotedInCurrentPoll) {
                    Button(
                        onClick = { onVote(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("Agree")
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
                            onClick = { onVote(false) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                        ) {
                            Text("Disagree")
                        }
                    } else {
                        Text(
                            text = "Waiting...", 
                            color = Color.Gray, 
                            fontSize = 12.sp
                        )
                    }
                    
                    Button(
                        onClick = onCancelVote,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray, contentColor = Color.Black)
                    ) {
                        Text("Cancel Request")
                    }
                }
            }
        )
    }
}

enum class MenuEdge {
    LEFT, RIGHT
}

@Composable
fun EdgeSelector(
    activeEdge: MenuEdge,
    onEdgeSelected: (MenuEdge) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("POSITION", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth()
        ) {
            MenuEdge.values().forEach { edge ->
                val isSelected = activeEdge == edge
                val text = when (edge) {
                    MenuEdge.LEFT -> "⬅️"
                    MenuEdge.RIGHT -> "➡️"
                }
                IconButton(
                    onClick = { onEdgeSelected(edge) },
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else Color.Transparent,
                            RoundedCornerShape(4.dp)
                        )
                ) {
                    Text(text, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun MenuControls(
    isGameActive: Boolean,
    isMapLocked: Boolean,
    onToggleMapLock: () -> Unit,
    onToggleArView: () -> Unit,
    onInitiateEndGameVote: () -> Unit,
    onLeaveMatch: () -> Unit,
    onShowSettings: () -> Unit,
    onClearPoints: () -> Unit,
    hasPoints: Boolean,
    onStartGame: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isGameActive) {
            Button(
                onClick = onToggleMapLock,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isMapLocked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                    contentColor = if (isMapLocked) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onTertiary
                ),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                Text(if (isMapLocked) "🔓 Unlock" else "🔒 Lock", fontSize = 11.sp)
            }

            Button(
                onClick = onToggleArView,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                Text("AR HUD", fontSize = 11.sp)
            }

            LongPressButton(
                text = "End (Hold)",
                onComplete = onInitiateEndGameVote,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth()
            )

            LongPressButton(
                text = "Leave (Hold)",
                onComplete = onLeaveMatch,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Button(
                onClick = onShowSettings,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                Text("Settings", fontSize = 11.sp)
            }

            Button(
                onClick = onClearPoints,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                Text("Clear", fontSize = 11.sp)
            }
if (hasPoints) {
    Button(
        onClick = onStartGame,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 6.dp)
    ) {
        Text("Start Game", fontSize = 11.sp)
    }
}
}
}
}

enum class DrawMode {
    TAP_POINTS, BOX_DRAW, EXPLORE
}

@Composable
fun DrawModeSelector(
    activeMode: DrawMode,
    onModeSelected: (DrawMode) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("DRAW MODE", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            DrawMode.values().forEach { mode ->
                val isSelected = activeMode == mode
                val text = when (mode) {
                    DrawMode.TAP_POINTS -> "📍 Points"
                    DrawMode.BOX_DRAW -> "🟩 Box"
                    DrawMode.EXPLORE -> "🧭 View"
                }
                Button(
                    onClick = { onModeSelected(mode) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 1.dp)
                ) {
                    Text(text, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

@kotlinx.serialization.Serializable
data class SavedBattleProfile(
    val id: String,
    val name: String,
    val points: List<PointDouble>,
    val maxRounds: Int,
    val prepTimeSeconds: Int,
    val shrinkTimeSeconds: Int,
    val showNextRing: Boolean
)

@kotlinx.serialization.Serializable
data class PointDouble(val lat: Double, val lng: Double)

object SavedProfilesManager {
    private const val PREFS_NAME = "foam_dart_battle_profiles"
    private const val KEY_PROFILES = "saved_profiles"

    fun saveProfiles(context: android.content.Context, profiles: List<SavedBattleProfile>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val json = kotlinx.serialization.json.Json.encodeToString(profiles)
        prefs.edit().putString(KEY_PROFILES, json).apply()
    }

    fun loadProfiles(context: android.content.Context): List<SavedBattleProfile> {
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        return try {
            kotlinx.serialization.json.Json.decodeFromString<List<SavedBattleProfile>>(json)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
