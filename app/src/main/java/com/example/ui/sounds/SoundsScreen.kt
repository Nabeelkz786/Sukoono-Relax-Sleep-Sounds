package com.example.ui.sounds

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.EmojiNature
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fireplace
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.FlutterDash
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Sailing
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Toys
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Tsunami
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.Water
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.audio.SoundAccessTier
import com.example.audio.SoundCategoryConfig
import com.example.audio.SoundDownloadState
import com.example.audio.SoundTrack
import com.example.ui.components.ZenithTopBar
import com.example.ui.pro.findActivity
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.CyanFocus
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.PlusJakartaSansFamily
import com.example.ui.theme.RoseWarning
import com.example.ui.theme.VioletLight
import com.example.ui.theme.VioletPrimary
import com.example.viewmodel.ZenithViewModel

@Composable
fun SoundsScreen(
    viewModel: ZenithViewModel,
    modifier: Modifier = Modifier
) {
    val activeSound by viewModel.activeSound.collectAsState()
    val mixerLayers by viewModel.mixerLayers.collectAsState()
    val isPlaying by viewModel.isSoundPlaying.collectAsState()
    val volume by viewModel.soundVolume.collectAsState()
    val sleepTimerMinutes by viewModel.sleepTimerMinutes.collectAsState()
    val sleepTimerRemaining by viewModel.sleepTimerRemainingSeconds.collectAsState()
    val entitlementState by viewModel.entitlementState.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val currentTimeMs by viewModel.currentTimeMs.collectAsState()
    val pendingRewardSound by viewModel.pendingRewardUnlockSound.collectAsState()
    val rewardedAdStatus by viewModel.rewardedAdStatus.collectAsState()
    val rewardAdMessage by viewModel.rewardAdMessage.collectAsState()
    val soundFeedbackMessage by viewModel.soundFeedbackMessage.collectAsState()

    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsState()
    val favoritesOnly by viewModel.favoritesOnly.collectAsState()
    val filteredSections by viewModel.filteredSoundSections.collectAsState()
    val downloadStates by viewModel.downloadStates.collectAsState()

    var askedNotificationPermission by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Playback proceeds regardless of notification preference */ }

    val requestNotificationIfNeeded: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !askedNotificationPermission &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askedNotificationPermission = true
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .testTag("sounds_lazy_list"),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item(key = "header") {
                ZenithTopBar(
                    title = "Sukoono Sounds",
                    subtitle = if (entitlementState.isPro) {
                        "Pro Active • ${viewModel.totalSoundsCount} Soundscapes • Ad-Free"
                    } else {
                        "${viewModel.totalSoundsCount} calming soundscapes • Seamless loop"
                    },
                    trailingContent = {
                        GoProHeaderBadge(
                            isPro = entitlementState.isPro,
                            onClick = { viewModel.openProUpgrade() }
                        )
                    }
                )
            }

            item(key = "now_playing_hero") {
                NowPlayingHeroCard(
                    activeSound = activeSound,
                    mixerLayers = mixerLayers,
                    isPlaying = isPlaying,
                    isPro = entitlementState.isPro,
                    volume = volume,
                    sleepTimerMinutes = sleepTimerMinutes,
                    sleepTimerRemainingSeconds = sleepTimerRemaining,
                    onTogglePlayPause = {
                        requestNotificationIfNeeded()
                        viewModel.toggleSoundPlayback()
                    },
                    onStop = { viewModel.stopAmbientSound() },
                    onVolumeChange = { viewModel.setAmbientVolume(it) },
                    onLayerVolumeChange = { sound, vol -> viewModel.setMixLayerVolume(sound, vol) },
                    onRemoveLayer = { sound -> viewModel.toggleMixLayer(sound) },
                    onOpenProUpgrade = { viewModel.openProUpgrade() },
                    onSelectSleepTimer = { viewModel.setSleepTimer(it) },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }

            item(key = "feedback_banner") {
                AnimatedVisibility(visible = soundFeedbackMessage != null) {
                    soundFeedbackMessage?.let { msg ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = AmberAccent.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp)
                                .testTag("sound_feedback_banner")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.WifiOff,
                                    contentDescription = "Connection notice",
                                    tint = AmberAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = { viewModel.dismissSoundFeedback() },
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text("OK", color = AmberAccent, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            item(key = "search_bar") {
                SoundsSearchBar(
                    query = searchQuery,
                    totalCount = viewModel.totalSoundsCount,
                    onQueryChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }

            item(key = "category_filters") {
                HorizontalCategoryFilterRow(
                    categories = viewModel.soundCategories,
                    totalCount = viewModel.totalSoundsCount,
                    favoritesCount = entitlementState.favoriteSoundIds.size,
                    selectedCategoryId = selectedCategoryId,
                    favoritesOnly = favoritesOnly,
                    onSelectAll = { viewModel.selectCategory(null) },
                    onSelectFavorites = { viewModel.selectFavoritesFilter() },
                    onSelectCategory = { viewModel.selectCategory(it) },
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
            }

            if (filteredSections.isEmpty()) {
                item(key = "empty_state") {
                    EmptySoundFilterState(
                        favoritesOnly = favoritesOnly,
                        searchQuery = searchQuery,
                        onResetFilters = {
                            viewModel.setSearchQuery("")
                            viewModel.selectCategory(null)
                        },
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)
                    )
                }
            } else {
                filteredSections.forEach { section ->
                    item(key = "cat_header_${section.category.id}") {
                        CategorySectionHeader(
                            category = section.category,
                            trackCount = section.tracks.size,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp)
                        )
                    }

                    items(
                        items = section.tracks,
                        key = { it.id }
                    ) { sound ->
                        val isUnlocked = entitlementState.isSoundUnlocked(sound, currentTimeMs)
                        val isFavorite = entitlementState.favoriteSoundIds.contains(sound.id)
                        val isInMix = mixerLayers.keys.any { it.id == sound.id }
                        val isSelected = isInMix || activeSound?.id == sound.id
                        val isTrackPlaying = isSelected && isPlaying
                        val countdownLabel = entitlementState.formatRemainingTime(sound, currentTimeMs)
                        val dlState = downloadStates[sound.id] ?: SoundDownloadState.Idle

                        SoundTrackCard(
                            sound = sound,
                            isProUser = entitlementState.isPro,
                            isUnlocked = isUnlocked,
                            isFavorite = isFavorite,
                            isInMix = isInMix,
                            isSelected = isSelected,
                            isPlaying = isTrackPlaying,
                            downloadState = dlState,
                            rewardCountdownLabel = countdownLabel,
                            onCardClick = {
                                if (isUnlocked) requestNotificationIfNeeded()
                                viewModel.onSoundCardClicked(sound)
                            },
                            onFavoriteToggle = { viewModel.toggleFavorite(sound) },
                            onDownloadClick = { viewModel.downloadSoundForOffline(sound) },
                            onMixToggle = {
                                requestNotificationIfNeeded()
                                viewModel.toggleMixLayer(sound)
                            },
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }

    pendingRewardSound?.let { lockedSound ->
        RewardedUnlockDialog(
            sound = lockedSound,
            isOnline = isOnline,
            adStatus = rewardedAdStatus,
            feedbackMessage = rewardAdMessage,
            onWatchAd = {
                requestNotificationIfNeeded()
                viewModel.watchRewardedAdToUnlock(activity, lockedSound)
            },
            onRetryAd = { viewModel.retryRewardedAdPreload() },
            onGoPro = { viewModel.openProUpgrade() },
            onDismiss = { viewModel.dismissRewardUnlockPrompt() }
        )
    }
}

@Composable
private fun SoundsSearchBar(
    query: String,
    totalCount: Int,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = {
            Text(
                text = "Search $totalCount sounds (rain, fan, birds, brown noise...)",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search sounds",
                tint = VioletLight
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.testTag("sounds_search_clear")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            focusedBorderColor = VioletLight,
            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("sounds_search_input")
    )
}

@Composable
private fun GoProHeaderBadge(
    isPro: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isPro) EmeraldSuccess.copy(alpha = 0.18f) else AmberAccent.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, if (isPro) EmeraldSuccess else AmberAccent),
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("sounds_go_pro_button")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.WorkspacePremium,
                contentDescription = if (isPro) "Pro Active" else "Go Pro",
                tint = if (isPro) EmeraldSuccess else AmberAccent,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = if (isPro) "PRO" else "Go Pro",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = if (isPro) EmeraldSuccess else AmberAccent
            )
        }
    }
}

@Composable
private fun HorizontalCategoryFilterRow(
    categories: List<SoundCategoryConfig>,
    totalCount: Int,
    favoritesCount: Int,
    selectedCategoryId: String?,
    favoritesOnly: Boolean,
    onSelectAll: () -> Unit,
    onSelectFavorites: () -> Unit,
    onSelectCategory: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .testTag("category_tabs_row"),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "all") {
            FilterChipPill(
                label = "All ($totalCount)",
                selected = selectedCategoryId == null && !favoritesOnly,
                accentColor = VioletLight,
                onClick = onSelectAll,
                testTag = "sound_cat_all"
            )
        }

        item(key = "favorites") {
            FilterChipPill(
                label = if (favoritesCount > 0) "Favorites ($favoritesCount)" else "Favorites",
                leadingIcon = Icons.Filled.Favorite,
                selected = favoritesOnly,
                accentColor = RoseWarning,
                onClick = onSelectFavorites,
                testTag = "sound_cat_favorites"
            )
        }

        items(
            items = categories,
            key = { it.id }
        ) { category ->
            val accent = Color(android.graphics.Color.parseColor(category.accentHex))
            FilterChipPill(
                label = category.title,
                selected = !favoritesOnly && selectedCategoryId == category.id,
                accentColor = accent,
                onClick = { onSelectCategory(category.id) },
                testTag = "sound_cat_${category.id.lowercase()}"
            )
        }
    }
}

