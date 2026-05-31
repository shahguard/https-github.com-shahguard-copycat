package com.example.ui.viewmodel

import android.app.Application
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.VoiceEchoEngine
import com.example.db.PetDatabase
import com.example.db.PetEntity
import com.example.db.PetRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.FlowPreview
import java.util.Locale

enum class ChatRole {
    USER, BUSTER, ERROR
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: ChatRole,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class PetVisualState {
    IDLE, EATING, BATHING, SLEEPING, DIZZY, HEARING, SPEAKING, HAPPY_DANCE
}

enum class ActiveRoom {
    PLAYROOM, KITCHEN, BATHROOM, BEDROOM, GAME_ROOM
}

// Data models for Customization Shop
data class ShopItem(
    val id: String,
    val name: String,
    val price: Int,
    val isOutfit: Boolean,
    val unlockLevel: Int = 1
)

data class FoodItem(
    val id: String,
    val name: String,
    val cost: Int,
    val hungerRestore: Float,
    val funAdded: Float = 0f,
    val iconEmoji: String
)

class PetViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "PetViewModel"
    private val repository: PetRepository
    private val voiceEngine = VoiceEchoEngine()
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val stringListType = Types.newParameterizedType(List::class.java, String::class.java)

    // OpenRouter AI Chat integration states
    private val openRouterClient = com.example.network.OpenRouterClient()
    private val sharedPref = application.getSharedPreferences("AppSettings", android.content.Context.MODE_PRIVATE)

    private val _openRouterApiKey = MutableStateFlow(sharedPref.getString("API_KEY", "") ?: "")
    val openRouterApiKey: StateFlow<String> = _openRouterApiKey.asStateFlow()

    private val _openRouterModel = MutableStateFlow(sharedPref.getString("SELECTED_MODEL", "google/gemini-2.5-flash:free") ?: "google/gemini-2.5-flash:free")
    val openRouterModel: StateFlow<String> = _openRouterModel.asStateFlow()

    private val _aiModeEnabled = MutableStateFlow(sharedPref.getBoolean("AI_MODE_ENABLED", false))
    val aiModeEnabled: StateFlow<Boolean> = _aiModeEnabled.asStateFlow()

    private val _aiChatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val aiChatMessages: StateFlow<List<ChatMessage>> = _aiChatMessages.asStateFlow()

    private val _aiLoading = MutableStateFlow(false)
    val aiLoading: StateFlow<Boolean> = _aiLoading.asStateFlow()

    private var tts: TextToSpeech? = null
    private val jsonAdapter = moshi.adapter<List<String>>(stringListType)

    // Reactive database holder
    private val _petStats = MutableStateFlow(PetEntity())
    val petStats: StateFlow<PetEntity> = _petStats.asStateFlow()

    // Screen and active room state
    private val _currentRoom = MutableStateFlow(ActiveRoom.PLAYROOM)
    val currentRoom: StateFlow<ActiveRoom> = _currentRoom.asStateFlow()

    // Character drawing visual states
    private val _visualState = MutableStateFlow(PetVisualState.IDLE)
    val visualState: StateFlow<PetVisualState> = _visualState.asStateFlow()

    // Microphone audio level output for visualization
    val micAmplitude: StateFlow<Float> = voiceEngine.amplitude

    // Voice filter preferences
    private val _selectedVoiceFilter = MutableStateFlow("default")
    val selectedVoiceFilter: StateFlow<String> = _selectedVoiceFilter.asStateFlow()

    // Active food item eating sequence state
    private val _eatingFoodEmoji = MutableStateFlow<String?>(null)
    val eatingFoodEmoji: StateFlow<String?> = _eatingFoodEmoji.asStateFlow()

    // Timer jobs for decay & sleeping recovery
    private var decayJob: Job? = null
    
    // Bubble POP Mini-Game states
    data class Bubble(
        val id: Int,
        var x: Float,
        var y: Float,
        val radius: Float = 40f,
        val speed: Float,
        val colorHex: String,
        val isGold: Boolean = false
    )

    private val _gameBubbles = MutableStateFlow<List<Bubble>>(emptyList())
    val gameBubblesRef: StateFlow<List<Bubble>> = _gameBubbles.asStateFlow()

    private val _gameScore = MutableStateFlow(0)
    val gameScore: StateFlow<Int> = _gameScore.asStateFlow()

