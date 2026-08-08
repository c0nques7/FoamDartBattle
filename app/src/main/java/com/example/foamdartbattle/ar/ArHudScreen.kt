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

        // --- TECHNICAL HUD CANVAS OVERLAY (CROSSHAIR & SENSORS) ---
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            
            // 1. Central Tech Crosshair Ring
            drawCircle(
                color = Color.Cyan,
                radius = 60f,
                center = center,
                style = Stroke(width = 3f)
            )
            drawCircle(
                color = Color.Cyan.copy(alpha = 0.3f),
                radius = 15f,
                center = center
            )
            
            // 2. Crosshair Tick Lines (Tech / Sight feel)
            drawLine(
                color = Color.Cyan,
                start = Offset(center.x - 90f, center.y),
                end = Offset(center.x - 40f, center.y),
                strokeWidth = 4f
            )
            drawLine(
                color = Color.Cyan,
                start = Offset(center.x + 40f, center.y),
                end = Offset(center.x + 90f, center.y),
                strokeWidth = 4f
            )
            drawLine(
                color = Color.Cyan,
                start = Offset(center.x, center.y - 90f),
                end = Offset(center.x, center.y - 40f),
                strokeWidth = 4f
            )
            drawLine(
                color = Color.Cyan,
                start = Offset(center.x, center.y + 40f),
                end = Offset(center.x, center.y + 90f),
                strokeWidth = 4f
            )
        }

        // --- TOP LEFT GAME ENGINE STATISTICS ---
        if (gameState.isGameActive) {
            Card(
                modifier = Modifier
                    .padding(top = 16.dp, start = 16.dp, end = 210.dp) // Leave room on the right for the Minimap!
                    .align(Alignment.TopStart),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (gameState.isEliminated) "ELIMINATED" else "SYSTEMS ACTIVE",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (gameState.isEliminated) Color.Red else Color.Cyan
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Round", fontSize = 9.sp, color = Color.Gray)
                            Text("${gameState.currentRound}/${gameState.settings.maxRounds}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("HP", fontSize = 9.sp, color = Color.Gray)
                            Text("${gameState.playerHealth}%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (gameState.playerHealth < 30) Color.Red else Color.Green)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val phaseLabel = if (gameState.currentPhase == GamePhase.PREP) "Prep" else "Shrink"
                            Text(phaseLabel, fontSize = 9.sp, color = Color.Gray)
                            Text("${gameState.phaseTimeLeftSeconds}s", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (gameState.currentPhase == GamePhase.SHRINK) Color.Red else Color.White)
                        }
                    }
                }
            }
        }

        // --- TOP RIGHT CORNER TRANSPARENT MINIMAP OVERLAY ---
        if (gameState.isGameActive && gameState.zoneCenter != null) {
            Box(
                modifier = Modifier
                    .padding(top = 16.dp, end = 16.dp)
                    .size(170.dp)
                    .align(Alignment.TopEnd)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
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

        // --- BOTTOM NAVIGATION CONTROLS (Centering Map View Link) ---
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            Button(
                onClick = onToggleMapView,
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray.copy(alpha = 0.8f))
            ) {
                Text("Map View", color = Color.White)
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
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
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
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                            ) {
                                Text("Keep Playing")
                            }
                        } else {
                            Text("Waiting...", color = Color.Gray, fontSize = 12.sp)
                        }
                        
                        Button(
                            onClick = { gameViewModel.cancelEndGameVote(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray, contentColor = Color.Black)
                        ) {
                            Text("Cancel Request")
                        }
                    }
                }
            )
        }
    }
}
