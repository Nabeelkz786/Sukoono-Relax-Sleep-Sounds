package com.example.ui.sounds

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audio.SoundTrack
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.PlusJakartaSansFamily
import com.example.ui.theme.VioletLight
import com.example.ui.theme.VioletPrimary
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun NowPlayingHeroCard(
    activeSound: SoundTrack?,
    mixerLayers: Map<SoundTrack, Float>,
    isPlaying: Boolean,
    isPro: Boolean,
    volume: Float,
    sleepTimerMinutes: Int?,
    sleepTimerRemainingSeconds: Int,
    onTogglePlayPause: () -> Unit,
    onStop: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onLayerVolumeChange: (SoundTrack, Float) -> Unit,
    onRemoveLayer: (SoundTrack) -> Unit,
    onOpenProUpgrade: () -> Unit,
    onSelectSleepTimer: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (activeSound == null) {
        VioletLight
    } else {
        Color(android.graphics.Color.parseColor(activeSound.accentHex))
    }

    val isMultiMix = mixerLayers.size > 1
    val heroTitle = when {
        isMultiMix -> mixerLayers.keys.joinToString(" + ") { it.title }
        activeSound != null -> activeSound.title
        else -> "Choose a Soundscape"
    }
    val heroSubtitle = when {
        isMultiMix -> "Multi-Sound Mixer (${mixerLayers.size}/3 active layers)"
        activeSound != null -> activeSound.description
        else -> "44+ calming ambient soundscapes • Seamless stereo loop"
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (isPlaying) accent.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("now_playing_card")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(accent.copy(alpha = 0.35f), VioletPrimary.copy(alpha = 0.25f))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                isMultiMix -> Icons.Filled.Tune
                                activeSound == null -> Icons.Filled.Spa
                                else -> soundIconForKey(activeSound.iconKey)
                            },
                            contentDescription = heroTitle,
                            tint = accent,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isPlaying) "NOW PLAYING • SEAMLESS STEREO" else "SLEEP & RELAX PLAYER",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isPlaying) accent else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = heroTitle,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = PlusJakartaSansFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = heroSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (isPlaying) accent else VioletPrimary)
                            .testTag("sound_play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause soundscape" else "Play soundscape",
                            tint = Color.White
                        )
                    }

                    if (activeSound != null) {
                        IconButton(
                            onClick = onStop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .testTag("sound_stop_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Stop,
                                contentDescription = "Stop soundscape",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            StereoWaveHorizon(
                isPlaying = isPlaying,
                accentColor = accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Master Volume Control
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Master Volume",
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Slider(
                    value = volume,
                    onValueChange = onVolumeChange,
                    colors = SliderDefaults.colors(
                        thumbColor = accent,
                        activeTrackColor = accent,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("sounds_volume_slider")
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${(volume * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Multi-Sound Mixer Section (Pro feature)
            if (isPro) {
                if (mixerLayers.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("multi_sound_mixer_panel")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Tune,
                                        contentDescription = "Multi-Sound Mixer",
                                        tint = VioletLight,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "MULTI-SOUND MIXER (${mixerLayers.size}/3)",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = VioletLight
                                    )
                                }
                                Text(
                                    text = "Tap +Mix on tracks",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            mixerLayers.forEach { (layerSound, layerVol) ->
                                val layerAccent = Color(android.graphics.Color.parseColor(layerSound.accentHex))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = layerSound.title,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.width(116.dp)
                                    )
                                    Slider(
                                        value = layerVol,
                                        onValueChange = { onLayerVolumeChange(layerSound, it) },
                                        valueRange = 0.05f..1.0f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = layerAccent,
                                            activeTrackColor = layerAccent,
                                            inactiveTrackColor = MaterialTheme.colorScheme.surface
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("mixer_slider_${layerSound.id}")
                                    )
                                    IconButton(
                                        onClick = { onRemoveLayer(layerSound) },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("mixer_remove_${layerSound.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Remove ${layerSound.title} from mix",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AmberAccent.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onOpenProUpgrade)
                        .testTag("mixer_pro_upsell_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Filled.Tune,
                                contentDescription = "Multi-Sound Mixer",
                                tint = AmberAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Multi-Sound Mixer (blend 2–3 sounds)",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.WorkspacePremium,
                                contentDescription = "Pro",
                                tint = AmberAccent,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "PRO",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = AmberAccent
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sleep Timer Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Bedtime,
                        contentDescription = "Sleep Timer",
                        tint = AmberAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SLEEP FADE TIMER",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(visible = sleepTimerMinutes != null && sleepTimerRemainingSeconds > 0) {
                    val mins = sleepTimerRemainingSeconds / 60
                    val secs = sleepTimerRemainingSeconds % 60
                    Text(
                        text = String.format(Locale.getDefault(), "%02d:%02d", mins, secs),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = AmberAccent,
                        modifier = Modifier.testTag("sleep_timer_countdown")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val timerPresets = listOf<Int?>(null, 15, 30, 45, 60, 90)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                timerPresets.forEach { preset ->
                    val selected = sleepTimerMinutes == preset
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) AmberAccent.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        border = if (selected) BorderStroke(1.dp, AmberAccent) else null,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectSleepTimer(preset) }
                            .testTag("sleep_timer_${preset ?: 0}")
                    ) {
                        Text(
                            text = if (preset == null) "Off" else "${preset}m",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (selected) AmberAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StereoWaveHorizon(
    isPlaying: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "stereo_wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    Canvas(modifier = modifier) {
        val midY = size.height / 2f
        val amplitude = if (isPlaying) size.height * 0.32f else size.height * 0.08f
        val leftPath = Path()
        val rightPath = Path()
        val steps = 48

        for (i in 0..steps) {
            val x = (i.toFloat() / steps) * size.width
            val normX = i.toFloat() / steps
            val envelope = sin(normX * PI).toFloat()
            val yL = midY + sin(normX * 4.5f * PI.toFloat() + phase) * amplitude * envelope
            val yR = midY + sin(normX * 3.8f * PI.toFloat() - phase * 0.85f) * amplitude * 0.75f * envelope
            if (i == 0) {
                leftPath.moveTo(x, yL)
                rightPath.moveTo(x, yR)
            } else {
                leftPath.lineTo(x, yL)
                rightPath.lineTo(x, yR)
            }
        }

        drawLine(
            color = accentColor.copy(alpha = 0.14f),
            start = Offset(0f, midY),
            end = Offset(size.width, midY),
            strokeWidth = 1.dp.toPx()
        )
        drawPath(
            path = rightPath,
            color = VioletLight.copy(alpha = if (isPlaying) 0.55f else 0.25f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
            path = leftPath,
            color = accentColor.copy(alpha = if (isPlaying) 0.9f else 0.35f),
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
