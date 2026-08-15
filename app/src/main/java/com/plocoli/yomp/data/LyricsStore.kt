package com.plocoli.yomp.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Caches fetched lyrics per track (SharedPreferences, same pattern as the other stores).
 * A kid replays the same songs constantly, so this keeps the network out of the loop —
 * and lyrics keep working offline once seen.
 *
 * Misses are cached too (otherwise every replay of a song with no lyrics hits the network
 * again), but only for [MISS_TTL_MS] — LRCLIB grows as people contribute, so a song that
 * has no lyrics today may have them next month.
 */
object LyricsStore {
    private const val PREFS = "yomp_lyrics"
    private const val MISS_TTL_MS = 7L * 24 * 60 * 60 * 1000   // 7 days
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Entry(val found: Boolean, val at: Long, val lyrics: Lyrics? = null)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Returns Result.success(lyrics-or-null) when the answer is cached, or null when it is not
     * cached at all (i.e. the caller should go and fetch). A cached miss is success(null).
     */
    fun get(context: Context, trackId: String): Result<Lyrics?>? {
        val raw = prefs(context).getString(trackId, null) ?: return null
        val entry = runCatching { json.decodeFromString<Entry>(raw) }.getOrNull() ?: return null
        if (!entry.found && System.currentTimeMillis() - entry.at > MISS_TTL_MS) return null
        return Result.success(entry.lyrics.takeIf { entry.found })
    }

    fun put(context: Context, trackId: String, lyrics: Lyrics?) {
        val entry = Entry(found = lyrics != null, at = System.currentTimeMillis(), lyrics = lyrics)
        prefs(context).edit().putString(trackId, json.encodeToString(entry)).apply()
    }

    /** Parent panel: forget everything so newly contributed lyrics get picked up right away. */
    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