@Composable
private fun FilterChipPill(
    label: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    testTag: String,
    leadingIcon: ImageVector? = null
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) accentColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = if (selected) BorderStroke(1.dp, accentColor) else null,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CategorySectionHeader(
    category: SoundCategoryConfig,
    trackCount: Int,
    modifier: Modifier = Modifier
) {
    val accent = Color(android.graphics.Color.parseColor(category.accentHex))
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = category.fullTitle,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = PlusJakartaSansFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = accent
            )
            Text(
                text = "$trackCount tracks",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = category.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptySoundFilterState(
    favoritesOnly: Boolean,
    searchQuery: String,
    onResetFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .testTag("sounds_empty_state")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (favoritesOnly) Icons.Outlined.FavoriteBorder else Icons.Filled.Search,
                contentDescription = "No sounds found",
                tint = VioletLight,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (favoritesOnly) "No favorite sounds yet" else "No matching sounds",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (favoritesOnly) {
                    "Tap the heart icon on any soundscape to bookmark it for quick sleep & focus access."
                } else {
                    "No sounds matched \"$searchQuery\". Try another keyword or clear your filters."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onResetFilters) {
                Text("Show All Sounds", color = VioletLight, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SoundTrackCard(
    sound: SoundTrack,
    isProUser: Boolean,
    isUnlocked: Boolean,
    isFavorite: Boolean,
    isInMix: Boolean,
    isSelected: Boolean,
    isPlaying: Boolean,
    downloadState: SoundDownloadState,
    rewardCountdownLabel: String?,
    onCardClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onDownloadClick: () -> Unit,
    onMixToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = Color(android.graphics.Color.parseColor(sound.accentHex))
    val isDownloading = downloadState is SoundDownloadState.Downloading
    val isCached = downloadState is SoundDownloadState.Cached

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) BorderStroke(1.2.dp, accent.copy(alpha = 0.7f)) else null,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onCardClick)
            .testTag("sound_item_${sound.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = if (isUnlocked) 0.18f else 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = soundIconForKey(sound.iconKey),
                        contentDescription = sound.title,
                        tint = if (isUnlocked) accent else accent.copy(alpha = 0.65f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = sound.title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = PlusJakartaSansFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        TierStatusBadge(
                            sound = sound,
                            isProUser = isProUser,
                            isUnlocked = isUnlocked,
                            rewardCountdownLabel = rewardCountdownLabel
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = sound.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    CacheStatusLine(
                        sound = sound,
                        isUnlocked = isUnlocked,
                        isCached = isCached,
                        downloadState = downloadState,
                        onDownloadClick = onDownloadClick
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("favorite_toggle_${sound.id}")
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isFavorite) "Remove ${sound.title} from favorites" else "Bookmark ${sound.title}",
                        tint = if (isFavorite) RoseWarning else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (isProUser) {
                    IconButton(
                        onClick = onMixToggle,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (isInMix) VioletLight.copy(alpha = 0.25f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            )
                            .testTag("mix_toggle_${sound.id}")
                    ) {
                        Icon(
                            imageVector = if (isInMix) Icons.Filled.Check else Icons.Filled.Add,
                            contentDescription = if (isInMix) "In mix" else "Add ${sound.title} to mix",
                            tint = if (isInMix) VioletLight else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isPlaying -> accent
                                !isUnlocked && sound.accessTier == SoundAccessTier.PRO -> AmberAccent.copy(alpha = 0.16f)
                                !isUnlocked && sound.accessTier == SoundAccessTier.REWARDED_AD -> CyanFocus.copy(alpha = 0.16f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            isPlaying -> Icons.Filled.Pause
                            !isUnlocked && sound.accessTier == SoundAccessTier.PRO -> Icons.Filled.WorkspacePremium
                            !isUnlocked && sound.accessTier == SoundAccessTier.REWARDED_AD -> Icons.Filled.OndemandVideo
                            else -> Icons.Filled.PlayArrow
                        },
                        contentDescription = when {
                            isPlaying -> "Pause ${sound.title}"
                            !isUnlocked -> "Unlock ${sound.title}"
                            else -> "Play ${sound.title}"
                        },
                        tint = when {
                            isPlaying -> Color.White
                            !isUnlocked && sound.accessTier == SoundAccessTier.PRO -> AmberAccent
                            !isUnlocked && sound.accessTier == SoundAccessTier.REWARDED_AD -> CyanFocus
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (isDownloading) {
                val pct = (downloadState as SoundDownloadState.Downloading).progress
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { pct },
                    color = accent,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .testTag("download_progress_${sound.id}")
                )
            }
        }
    }
}

@Composable
private fun CacheStatusLine(
    sound: SoundTrack,
    isUnlocked: Boolean,
    isCached: Boolean,
    downloadState: SoundDownloadState,
    onDownloadClick: () -> Unit
) {
    when {
        sound.bundledLocally -> {
            Text(
                text = "Bundled • Instant Offline",
                style = MaterialTheme.typography.labelSmall,
                color = EmeraldSuccess.copy(alpha = 0.85f)
            )
        }
        downloadState is SoundDownloadState.Downloading -> {
            val pct = (downloadState.progress * 100).toInt().coerceIn(1, 100)
            Text(
                text = "Caching loop • $pct%",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                color = CyanFocus
            )
        }
        isCached -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.testTag("badge_cached_${sound.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudDone,
                    contentDescription = "Cached offline",
                    tint = EmeraldSuccess,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Cached • Offline Ready",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldSuccess
                )
            }
        }
        isUnlocked -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDownloadClick)
                    .padding(vertical = 2.dp, horizontal = 2.dp)
                    .testTag("download_button_${sound.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudDownload,
                    contentDescription = "Download ${sound.title} for offline playback",
                    tint = CyanFocus,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Stream / Cache (${sound.estimatedSizeKb} KB)",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanFocus
                )
            }
        }
        else -> {
            Text(
                text = "On-demand cloud loop (${sound.estimatedSizeKb} KB)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
        }
    }
}

@Composable
private fun TierStatusBadge(
    sound: SoundTrack,
    isProUser: Boolean,
    isUnlocked: Boolean,
    rewardCountdownLabel: String?
) {
    val tier = sound.accessTier
    if (tier == SoundAccessTier.FREE) return

    when (tier) {
        SoundAccessTier.REWARDED_AD -> {
            if (isProUser) {
                return
            } else if (isUnlocked && rewardCountdownLabel != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = EmeraldSuccess.copy(alpha = 0.16f),
                    modifier = Modifier.testTag("badge_unlocked_24h_${sound.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LockOpen,
                            contentDescription = "Unlocked for 24 hours",
                            tint = EmeraldSuccess,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = rewardCountdownLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = EmeraldSuccess
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CyanFocus.copy(alpha = 0.16f),
                    modifier = Modifier.testTag("badge_watch_ad_${sound.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.OndemandVideo,
                            contentDescription = "Watch Ad to unlock",
                            tint = CyanFocus,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Watch Ad",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = CyanFocus
                        )
                    }
                }
            }
        }
        SoundAccessTier.PRO -> {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isProUser) EmeraldSuccess.copy(alpha = 0.16f) else AmberAccent.copy(alpha = 0.18f),
                modifier = Modifier.testTag("badge_pro_${sound.id}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.WorkspacePremium,
                        contentDescription = "Pro Sound",
                        tint = if (isProUser) EmeraldSuccess else AmberAccent,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Pro",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isProUser) EmeraldSuccess else AmberAccent
                    )
                }
            }
        }
        SoundAccessTier.FREE -> Unit
    }
}

