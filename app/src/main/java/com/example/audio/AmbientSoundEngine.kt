package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Continuous Stereo Playback Engine for seamless single & multi-layer (up to 3 tracks)
 * ambient soundscapes. Mixes bundled real-time synthesizers and on-demand cached
 * loop files through a continuous AudioTrack stream with zero gap or click at loop points.
 */
class AmbientSoundEngine {
    private var audioTrack: AudioTrack? = null
    private var soundJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private val activeMix = ConcurrentHashMap<String, Pair<SoundTrack, Float>>()
    private val layerSources = ConcurrentHashMap<String, StereoLayerSource>()

    @Volatile
    private var masterVolume = 0.65f

    @Volatile
    private var sleepFadeMultiplier = 1.0f

    @Volatile
    private var sampleCounter: Long = 0L

    @Synchronized
    fun setSound(
        track: SoundTrack?,
        newVolume: Float = masterVolume,
        cacheManager: SoundCacheManager? = null
    ) {
        if (track == null) {
            stop()
            return
        }
        setMix(mapOf(track to 1.0f), newVolume, cacheManager)
    }

    @Synchronized
    fun setMix(
        layers: Map<SoundTrack, Float>,
        newMasterVolume: Float = masterVolume,
        cacheManager: SoundCacheManager? = null
    ) {
        masterVolume = newMasterVolume.coerceIn(0.0f, 1.0f)
        val cleanLayers = layers.entries
            .take(MAX_MIX_LAYERS)
            .associate { it.key.id to (it.key to it.value.coerceIn(0.05f, 1.0f)) }

        if (cleanLayers.isEmpty()) {
            stopImmediate()
            return
        }

        val sameKeys = activeMix.keys == cleanLayers.keys
        activeMix.clear()
        activeMix.putAll(cleanLayers)

        // Retain existing streaming sources for active layers so volume/layer updates are click-free
        layerSources.keys.retainAll(cleanLayers.keys)
        cleanLayers.values.forEachIndexed { idx, (track, _) ->
            layerSources.computeIfAbsent(track.id) {
                createSourceForTrack(track, idx, cacheManager)
            }
        }

        if (sameKeys && audioTrack != null) {
            try {
                audioTrack?.setVolume(masterVolume)
            } catch (_: Exception) {}
            return
        }

        if (audioTrack != null && soundJob?.isActive == true) {
            try {
                audioTrack?.setVolume(masterVolume)
            } catch (_: Exception) {}
            return
        }

        stopAudioStreamOnly()
        sleepFadeMultiplier = 1.0f
        sampleCounter = 0L

        val sampleRate = SoundscapeSynthesizer.DEFAULT_SAMPLE_RATE
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(16384)
        val bufferSize = ((minBufferSize + 3) / 4) * 4

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track.setVolume(masterVolume)
        track.play()
        audioTrack = track

        soundJob = scope.launch {
            val chunkShorts = 2048
            val buffer = ShortArray(chunkShorts)

            while (isActive && activeMix.isNotEmpty()) {
                val snapshot = activeMix.entries.mapNotNull { entry ->
                    val source = layerSources[entry.key]
                    if (source != null) source to entry.value.second else null
                }
                if (snapshot.isEmpty()) break

                sampleCounter = SoundscapeSynthesizer.renderMixedStereo(
                    layers = snapshot,
                    outBuffer = buffer,
                    sampleCounterStart = sampleCounter,
                    sampleRate = sampleRate,
                    fadeOutMultiplier = sleepFadeMultiplier
                )
                val written = track.write(buffer, 0, buffer.size)
                if (written < 0) break
            }
        }
    }

    private fun createSourceForTrack(
        track: SoundTrack,
        layerIndex: Int,
        cacheManager: SoundCacheManager?
    ): StereoLayerSource {
        if (!track.bundledLocally && cacheManager != null) {
            val cachedPcm = cacheManager.readCachedLoopPcm(track.id)
            if (cachedPcm != null && cachedPcm.size >= 2) {
                return CachedLoopLayerSource(cachedPcm)
            }
        }
        return RealtimeLayerSource(
            track = track,
            synthesizer = SoundscapeSynthesizer(
                sampleRate = SoundscapeSynthesizer.DEFAULT_SAMPLE_RATE,
                seed = 42L + track.id.hashCode().toLong() * 31L + layerIndex
            )
        )
    }

    fun updateLayerVolume(soundId: String, layerVolume: Float) {
        val current = activeMix[soundId] ?: return
        activeMix[soundId] = current.first to layerVolume.coerceIn(0.05f, 1.0f)
    }

    fun setVolume(vol: Float) {
        masterVolume = vol.coerceIn(0.0f, 1.0f)
        try {
            audioTrack?.setVolume(masterVolume)
        } catch (_: Exception) {}
    }

    fun setSleepFadeMultiplier(multiplier: Float) {
        sleepFadeMultiplier = multiplier.coerceIn(0.0f, 1.0f)
    }

    fun getVolume(): Float = masterVolume

    fun getCurrentSound(): SoundTrack? = activeMix.values.firstOrNull()?.first

    fun isPlaying(): Boolean = activeMix.isNotEmpty() && audioTrack != null

    @Synchronized
    fun stop() {
        stopImmediate()
    }

    @Synchronized
    private fun stopAudioStreamOnly() {
        soundJob?.cancel()
        soundJob = null
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }

    @Synchronized
    private fun stopImmediate() {
        stopAudioStreamOnly()
        activeMix.clear()
        layerSources.clear()
        sleepFadeMultiplier = 1.0f
        sampleCounter = 0L
    }

    companion object {
        const val MAX_MIX_LAYERS = 3
    }
}
