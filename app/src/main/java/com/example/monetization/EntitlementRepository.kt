package com.example.monetization

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.audio.SoundAccessTier
import com.example.audio.SoundTrack
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.entitlementDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sukoono_entitlements"
)

data class EntitlementState(
    val hasActiveSubscription: Boolean = false,
    val hasLifetimeUnlock: Boolean = false,
    val rewardUnlockExpirations: Map<String, Long> = emptyMap(),
    val favoriteSoundIds: Set<String> = emptySet()
) {
    val isPro: Boolean
        get() = hasActiveSubscription || hasLifetimeUnlock

    fun isSoundUnlocked(sound: SoundTrack?, nowMs: Long): Boolean {
        if (sound == null) return true
        if (isPro) return true
        return when (sound.accessTier) {
            SoundAccessTier.FREE -> true
            SoundAccessTier.REWARDED_AD -> (rewardUnlockExpirations[sound.id] ?: 0L) > nowMs
            SoundAccessTier.PRO -> false
        }
    }

    fun remainingUnlockMillis(sound: SoundTrack, nowMs: Long): Long {
        if (sound.accessTier != SoundAccessTier.REWARDED_AD || isPro) return 0L
        val expiresAt = rewardUnlockExpirations[sound.id] ?: return 0L
        return (expiresAt - nowMs).coerceAtLeast(0L)
    }

    fun formatRemainingTime(sound: SoundTrack, nowMs: Long): String? {
        val remMs = remainingUnlockMillis(sound, nowMs)
        if (remMs <= 0L) return null
        val totalMinutes = ((remMs + 59_999L) / 60_000L).toInt()
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        return when {
            hours >= 24 -> "Unlocked for 24h"
            hours > 0 -> "Unlocked • ${hours}h ${mins}m"
            else -> "Unlocked • ${mins.coerceAtLeast(1)}m"
        }
    }
}

/**
 * Local DataStore repository for 24-hour rewarded unlock timestamps, Pro entitlements,
 * and user-bookmarked Favorite sound IDs — 100% data-driven so new JSON catalog sounds
 * automatically persist without schema changes.
 */
class EntitlementRepository(
    private val dataStore: DataStore<Preferences>
) {
    constructor(context: Context) : this(context.applicationContext.entitlementDataStore)

    val entitlementState: Flow<EntitlementState> = dataStore.data.map { prefs ->
        val subActive = prefs[KEY_PRO_SUBSCRIPTION] ?: false
        val lifetime = prefs[KEY_PRO_LIFETIME] ?: false
        val favorites = prefs[KEY_FAVORITE_SOUND_IDS] ?: emptySet()
        val unlocks = mutableMapOf<String, Long>()

        prefs.asMap().forEach { (key, value) ->
            if (key.name.startsWith(REWARD_KEY_PREFIX) && value is Long && value > 0L) {
                val soundId = key.name.removePrefix(REWARD_KEY_PREFIX)
                unlocks[soundId] = value
            }
        }

        EntitlementState(
            hasActiveSubscription = subActive,
            hasLifetimeUnlock = lifetime,
            rewardUnlockExpirations = unlocks,
            favoriteSoundIds = favorites
        )
    }

    suspend fun grantRewardedUnlock(
        sound: SoundTrack,
        nowMs: Long = System.currentTimeMillis(),
        durationMs: Long = TWENTY_FOUR_HOURS_MS
    ) {
        if (sound.accessTier != SoundAccessTier.REWARDED_AD) return
        val expiresAt = nowMs + durationMs
        dataStore.edit { prefs ->
            prefs[rewardKey(sound.id)] = expiresAt
        }
    }

    suspend fun toggleFavorite(soundId: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_FAVORITE_SOUND_IDS]?.toMutableSet() ?: mutableSetOf()
            if (current.contains(soundId)) {
                current.remove(soundId)
            } else {
                current.add(soundId)
            }
            prefs[KEY_FAVORITE_SOUND_IDS] = current
        }
    }

    suspend fun setProSubscription(active: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_PRO_SUBSCRIPTION] = active
        }
    }

    suspend fun setLifetimeUnlocked(unlocked: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_PRO_LIFETIME] = unlocked
        }
    }

    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

    companion object {
        const val TWENTY_FOUR_HOURS_MS = 24L * 60L * 60L * 1000L
        private const val REWARD_KEY_PREFIX = "reward_unlock_"
        private val KEY_PRO_SUBSCRIPTION = booleanPreferencesKey("pro_subscription_active")
        private val KEY_PRO_LIFETIME = booleanPreferencesKey("pro_lifetime_unlocked")
        private val KEY_FAVORITE_SOUND_IDS = stringSetPreferencesKey("favorite_sound_ids")

        private fun rewardKey(soundId: String): Preferences.Key<Long> {
            return longPreferencesKey("$REWARD_KEY_PREFIX$soundId")
        }
    }
}
