package com.example.ui.sounds

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.audio.SoundTrack
import com.example.monetization.RewardedAdStatus
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.CyanFocus
import com.example.ui.theme.VioletLight
import com.example.ui.theme.VioletPrimary

@Composable
fun RewardedUnlockDialog(
    sound: SoundTrack,
    isOnline: Boolean,
    adStatus: RewardedAdStatus,
    feedbackMessage: String?,
    onWatchAd: () -> Unit,
    onRetryAd: () -> Unit,
    onGoPro: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        icon = {
            Icon(
                imageVector = if (isOnline) Icons.Filled.OndemandVideo else Icons.Filled.WifiOff,
                contentDescription = if (isOnline) "Watch Rewarded Ad" else "Offline",
                tint = if (isOnline) CyanFocus else AmberAccent,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Unlock ${sound.title}",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (isOnline) {
                        "Watch a short, optional video ad to unlock ${sound.title} for the next 24 hours—even if you go offline afterward."
                    } else {
                        "No internet connection detected. Ads cannot load while offline. Connect to the internet to unlock for 24h, or upgrade to Pro for full offline playback."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val statusText = feedbackMessage ?: when (adStatus) {
                    is RewardedAdStatus.Failed -> adStatus.message
                    RewardedAdStatus.Loading -> "Preparing reward video..."
                    RewardedAdStatus.OfflineUnavailable ->
                        "Connect to the internet to load this reward video."
                    else -> null
                }

                if (statusText != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AmberAccent.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reward_ad_feedback_banner")
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isOnline) {
                    Button(
                        onClick = onWatchAd,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("watch_reward_ad_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.OndemandVideo,
                            contentDescription = "Watch Ad",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Watch Ad • Unlock 24h",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Button(
                        onClick = onRetryAd,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("retry_reward_ad_button")
                    ) {
                        Text("Retry Connection", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = onGoPro,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, AmberAccent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("reward_dialog_go_pro_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.WorkspacePremium,
                        contentDescription = "Go Pro",
                        tint = AmberAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Go Pro • Ad-Free & Offline",
                        color = AmberAccent,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("reward_dialog_cancel_button")
                    ) {
                        Text("Not Now", color = VioletLight)
                    }
                }
            }
        }
    )
}
