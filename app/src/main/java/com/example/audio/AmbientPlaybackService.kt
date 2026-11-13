package com.example.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared audio playback state & controller bridged between UI/ViewModel and [AmbientPlaybackService].
 * Supports single-track playback as well as Pro Multi-Sound Mixer (up to 3 simultaneous layers).
 */
object AmbientAudioController {
    val engine = AmbientSoundEngine()

    private val _activeSound = MutableStateFlow<SoundTrack?>(null)
    val activeSound: StateFlow<SoundTrack?> = _activeSound.asStateFlow()

    private val _mixerLayers = MutableStateFlow<Map<SoundTrack, Float>>(emptyMap())
    val mixerLayers: StateFlow<Map<SoundTrack, Float>> = _mixerLayers.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _volume = MutableStateFlow(0.65f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    fun play(
        context: Context,
        sound: SoundTrack?,
        volumeLevel: Float = _volume.value,
        cacheManager: SoundCacheManager? = null
    ) {
        if (sound == null) {
            stop(context)
            return
        }
        playMix(context, mapOf(sound to 1.0f), volumeLevel, cacheManager)
    }

    fun playMix(
        context: Context,
        layers: Map<SoundTrack, Float>,
        volumeLevel: Float = _volume.value,
        cacheManager: SoundCacheManager? = null
    ) {
        val clean = layers.entries
            .take(AmbientSoundEngine.MAX_MIX_LAYERS)
            .associate { it.key to it.value.coerceIn(0.05f, 1.0f) }

        if (clean.isEmpty()) {
            stop(context)
            return
        }

        _volume.value = volumeLevel.coerceIn(0f, 1f)
        _mixerLayers.value = clean
        _activeSound.value = clean.keys.first()
        _isPlaying.value = true
        engine.setMix(clean, _volume.value, cacheManager)

        startPlaybackForegroundService(context, clean.keys.first())
    }

    fun toggleMixLayer(
        context: Context,
        sound: SoundTrack,
        defaultLayerVolume: Float = 0.7f,
        cacheManager: SoundCacheManager? = null
    ) {
        val current = _mixerLayers.value.toMutableMap()
        val existingKey = current.keys.firstOrNull { it.id == sound.id }
        if (existingKey != null) {
            current.remove(existingKey)
            if (current.isEmpty()) {
                stop(context)
            } else {
                playMix(context, current, _volume.value, cacheManager)
            }
        } else {
            if (current.size >= AmbientSoundEngine.MAX_MIX_LAYERS) {
                val firstKey = current.keys.first()
                current.remove(firstKey)
            }
            current[sound] = defaultLayerVolume.coerceIn(0.05f, 1.0f)
            playMix(context, current, _volume.value, cacheManager)
        }
    }

    fun setMixLayerVolume(soundId: String, layerVolume: Float) {
        val clamped = layerVolume.coerceIn(0.05f, 1.0f)
        val current = _mixerLayers.value
        val key = current.keys.firstOrNull { it.id == soundId } ?: return
        _mixerLayers.value = current.toMutableMap().apply { put(key, clamped) }
        engine.updateLayerVolume(soundId, clamped)
    }

    private fun startPlaybackForegroundService(context: Context, primarySound: SoundTrack) {
        try {
            val intent = Intent(context, AmbientPlaybackService::class.java).apply {
                action = AmbientPlaybackService.ACTION_PLAY
                putExtra(AmbientPlaybackService.EXTRA_SOUND_ID, primarySound.id)
            }
            ContextCompat.startForegroundService(context, intent)
        } catch (_: Exception) {
            // Safe fallback in test environments
        }
    }

    fun pause(context: Context) {
        _isPlaying.value = false
        engine.stop()
        try {
            val intent = Intent(context, AmbientPlaybackService::class.java).apply {
                action = AmbientPlaybackService.ACTION_STOP
            }
            context.stopService(intent)
        } catch (_: Exception) {}
    }

    fun stop(context: Context) {
        _isPlaying.value = false
        _activeSound.value = null
        _mixerLayers.value = emptyMap()
        engine.stop()
        try {
            val intent = Intent(context, AmbientPlaybackService::class.java).apply {
                action = AmbientPlaybackService.ACTION_STOP
            }
            context.stopService(intent)
        } catch (_: Exception) {}
    }

    fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0f, 1f)
        _volume.value = clamped
        engine.setVolume(clamped)
    }

    fun setSleepFadeMultiplier(multiplier: Float) {
        engine.setSleepFadeMultiplier(multiplier)
    }
}

class AmbientPlaybackService : Service() {

    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                AmbientAudioController.pause(this)
                stopSelf()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                AmbientAudioController.engine.setSleepFadeMultiplier(0.15f)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                AmbientAudioController.engine.setSleepFadeMultiplier(0.35f)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                AmbientAudioController.engine.setSleepFadeMultiplier(1.0f)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                abandonAudioFocus()
                AmbientAudioController.pause(this)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PLAY -> {
                val soundId = intent.getStringExtra(EXTRA_SOUND_ID)
                val sound = SoundCatalogRepository.getInstance(this).findSoundById(soundId)
                    ?: AmbientAudioController.activeSound.value

                if (sound == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                requestAudioFocus()
                val notification = buildNotification(sound)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
        }
        return START_STICKY
    }

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
            focusRequest = req
            am.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(focusChangeListener)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ambient Sleep & Focus Soundscapes",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Continuous background playback for soothing sleep and relaxation audio"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(sound: SoundTrack): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AmbientPlaybackService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val layers = AmbientAudioController.mixerLayers.value
        val title = if (layers.size > 1) {
            layers.keys.joinToString(" + ") { it.title }
        } else {
            sound.title
        }
        val subtitle = if (layers.size > 1) {
            "Multi-Sound Mix (${layers.size} layers) • Seamless Loop"
        } else {
            val catName = SoundCatalogRepository.getInstance(this)
                .getCategories()
                .firstOrNull { it.id == sound.categoryId }
                ?.fullTitle ?: "Ambient Audio"
            "$catName • Seamless Loop"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .build()
    }

    override fun onDestroy() {
        abandonAudioFocus()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "zenith_ambient_playback"
        const val NOTIFICATION_ID = 4042
        const val ACTION_PLAY = "com.example.audio.ACTION_PLAY"
        const val ACTION_STOP = "com.example.audio.ACTION_STOP"
        const val EXTRA_SOUND_ID = "extra_sound_id"
    }
}
