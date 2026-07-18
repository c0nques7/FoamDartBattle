package com.example.foamdartbattle.maps

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
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
    onToggleArView: () -> Unit = {},
    onGeofenceDefined: (List<LatLng>) -> Unit = {}
) {
    val context = LocalContext.current
    val gameState by gameViewModel.uiState.collectAsState()

    var showSettingsSheet by remember { mutableStateOf(false) }
    var currentSettings by remember { mutableStateOf(GameSettings()) }

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
                    if (!gameState.isGameActive) {
                        polygonPoints = polygonPoints + latLng
                        Toast.makeText(context, "Boundary point added!", Toast.LENGTH_SHORT).show()
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
                    polygonPoints.forEach { point ->
                        Marker(
                            state = rememberMarkerState(key = point.toString(), position = point),
                            title = "Geofence Point"
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
            
            // --- TOP GAME STATUS BAR & LEFT CONTROL PANEL ---
            if (gameState.isGameActive) {
                val phaseColor = when (gameState.currentPhase) {
                    GamePhase.PREP -> Color.DarkGray
                    GamePhase.SHRINK -> Color.Red
                }

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
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("MATCH STATUS", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (gameState.isEliminated) "ELIMINATED" else "ACTIVE",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (gameState.isEliminated) Color.Red else MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Box(modifier = Modifier.height(24.dp).width(1.dp).background(Color.Gray.copy(alpha = 0.3f)))

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("ROUND", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Text("${gameState.currentRound}/${gameState.settings.maxRounds}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }

                        Box(modifier = Modifier.height(24.dp).width(1.dp).background(Color.Gray.copy(alpha = 0.3f)))

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("HP STATUS", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Text(
                                "${gameState.playerHealth}%",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (gameState.playerHealth < 30) Color.Red else Color.Unspecified
                            )
                        }

                        Box(modifier = Modifier.height(24.dp).width(1.dp).background(Color.Gray.copy(alpha = 0.3f)))

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val phaseLabel = if (gameState.currentPhase == GamePhase.PREP) "ZONE STEADY" else "ZONE SHRINKING"
                            Text(phaseLabel, fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Text("${gameState.phaseTimeLeftSeconds}s", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = phaseColor)
                        }

                        Box(modifier = Modifier.height(24.dp).width(1.dp).background(Color.Gray.copy(alpha = 0.3f)))

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("TOTAL TIME", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Text("${gameState.totalTimeSeconds}s", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // 2. LEFT CONTROL PANEL CARD (Buttons on the left side)
                Card(
                    modifier = Modifier
                        .width(160.dp)
                        .padding(start = 16.dp, bottom = 16.dp)
                        .align(Alignment.BottomStart),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("CONTROLS", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        
                        Button(
                            onClick = { isMapLocked = !isMapLocked },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isMapLocked) Color.DarkGray else MaterialTheme.colorScheme.tertiary
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text(if (isMapLocked) "🔓 Unlock" else "🔒 Lock", fontSize = 12.sp)
                        }

                        Button(
                            onClick = onToggleArView,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text("AR HUD", fontSize = 12.sp)
                        }

                        // Press and Hold End Match Button (consensus voting)
                        LongPressButton(
                            text = "End (Hold)",
                            onComplete = { gameViewModel.initiateEndGameVote(context) },
                            color = Color.Red,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Press and Hold Leave Match Button (individual exit/abandon)
                        LongPressButton(
                            text = "Leave (Hold)",
                            onComplete = { gameViewModel.leaveMatch(context) },
                            color = Color.DarkGray,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                // Setup UI when game is inactive
                if (polygonPoints.size >= 3) {
                    Button(
                        onClick = {
                            val dummyGeofenceManager = com.example.foamdartbattle.geofence.GeofenceManager(context)
                            val center = dummyGeofenceManager.calculateCenter(polygonPoints)
                            val safeRadius = dummyGeofenceManager.calculateRadius(center, polygonPoints)
                            val damageRadius = dummyGeofenceManager.calculateCircumscribedRadius(center, polygonPoints)

                            gameViewModel.startGame(context, center, safeRadius, damageRadius, currentSettings)
                            polygonPoints = emptyList()
                            Toast.makeText(context, "Battle Royale Started!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 32.dp)
                    ) {
                        Text("Start Battle Royale")
                    }
                }
                
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Developer Mode settings button
                    Button(
                        onClick = { showSettingsSheet = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Dev Mode")
                    }

                    Button(
                        onClick = { polygonPoints = emptyList() }
                    ) {
                        Text("Clear")
                    }
                }
            }

            // Developer Settings Bottom Sheet
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
                            text = "Developer Mode Settings",
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
