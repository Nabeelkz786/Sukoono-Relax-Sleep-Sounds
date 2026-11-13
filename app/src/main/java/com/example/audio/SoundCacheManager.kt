package com.example.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

sealed interface SoundDownloadState {
    data object Idle : SoundDownloadState
    data class Downloading(val progress: Float) : SoundDownloadState
    data class Cached(val sizeBytes: Long) : SoundDownloadState
    data class Failed(val message: String) : SoundDownloadState
}

/**
 * Manages on-demand downloading/streaming and local caching for non-bundled soundscapes.
 *
 * Keeps the initial APK small by bundling only core Free tracks locally while caching
 * additional seamless loops into app-private `filesDir/sound_cache/` with zero storage permissions.
 */
class SoundCacheManager(private val appContext: Context) {

    private val cacheDir: File by lazy {
        File(appContext.filesDir, CACHE_FOLDER).apply {
            if (!exists()) mkdirs()
        }
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    private val downloadMutex = Mutex()

    private val _downloadStates = MutableStateFlow<Map<String, SoundDownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, SoundDownloadState>> = _downloadStates.asStateFlow()

    init {
        refreshCachedIndex()
    }

    fun refreshCachedIndex() {
        val current = _downloadStates.value.toMutableMap()
        cacheDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(LOOP_EXT) && it.length() > HEADER_BYTES }
            ?.forEach { file ->
                val id = file.name.removeSuffix(LOOP_EXT)
                current[id] = SoundDownloadState.Cached(file.length())
            }
        _downloadStates.value = current
    }

    fun isSoundReadyLocally(track: SoundTrack): Boolean {
        if (track.bundledLocally) return true
        return cacheFile(track.id).let { it.exists() && it.length() > HEADER_BYTES }
    }

    fun getCacheFile(soundId: String): File = cacheFile(soundId)

    private fun cacheFile(soundId: String): File = File(cacheDir, "$soundId$LOOP_EXT")

    fun updateState(soundId: String, state: SoundDownloadState) {
        _downloadStates.value = _downloadStates.value.toMutableMap().apply {
            put(soundId, state)
        }
    }

    /**
     * Ensures a non-bundled sound is cached locally before offline/seamless playback.
     * Supports remote HTTP download when [SoundTrack.remoteUrl] is provided and falls back
     * to on-demand seamless loop generation + disk caching when no CDN URL is set.
     */
    suspend fun ensureCached(
        track: SoundTrack,
        isOnline: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (track.bundledLocally || isSoundReadyLocally(track)) {
            if (!track.bundledLocally) {
                val len = cacheFile(track.id).length()
                updateState(track.id, SoundDownloadState.Cached(len))
            }
            return@withContext Result.success(Unit)
        }

        if (!isOnline) {
            val msg = "Connect to the internet to download \"${track.title}\" for offline listening."
            updateState(track.id, SoundDownloadState.Failed(msg))
            return@withContext Result.failure(IllegalStateException(msg))
        }

        downloadMutex.withLock {
            // Re-check inside lock
            if (isSoundReadyLocally(track)) {
                val len = cacheFile(track.id).length()
                updateState(track.id, SoundDownloadState.Cached(len))
                return@withLock Result.success(Unit)
            }

            updateState(track.id, SoundDownloadState.Downloading(0.05f))

            val tempFile = File(cacheDir, "${track.id}$LOOP_EXT.tmp")
            val targetFile = cacheFile(track.id)

            try {
                val remote = track.remoteUrl
                if (!remote.isNullOrBlank()) {
                    downloadFromUrl(track.id, remote, tempFile)
                } else {
                    synthesizeAndWriteLoopFile(track, tempFile)
                }
                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
                updateState(track.id, SoundDownloadState.Cached(targetFile.length()))
                Result.success(Unit)
            } catch (t: Throwable) {
                tempFile.delete()
                val msg = t.localizedMessage ?: "Failed to download ${track.title}."
                updateState(track.id, SoundDownloadState.Failed(msg))
                Result.failure(t)
            }
        }
    }

