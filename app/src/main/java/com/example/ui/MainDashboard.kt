package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Space
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.window.Dialog
import com.example.ui.custom.BusterCharacterView
import com.example.ui.viewmodel.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainDashboardView(viewModel: PetViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density

    // ViewModel observers
    val pet by viewModel.petStats.collectAsState()
    val activeRoom by viewModel.currentRoom.collectAsState()
    val visualState by viewModel.visualState.collectAsState()
    val micAmp by viewModel.micAmplitude.collectAsState()
    val eatingEmoji by viewModel.eatingFoodEmoji.collectAsState()
    val selectedFilter by viewModel.selectedVoiceFilter.collectAsState()
    val gameNotification by viewModel.gameNotification.collectAsState()

    // OpenRouter state observers
    val aiChatMessages by viewModel.aiChatMessages.collectAsState()
    val aiLoading by viewModel.aiLoading.collectAsState()
    val aiModeEnabled by viewModel.aiModeEnabled.collectAsState()
    val apiKey by viewModel.openRouterApiKey.collectAsState()
    val model by viewModel.openRouterModel.collectAsState()

    // Local UI states
    var typedText by remember { mutableStateOf("") }
    var showConfigDialog by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var shopDrawerOpen by remember { mutableStateOf(false) }
    var adsCooldown by remember { mutableStateOf(0) } // seconds cooldown for ad button

    // Active soap scrubber coordinate for Bathroom
    var soapOffset by remember { mutableStateOf(Offset(200f, 600f)) }
    var isScrubbing by remember { mutableStateOf(false) }

    // Handle Mic permissions
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasMicPermission = isGranted
        }
    )

    // Cooldown clock for discrete reward-ad button to prevent spamming
    LaunchedEffect(adsCooldown) {
        if (adsCooldown > 0) {
            delay(1000)
            adsCooldown--
        }
    }

    // Main background gradients based on wallpaper selections
    val bgBrush = when (pet.currentBackgroundId) {
        "cosmic_galaxy" -> Brush.verticalGradient(
            colors = listOf(Color(0xFF0D1B2A), Color(0xFF1B263B), Color(0xFF415A77))
        )
        "candy_kitchen" -> Brush.verticalGradient(
            colors = listOf(Color(0xFFFCE4EC), Color(0xFFF8BBD0), Color(0xFFF48FB1))
        )
        "sunny_meadow" -> Brush.verticalGradient(
            colors = listOf(Color(0xFF81D4FA), Color(0xFFA5D6A7), Color(0xFF81C784))
        )
        "bedroom" -> Brush.verticalGradient(
            // Darker night gradient
            colors = listOf(Color(0xFF03071E), Color(0xFF0A0F2C), Color(0xFF1B263B))
        )
        else -> Brush.verticalGradient( // default slate playroom
            colors = listOf(Color(0xFF2E3440), Color(0xFF3B4252), Color(0xFF434C5E))
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("app_main_scaffold"),
        containerColor = Color.Transparent,
        bottomBar = {
            // Elegant M3-style navigation pill bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(16.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RoomBarPill(
                        label = "Playroom",
                        icon = Icons.Default.Face,
                        isActive = activeRoom == ActiveRoom.PLAYROOM,
                        testTag = "nav_playroom",
                        onClick = { viewModel.changeRoom(ActiveRoom.PLAYROOM) }
                    )
                    RoomBarPill(
                        label = "Kitchen",
                        icon = Icons.Default.ShoppingCart,
                        isActive = activeRoom == ActiveRoom.KITCHEN,
                        testTag = "nav_kitchen",
                        onClick = { viewModel.changeRoom(ActiveRoom.KITCHEN) }
                    )
                    RoomBarPill(
                        label = "Bathroom",
                        icon = Icons.Default.Refresh,
                        isActive = activeRoom == ActiveRoom.BATHROOM,
                        testTag = "nav_bathroom",
                        onClick = { viewModel.changeRoom(ActiveRoom.BATHROOM) }
                    )
                    RoomBarPill(
                        label = "Bedroom",
                        icon = Icons.Default.Favorite,
                        isActive = activeRoom == ActiveRoom.BEDROOM,
                        testTag = "nav_bedroom",
                        onClick = { viewModel.changeRoom(ActiveRoom.BEDROOM) }
                    )
                    RoomBarPill(
                        label = "Arcade",
                        icon = Icons.Default.Star,
                        isActive = activeRoom == ActiveRoom.GAME_ROOM,
                        testTag = "nav_arcade",
                        onClick = { viewModel.changeRoom(ActiveRoom.GAME_ROOM) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgBrush)
                .padding(innerPadding)
        ) {

            // 1. DYNAMIC BACKGROUND BACKGROUND DETAILS (Stars, Clouds, Moon etc)
            BackgroundDetailLayer(roomId = pet.currentBackgroundId)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // 2. HEADER: LEVEL INDICATORS & TOP STATS CARDS
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(16.dp)),
                    color = Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Pet Identification Badge
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${pet.level}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = pet.petName,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        text = "LVL ${pet.level} Pocket Friend",
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            // Coins and Gems metrics chips
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ResourceChip(
                                    icon = "🪙",
                                    amount = pet.coins,
                                    bgColor = Color(0xFFFFD54F).copy(alpha = 0.25f),
                                    textColor = Color(0xFFFFEB3B),
                                    testTag = "coin_chip"
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                ResourceChip(
                                    icon = "💎",
                                    amount = pet.gems,
                                    bgColor = Color(0xFF4FC3F7).copy(alpha = 0.25f),
                                    textColor = Color(0xFFE0F7FA),
                                    testTag = "gem_chip"
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // XP progression bar
                        val xpNeeded = pet.level * 100 + 50
                        val progressXp = (pet.xp.toFloat() / xpNeeded).coerceIn(0f, 1f)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "XP: ",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            LinearProgressIndicator(
                                progress = { progressXp },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(6.dp)
                                    .clip(CircleShape),
                                color = Color(0xFF81C784),
                                trackColor = Color.White.copy(alpha = 0.2f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${pet.xp}/$xpNeeded",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // 3. METABOLIC STATUS CONTROLLER SLIDERS (Hunger, Energy, Hygiene, Fun)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SurvivalStateRing(
                        label = "Foods",
                        iconEmoji = "🍕",
                        value = pet.hunger,
                        testTag = "vital_hunger",
                        onTap = { viewModel.changeRoom(ActiveRoom.KITCHEN) }
                    )
                    SurvivalStateRing(
                        label = "Sleep",
                        iconEmoji = "💤",
                        value = pet.energy,
                        testTag = "vital_sleep",
                        onTap = { viewModel.changeRoom(ActiveRoom.BEDROOM) }
                    )
                    SurvivalStateRing(
                        label = "Wash",
                        iconEmoji = "🧼",
                        value = pet.hygiene,
                        testTag = "vital_hygiene",
                        onTap = { viewModel.changeRoom(ActiveRoom.BATHROOM) }
                    )
                    SurvivalStateRing(
                        label = "Happiness",
                        iconEmoji = "🎈",
                        value = pet.funLevel,
                        testTag = "vital_fun",
                        onTap = { viewModel.changeRoom(ActiveRoom.PLAYROOM) }
                    )
                }

                // 4. MAIN INTERACTIVE SPACE RENDERING
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (activeRoom == ActiveRoom.GAME_ROOM && viewModel.gameActive.value) {
                        // Renders falling bubbles arcade game internally!
                        GameArcadeOverlay(
                            bubbles = viewModel.gameBubblesRef.collectAsState().value,
                            score = viewModel.gameScore.collectAsState().value,
                            onPop = { viewModel.popBubble(it) },
                            onStop = { viewModel.stopBubbleGame() }
                        )
                    } else {
                        // Core pet interactive drawing
                        BusterCharacterView(
                            visualState = visualState,
                            outfitId = pet.currentOutfitId,
                            micAmplitude = micAmp,
                            modifier = Modifier
                                .fillMaxSize(0.92f)
                                .testTag("buster_character"),
                            onPoke = { tx, ty, w, h ->
                                viewModel.interactTouch(tx, ty, w, h)
                            }
                        )

                        // Eating food animation indicator overlay
                        eatingEmoji?.let { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(Color.White.copy(alpha = 0.25f), CircleShape)
                                    .align(Alignment.Center),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = emoji,
                                    fontSize = 42.sp,
                                    modifier = Modifier.graphicsLayer {
                                        rotationZ = (Math.random() * 20 - 10).toFloat()
                                    }
                                )
                            }
                        }

                        // Floating Speech Bubble above Buster's head (if AI chat reply is present)
                        val latestBusterBubble = aiChatMessages.lastOrNull { it.role == ChatRole.BUSTER || it.role == ChatRole.ERROR }
                        if (aiModeEnabled && latestBusterBubble != null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 40.dp)
                                    .fillMaxWidth(0.9f)
                                    .background(
                                        brush = Brush.horizontalGradient(listOf(Color(0xFFEEEEFF), Color(0xFFF4F6FF))),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .border(2.dp, Color(0xFF7E57C2), RoundedCornerShape(16.dp))
                                    .padding(10.dp)
                                    .testTag("buster_speech_bubble")
                            ) {
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = if (latestBusterBubble.role == ChatRole.ERROR) "⚠️ Brain Alert" else "🐱 ${pet.petName}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = if (latestBusterBubble.role == ChatRole.ERROR) Color.Red else Color(0xFF5E35B1)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clickable { viewModel.stopSpeaking() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("🤫", fontSize = 14.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = latestBusterBubble.text,
                                        color = Color(0xFF1E1E1E),
                                        fontSize = 13.sp,
                                        lineHeight = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        } else if (aiModeEnabled && aiLoading) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 40.dp)
                                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                                    .padding(vertical = 6.dp, horizontal = 12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${pet.petName} is cooking a response...",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Bathroom Sponge Soap-Scruber Overlay
                        if (activeRoom == ActiveRoom.BATHROOM) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                isScrubbing = true
                                                soapOffset = offset
                                            },
                                            onDragEnd = {
                                                isScrubbing = false
                                                viewModel.finishShower()
                                            },
                                            onDragCancel = {
                                                isScrubbing = false
                                                viewModel.finishShower()
                                            }
                                        ) { change, dragAmount ->
                                            change.consume()
                                            soapOffset += dragAmount
                                            
                                            // As soap sweeps center of buster body, reward cleanliness!
                                            val busterCx = size.width / 2f
                                            val busterCy = size.height * 0.58f
                                            val dist = Math.sqrt(
                                                Math.pow((soapOffset.x - busterCx).toDouble(), 2.0) +
                                                Math.pow((soapOffset.y - busterCy).toDouble(), 2.0)
                                            )
                                            if (dist < size.width * 0.35f) {
                                                viewModel.showerPet(0.6f) // add 0.6% clean per drag tick!
                                            }
                                        }
                                    }
                            ) {
                                // Draw Soap item cursor
                                Card(
                                    modifier = Modifier
                                        .offset(
                                            x = (soapOffset.x / density).dp - 32.dp,
                                            y = (soapOffset.y / density).dp - 32.dp
                                        )
                                        .size(64.dp)
                                        .testTag("soap_scrubber"),
                                    shape = CircleShape,
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isScrubbing) Color(0xFF80DEEA) else Color.White
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = "🧽", fontSize = 32.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. INTERACTIVE CONTEXTUAL DRAWER SHELVES & CONTROLS
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(2.dp, RoundedCornerShape(16.dp)),
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = activeRoom,
                            label = "ConsoleSelection"
                        ) { room ->
                            when (room) {
                                ActiveRoom.PLAYROOM -> {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        // Mode selector row (Echo Repeater vs AI Chat Buddy)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceEvenly,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            FilterChip(
                                                selected = !aiModeEnabled,
                                                onClick = { viewModel.saveOpenRouterConfig(apiKey, model, false) },
                                                label = { Text("🎙️ Offline Echo", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            )
                                            FilterChip(
                                                selected = aiModeEnabled,
                                                onClick = { viewModel.saveOpenRouterConfig(apiKey, model, true) },
                                                label = { Text("🧠 AI Buddy", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            )
                                        }

                                        if (!aiModeEnabled) {
                                            // Render original Voice Recorder & Repeater controls!
                                            Text(
                                                text = "Voice Recorder & Repeater",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(bottom = 6.dp)
                                            )

                                            // Mic filter switchers row
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceEvenly
                                            ) {
                                                VoiceFilterTab(
                                                    label = "Cute",
                                                    emoji = "🐿️",
                                                    isSelected = selectedFilter == "chipmunk" || selectedFilter == "default",
                                                    onClick = { viewModel.setVoiceFilter("chipmunk") }
                                                )
                                                VoiceFilterTab(
                                                    label = "Deep",
                                                    emoji = "👹",
                                                    isSelected = selectedFilter == "giant",
                                                    onClick = { viewModel.setVoiceFilter("giant") }
                                                )
                                                VoiceFilterTab(
                                                    label = "Robot",
                                                    emoji = "🤖",
                                                    isSelected = selectedFilter == "robot",
                                                    onClick = { viewModel.setVoiceFilter("robot") }
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(10.dp))

                                            // Large Mic Button
                                            Button(
                                                onClick = {
                                                    if (!hasMicPermission) {
                                                        launcher.launch(Manifest.permission.RECORD_AUDIO)
                                                    } else {
                                                        if (!isRecording) {
                                                            isRecording = true
                                                            viewModel.startListeningRecording()
                                                        } else {
                                                            isRecording = false
                                                            viewModel.stopListeningRecording()
                                                        }
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isRecording) Color(0xFFF44336) else MaterialTheme.colorScheme.primary
                                                ),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth(0.85f)
                                                    .height(48.dp)
                                                    .testTag("mic_record_button")
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Center
                                                ) {
                                                    Icon(
                                                        imageVector = if (isRecording) Icons.Default.Close else Icons.Default.Mic,
                                                        contentDescription = "Microphone Trigger"
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = if (isRecording) "Recording... Tap to End" else "Hold / Tap to Speak!",
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        } else {
                                            // Render modern OpenRouter AI chat input drawer!
                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = "Talk with AI ${pet.petName}",
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 12.sp
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Surface(
                                                            shape = CircleShape,
                                                            color = Color(0xFFFFD54F).copy(alpha = 0.25f),
                                                            modifier = Modifier.clickable { showConfigDialog = true }
                                                        ) {
                                                            Text(
                                                                text = when {
                                                                    model.contains("llama") -> " 🦙 "
                                                                    model.contains("gemini") -> " ⚡ "
                                                                    else -> " 🤖 "
                                                                },
                                                                fontSize = 9.sp,
                                                                color = Color.White,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                    
                                                    // Settings config button toggles setup overlay
                                                    IconButton(
                                                        onClick = { showConfigDialog = true },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Settings,
                                                            contentDescription = "AI settings credentials",
                                                            tint = Color(0xFFFFD54F),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                                
                                                Spacer(modifier = Modifier.height(4.dp))
                                                
                                                // Message Send row
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    OutlinedTextField(
                                                        value = typedText,
                                                        onValueChange = { typedText = it },
                                                        placeholder = { Text("Ask Buster to sing, dance, or chat...", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp) },
                                                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 12.sp),
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(48.dp)
                                                            .testTag("ai_text_input"),
                                                        shape = RoundedCornerShape(12.dp),
                                                        colors = OutlinedTextFieldDefaults.colors(
                                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                            unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
                                                            focusedContainerColor = Color.Black.copy(alpha = 0.2f),
                                                            unfocusedContainerColor = Color.Black.copy(alpha = 0.2f)
                                                        ),
                                                        singleLine = true
                                                    )
                                                    
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    
                                                    IconButton(
                                                        onClick = {
                                                            val trimmed = typedText.trim()
                                                            if (trimmed.isNotEmpty()) {
                                                                viewModel.sendMessageToBuster(trimmed)
                                                                typedText = ""
                                                            }
                                                        },
                                                        modifier = Modifier
                                                            .size(38.dp)
                                                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                                                            .testTag("ai_send_button")
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Send,
                                                            contentDescription = "Send text",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                ActiveRoom.KITCHEN -> {
                                    // Kitchen Food Tray Drawer
                                    Column(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Feed Buster (Buy Food Ingredients)",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                        )
                                        
                                        // Scrollable foods list
                                        LazyRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            items(viewModel.availableFoods) { food ->
                                                FoodTrayItemCard(food = food, onBuy = {
                                                    viewModel.feedPet(food)
                                                })
                                            }
                                        }
                                    }
                                }

                                ActiveRoom.BATHROOM -> {
                                    // Bathroom Shower help card
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "Washing Bubbles Simulator",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Swipe the sponge 🧽 directly over Buster to brush and shower him!",
                                            color = Color.White.copy(alpha = 0.8f),
                                            textAlign = TextAlign.Center,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                ActiveRoom.BEDROOM -> {
                                    // Bedroom Glow lamps panel
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "Cozy Sleeping Quarters",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        
                                        var lampsOff by remember { mutableStateOf(pet.currentBackgroundId == "bedroom") }

                                        Button(
                                            onClick = {
                                                lampsOff = !lampsOff
                                                if (lampsOff) {
                                                    viewModel.buyShopItem(ShopItem("bedroom", "Bedroom Wallpaper", 0, false))
                                                } else {
                                                    viewModel.buyShopItem(ShopItem("playroom", "Playroom Wallpaper", 0, false))
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (lampsOff) Color(0xFF3F51B5) else Color(0xFFFFC107)
                                            ),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(
                                                text = if (lampsOff) "💡 Turn Lights On" else "🌙 Turn Lights Off (Go Sleep)",
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }

                                ActiveRoom.GAME_ROOM -> {
                                    // Game room activation button
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        if (viewModel.gameActive.value) {
                                            Text(
                                                text = "Bubble Pop Game Active!",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        } else {
                                            Text(
                                                text = "Pocket Pet Games Arcade",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Button(
                                                onClick = { viewModel.startBubbleGame() },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color(0xFF66BB6A)
                                                ),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.testTag("arcade_launch_button")
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(text = "🕹️ Start Bubble Pop!")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 6. EXTRA REWARDING DISCRETE AD & WARDROBE CONTROLS ROW
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Reward-based voluntarily discrete ad-block Simulator
                    Button(
                        onClick = {
                            if (adsCooldown == 0) {
                                viewModel.addXp(20)
                                scope.launch {
                                    viewModel.buyShopItem(ShopItem("coins", "Ad Prize", -100, isOutfit = true))
                                }
                                adsCooldown = 25 // 25s cooldown to prevent abusive click spam
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (adsCooldown > 0) Color.Gray else Color(0xFFFF5252)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        enabled = adsCooldown == 0,
                        modifier = Modifier.weight(1f).padding(end = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.PlayCircle, contentDescription = "Simulate Ad")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (adsCooldown > 0) "Ad Ready in ${adsCooldown}s" else "Bonus +100 Coins (Ad)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Wardrobe customizing button
                    Button(
                        onClick = { shopDrawerOpen = !shopDrawerOpen },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(0.8f)
                            .testTag("wardrobe_toggle")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.ShoppingCart, contentDescription = "Wardrobe Toggle")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Wardrobe", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 7. LEVEL-UPS & GAME TICKERS TOAST NOTIFICATION FLOATER
            gameNotification?.let { msg ->
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 100.dp)
                        .shadow(8.dp, RoundedCornerShape(18.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = msg,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // 8. TRANSPARENT WARDROBE SHOP DRAWER SHEET OVERLAY
            if (shopDrawerOpen) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.45f)
                        .align(Alignment.BottomCenter)
                        .shadow(24.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Buster's Customization Shop",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            IconButton(onClick = { shopDrawerOpen = false }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Close shop")
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Grid of Shop Items
                        Text(
                            text = "Wearables & Skins",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(viewModel.shopItems) { item ->
                                ShopDrawerItem(
                                    item = item,
                                    currentOutfit = pet.currentOutfitId,
                                    currentWallpaper = pet.currentBackgroundId,
                                    level = pet.level,
                                    onBuyOrEquip = { viewModel.buyShopItem(item) }
                                )
                            }
                        }
                    }
                }
            }

            // 9. OPENROUTER AI CONFIGURATION DIALOG
            if (showConfigDialog) {
                androidx.compose.ui.window.Dialog(onDismissRequest = { showConfigDialog = false }) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .testTag("ai_config_dialog")
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(20.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "🧠 ${pet.petName}'s AI Setup",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "1. Sign up for free at openrouter.ai",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "2. Generate your free API key in settings",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "3. Paste key below. Start chatting!",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(14.dp))
                            
                            var tempKey by remember { mutableStateOf(apiKey) }
                            var tempModel by remember { mutableStateOf(model) }
                            var tempModeEnabled by remember { mutableStateOf(aiModeEnabled) }
                            
                            OutlinedTextField(
                                value = tempKey,
                                onValueChange = { tempKey = it },
                                label = { Text("OpenRouter API Key", fontSize = 11.sp) },
                                placeholder = { Text("sk-or-...", fontSize = 11.sp) },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                                modifier = Modifier.fillMaxWidth().testTag("config_key_input"),
                                visualTransformation = if (tempKey.startsWith("sk-or-") && tempKey.length > 10) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text(
                                text = "Choose Intelligence Profile",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.align(Alignment.Start)
                            )
                            
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            val modelOptions = listOf(
                                "google/gemini-2.5-flash:free",
                                "meta-llama/llama-3-8b-instruct:free",
                                "google/gemini-2.5-pro",
                                "openai/gpt-4o-mini"
                            )
                            
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                modelOptions.forEach { opt ->
                                    val prettyName = when (opt) {
                                        "google/gemini-2.5-flash:free" -> "⚡ Gemini 2.5 Flash (Free)"
                                        "meta-llama/llama-3-8b-instruct:free" -> "🦙 Llama 3 8B (Free)"
                                        "google/gemini-2.5-pro" -> "🧠 Gemini 2.5 Pro (Smart)"
                                        else -> "🤖 GPT-4o Mini (Compact)"
                                    }
                                    
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (tempModel == opt) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { tempModel = opt }
                                            .padding(vertical = 1.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = tempModel == opt,
                                                onClick = { tempModel = opt },
                                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(text = prettyName, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Enable AI Chat Buddy", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Switch(
                                    checked = tempModeEnabled,
                                    onCheckedChange = { tempModeEnabled = it }
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { showConfigDialog = false }) {
                                    Text("Cancel")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        viewModel.saveOpenRouterConfig(tempKey, tempModel, tempModeEnabled)
                                        showConfigDialog = false
                                    }
                                ) {
                                    Text("Save Key")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────
// CORE SUPPORTING COMPOSABLES
// ──────────────────────────────────────────────

@Composable
fun BackgroundDetailLayer(roomId: String) {
    if (roomId == "bedroom") {
        // Draw dawning night star constellations
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Drawing stars
                drawCircle(Color.White.copy(alpha = 0.7f), radius = 3f, center = Offset(size.width * 0.15f, size.height * 0.12f))
                drawCircle(Color.White.copy(alpha = 0.85f), radius = 4f, center = Offset(size.width * 0.35f, size.height * 0.08f))
                drawCircle(Color.White.copy(alpha = 0.5f), radius = 2.5f, center = Offset(size.width * 0.78f, size.height * 0.15f))
                drawCircle(Color.White.copy(alpha = 0.65f), radius = 3f, center = Offset(size.width * 0.65f, size.height * 0.28f))
                drawCircle(Color.White.copy(alpha = 0.75f), radius = 4f, center = Offset(size.width * 0.88f, size.height * 0.42f))

                // Yellow Sleepy crescent moon
                drawCircle(
                    color = Color(0xFFFFF59D),
                    radius = 35f,
                    center = Offset(size.width * 0.82f, size.height * 0.12f)
                )
                drawCircle(
                    color = Color(0xFF03071E), // matching night sky to clip moon
                    radius = 32f,
                    center = Offset(size.width * 0.77f, size.height * 0.11f)
                )
            }
        }
    } else if (roomId == "sunny_meadow") {
        // Paint nice dynamic sky clouds
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Giant cloud shapes
                drawCircle(Color.White.copy(alpha = 0.6f), radius = 80f, center = Offset(size.width * 0.22f, size.height * 0.18f))
                drawCircle(Color.White.copy(alpha = 0.6f), radius = 60f, center = Offset(size.width * 0.35f, size.height * 0.18f))
                
                drawCircle(Color.White.copy(alpha = 0.55f), radius = 70f, center = Offset(size.width * 0.8f, size.height * 0.12f))
            }
        }
    } else if (roomId == "cosmic_galaxy") {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Purple and blue nebula gas highlights
                drawCircle(Color(0xFFBA68C8).copy(alpha = 0.15f), radius = 200f, center = Offset(size.width * 0.2f, size.height * 0.3f))
                drawCircle(Color(0xFF26C6DA).copy(alpha = 0.12f), radius = 250f, center = Offset(size.width * 0.8f, size.height * 0.5f))
            }
        }
    }
}

@Composable
fun RoomBarPill(
    label: String,
    icon: ImageVector,
    isActive: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    else Color.Transparent
                )
                .padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) MaterialTheme.colorScheme.primary else Color.Gray,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (isActive) MaterialTheme.colorScheme.primary else Color.Gray,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun ResourceChip(
    icon: String,
    amount: Int,
    bgColor: Color,
    textColor: Color,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        modifier = Modifier.testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = icon, fontSize = 14.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$amount",
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun SurvivalStateRing(
    label: String,
    iconEmoji: String,
    value: Float,
    testTag: String,
    onTap: () -> Unit
) {
    val barColor = when {
        value < 30f -> Color(0xFFEF5350)  // Red hungry/dirty warning
        value < 65f -> Color(0xFFFFB74D)  // Gold warning
        else -> Color(0xFF81C784)         // Healthy green
    }

    Card(
        modifier = Modifier
            .width(78.dp)
            .clickable(onClick = onTap)
            .testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = iconEmoji, fontSize = 22.sp)
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { value / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(CircleShape),
                color = barColor,
                trackColor = Color.White.copy(alpha = 0.15f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${value.toInt()}%",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
fun VoiceFilterTab(
    label: String,
    emoji: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f),
        modifier = Modifier
            .clickable(onClick = onClick)
            .width(85.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = emoji, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun FoodTrayItemCard(
    food: FoodItem,
    onBuy: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(105.dp)
            .testTag("food_item_${food.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.15f)
        )
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = food.iconEmoji, fontSize = 28.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = food.name,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Text(
                text = "+${food.hungerRestore.toInt()} Food",
                fontSize = 10.sp,
                color = Color(0xFFA5D6A7)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onBuy,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB74D)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            ) {
                Text(
                    text = "🪙 ${food.cost}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF3E2723)
                )
            }
        }
    }
}

@Composable
fun ShopDrawerItem(
    item: ShopItem,
    currentOutfit: String,
    currentWallpaper: String,
    level: Int,
    onBuyOrEquip: () -> Unit
) {
    val isUnlocked = level >= item.unlockLevel
    val isEquipped = if (item.isOutfit) currentOutfit == item.id else currentWallpaper == item.id

    Card(
        modifier = Modifier
            .width(115.dp)
            .testTag("shop_item_${item.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isEquipped) MaterialTheme.colorScheme.primaryContainer 
                             else Color.Black.copy(alpha = 0.05f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Visual representations based on item id
            val representation = when (item.id) {
                "sunglasses" -> "🕶️"
                "viking" -> "🪖"
                "royal_crown" -> "👑"
                "scarf" -> "🧣"
                "playroom" -> "🏡"
                "cosmic_galaxy" -> "🌌"
                "candy_kitchen" -> "🍭"
                "sunny_meadow" -> "🌴"
                else -> "🐱"
            }

            Text(text = representation, fontSize = 28.sp)
            Text(
                text = item.name,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )

            if (!isUnlocked) {
                Text(text = "Lvl ${item.unlockLevel} Lock", fontSize = 10.sp, color = Color.Red)
            } else {
                Text(
                     text = if (isEquipped) "Active" else "Owned/Buy", 
                     fontSize = 10.sp, 
                     color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onBuyOrEquip,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isEquipped) Color.Gray else Color(0xFF4CAF50)
                ),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
                enabled = isUnlocked
            ) {
                Text(
                    text = if (isEquipped) "Equipped" else "🪙 ${item.price}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ──────────────────────────────────────────────
// FULL BUBBLE POP MINI GAME OVERLAY CANVAS
// ──────────────────────────────────────────────
@Composable
fun GameArcadeOverlay(
    bubbles: List<PetViewModel.Bubble>,
    score: Int,
    onPop: (Int) -> Unit,
    onStop: () -> Unit
) {
    val density = LocalDensity.current.density
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .border(2.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
    ) {
        // Star decorations
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(Color.White.copy(alpha = 0.5f), radius = 2f, center = Offset(200f, 200f))
            drawCircle(Color.White.copy(alpha = 0.6f), radius = 2.5f, center = Offset(600f, 400f))
            drawCircle(Color.White.copy(alpha = 0.4f), radius = 3f, center = Offset(400f, 800f))
        }

        // Tap interactions coordinates to POP bubbles!
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(bubbles) {
                    detectTapGestures { tapOffset ->
                        // Iterate and see if tap hits any active bubble coordinates
                        for (b in bubbles) {
                            val distSq = Math.pow((tapOffset.x - b.x).toDouble(), 2.0) +
                                         Math.pow((tapOffset.y - b.y).toDouble(), 2.0)
                            val limit = Math.pow(b.radius.toDouble() * 1.5, 2.0) // slightly larger trigger box
                            if (distSq <= limit) {
                                onPop(b.id)
                                break
                            }
                        }
                    }
                }
        ) {
            // Render bubbles as individual items
            for (b in bubbles) {
                val floatColor = Color(android.graphics.Color.parseColor(b.colorHex))
                Box(
                    modifier = Modifier
                        .offset(
                            x = (b.x / density).dp - (b.radius / density).dp,
                            y = (b.y / density).dp - (b.radius / density).dp
                        )
                        .size((b.radius * 2 / density).dp)
                        .background(floatColor.copy(alpha = 0.75f), CircleShape)
                        .border(1.5.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (b.isGold) "🪙" else "🎈",
                        fontSize = (b.radius * 0.9f / density).dp.value.sp
                    )
                }
            }
        }

        // Dashboard Stats
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Score: $score",
                    color = Color.Yellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = "Taps coins + bubbles!",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp
                )
            }

            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = "Quit Game", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }
    }
}
