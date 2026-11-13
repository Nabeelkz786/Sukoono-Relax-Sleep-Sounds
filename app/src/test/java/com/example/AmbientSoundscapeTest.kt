package com.example

import android.app.Application
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AmbientAudioController
import com.example.audio.CachedLoopLayerSource
import com.example.audio.RealtimeLayerSource
import com.example.audio.SoundAccessTier
import com.example.audio.SoundCacheManager
import com.example.audio.SoundCatalogRepository
import com.example.audio.SoundDownloadState
import com.example.audio.SoundscapeSynthesizer
import com.example.data.ZenithDatabase
import com.example.data.repository.ZenithRepository
import com.example.monetization.AdMobManager
import com.example.monetization.EntitlementRepository
import com.example.ui.pro.ProUpgradeScreen
import com.example.ui.sounds.SoundsScreen
import com.example.ui.theme.ZenithTheme
import com.example.viewmodel.ZenithViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AmbientSoundscapeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var appContext: Application
    private lateinit var database: ZenithDatabase
    private lateinit var repository: ZenithRepository
    private lateinit var entitlementRepository: EntitlementRepository
    private lateinit var catalogRepository: SoundCatalogRepository
    private lateinit var soundCacheManager: SoundCacheManager
    private lateinit var dataStoreFile: File

    @Before
    fun setup() {
        appContext = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(appContext, ZenithDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ZenithRepository(database)

        dataStoreFile = File(appContext.filesDir, "test_entitlements_${System.nanoTime()}.preferences_pb")
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + Job()),
            produceFile = { dataStoreFile }
        )
        entitlementRepository = EntitlementRepository(testDataStore)
        catalogRepository = SoundCatalogRepository(appContext)
        soundCacheManager = SoundCacheManager(appContext)
        AmbientAudioController.stop(appContext)
    }

    @After
    fun teardown() {
        AmbientAudioController.stop(appContext)
        database.close()
        dataStoreFile.delete()
    }

    @Test
    fun jsonCatalog_loads44TracksAcrossFiveCategoriesAndThreeTiers() {
        val catalog = catalogRepository.getCatalog()
        assertEquals(5, catalog.categories.size)
        assertEquals(44, catalog.sounds.size)

        assertEquals(7, catalogRepository.tracksForCategory("NATURE_FOREST").size)
        assertEquals(9, catalogRepository.tracksForCategory("RAIN_WATER").size)
        assertEquals(9, catalogRepository.tracksForCategory("ANIMALS_WILDLIFE").size)
        assertEquals(12, catalogRepository.tracksForCategory("ELECTRONICS_NOISE").size)
        assertEquals(7, catalogRepository.tracksForCategory("COZY_FOCUS").size)

        val freeTracks = catalogRepository.tracksForTier(SoundAccessTier.FREE)
        val rewardedTracks = catalogRepository.tracksForTier(SoundAccessTier.REWARDED_AD)
        val proTracks = catalogRepository.tracksForTier(SoundAccessTier.PRO)

        assertEquals(10, freeTracks.size)
        assertEquals(23, rewardedTracks.size)
        assertEquals(11, proTracks.size)

        // Only core Free tracks are bundled locally to keep APK small
        assertTrue(freeTracks.all { it.bundledLocally })
        assertTrue(rewardedTracks.none { it.bundledLocally })
        assertTrue(proTracks.none { it.bundledLocally })
    }

    @Test
    fun synthesizer_generatesCleanSpikeFreeSeamlessStereoLoopsAndMixedCachePlayback() {
        val sampleRate = SoundscapeSynthesizer.DEFAULT_SAMPLE_RATE
        val durationSeconds = 1
        val expectedSamples = sampleRate * durationSeconds * 2

        val allSounds = catalogRepository.getAllSounds()
        allSounds.forEach { track ->
            val synth = SoundscapeSynthesizer(sampleRate = sampleRate, seed = 100L + track.id.hashCode())
            val pcm = synth.generateSeamlessLoopPcm(track = track, durationSeconds = durationSeconds)

            assertEquals("Stereo sample count mismatch for ${track.id}", expectedSamples, pcm.size)

            var sumSqL = 0.0
            var sumSqR = 0.0
            var maxAbs = 0
            val frames = pcm.size / 2
            for (f in 0 until frames) {
                val l = pcm[f * 2].toInt()
                val r = pcm[f * 2 + 1].toInt()
                sumSqL += l.toDouble() * l.toDouble()
                sumSqR += r.toDouble() * r.toDouble()
                maxAbs = maxOf(maxAbs, abs(l), abs(r))
            }

            val rmsL = sqrt(sumSqL / frames)
            val rmsR = sqrt(sumSqR / frames)

            assertTrue("${track.id} L channel RMS should be audible ($rmsL)", rmsL > 50.0)
            assertTrue("${track.id} R channel RMS should be audible ($rmsR)", rmsR > 50.0)
            assertTrue("${track.id} peak amplitude must stay spike-free ($maxAbs)", maxAbs <= 26000)

            val lastL = pcm[pcm.size - 2].toInt()
            val lastR = pcm[pcm.size - 1].toInt()
            val firstL = pcm[0].toInt()
            val firstR = pcm[1].toInt()
            assertTrue("${track.id} loop L seam smooth", abs(lastL - firstL) < 5500)
            assertTrue("${track.id} loop R seam smooth", abs(lastR - firstR) < 5500)
        }

        // Mix real-time bundled source + cached seamless loop source together
        val rainTrack = catalogRepository.findSoundById("light_morning_rain")!!
        val fanTrack = catalogRepository.findSoundById("box_fan_hum")!!
        val deltaTrack = catalogRepository.findSoundById("binaural_beats_delta")!!

        val deltaPcm = SoundscapeSynthesizer(sampleRate, 77L).generateSeamlessLoopPcm(deltaTrack, durationSeconds = 1)
        val mixBuffer = ShortArray(sampleRate * 2)
        val layers = listOf(
            RealtimeLayerSource(rainTrack, SoundscapeSynthesizer(sampleRate, 1L)) to 0.8f,
            RealtimeLayerSource(fanTrack, SoundscapeSynthesizer(sampleRate, 2L)) to 0.6f,
            CachedLoopLayerSource(deltaPcm) to 0.5f
        )
        SoundscapeSynthesizer.renderMixedStereo(
            layers = layers,
            outBuffer = mixBuffer,
            sampleCounterStart = 0L,
            sampleRate = sampleRate
        )
        val maxMixPeak = mixBuffer.maxOf { abs(it.toInt()) }
        assertTrue("Multi-sound mixer peak must stay spike-free ($maxMixPeak)", maxMixPeak in 500..26000)
    }

    @Test
    fun soundCacheManager_cachesOnDemandAndRejectsUncachedOffline() = runBlocking {
        val oceanTrack = catalogRepository.findSoundById("ocean_waves_sand")!!
        soundCacheManager.deleteCachedSound(oceanTrack.id)
        assertFalse(soundCacheManager.isSoundReadyLocally(oceanTrack))

        // Offline uncached download fails gracefully
        val offlineAttempt = soundCacheManager.ensureCached(oceanTrack, isOnline = false)
        assertTrue(offlineAttempt.isFailure)
        assertTrue(soundCacheManager.downloadStates.value[oceanTrack.id] is SoundDownloadState.Failed)

        // Online download caches seamlessly to disk
        val onlineAttempt = soundCacheManager.ensureCached(oceanTrack, isOnline = true)
        assertTrue(onlineAttempt.isSuccess)
        assertTrue(soundCacheManager.isSoundReadyLocally(oceanTrack))
        assertTrue(soundCacheManager.downloadStates.value[oceanTrack.id] is SoundDownloadState.Cached)

        val pcm = soundCacheManager.readCachedLoopPcm(oceanTrack.id)
        assertNotNull(pcm)
        assertEquals(
            SoundscapeSynthesizer.DEFAULT_SAMPLE_RATE * SoundCacheManager.CACHED_LOOP_SECONDS * 2,
            pcm!!.size
        )

        // Cached track succeeds even when offline afterward
        val offlineCachedAttempt = soundCacheManager.ensureCached(oceanTrack, isOnline = false)
        assertTrue(offlineCachedAttempt.isSuccess)
    }

    @Test
    fun entitlementRepository_persists24HourUnlockProStatusAndFavorites() = runBlocking {
        val baseNow = 1_700_000_000_000L
        val freeRain = catalogRepository.findSoundById("light_morning_rain")!!
        val ocean = catalogRepository.findSoundById("ocean_waves_sand")!!
        val delta = catalogRepository.findSoundById("binaural_beats_delta")!!

        val initial = entitlementRepository.entitlementState.first()
        assertFalse(initial.isPro)
        assertTrue(initial.isSoundUnlocked(freeRain, baseNow))
        assertFalse(initial.isSoundUnlocked(ocean, baseNow))
        assertFalse(initial.isSoundUnlocked(delta, baseNow))

        // Grant 24h rewarded unlock
        entitlementRepository.grantRewardedUnlock(ocean, nowMs = baseNow)
        val afterReward = entitlementRepository.entitlementState.first()
        assertTrue(afterReward.isSoundUnlocked(ocean, baseNow + 1000L))
        assertEquals("Unlocked for 24h", afterReward.formatRemainingTime(ocean, baseNow))
        assertEquals(
            "Unlocked • 12h 0m",
            afterReward.formatRemainingTime(ocean, baseNow + 12L * 60L * 60L * 1000L)
        )
        assertFalse(afterReward.isSoundUnlocked(ocean, baseNow + EntitlementRepository.TWENTY_FOUR_HOURS_MS + 1L))

        // Favorites bookmarking persistence
        entitlementRepository.toggleFavorite(freeRain.id)
        entitlementRepository.toggleFavorite(delta.id)
        val afterFavs = entitlementRepository.entitlementState.first()
        assertEquals(setOf(freeRain.id, delta.id), afterFavs.favoriteSoundIds)

        entitlementRepository.toggleFavorite(freeRain.id)
        assertEquals(setOf(delta.id), entitlementRepository.entitlementState.first().favoriteSoundIds)

        // Lifetime Pro unlocks all tracks
        entitlementRepository.setLifetimeUnlocked(true)
        val afterPro = entitlementRepository.entitlementState.first()
        assertTrue(afterPro.isPro)
        assertTrue(afterPro.isSoundUnlocked(ocean, baseNow))
        assertTrue(afterPro.isSoundUnlocked(delta, baseNow))
    }

    @Test
    fun soundsScreen_supportsSearch_categoryTabs_favorites_tierBadges_andSleepTimer() {
        val viewModel = ZenithViewModel(
            application = appContext,
            repository = repository,
            entitlementRepository = entitlementRepository,
            soundCatalogRepository = catalogRepository,
            soundCacheManager = soundCacheManager
        )

        composeTestRule.setContent {
            ZenithTheme {
                SoundsScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("now_playing_card").assertIsDisplayed()

        // Horizontal category filter tab: Electronics -> Box Fan Hum (Free, no badge)
        composeTestRule.onNodeWithTag("sounds_lazy_list")
            .performScrollToNode(hasTestTag("category_tabs_row"))
        composeTestRule.onNodeWithTag("category_tabs_row")
            .performScrollToNode(hasTestTag("sound_cat_electronics_noise"))
        composeTestRule.onNodeWithTag("sound_cat_electronics_noise").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("sounds_lazy_list")
            .performScrollToNode(hasTestTag("sound_item_box_fan_hum"))
        assertTrue(composeTestRule.onAllNodesWithTag("badge_watch_ad_box_fan_hum").fetchSemanticsNodes().isEmpty())
        assertTrue(composeTestRule.onAllNodesWithTag("badge_pro_box_fan_hum").fetchSemanticsNodes().isEmpty())

        // Bookmark Box Fan Hum as Favorite
        composeTestRule.onNodeWithTag("favorite_toggle_box_fan_hum", useUnmergedTree = true).performClick()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            ShadowLooper.idleMainLooper()
            viewModel.entitlementState.value.favoriteSoundIds.contains("box_fan_hum")
        }

        // Play Box Fan Hum (Free bundled sound)
        composeTestRule.onNodeWithTag("sound_item_box_fan_hum").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            ShadowLooper.idleMainLooper()
            viewModel.activeSound.value?.id == "box_fan_hum" && viewModel.isSoundPlaying.value
        }

        // Switch to Cozy & Focus and verify Pro badge on Tibetan Singing Bowls
        composeTestRule.onNodeWithTag("sounds_lazy_list")
            .performScrollToNode(hasTestTag("category_tabs_row"))
        composeTestRule.onNodeWithTag("category_tabs_row")
            .performScrollToNode(hasTestTag("sound_cat_cozy_focus"))
        composeTestRule.onNodeWithTag("sound_cat_cozy_focus").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("sounds_lazy_list")
            .performScrollToNode(hasTestTag("sound_item_tibetan_singing_bowls"))
        composeTestRule.onNodeWithTag("badge_pro_tibetan_singing_bowls", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("sound_item_tibetan_singing_bowls").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            ShadowLooper.idleMainLooper()
            viewModel.showProScreen.value
        }
        viewModel.closeProUpgrade()

        // Switch to Favorites tab and verify bookmarked Box Fan Hum appears
        composeTestRule.onNodeWithTag("sounds_lazy_list")
            .performScrollToNode(hasTestTag("category_tabs_row"))
        composeTestRule.onNodeWithTag("category_tabs_row")
            .performScrollToNode(hasTestTag("sound_cat_favorites"))
        composeTestRule.onNodeWithTag("sound_cat_favorites").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sounds_lazy_list")
            .performScrollToNode(hasTestTag("sound_item_box_fan_hum"))
        composeTestRule.onNodeWithTag("sound_item_box_fan_hum").assertIsDisplayed()

        // Switch to All & Search for "Ocean" -> opens Tier 2 Rewarded Ad prompt
        composeTestRule.onNodeWithTag("category_tabs_row")
            .performScrollToNode(hasTestTag("sound_cat_all"))
        composeTestRule.onNodeWithTag("sound_cat_all").performClick()
        composeTestRule.onNodeWithTag("sounds_lazy_list")
            .performScrollToNode(hasTestTag("sounds_search_input"))
        composeTestRule.onNodeWithTag("sounds_search_input").performTextInput("Ocean")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("sounds_lazy_list")
            .performScrollToNode(hasTestTag("sound_item_ocean_waves_sand"))
        composeTestRule.onNodeWithTag("badge_watch_ad_ocean_waves_sand", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("sound_item_ocean_waves_sand").performClick()

        composeTestRule.waitUntil(timeoutMillis = 3000) {
            ShadowLooper.idleMainLooper()
            viewModel.pendingRewardUnlockSound.value?.id == "ocean_waves_sand"
        }
        composeTestRule.onNodeWithText("Unlock Ocean Waves on Sand").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reward_dialog_cancel_button").performClick()
        composeTestRule.onNodeWithTag("sounds_search_clear").performClick()

        // Activate 30m sleep timer & then stop playback
        composeTestRule.onNodeWithTag("sounds_lazy_list")
            .performScrollToNode(hasTestTag("now_playing_card"))
        composeTestRule.onNodeWithTag("sleep_timer_30").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            ShadowLooper.idleMainLooper()
            viewModel.sleepTimerMinutes.value == 30 && viewModel.sleepTimerRemainingSeconds.value > 0
        }
        composeTestRule.onNodeWithTag("sleep_timer_countdown").assertIsDisplayed()

        composeTestRule.onNodeWithTag("sound_stop_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            ShadowLooper.idleMainLooper()
            viewModel.activeSound.value == null && !viewModel.isSoundPlaying.value
        }
        assertNull(viewModel.sleepTimerMinutes.value)
    }

    @Test
    fun proUser_canMixMultipleSoundsSimultaneously() = runBlocking {
        entitlementRepository.setLifetimeUnlocked(true)
        val rain = catalogRepository.findSoundById("light_morning_rain")!!
        val delta = catalogRepository.findSoundById("binaural_beats_delta")!!
        soundCacheManager.ensureCached(delta, isOnline = true)

        val viewModel = ZenithViewModel(
            application = appContext,
            repository = repository,
            entitlementRepository = entitlementRepository,
            soundCatalogRepository = catalogRepository,
            soundCacheManager = soundCacheManager
        )

        composeTestRule.setContent {
            ZenithTheme {
                SoundsScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            ShadowLooper.idleMainLooper()
            viewModel.entitlementState.value.isPro
        }

        viewModel.toggleMixLayer(rain)
        viewModel.toggleMixLayer(delta)

        composeTestRule.waitUntil(timeoutMillis = 4000) {
            ShadowLooper.idleMainLooper()
            viewModel.mixerLayers.value.size == 2 && viewModel.isSoundPlaying.value
        }
        composeTestRule.onNodeWithTag("multi_sound_mixer_panel").assertIsDisplayed()
        assertEquals(2, viewModel.mixerLayers.value.size)
    }

    @Test
    fun proUpgradeScreen_displaysSubscriptionAndLifetimeOptions() {
        val viewModel = ZenithViewModel(
            application = appContext,
            repository = repository,
            entitlementRepository = entitlementRepository,
            soundCatalogRepository = catalogRepository,
            soundCacheManager = soundCacheManager
        )

        composeTestRule.setContent {
            ZenithTheme {
                ProUpgradeScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("pro_plan_monthly").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("pro_plan_yearly").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("pro_plan_lifetime").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("pro_restore_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun adMobConfiguration_matchesManifestAppIdAndUnitIds() {
        @Suppress("DEPRECATION")
        val appInfo = appContext.packageManager.getApplicationInfo(
            appContext.packageName,
            PackageManager.GET_META_DATA
        )
        val manifestAppId = appInfo.metaData?.getString("com.google.android.gms.ads.APPLICATION_ID")
        assertEquals("ca-app-pub-9368131239345913~8520686409", manifestAppId)
        assertEquals("ca-app-pub-9368131239345913/3552264902", AdMobManager.BANNER_AD_UNIT_ID)
        assertEquals("ca-app-pub-9368131239345913/7080446373", AdMobManager.REWARDED_AD_UNIT_ID)
    }
}
