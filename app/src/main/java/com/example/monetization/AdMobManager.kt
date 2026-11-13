package com.example.monetization

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.content.ContextCompat
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.atomic.AtomicBoolean

sealed interface RewardedAdStatus {
    data object Idle : RewardedAdStatus
    data object Loading : RewardedAdStatus
    data object Ready : RewardedAdStatus
    data object OfflineUnavailable : RewardedAdStatus
    data class Failed(val message: String) : RewardedAdStatus
}

/**
 * Calm, non-disruptive Google AdMob manager.
 *
 * Strictly avoids interstitial/pop-up ads during playback. Manages only:
 * 1. User-initiated Rewarded Ads to unlock Tier 2 sounds for 24 hours.
 * 2. Subtle bottom Banner Ad (disabled for Pro users and when offline).
 */
class AdMobManager(private val appContext: Context) {

    private var rewardedAd: RewardedAd? = null
    private var isInitialized = false

    private val _adStatus = MutableStateFlow<RewardedAdStatus>(RewardedAdStatus.Idle)
    val adStatus: StateFlow<RewardedAdStatus> = _adStatus.asStateFlow()

    fun initialize(isPro: Boolean) {
        if (isPro) {
            rewardedAd = null
            _adStatus.value = RewardedAdStatus.Idle
            return
        }
        ensureMobileAdsInitialized(appContext)
        isInitialized = true
        preloadRewardedAd(isPro = false)
    }

    fun preloadRewardedAd(isPro: Boolean) {
        if (isPro) {
            rewardedAd = null
            _adStatus.value = RewardedAdStatus.Idle
            return
        }
        if (!isNetworkConnected(appContext)) {
            _adStatus.value = RewardedAdStatus.OfflineUnavailable
            return
        }
        if (rewardedAd != null || _adStatus.value == RewardedAdStatus.Loading) return

        _adStatus.value = RewardedAdStatus.Loading
        try {
            ensureMobileAdsInitialized(appContext)
            val request = AdRequest.Builder().build()
            RewardedAd.load(
                appContext,
                REWARDED_AD_UNIT_ID,
                request,
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        rewardedAd = ad
                        _adStatus.value = RewardedAdStatus.Ready
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        rewardedAd = null
                        _adStatus.value = if (!isNetworkConnected(appContext)) {
                            RewardedAdStatus.OfflineUnavailable
                        } else {
                            RewardedAdStatus.Failed(
                                "Ad is temporarily unavailable (${error.message}). Please try again shortly."
                            )
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            rewardedAd = null
            _adStatus.value = RewardedAdStatus.Failed(
                t.localizedMessage ?: "Unable to prepare rewarded ad right now."
            )
        }
    }

    fun showRewardedAd(
        activity: Activity?,
        onBeforeShow: () -> Unit,
        onRewardEarned: () -> Unit,
        onClosedWithoutReward: () -> Unit,
        onUnavailable: (String) -> Unit
    ) {
        if (!isNetworkConnected(appContext)) {
            _adStatus.value = RewardedAdStatus.OfflineUnavailable
            onUnavailable(
                "You're offline. Connect to the internet to watch a short ad and unlock this sound for 24 hours, or upgrade to Pro for full offline access."
            )
            return
        }

        if (activity == null) {
            onUnavailable("Unable to display ad outside an active screen.")
            return
        }

        val ad = rewardedAd
        if (ad == null) {
            preloadRewardedAd(isPro = false)
            val msg = when (val status = _adStatus.value) {
                is RewardedAdStatus.Failed -> status.message
                RewardedAdStatus.OfflineUnavailable ->
                    "No internet connection detected. Connect to watch an ad or upgrade to Pro."
                else ->
                    "Preparing a calming reward video... Please tap Watch Ad again in a moment."
            }
            onUnavailable(msg)
            return
        }

        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                _adStatus.value = RewardedAdStatus.Idle
                preloadRewardedAd(isPro = false)
                if (!earned) {
                    onClosedWithoutReward()
                }
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                rewardedAd = null
                _adStatus.value = RewardedAdStatus.Failed(adError.message)
                preloadRewardedAd(isPro = false)
                onUnavailable("Could not display the video (${adError.message}). Please try again.")
            }
        }

        onBeforeShow()
        ad.show(activity) {
            earned = true
            onRewardEarned()
        }
    }

    companion object {
        const val BANNER_AD_UNIT_ID = "ca-app-pub-9368131239345913/3552264902"
        const val REWARDED_AD_UNIT_ID = "ca-app-pub-9368131239345913/7080446373"

        private val sdkInitialized = AtomicBoolean(false)

        fun ensureMobileAdsInitialized(context: Context) {
            if (sdkInitialized.compareAndSet(false, true)) {
                try {
                    MobileAds.initialize(context.applicationContext) {}
                } catch (_: Throwable) {
                    // Safe fallback in unit/Robolectric environments
                }
            }
        }

        fun isNetworkConnected(context: Context): Boolean {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_NETWORK_STATE
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) return true
            return try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    ?: return false
                val activeNetwork = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } catch (_: Throwable) {
                false
            }
        }

        fun observeNetworkStatus(context: Context): Flow<Boolean> = callbackFlow {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm == null) {
                trySend(false)
                close()
                return@callbackFlow
            }

            trySend(isNetworkConnected(context))

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    trySend(true)
                }

                override fun onLost(network: Network) {
                    trySend(isNetworkConnected(context))
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    trySend(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
                }
            }

            try {
                val req = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                cm.registerNetworkCallback(req, callback)
            } catch (_: Throwable) {
                trySend(isNetworkConnected(context))
            }

            awaitClose {
                try {
                    cm.unregisterNetworkCallback(callback)
                } catch (_: Throwable) {}
            }
        }
    }
}