internal fun soundIconForKey(iconKey: String): ImageVector {
    return when (iconKey) {
        "water_drop" -> Icons.Filled.WaterDrop
        "waves" -> Icons.Filled.Waves
        "air" -> Icons.Filled.Air
        "eco" -> Icons.Filled.Eco
        "park" -> Icons.Filled.Park
        "forest" -> Icons.Filled.Forest
        "landscape" -> Icons.Filled.Landscape
        "wb_sunny" -> Icons.Filled.WbSunny
        "beach_access" -> Icons.Filled.BeachAccess
        "thunderstorm" -> Icons.Filled.Thunderstorm
        "umbrella" -> Icons.Filled.Umbrella
        "directions_car" -> Icons.Filled.DirectionsCar
        "pool" -> Icons.Filled.Pool
        "water" -> Icons.Filled.Water
        "local_fire_department" -> Icons.Filled.LocalFireDepartment
        "fireplace" -> Icons.Filled.Fireplace
        "local_cafe" -> Icons.Filled.LocalCafe
        "schedule" -> Icons.Filled.Schedule
        "self_improvement" -> Icons.Filled.SelfImprovement
        "flutter_dash" -> Icons.Filled.FlutterDash
        "pets" -> Icons.Filled.Pets
        "nights_stay" -> Icons.Filled.NightsStay
        "bedtime" -> Icons.Filled.Bedtime
        "sailing" -> Icons.Filled.Sailing
        "emoji_nature" -> Icons.Filled.EmojiNature
        "tsunami" -> Icons.Filled.Tsunami
        "mode_fan" -> Icons.Filled.Toys
        "toys" -> Icons.Filled.Toys
        "ac_unit" -> Icons.Filled.AcUnit
        "local_laundry_service" -> Icons.Filled.LocalLaundryService
        "cleaning_services" -> Icons.Filled.CleaningServices
        "flight" -> Icons.Filled.Flight
        "train" -> Icons.Filled.Train
        "graphic_eq" -> Icons.Filled.GraphicEq
        "radio" -> Icons.Filled.Radio
        "headphones" -> Icons.Filled.Headphones
        "volume_off" -> Icons.AutoMirrored.Filled.VolumeOff
        else -> Icons.Filled.Spa
    }
}