    private val _gameActive = MutableStateFlow(false)
    val gameActive: StateFlow<Boolean> = _gameActive.asStateFlow()

    private var gameLoopJob: Job? = null
    private var bubbleIdCounter = 0

    // Notification updates for levels and actions
    private val _gameNotification = MutableStateFlow<String?>(null)
    val gameNotification: StateFlow<String?> = _gameNotification.asStateFlow()

    // List of foods in Kitchen
    val availableFoods = listOf(
        FoodItem("apple", "Juicy Apple", 10, 20f, 5f, "🍎"),
        FoodItem("donut", "Glazed Donut", 20, 35f, 15f, "🍩"),
        FoodItem("pizza", "Gourmet Pizza", 40, 60f, 25f, "🍕"),
        FoodItem("broccoli", "Super Broccoli", 8, 15f, -2f, "🥦"),
        FoodItem("icecream", "Strawberry Cup", 15, 25f, 20f, "🍨")
    )

    // List of clothing and wallpapers in shop
    val shopItems = listOf(
        ShopItem("default", "Classic Fur", 0, isOutfit = true),
        ShopItem("sunglasses", "Specs Classic", 80, isOutfit = true),
        ShopItem("viking", "Viking Helmet", 250, isOutfit = true, unlockLevel = 2),
        ShopItem("royal_crown", "Royal Crown", 500, isOutfit = true, unlockLevel = 3),
        ShopItem("scarf", "Cozy Winter Scarf", 120, isOutfit = true),
        
        ShopItem("playroom", "Slate Playroom", 0, isOutfit = false),
        ShopItem("cosmic_galaxy", "Cosmic Sky", 200, isOutfit = false, unlockLevel = 2),
        ShopItem("candy_kitchen", "Pastel Kitchen", 180, isOutfit = false),
        ShopItem("sunny_meadow", "Sunny Grassland", 320, isOutfit = false, unlockLevel = 3)
    )

