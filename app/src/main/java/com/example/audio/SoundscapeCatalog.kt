package com.example.audio

import android.content.Context
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

enum class SoundAccessTier(val label: String) {
    FREE("Free"),
    REWARDED_AD("Watch Ad • 24h"),
    PRO("Pro")
}

@JsonClass(generateAdapter = true)
data class SoundCategoryConfig(
    val id: String,
    val title: String,
    val fullTitle: String,
    val subtitle: String,
    val accentHex: String
)

@JsonClass(generateAdapter = true)
data class SynthProfileConfig(
    val engineMode: String,
    val lowPassCutoff: Double = 0.18,
    val pinkWeight: Double = 0.65,
    val brownWeight: Double = 0.35,
    val lfoRateHz: Double = 0.10,
    val lfoDepth: Double = 0.15,
    val toneHzLeft: Double = 0.0,
    val toneHzRight: Double = 0.0,
    val eventDensity: Double = 0.25
)

@JsonClass(generateAdapter = true)
data class SoundTrack(
    val id: String,
    val title: String,
    val description: String,
    val categoryId: String,
    val tier: String,
    val iconKey: String,
    val accentHex: String,
    val bundledLocally: Boolean = true,
    val estimatedSizeKb: Int = 0,
    val remoteUrl: String? = null,
    val acousticSpec: String = "44.1kHz Stereo • Seamless Loop",
    val tags: List<String> = emptyList(),
    val synthProfile: SynthProfileConfig
) {
    val accessTier: SoundAccessTier
        get() = when (tier.uppercase()) {
            "FREE" -> SoundAccessTier.FREE
            "PRO" -> SoundAccessTier.PRO
            else -> SoundAccessTier.REWARDED_AD
        }

    fun matchesQuery(query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase()
        return title.lowercase().contains(q) ||
            description.lowercase().contains(q) ||
            categoryId.lowercase().contains(q) ||
            tags.any { it.lowercase().contains(q) }
    }
}

@JsonClass(generateAdapter = true)
data class SoundCatalog(
    val version: Int = 1,
    val categories: List<SoundCategoryConfig> = emptyList(),
    val sounds: List<SoundTrack> = emptyList()
)

/**
 * Scalable, JSON-driven Sound Catalog Repository backed by `assets/sound_catalog.json`.
 * New categories and sounds can be added directly in JSON without modifying UI code.
 */
class SoundCatalogRepository(private val appContext: Context) {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Volatile
    private var cachedCatalog: SoundCatalog? = null

    fun getCatalog(): SoundCatalog {
        cachedCatalog?.let { return it }
        synchronized(this) {
            cachedCatalog?.let { return it }
            val json = appContext.assets.open(CATALOG_ASSET_FILE)
                .bufferedReader()
                .use { it.readText() }
            val adapter = moshi.adapter(SoundCatalog::class.java)
            val parsed = adapter.fromJson(json) ?: SoundCatalog()
            cachedCatalog = parsed
            return parsed
        }
    }

    fun getCategories(): List<SoundCategoryConfig> = getCatalog().categories

    fun getAllSounds(): List<SoundTrack> = getCatalog().sounds

    fun findSoundById(id: String?): SoundTrack? {
        if (id.isNullOrBlank()) return null
        return getCatalog().sounds.firstOrNull { it.id == id }
    }

    fun tracksForCategory(categoryId: String): List<SoundTrack> {
        return getCatalog().sounds.filter { it.categoryId == categoryId }
    }

    fun tracksForTier(tier: SoundAccessTier): List<SoundTrack> {
        return getCatalog().sounds.filter { it.accessTier == tier }
    }

    fun defaultFocusQuickSounds(): List<SoundTrack> {
        return getCatalog().sounds.filter { it.bundledLocally }
    }

    companion object {
        const val CATALOG_ASSET_FILE = "sound_catalog.json"

        @Volatile
        private var sharedInstance: SoundCatalogRepository? = null

        fun getInstance(context: Context): SoundCatalogRepository {
            return sharedInstance ?: synchronized(this) {
                sharedInstance ?: SoundCatalogRepository(context.applicationContext).also {
                    sharedInstance = it
                }
            }
        }
    }
}
