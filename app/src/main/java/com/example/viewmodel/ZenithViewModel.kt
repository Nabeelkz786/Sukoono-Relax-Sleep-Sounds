package com.example.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AmbientAudioController
import com.example.audio.SoundAccessTier
import com.example.audio.SoundCacheManager
import com.example.audio.SoundCatalogRepository
import com.example.audio.SoundCategoryConfig
import com.example.audio.SoundDownloadState
import com.example.audio.SoundTrack
import com.example.data.ZenithDatabase
import com.example.data.model.FocusSession
import com.example.data.model.Habit
import com.example.data.model.HabitCompletion
import com.example.data.model.MindfulReflection
import com.example.data.model.TimeboxTask
import com.example.data.repository.DayCompletionStatus
import com.example.data.repository.HabitHistoryEntry
import com.example.data.repository.HabitWithStatus
import com.example.data.repository.ZenithRepository
import com.example.monetization.AdMobManager
import com.example.monetization.BillingManager
import com.example.monetization.BillingUiState
import com.example.monetization.EntitlementRepository
import com.example.monetization.EntitlementState
import com.example.monetization.ProPlanOption
import com.example.monetization.RewardedAdStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class ZenithTab(val title: String) {
    SOUNDS("Sounds"),
    FOCUS("Focus"),
    HABITS("Habits"),
    PLANNER("Planner"),
    INSIGHTS("Insights")
}

enum class FocusMode(val label: String, val minutes: Int) {
    POMODORO("Pomodoro", 25),
    DEEP_FLOW("Deep Flow", 50),
    SHORT_BREAK("Short Break", 5),
    LONG_BREAK("Long Break", 15),
    QUICK_SPRINT("Sprint", 15)
}

data class FocusUiState(
    val mode: FocusMode = FocusMode.POMODORO,
    val totalSeconds: Int = 25 * 60,
    val remainingSeconds: Int = 25 * 60,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val selectedTag: String = "Deep Work",
    val availableTags: List<String> = listOf("Deep Work", "Coding", "Writing", "Mindfulness", "Design", "Study", "Reading"),
    val ambientSound: SoundTrack? = null,
    val ambientVolume: Float = 0.5f,
    val showCelebration: Boolean = false,
    val todayCompletedMinutes: Int = 0,
    val todayCompletedSessionsCount: Int = 0
)

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val iconName: String,
    val isUnlocked: Boolean,
    val progress: Float
)

data class CategorizedSoundSection(
    val category: SoundCategoryConfig,
    val tracks: List<SoundTrack>
)

class ZenithViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: ZenithRepository = ZenithRepository(ZenithDatabase.getInstance(application)),
    private val entitlementRepository: EntitlementRepository = EntitlementRepository(application),
    private val adMobManager: AdMobManager = AdMobManager(application),
    private val billingManager: BillingManager = BillingManager(application, entitlementRepository),
    private val soundCatalogRepository: SoundCatalogRepository = SoundCatalogRepository.getInstance(application),
    private val soundCacheManager: SoundCacheManager = SoundCacheManager(application)
) : AndroidViewModel(application) {
    private val _currentTab = MutableStateFlow(ZenithTab.SOUNDS)
    val currentTab: StateFlow<ZenithTab> = _currentTab.asStateFlow()

    private val _showReflectSheet = MutableStateFlow(false)
    val showReflectSheet: StateFlow<Boolean> = _showReflectSheet.asStateFlow()

    private val _showProScreen = MutableStateFlow(false)
    val showProScreen: StateFlow<Boolean> = _showProScreen.asStateFlow()

    private val _pendingRewardUnlockSound = MutableStateFlow<SoundTrack?>(null)
    val pendingRewardUnlockSound: StateFlow<SoundTrack?> = _pendingRewardUnlockSound.asStateFlow()

    private val _rewardAdMessage = MutableStateFlow<String?>(null)
    val rewardAdMessage: StateFlow<String?> = _rewardAdMessage.asStateFlow()

    private val _soundFeedbackMessage = MutableStateFlow<String?>(null)
    val soundFeedbackMessage: StateFlow<String?> = _soundFeedbackMessage.asStateFlow()

    private val _focusState = MutableStateFlow(FocusUiState())
    val focusState: StateFlow<FocusUiState> = _focusState.asStateFlow()

    // Data-driven sound catalog
    val soundCategories: List<SoundCategoryConfig> = soundCatalogRepository.getCategories()
    val allSoundTracks: List<SoundTrack> = soundCatalogRepository.getAllSounds()
    val totalSoundsCount: Int = allSoundTracks.size
    val quickFocusSounds: List<SoundTrack> = soundCatalogRepository.defaultFocusQuickSounds()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<String?>(null)
    val selectedCategoryId: StateFlow<String?> = _selectedCategoryId.asStateFlow()

    private val _favoritesOnly = MutableStateFlow(false)
    val favoritesOnly: StateFlow<Boolean> = _favoritesOnly.asStateFlow()

    val activeSound: StateFlow<SoundTrack?> = AmbientAudioController.activeSound
    val mixerLayers: StateFlow<Map<SoundTrack, Float>> = AmbientAudioController.mixerLayers
    val isSoundPlaying: StateFlow<Boolean> = AmbientAudioController.isPlaying
    val soundVolume: StateFlow<Float> = AmbientAudioController.volume
    val downloadStates: StateFlow<Map<String, SoundDownloadState>> = soundCacheManager.downloadStates

    val entitlementState: StateFlow<EntitlementState> = entitlementRepository.entitlementState.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        EntitlementState()
    )

    val filteredSoundSections: StateFlow<List<CategorizedSoundSection>> = combine(
        _searchQuery,
        _selectedCategoryId,
        _favoritesOnly,
        entitlementState
    ) { query, catId, favOnly, ent ->
        val catsToShow = if (catId != null) {
            soundCategories.filter { it.id == catId }
        } else {
            soundCategories
        }

        catsToShow.mapNotNull { category ->
            val matchingTracks = allSoundTracks.filter { track ->
                track.categoryId == category.id &&
                    (!favOnly || ent.favoriteSoundIds.contains(track.id)) &&
                    track.matchesQuery(query)
            }
            if (matchingTracks.isEmpty()) null else CategorizedSoundSection(category, matchingTracks)
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        soundCategories.map { cat ->
            CategorizedSoundSection(cat, allSoundTracks.filter { it.categoryId == cat.id })
        }
    )

    val isOnline: StateFlow<Boolean> = AdMobManager.observeNetworkStatus(application).stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        AdMobManager.isNetworkConnected(application)
    )

    val rewardedAdStatus: StateFlow<RewardedAdStatus> = adMobManager.adStatus
    val billingState: StateFlow<BillingUiState> = billingManager.billingState

    private val _currentTimeMs = MutableStateFlow(System.currentTimeMillis())
    val currentTimeMs: StateFlow<Long> = _currentTimeMs.asStateFlow()

    private val _sleepTimerMinutes = MutableStateFlow<Int?>(null)
    val sleepTimerMinutes: StateFlow<Int?> = _sleepTimerMinutes.asStateFlow()

    private val _sleepTimerRemainingSeconds = MutableStateFlow(0)
    val sleepTimerRemainingSeconds: StateFlow<Int> = _sleepTimerRemainingSeconds.asStateFlow()

    private var timerJob: Job? = null
    private var sleepTimerJob: Job? = null

    private val _selectedDate = MutableStateFlow(ZenithRepository.getTodayString())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    init {
        billingManager.startConnection()

        viewModelScope.launch {
            entitlementState.collect { ent ->
                adMobManager.initialize(ent.isPro)
            }
        }

        viewModelScope.launch {
            while (isActive) {
                delay(30_000L)
                _currentTimeMs.value = System.currentTimeMillis()
            }
        }

        viewModelScope.launch {
            repository.allFocusSessions.collect { sessions ->
                val todayStart = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                val todaySessions = sessions.filter { it.timestamp >= todayStart && (it.sessionType == "POMODORO" || it.sessionType == "FLOW") }
                val minutes = todaySessions.sumOf { it.durationMinutes }
                _focusState.value = _focusState.value.copy(
                    todayCompletedMinutes = minutes,
                    todayCompletedSessionsCount = todaySessions.size
                )
            }
        }

        viewModelScope.launch {
            combine(AmbientAudioController.activeSound, AmbientAudioController.volume) { sound, vol ->
                sound to vol
            }.collect { (sound, vol) ->
                _focusState.value = _focusState.value.copy(
                    ambientSound = sound,
                    ambientVolume = vol
                )
            }
        }
    }

    val allSessions = repository.allFocusSessions.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val allHabitCompletions: StateFlow<List<HabitCompletion>> = repository.allCompletions.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val habitHistoryEntries: StateFlow<List<HabitHistoryEntry>> = combine(
        repository.allHabits,
        repository.allCompletions
    ) { habits, completions ->
        val habitMap = habits.associateBy { it.id }
        completions.mapNotNull { completion ->
            habitMap[completion.habitId]?.let { habit ->
                HabitHistoryEntry(completion = completion, habit = habit)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val habitsWithStatus: StateFlow<List<HabitWithStatus>> = combine(
        repository.allHabits,
        repository.allCompletions,
        _selectedDate
    ) { habits, completions, selectedDate ->
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dayLetterFormat = SimpleDateFormat("EEEEE", Locale.getDefault())

        habits.map { habit ->
            val habitCompletions = completions.filter { it.habitId == habit.id }
            val completedDatesSet = habitCompletions.map { it.dateString }.toSet()
            val isCompleted = completedDatesSet.contains(selectedDate)
            val (currentStreak, bestStreak) = ZenithRepository.calculateStreak(habit.id, habitCompletions)

            val recentDays = mutableListOf<DayCompletionStatus>()
            var countInPastWeek = 0
            for (offset in 6 downTo 0) {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -offset)
                val dStr = sdf.format(cal.time)
                val dayLabel = dayLetterFormat.format(cal.time)
                val done = completedDatesSet.contains(dStr)
                if (done) countInPastWeek++
                recentDays.add(
                    DayCompletionStatus(
                        dateString = dStr,
                        dayLabel = dayLabel,
                        isCompleted = done
                    )
                )
            }

            HabitWithStatus(
                habit = habit,
                isCompletedToday = isCompleted,
                currentStreak = currentStreak,
                bestStreak = bestStreak,
                weeklyCompletions = countInPastWeek,
                recentDays = recentDays,
                totalCompletions = habitCompletions.size
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayTimeboxTasks: StateFlow<List<TimeboxTask>> = _selectedDate.combine(repository.getTasksForDate(ZenithRepository.getTodayString())) { date, _ ->
        date
    }.combine(repository.allHabits) { date, _ ->
        date
    }.combine(repository.getTasksForDate(_selectedDate.value)) { _, tasks ->
        tasks
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _tasksForDate = MutableStateFlow<List<TimeboxTask>>(emptyList())
    val tasksForDate: StateFlow<List<TimeboxTask>> = _tasksForDate.asStateFlow()

    init {
        viewModelScope.launch {
            _selectedDate.collect { date ->
                repository.getTasksForDate(date).collect { tasks ->
                    _tasksForDate.value = tasks
                }
            }
        }
    }

    private val _currentReflection = MutableStateFlow<MindfulReflection?>(null)
    val currentReflection: StateFlow<MindfulReflection?> = _currentReflection.asStateFlow()

    init {
        viewModelScope.launch {
            _selectedDate.collect { date ->
                repository.getReflectionForDate(date).collect { reflection ->
                    _currentReflection.value = reflection
                }
            }
        }
    }

    fun selectTab(tab: ZenithTab) {
        _showReflectSheet.value = false
        _showProScreen.value = false
        _currentTab.value = tab
    }

    fun openReflectJournal() {
        _showReflectSheet.value = true
    }

    fun closeReflectJournal() {
        _showReflectSheet.value = false
    }

    fun openProUpgrade() {
        _pendingRewardUnlockSound.value = null
        _rewardAdMessage.value = null
        _showProScreen.value = true
    }

    fun closeProUpgrade() {
        _showProScreen.value = false
        billingManager.clearStatusMessage()
    }

    fun setSelectedDate(dateString: String) {
        _selectedDate.value = dateString
    }

    // SEARCH, CATEGORY FILTER & FAVORITES
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(categoryId: String?) {
        _favoritesOnly.value = false
        _selectedCategoryId.value = categoryId
    }

    fun selectFavoritesFilter() {
        _selectedCategoryId.value = null
        _favoritesOnly.value = true
    }

    fun toggleFavorite(sound: SoundTrack) {
        triggerLightClick()
        viewModelScope.launch {
            entitlementRepository.toggleFavorite(sound.id)
        }
    }

    fun dismissSoundFeedback() {
        _soundFeedbackMessage.value = null
    }

    // FOCUS CONTROLS
    fun setFocusMode(mode: FocusMode) {
        if (_focusState.value.isRunning) {
            pauseTimer()
        }
        val seconds = mode.minutes * 60
        _focusState.value = _focusState.value.copy(
            mode = mode,
            totalSeconds = seconds,
            remainingSeconds = seconds,
            isRunning = false,
            isPaused = false
        )
    }

    fun setSessionTag(tag: String) {
        _focusState.value = _focusState.value.copy(selectedTag = tag)
    }

    fun toggleStartPause() {
        if (_focusState.value.isRunning) {
            pauseTimer()
        } else {
            startTimer()
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        _focusState.value = _focusState.value.copy(isRunning = true, isPaused = false, showCelebration = false)

        _focusState.value.ambientSound?.let { sound ->
            AmbientAudioController.setSleepFadeMultiplier(1.0f)
            AmbientAudioController.play(
                getApplication(),
                sound,
                _focusState.value.ambientVolume,
                soundCacheManager
            )
        }

        timerJob = viewModelScope.launch {
            while (isActive && _focusState.value.remainingSeconds > 0) {
                delay(1000)
                val currentRem = _focusState.value.remainingSeconds - 1
                _focusState.value = _focusState.value.copy(remainingSeconds = currentRem)

                if (currentRem == 0) {
                    onTimerFinished()
                    break
                }
            }
        }
    }

    private fun pauseTimer() {
        timerJob?.cancel()
        timerJob = null
        _focusState.value = _focusState.value.copy(isRunning = false, isPaused = true)
        AmbientAudioController.pause(getApplication())
    }

    fun resetTimer() {
        timerJob?.cancel()
        timerJob = null
        AmbientAudioController.pause(getApplication())
        val seconds = _focusState.value.mode.minutes * 60
        _focusState.value = _focusState.value.copy(
            totalSeconds = seconds,
            remainingSeconds = seconds,
            isRunning = false,
            isPaused = false,
            showCelebration = false
        )
    }

    fun skipOrComplete() {
        onTimerFinished()
    }

    private fun onTimerFinished() {
        timerJob?.cancel()
        timerJob = null
        AmbientAudioController.pause(getApplication())
        triggerVibration()

        val completedMode = _focusState.value.mode
        val minutesCompleted = completedMode.minutes
        val isWorkSession = completedMode == FocusMode.POMODORO || completedMode == FocusMode.DEEP_FLOW || completedMode == FocusMode.QUICK_SPRINT

        if (isWorkSession) {
            viewModelScope.launch {
                repository.logFocusSession(
                    FocusSession(
                        durationMinutes = minutesCompleted,
                        sessionType = if (completedMode == FocusMode.DEEP_FLOW) "FLOW" else "POMODORO",
                        tag = _focusState.value.selectedTag
                    )
                )
            }
        }

        _focusState.value = _focusState.value.copy(
            isRunning = false,
            isPaused = false,
            remainingSeconds = 0,
            showCelebration = true
        )
    }

    fun dismissCelebration() {
        _focusState.value = _focusState.value.copy(showCelebration = false)
        if (_focusState.value.mode == FocusMode.POMODORO) {
            setFocusMode(FocusMode.SHORT_BREAK)
        } else if (_focusState.value.mode == FocusMode.SHORT_BREAK || _focusState.value.mode == FocusMode.LONG_BREAK) {
            setFocusMode(FocusMode.POMODORO)
        } else {
            resetTimer()
        }
    }

    // AMBIENT SOUNDSCAPE, ON-DEMAND CACHE, 3-TIER ACCESS, MIXER & SLEEP TIMER CONTROLS
    fun isSoundUnlocked(sound: SoundTrack?): Boolean {
        return entitlementState.value.isSoundUnlocked(sound, _currentTimeMs.value)
    }

    fun isSoundReadyLocally(sound: SoundTrack): Boolean {
        return soundCacheManager.isSoundReadyLocally(sound)
    }

    fun onSoundCardClicked(sound: SoundTrack) {
        val now = System.currentTimeMillis()
        _currentTimeMs.value = now
        if (entitlementState.value.isSoundUnlocked(sound, now)) {
            if (activeSound.value?.id == sound.id && isSoundPlaying.value && mixerLayers.value.size <= 1) {
                toggleSoundPlayback()
            } else {
                playOrDownloadThenPlay(sound)
            }
            return
        }

        triggerLightClick()
        when (sound.accessTier) {
            SoundAccessTier.FREE -> playOrDownloadThenPlay(sound)
            SoundAccessTier.REWARDED_AD -> {
                _rewardAdMessage.value = null
                adMobManager.preloadRewardedAd(isPro = false)
                _pendingRewardUnlockSound.value = sound
            }
            SoundAccessTier.PRO -> openProUpgrade()
        }
    }

    private fun playOrDownloadThenPlay(sound: SoundTrack) {
        if (soundCacheManager.isSoundReadyLocally(sound)) {
            playAmbientSound(sound)
            return
        }

        _soundFeedbackMessage.value = null
        viewModelScope.launch {
            val result = soundCacheManager.ensureCached(sound, isOnline.value)
            if (result.isSuccess) {
                playAmbientSound(sound)
            } else {
                _soundFeedbackMessage.value = result.exceptionOrNull()?.message
                    ?: "Connect to the internet to download \"${sound.title}\"."
            }
        }
    }

    fun downloadSoundForOffline(sound: SoundTrack) {
        if (!isSoundUnlocked(sound)) {
            onSoundCardClicked(sound)
            return
        }
        triggerLightClick()
        _soundFeedbackMessage.value = null
        viewModelScope.launch {
            val result = soundCacheManager.ensureCached(sound, isOnline.value)
            if (result.isFailure) {
                _soundFeedbackMessage.value = result.exceptionOrNull()?.message
                    ?: "Connect to the internet to cache \"${sound.title}\"."
            }
        }
    }

    fun dismissRewardUnlockPrompt() {
        _pendingRewardUnlockSound.value = null
        _rewardAdMessage.value = null
    }

    fun retryRewardedAdPreload() {
        _rewardAdMessage.value = null
        adMobManager.preloadRewardedAd(entitlementState.value.isPro)
    }

    fun watchRewardedAdToUnlock(activity: Activity?, sound: SoundTrack) {
        adMobManager.showRewardedAd(
            activity = activity,
            onBeforeShow = {
                if (isSoundPlaying.value) {
                    AmbientAudioController.pause(getApplication())
                }
            },
            onRewardEarned = {
                viewModelScope.launch {
                    val now = System.currentTimeMillis()
                    entitlementRepository.grantRewardedUnlock(sound, nowMs = now)
                    _currentTimeMs.value = now
                    _pendingRewardUnlockSound.value = null
                    _rewardAdMessage.value = null
                    playOrDownloadThenPlay(sound)
                }
            },
            onClosedWithoutReward = {
                _rewardAdMessage.value =
                    "Watch the full reward video to unlock ${sound.title} for 24 hours."
            },
            onUnavailable = { message ->
                _rewardAdMessage.value = message
            }
        )
    }

    fun toggleMixLayer(sound: SoundTrack) {
        if (!entitlementState.value.isPro) {
            openProUpgrade()
            return
        }
        triggerLightClick()
        if (soundCacheManager.isSoundReadyLocally(sound)) {
            AmbientAudioController.setSleepFadeMultiplier(1.0f)
            AmbientAudioController.toggleMixLayer(
                context = getApplication(),
                sound = sound,
                cacheManager = soundCacheManager
            )
            return
        }

        viewModelScope.launch {
            val result = soundCacheManager.ensureCached(sound, isOnline.value)
            if (result.isSuccess) {
                AmbientAudioController.setSleepFadeMultiplier(1.0f)
                AmbientAudioController.toggleMixLayer(
                    context = getApplication(),
                    sound = sound,
                    cacheManager = soundCacheManager
                )
            } else {
                _soundFeedbackMessage.value = result.exceptionOrNull()?.message
                    ?: "Connect to the internet to download \"${sound.title}\" for mixing."
            }
        }
    }

    fun setMixLayerVolume(sound: SoundTrack, layerVolume: Float) {
        if (!entitlementState.value.isPro) return
        AmbientAudioController.setMixLayerVolume(sound.id, layerVolume)
    }

    fun purchaseProPlan(activity: Activity?, plan: ProPlanOption) {
        billingManager.launchPurchase(activity, plan)
    }

    fun restoreProPurchases() {
        billingManager.restorePurchases()
    }

    fun clearBillingMessage() {
        billingManager.clearStatusMessage()
    }

    fun playAmbientSound(sound: SoundTrack?) {
        triggerLightClick()
        if (sound == null) {
            stopAmbientSound()
            return
        }
        AmbientAudioController.setSleepFadeMultiplier(1.0f)
        AmbientAudioController.play(getApplication(), sound, soundVolume.value, soundCacheManager)
        _focusState.value = _focusState.value.copy(ambientSound = sound)
    }

    fun toggleSoundPlayback() {
        triggerLightClick()
        if (isSoundPlaying.value) {
            AmbientAudioController.pause(getApplication())
        } else {
            val currentLayers = mixerLayers.value
            if (currentLayers.size > 1 && entitlementState.value.isPro) {
                AmbientAudioController.setSleepFadeMultiplier(1.0f)
                AmbientAudioController.playMix(getApplication(), currentLayers, soundVolume.value, soundCacheManager)
            } else {
                val active = activeSound.value
                val target = if (active != null && isSoundUnlocked(active)) {
                    active
                } else {
                    quickFocusSounds.firstOrNull() ?: allSoundTracks.firstOrNull()
                }
                if (target != null) {
                    playOrDownloadThenPlay(target)
                }
            }
        }
    }

    fun stopAmbientSound() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerMinutes.value = null
        _sleepTimerRemainingSeconds.value = 0
        AmbientAudioController.setSleepFadeMultiplier(1.0f)
        AmbientAudioController.stop(getApplication())
        _focusState.value = _focusState.value.copy(ambientSound = null)
    }

    fun setSleepTimer(minutes: Int?) {
        triggerLightClick()
        sleepTimerJob?.cancel()
        sleepTimerJob = null

        if (minutes == null || minutes <= 0) {
            _sleepTimerMinutes.value = null
            _sleepTimerRemainingSeconds.value = 0
            AmbientAudioController.setSleepFadeMultiplier(1.0f)
            return
        }

        _sleepTimerMinutes.value = minutes
        _sleepTimerRemainingSeconds.value = minutes * 60
        AmbientAudioController.setSleepFadeMultiplier(1.0f)

        sleepTimerJob = viewModelScope.launch {
            while (isActive && _sleepTimerRemainingSeconds.value > 0) {
                delay(1000)
                val next = _sleepTimerRemainingSeconds.value - 1
                _sleepTimerRemainingSeconds.value = next

                if (next in 1..10) {
                    AmbientAudioController.setSleepFadeMultiplier(next / 10f)
                }

                if (next <= 0) {
                    stopAmbientSound()
                    break
                }
            }
        }
    }

    fun setAmbientSound(sound: SoundTrack?) {
        if (sound == null) {
            stopAmbientSound()
            return
        }
        val now = System.currentTimeMillis()
        _currentTimeMs.value = now
        if (!entitlementState.value.isSoundUnlocked(sound, now)) {
            onSoundCardClicked(sound)
            return
        }
        _focusState.value = _focusState.value.copy(ambientSound = sound)
        if (_focusState.value.isRunning) {
            playOrDownloadThenPlay(sound)
        }
    }

    fun setAmbientVolume(vol: Float) {
        AmbientAudioController.setVolume(vol)
        _focusState.value = _focusState.value.copy(ambientVolume = vol.coerceIn(0f, 1f))
    }

    // HABIT ACTIONS
    fun toggleHabit(habitWithStatus: HabitWithStatus) {
        triggerLightClick()
        viewModelScope.launch {
            repository.toggleHabitCompletion(
                habitId = habitWithStatus.habit.id,
                dateString = _selectedDate.value,
                isCurrentlyCompleted = habitWithStatus.isCompletedToday
            )
        }
    }

    fun toggleHabitForDate(habitId: Long, dateString: String, isCurrentlyCompleted: Boolean) {
        triggerLightClick()
        viewModelScope.launch {
            repository.toggleHabitCompletion(
                habitId = habitId,
                dateString = dateString,
                isCurrentlyCompleted = isCurrentlyCompleted
            )
        }
    }

    fun removeHistoryEntry(completionId: Long) {
        triggerLightClick()
        viewModelScope.launch {
            repository.deleteCompletionById(completionId)
        }
    }

    fun addNewHabit(
        title: String,
        description: String,
        category: String,
        colorHex: String,
        iconName: String,
        targetDays: Int
    ) {
        viewModelScope.launch {
            repository.addHabit(
                Habit(
                    title = title.trim(),
                    description = description.trim(),
                    category = category,
                    colorHex = colorHex,
                    iconName = iconName,
                    targetDaysPerWeek = targetDays
                )
            )
        }
    }

    fun deleteHabit(habit: Habit) {
        viewModelScope.launch {
            repository.deleteHabit(habit)
        }
    }

    // TIMEBOX ACTIONS
    fun addTimeboxTask(
        title: String,
        startTime: String,
        durationMinutes: Int,
        category: String,
        priority: String
    ) {
        viewModelScope.launch {
            repository.addTask(
                TimeboxTask(
                    dateString = _selectedDate.value,
                    startTime = startTime,
                    durationMinutes = durationMinutes,
                    title = title.trim(),
                    category = category,
                    priority = priority,
                    isCompleted = false
                )
            )
        }
    }

    fun toggleTask(task: TimeboxTask) {
        triggerLightClick()
        viewModelScope.launch {
            repository.toggleTaskCompletion(task.id, !task.isCompleted)
        }
    }

    fun deleteTask(task: TimeboxTask) {
        viewModelScope.launch {
            repository.deleteTask(task)
        }
    }

    // REFLECTION ACTIONS
    fun saveReflection(mood: String, gratitude: String, intentions: String) {
        triggerLightClick()
        viewModelScope.launch {
            repository.saveReflection(
                MindfulReflection(
                    dateString = _selectedDate.value,
                    mood = mood,
                    gratitude = gratitude.trim(),
                    intentions = intentions.trim()
                )
            )
        }
    }

    // Haptics
    private fun triggerVibration() {
        try {
            val app = getApplication<Application>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 300), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(300)
                }
            }
        } catch (_: Exception) {}
    }

    private fun triggerLightClick() {
        try {
            val app = getApplication<Application>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val vibrator = app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            }
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        sleepTimerJob?.cancel()
        billingManager.endConnection()
    }
}