    init {
        val database = PetDatabase.getDatabase(application)
        repository = PetRepository(database.petDao())

        // Initialize cartoon text-to-speech engine
        tts = TextToSpeech(application) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.apply {
                    language = Locale.getDefault()
                    // Buster the cat speaking pitch modification (High-pitched, animated)
                    setPitch(1.65f)
                    setSpeechRate(1.1f)
                }
            } else {
                Log.e(TAG, "TextToSpeech initialization failed with status $status")
            }
        }

        // Collect stats flow from DB
        viewModelScope.launch {
            repository.petStatsFlow.collectLatest { entity ->
                _petStats.value = entity
            }
        }

        // Collect visual state changes to notify 3D Unity Character View with High-Efficiency Thread Boundary Isolation
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            _visualState
                .debounce(200L)
                .collect { state ->
                    val actionTag = when (state) {
                        PetVisualState.HAPPY_DANCE -> "dance"
                        PetVisualState.SPEAKING -> "sing"
                        PetVisualState.HEARING -> "hear"
                        PetVisualState.EATING -> "eat"
                        PetVisualState.SLEEPING -> "sleep"
                        PetVisualState.BATHING -> "shower"
                        PetVisualState.DIZZY -> "dizzy"
                        else -> "idle"
                    }
                    // Safely dispatch the message on background worker thread
                    withContext(Dispatchers.Default) {
                        try {
                            com.example.unity.UnityBridge.sendActionTo3DCat(actionTag)
                        } catch (t: Throwable) {
                            Log.e(TAG, "Dynamic Unity message dropped: $actionTag", t)
                        }
                    }
                }
        }

        // Initialize and calculate passive offline decay
        viewModelScope.launch {
            val loadedStats = repository.getPetStats()
            calculateOfflineDecay(loadedStats)
            startSurvivalDecayLoop()
        }
    }

    private fun calculateOfflineDecay(stats: PetEntity) {
        val now = System.currentTimeMillis()
        val elapsedSeconds = (now - stats.lastUpdatedTs) / 1000L
        if (elapsedSeconds <= 10L) return // Filter out immediate restarts

        // Hunger decay: 1 point every 3 minutes (180s)
        val decayHunger = (elapsedSeconds / 180f).coerceAtLeast(0f)
        // Hygiene decay: 1 point every 4 minutes (240s)
        val decayHygiene = (elapsedSeconds / 240f).coerceAtLeast(0f)
        // Fun decay: 1 point every 2 minutes (120s)
        val decayFun = (elapsedSeconds / 120f).coerceAtLeast(0f)

        // Energy behavior: If bedroom was sleeping, we RECOVER, otherwise we DECAY
        val finalEnergy = if (stats.currentBackgroundId == "bedroom" && stats.energy < 100f) {
            // Recover 1 point every 40 seconds
            val recoverEnergy = elapsedSeconds / 40f
            (stats.energy + recoverEnergy).coerceIn(0f, 100f)
        } else {
            // Decay 1 point every 5 minutes (300s)
            val decayEnergy = elapsedSeconds / 300f
            (stats.energy - decayEnergy).coerceIn(0f, 100f)
        }

        val updated = stats.copy(
            hunger = (stats.hunger - decayHunger).coerceIn(0f, 100f),
            hygiene = (stats.hygiene - decayHygiene).coerceIn(0f, 100f),
            funLevel = (stats.funLevel - decayFun).coerceIn(0f, 100f),
            energy = finalEnergy,
            lastUpdatedTs = now
        )

        viewModelScope.launch(Dispatchers.IO) {
            repository.savePetStats(updated)
        }
        showNotification("Buster missed you! Stats updated.")
    }

    private fun startSurvivalDecayLoop() {
        decayJob?.cancel()
        decayJob = viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(10000) // update decay matrix every 10 seconds
                val current = _petStats.value
                val isSleeping = _visualState.value == PetVisualState.SLEEPING || current.currentBackgroundId == "bedroom"

                // Active status decay formulas:
                val hungerDecay = 0.5f // hunger drops by 0.5 every 10s
                val hygieneDecay = 0.4f // hygiene drops by 0.4 every 10s
                val funDecay = 0.7f // fun drops by 0.7 every 10s

                // Sleeping speeds up energy recovery, decreases everything at minor rate
                val energyDelta = if (isSleeping) {
                    2.5f // recovers +2.5 energy every 10s
                } else {
                    -0.3f // decays -0.3 energy when active every 10s
                }

                val targetHunger = (current.hunger - hungerDecay).coerceIn(0f, 100f)
                val targetHygiene = (current.hygiene - hygieneDecay).coerceIn(0f, 100f)
                val targetFun = (current.funLevel - funDecay).coerceIn(0f, 100f)
                val targetEnergy = (current.energy + energyDelta).coerceIn(0f, 100f)

                val updated = current.copy(
                    hunger = targetHunger,
                    hygiene = targetHygiene,
                    funLevel = targetFun,
                    energy = targetEnergy,
                    lastUpdatedTs = System.currentTimeMillis()
                )

                _petStats.value = updated
                withContext(Dispatchers.IO) {
                    repository.savePetStats(updated)
                }

                // Auto-toggle visual sleeping state based on bedroom choice
                if (current.currentBackgroundId == "bedroom" && _visualState.value != PetVisualState.SLEEPING && targetEnergy < 100f) {
                    _visualState.value = PetVisualState.SLEEPING
                } else if (current.currentBackgroundId != "bedroom" && _visualState.value == PetVisualState.SLEEPING) {
                    _visualState.value = PetVisualState.IDLE
                }
            }
        }
    }

    fun changeRoom(room: ActiveRoom) {
        _currentRoom.value = room
        // Synchronize visual backgrounds
        val current = _petStats.value
        val bgId = when (room) {
            ActiveRoom.PLAYROOM -> "playroom"
            ActiveRoom.KITCHEN -> "playroom" // we can keep same or default custom
            ActiveRoom.BATHROOM -> "playroom"
            ActiveRoom.BEDROOM -> "bedroom"
            ActiveRoom.GAME_ROOM -> "playroom"
        }

        // Set visual states depending on room entry
        if (room == ActiveRoom.BEDROOM) {
            _visualState.value = PetVisualState.SLEEPING
        } else {
            if (_visualState.value == PetVisualState.SLEEPING) {
                _visualState.value = PetVisualState.IDLE
            }
        }

        // Stop recording when leaving playroom
        if (room != ActiveRoom.PLAYROOM) {
            voiceEngine.stopRecording()
            voiceEngine.stopPlayback()
        }

        // Stop mini-game if leaving games
        if (room != ActiveRoom.GAME_ROOM) {
            stopBubbleGame()
        }
    }

    // ──────────────────────────────────────────────
    // FEEDING & KITCHEN MECHANICS
    // ──────────────────────────────────────────────
    fun feedPet(food: FoodItem) {
        val current = _petStats.value
        if (current.coins < food.cost) {
            showNotification("Not enough coins! Play a mini-game to earn more.")
            return
        }

        if (current.hunger >= 98f) {
            showNotification("${current.petName} is already completely full!")
            return
        }

        // Subtract coins and add hunger stats, award some XP!
        val newCoins = current.coins - food.cost
        val newHunger = (current.hunger + food.hungerRestore).coerceIn(0f, 100f)
        val newFun = (current.funLevel + food.funAdded).coerceIn(0f, 100f)
        
        _eatingFoodEmoji.value = food.iconEmoji
        _visualState.value = PetVisualState.EATING

        viewModelScope.launch {
            // Animate eating for 1.8 seconds
            delay(1800)
            _eatingFoodEmoji.value = null
            _visualState.value = PetVisualState.IDLE

            addXp(15) // food gives 15 XP
            updateStats {
                it.copy(
                    coins = newCoins,
                    hunger = newHunger,
                    funLevel = newFun
                )
            }
        }
    }

    // ──────────────────────────────────────────────
    // SHOWERING & BATHROOM MECHANICS
    // ──────────────────────────────────────────────
    fun showerPet(percentageCleanEarned: Float) {
        val current = _petStats.value
        if (current.hygiene >= 100f) return

        _visualState.value = PetVisualState.BATHING
        
        val updatedHygiene = (current.hygiene + percentageCleanEarned).coerceIn(0f, 100f)
        viewModelScope.launch {
            updateStats {
                it.copy(hygiene = updatedHygiene)
            }
        }
    }

    fun finishShower() {
        if (_visualState.value == PetVisualState.BATHING) {
            _visualState.value = PetVisualState.IDLE
            addXp(12)
            showNotification("${_petStats.value.petName} is squeaky clean! +12 XP")
        }
    }

    // ──────────────────────────────────────────────
    // LAUNDRY / SHOP & CUSTOMIZATION MECHANICS
    // ──────────────────────────────────────────────
    fun buyShopItem(item: ShopItem) {
        val current = _petStats.value
        val listAdapter = jsonAdapter
        
        val unlockedList = if (item.isOutfit) {
            listAdapter.fromJson(current.unlockedOutfitsJson) ?: listOf("default")
        } else {
            listAdapter.fromJson(current.unlockedBackgroundsJson) ?: listOf("playroom")
        }

        if (unlockedList.contains(item.id)) {
            // Already unlocked, just equip it!
            equipItem(item)
            return
        }

        // Verify level requirement
        if (current.level < item.unlockLevel) {
            showNotification("Requires Level ${item.unlockLevel} to unlock!")
            return
        }

        // Verify currency
        if (current.coins < item.price) {
            showNotification("Need ${item.price - current.coins} more coins to purchase!")
            return
        }

        // Deduct and purchase!
        val updatedUnlockedList = unlockedList + item.id
        val updatedJson = listAdapter.toJson(updatedUnlockedList)

        viewModelScope.launch {
            updateStats {
                if (item.isOutfit) {
                    it.copy(
                        coins = it.coins - item.price,
                        currentOutfitId = item.id,
                        unlockedOutfitsJson = updatedJson
                    )
                } else {
                    it.copy(
                        coins = it.coins - item.price,
                        currentBackgroundId = item.id,
                        unlockedBackgroundsJson = updatedJson
                    )
                }
            }
            showNotification("Successfully purchased and equipped ${item.name}!")
        }
    }

    private fun equipItem(item: ShopItem) {
        viewModelScope.launch {
            updateStats {
                if (item.isOutfit) {
                    it.copy(currentOutfitId = item.id)
                } else {
                    it.copy(currentBackgroundId = item.id)
                }
            }
            showNotification("Equipped ${item.name}!")
        }
    }

    // ──────────────────────────────────────────────
    // POKE / PET / SOUND LOGIC (PLAYROOM INTERACTIVE)
    // ──────────────────────────────────────────────
    private var headTapCount = 0
    private var lastTapTime = 0L

    fun interactTouch(touchX: Float, touchY: Float, width: Int, height: Int) {
        val currentVisual = _visualState.value
        if (currentVisual == PetVisualState.SLEEPING || currentVisual == PetVisualState.EATING) return

        val now = System.currentTimeMillis()
        val isHeadTap = touchY < height * 0.45f
        val isChestTap = touchY >= height * 0.45f && touchY < height * 0.72f
        val isFeetTap = touchY >= height * 0.72f

        if (isHeadTap) {
            // Tapping high: head tap dizzy logic
            if (now - lastTapTime < 600) {
                headTapCount++
            } else {
                headTapCount = 1
            }
            lastTapTime = now

            if (headTapCount >= 3) {
                // Dizzy trigger!
                triggerDizzy()
                headTapCount = 0
            } else {
                triggerSmallAnimation(PetVisualState.DIZZY, 500)
                awardTapCoins(2)
            }
        } else if (isChestTap) {
            // Tickle tummy! Play happy dance
            triggerSmallAnimation(PetVisualState.HAPPY_DANCE, 1200)
            updateStatsAsync {
                val upFun = (it.funLevel + 8f).coerceAtIn(0f, 100f)
                it.copy(funLevel = upFun)
            }
            awardTapCoins(3)
        } else {
            // Foot interaction stomp
            triggerSmallAnimation(PetVisualState.HAPPY_DANCE, 800)
            awardTapCoins(2)
        }
    }

    private fun triggerDizzy() {
        triggerSmallAnimation(PetVisualState.DIZZY, 2000)
        showNotification("Buster got a bit dizzy! Tap slower.")
    }

    private fun triggerSmallAnimation(state: PetVisualState, duration: Long) {
        viewModelScope.launch {
            _visualState.value = state
            delay(duration)
            if (_visualState.value == state) {
                _visualState.value = PetVisualState.IDLE
            }
        }
    }

    private fun awardTapCoins(amount: Int) {
        viewModelScope.launch {
            updateStats {
                it.copy(coins = it.coins + amount)
            }
        }
    }

    // ──────────────────────────────────────────────
    // VOICE FILTER MODULATION
    // ──────────────────────────────────────────────
    fun setVoiceFilter(filter: String) {
        val current = _petStats.value
        // Verify milestones / level locks
        val priceLock = when (filter) {
            "giant" -> 2
            "robot" -> 3
            "alien" -> 4
            else -> 1
        }
        if (current.level < priceLock) {
            showNotification("Voice Filter '$filter' unlocks at Level $priceLock!")
            return
        }
        _selectedVoiceFilter.value = filter
        showNotification("Switched voice to $filter filter!")
    }

    fun startListeningRecording() {
        if (voiceEngine.isRecording || voiceEngine.isPlaying) return
        _visualState.value = PetVisualState.HEARING
        // Mute Unity background audio to prevent microphone stream leak feedback
        com.example.unity.UnityBridge.setUnityAudioMuted(true)
        
        voiceEngine.startRecording { audioData ->
            if (audioData.isNotEmpty()) {
                // Recording completed, now repeat!
                repeatWithSelectedFilter(audioData)
            } else {
                _visualState.value = PetVisualState.IDLE
                com.example.unity.UnityBridge.setUnityAudioMuted(false)
            }
        }
    }

    fun stopListeningRecording() {
        voiceEngine.stopRecording()
        com.example.unity.UnityBridge.setUnityAudioMuted(false)
    }

    private fun repeatWithSelectedFilter(audioData: ShortArray) {
        _visualState.value = PetVisualState.SPEAKING
        
        val factor = when (_selectedVoiceFilter.value) {
            "chipmunk" -> 1.7f
            "giant" -> 0.72f
            "alien" -> 1.3f
            else -> 1.35f // Default cute
        }
        val isRobot = _selectedVoiceFilter.value == "robot"

        voiceEngine.playWithFilter(audioData, pitchFactor = factor, isRobot = isRobot) {
            _visualState.value = PetVisualState.IDLE
            // Restore Unity game engine audio loop
            com.example.unity.UnityBridge.setUnityAudioMuted(false)
            // Reward for vocal playing
            viewModelScope.launch {
                addXp(8)
                updateStats {
                    val upFun = (it.funLevel + 10f).coerceAtIn(0f, 100f)
                    it.copy(coins = it.coins + 5, funLevel = upFun)
                }
            }
        }
    }

    // ──────────────────────────────────────────────
    // BUBBLE POP MINI-GAME LOGIC (COIN CATCHER)
    // ──────────────────────────────────────────────
    fun startBubbleGame() {
        if (_gameActive.value) return
        _gameActive.value = true
        _gameScore.value = 0
        _gameBubbles.value = emptyList()
        bubbleIdCounter = 0

        gameLoopJob = viewModelScope.launch(Dispatchers.Default) {
            var loopCount = 0
            while (_gameActive.value) {
                delay(30) // game runs at roughly ~30fps
                loopCount++

                // Retrieve and copy bubbles
                val currentList = _gameBubbles.value.map { it.copy() }
                val updatedList = mutableListOf<Bubble>()

                // Move current bubbles downwards
                for (b in currentList) {
                    b.y += b.speed
                    // Keep bubbles that are still within screen limits
                    if (b.y < 1200f) {
                        updatedList.add(b)
                    }
                }

                // Spawn a new bubble every ~1.2 seconds (40 frames)
                if (loopCount % 40 == 0) {
                    val isGold = Math.random() < 0.15
                    val spawnSpeed = (6f + Math.random() * 8f).toFloat()
                    val colorHex = if (isGold) "#FFD700" else listOf(
                        "#FF4081", "#3F51B5", "#00BCD4", "#4CAF50", "#FFC107"
                    ).random()

                    updatedList.add(
                        Bubble(
                            id = bubbleIdCounter++,
                            x = (50f + Math.random() * 800f).toFloat(),
                            y = -50f,
                            radius = (40f + Math.random() * 30f).toFloat(),
                            speed = spawnSpeed,
                            colorHex = colorHex,
                            isGold = isGold
                        )
                    )
                }

                _gameBubbles.value = updatedList
            }
        }
    }

    fun popBubble(bubbleId: Int) {
        val beforeList = _gameBubbles.value
        val target = beforeList.find { it.id == bubbleId } ?: return

        _gameBubbles.value = beforeList.filter { it.id != bubbleId }
        
        // Add scores & reward coins!
        val coinReward = if (target.isGold) 15 else 5
        _gameScore.value += if (target.isGold) 3 else 1

        viewModelScope.launch {
            updateStats {
                val upFun = (it.funLevel + 1.2f).coerceAtIn(0f, 100f)
                it.copy(
                    coins = it.coins + coinReward,
                    funLevel = upFun
                )
            }
        }
    }

    fun stopBubbleGame() {
        _gameActive.value = false
        gameLoopJob?.cancel()
        gameLoopJob = null
        
        if (_gameScore.value > 0) {
            val gainedXp = _gameScore.value * 2
            addXp(gainedXp)
            showNotification("Game End! Earned ${_gameScore.value} points and +$gainedXp XP!")
        }
        _gameBubbles.value = emptyList()
    }

    // ──────────────────────────────────────────────
    // XP & LEVEL PROGRESSION LOGIC
    // ──────────────────────────────────────────────
    fun addXp(amount: Int) {
        viewModelScope.launch {
            val current = _petStats.value
            var currentXp = current.xp + amount
            var currentLevel = current.level
            var xpNeeded = getXpNeeded(currentLevel)

            var leveledUp = false
            while (currentXp >= xpNeeded) {
                currentXp -= xpNeeded
                currentLevel++
                xpNeeded = getXpNeeded(currentLevel)
                leveledUp = true
            }

            if (leveledUp) {
                val reward = currentLevel * 80
                updateStats {
                    it.copy(
                        level = currentLevel,
                        xp = currentXp,
                        coins = it.coins + reward
                    )
                }
                showNotification("LEVEL UP! 🎉 Reached Level $currentLevel. Bonus +$reward coins!")
                // Trigger dance
                triggerSmallAnimation(PetVisualState.HAPPY_DANCE, 2500)
            } else {
                updateStats {
                    it.copy(xp = currentXp)
                }
            }
        }
    }

    private fun getXpNeeded(level: Int): Int {
        return level * 100 + 50
    }

    // ──────────────────────────────────────────────
    // DATABASE SYNCHRONIZATION HELPERS
    // ──────────────────────────────────────────────
    private suspend fun updateStats(transform: (PetEntity) -> PetEntity) {
        val current = _petStats.value
        val updated = transform(current)
        _petStats.value = updated
        withContext(Dispatchers.IO) {
            repository.savePetStats(updated)
        }
    }

    private fun updateStatsAsync(transform: (PetEntity) -> PetEntity) {
        viewModelScope.launch {
            updateStats(transform)
        }
    }

    private fun showNotification(message: String) {
        _gameNotification.value = message
        viewModelScope.launch {
            delay(3500)
            if (_gameNotification.value == message) {
                _gameNotification.value = null
            }
        }
    }

    // Coerce shortcut
    private fun Float.coerceAtIn(min: Float, max: Float): Float = this.coerceIn(min, max)

    override fun onCleared() {
        super.onCleared()
        decayJob?.cancel()
        gameLoopJob?.cancel()
        voiceEngine.stopRecording()
        voiceEngine.stopPlayback()
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {}
        speechRecognizer = null
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            // ignore
        }
    }

    fun speakText(text: String) {
        tts?.let {
            try {
                if (it.isSpeaking) {
                    it.stop()
                }
                _visualState.value = PetVisualState.SPEAKING
                it.speak(text, TextToSpeech.QUEUE_FLUSH, null, "BusterSpeakID")
                viewModelScope.launch {
                    val speakingDuration = (text.length * 68L).coerceIn(1200L, 6000L)
                    delay(speakingDuration)
                    if (_visualState.value == PetVisualState.SPEAKING) {
                        _visualState.value = PetVisualState.IDLE
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Speech Synthesis failure", e)
            }
        }
    }

    fun stopSpeaking() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            // ignore
        }
        if (_visualState.value == PetVisualState.SPEAKING) {
            _visualState.value = PetVisualState.IDLE
        }
    }

    fun sendMessageToBuster(userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty()) return
        
        val userMsg = ChatMessage(role = ChatRole.USER, text = trimmed)
        _aiChatMessages.value = _aiChatMessages.value + userMsg

        val apiKey = _openRouterApiKey.value
        val model = _openRouterModel.value

        if (apiKey.trim().isEmpty()) {
            val errorMsg = ChatMessage(
                role = ChatRole.ERROR,
                text = "No OpenRouter API Key configured! Please configure your OpenRouter key using the Brain icon settings."
            )
            _aiChatMessages.value = _aiChatMessages.value + errorMsg
            speakText("Please set up your Open Router API Key in settings.")
            return
        }

        _aiLoading.value = true
        _visualState.value = PetVisualState.HEARING

        viewModelScope.launch {
            try {
                val replyText = openRouterClient.fetchAIResponse(trimmed, apiKey, model)
                _aiLoading.value = false
                processCatResponse(replyText)
            } catch (e: Exception) {
                _aiLoading.value = false
                _visualState.value = PetVisualState.IDLE
                val errorMsg = ChatMessage(
                    role = ChatRole.ERROR,
                    text = "Buster's brain system had an error: ${e.localizedMessage}"
                )
                _aiChatMessages.value = _aiChatMessages.value + errorMsg
                speakText("I had trouble reaching my brain space. Check your internet connection.")
            }
        }
    }

    private fun processCatResponse(rawResponse: String) {
        var cleanText = rawResponse
        var shouldDance = false
        var shouldSing = false

        if (cleanText.contains("<dance>", ignoreCase = true)) {
            shouldDance = true
            cleanText = cleanText.replace("<dance>", "", ignoreCase = true)
        }
        if (cleanText.contains("<sing>", ignoreCase = true)) {
            shouldSing = true
            cleanText = cleanText.replace("<sing>", "", ignoreCase = true)
        }

        // Clean up duplicate triggers
        cleanText = cleanText.replace("<dance>", "", ignoreCase = true)
        cleanText = cleanText.replace("<sing>", "", ignoreCase = true)
        cleanText = cleanText.trim()

        _aiChatMessages.value = _aiChatMessages.value + ChatMessage(role = ChatRole.BUSTER, text = cleanText)
        speakText(cleanText)

        if (shouldDance) {
            _visualState.value = PetVisualState.HAPPY_DANCE
            showNotification("${_petStats.value.petName} is doing a happy dance! 🕺")
            viewModelScope.launch {
                delay(3000)
                if (_visualState.value == PetVisualState.HAPPY_DANCE) {
                    _visualState.value = PetVisualState.IDLE
                }
            }
        } else if (shouldSing) {
            _visualState.value = PetVisualState.HAPPY_DANCE
            showNotification("${_petStats.value.petName} is singing! 🎵")
            viewModelScope.launch {
                delay(3000)
                if (_visualState.value == PetVisualState.HAPPY_DANCE) {
                    _visualState.value = PetVisualState.IDLE
                }
            }
        }
    }

    fun addChatMessage(msg: ChatMessage) {
        _aiChatMessages.value = _aiChatMessages.value + msg
    }

    fun clearChatHistory() {
        _aiChatMessages.value = emptyList()
    }

    fun saveOpenRouterConfig(apiKey: String, model: String, modeEnabled: Boolean) {
        sharedPref.edit().apply {
            putString("API_KEY", apiKey.trim())
            putString("SELECTED_MODEL", model.trim())
            putBoolean("AI_MODE_ENABLED", modeEnabled)
            apply()
        }
        _openRouterApiKey.value = apiKey.trim()
        _openRouterModel.value = model.trim()
        _aiModeEnabled.value = modeEnabled
        showNotification("Buster's AI Brain config saved!")
    }

    // ──────────────────────────────────────────────
    // HANDS-FREE AI VOICE AGENT COMPANION LOOP
    // ──────────────────────────────────────────────
    private var speechRecognizer: android.speech.SpeechRecognizer? = null
    private val _isVoiceAgentListening = MutableStateFlow(false)
    val isVoiceAgentListening: StateFlow<Boolean> = _isVoiceAgentListening.asStateFlow()

    fun startVoiceAgentListening(context: android.content.Context) {
        if (_isVoiceAgentListening.value) return
        
        // Ensure standard recordings/playbacks are stopped so mic can be acquired cleanly
        voiceEngine.stopRecording()
        voiceEngine.stopPlayback()
        stopSpeaking()

        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault())
            putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        viewModelScope.launch(Dispatchers.Main) {
            try {
                if (android.speech.SpeechRecognizer.isRecognitionAvailable(context)) {
                    _isVoiceAgentListening.value = true
                    _visualState.value = PetVisualState.HEARING
                    com.example.unity.UnityBridge.setUnityAudioMuted(true)

                    speechRecognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(object : android.speech.RecognitionListener {
                            override fun onReadyForSpeech(params: android.os.Bundle?) {
                                Log.d(TAG, "Voice Agent SpeechRecognizer is target-ready")
                            }
                            override fun onBeginningOfSpeech() {}
                            override fun onRmsChanged(rmsdB: Float) {
                                // Maps RMS vocal sound volume to character amplitude (0f to 1f) for real-time talk wobble
                                val normalized = (rmsdB / 14f).coerceIn(0f, 1f)
                                voiceEngine.setAmplitude(normalized)
                            }
                            override fun onBufferReceived(buffer: ByteArray?) {}
                            override fun onEndOfSpeech() {
                                _isVoiceAgentListening.value = false
                                voiceEngine.setAmplitude(0f)
                            }
                            override fun onError(error: Int) {
                                Log.w(TAG, "Voice Agent SpeechRecognizer error: $error")
                                _isVoiceAgentListening.value = false
                                _visualState.value = PetVisualState.IDLE
                                com.example.unity.UnityBridge.setUnityAudioMuted(false)
                                val msg = when (error) {
                                    android.speech.SpeechRecognizer.ERROR_NO_MATCH -> "I couldn't hear what you said!"
                                    android.speech.SpeechRecognizer.ERROR_NETWORK, android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error with voice agent recognition."
                                    else -> "Speech recognizer is initializing or busy. Try typing instead!"
                                }
                                showNotification(msg)
                            }
                            override fun onResults(results: android.os.Bundle?) {
                                val matches = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                                if (!matches.isNullOrEmpty()) {
                                    val spokenText = matches[0]
                                    Log.d(TAG, "Voice Agent recognized user words: $spokenText")
                                    sendMessageToBuster(spokenText)
                                } else {
                                    _visualState.value = PetVisualState.IDLE
                                    com.example.unity.UnityBridge.setUnityAudioMuted(false)
                                }
                                _isVoiceAgentListening.value = false
                            }
                            override fun onPartialResults(partialResults: android.os.Bundle?) {}
                            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
                        })
                        startListening(intent)
                    }
                } else {
                    showNotification("Speech synthesis/recognition is not active on this device.")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "SpeechRecognizer helper failed to start", t)
                _isVoiceAgentListening.value = false
                _visualState.value = PetVisualState.IDLE
                com.example.unity.UnityBridge.setUnityAudioMuted(false)
            }
        }
    }

    fun stopVoiceAgentListening() {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
            } catch (t: Throwable) {
                // ignore
            }
            speechRecognizer = null
            _isVoiceAgentListening.value = false
            _visualState.value = PetVisualState.IDLE
            com.example.unity.UnityBridge.setUnityAudioMuted(false)
        }
    }
}