    private fun downloadFromUrl(soundId: String, url: String, outFile: File) {
        val request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code} while downloading soundscape")
            }
            val body = response.body ?: error("Empty response body")
            val contentLength = body.contentLength()
            var downloaded = 0L
            val buffer = ByteArray(16 * 1024)
            body.byteStream().use { input ->
                FileOutputStream(outFile).use { output ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (contentLength > 0L) {
                            val pct = (downloaded.toFloat() / contentLength.toFloat()).coerceIn(0.05f, 0.99f)
                            updateState(soundId, SoundDownloadState.Downloading(pct))
                        }
                    }
                }
            }
        }
    }

    private fun synthesizeAndWriteLoopFile(track: SoundTrack, outFile: File) {
        val synth = SoundscapeSynthesizer(
            sampleRate = SoundscapeSynthesizer.DEFAULT_SAMPLE_RATE,
            seed = 100L + track.id.hashCode().toLong()
        )
        val pcm = synth.generateSeamlessLoopPcm(
            track = track,
            durationSeconds = CACHED_LOOP_SECONDS,
            crossfadeSeconds = 1.25
        ) { synthProgress ->
            val pct = (0.05f + synthProgress * 0.75f).coerceIn(0.05f, 0.85f)
            updateState(track.id, SoundDownloadState.Downloading(pct))
        }

        val header = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(MAGIC_BYTES)
            putInt(SoundscapeSynthesizer.DEFAULT_SAMPLE_RATE)
            putInt(pcm.size)
        }

        FileOutputStream(outFile).use { fos ->
            fos.write(header.array())
            val chunkShorts = 8192
            val byteBuf = ByteBuffer.allocate(chunkShorts * 2).order(ByteOrder.LITTLE_ENDIAN)
            var offset = 0
            while (offset < pcm.size) {
                val count = minOf(chunkShorts, pcm.size - offset)
                byteBuf.clear()
                for (i in 0 until count) {
                    byteBuf.putShort(pcm[offset + i])
                }
                fos.write(byteBuf.array(), 0, count * 2)
                offset += count
                val pct = (0.85f + (offset.toFloat() / pcm.size) * 0.14f).coerceIn(0.85f, 0.99f)
                updateState(track.id, SoundDownloadState.Downloading(pct))
            }
            fos.flush()
        }
    }

    fun readCachedLoopPcm(soundId: String): ShortArray? {
        val file = cacheFile(soundId)
        if (!file.exists() || file.length() <= HEADER_BYTES) return null
        return try {
            val bytes = file.readBytes()
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(4)
            buf.get(magic)
            if (!magic.contentEquals(MAGIC_BYTES)) return null
            buf.getInt() // sampleRate
            val shortCount = buf.getInt()
            val availableShorts = (bytes.size - HEADER_BYTES) / 2
            val count = minOf(shortCount, availableShorts).coerceAtLeast(0)
            ShortArray(count) { buf.getShort() }
        } catch (_: Throwable) {
            null
        }
    }

    fun deleteCachedSound(soundId: String) {
        val file = cacheFile(soundId)
        if (file.exists()) {
            file.delete()
        }
        updateState(soundId, SoundDownloadState.Idle)
    }

    fun totalCachedBytes(): Long {
        return cacheDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(LOOP_EXT) }
            ?.sumOf { it.length() } ?: 0L
    }

    companion object {
        private const val CACHE_FOLDER = "sound_cache"
        private const val LOOP_EXT = ".sloop"
        private val MAGIC_BYTES = byteArrayOf('S'.code.toByte(), 'L'.code.toByte(), 'P'.code.toByte(), '1'.code.toByte())
        private const val HEADER_BYTES = 12
        const val CACHED_LOOP_SECONDS = 6
    }
}
