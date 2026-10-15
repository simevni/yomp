package com.plocoli.yomp.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One cached favorites rail: the title shown on Home and the tracks it held. */
@Serializable
data class CachedRail(val title: String, val query: String, val tracks: List<Track>, val albumId: String? = null)

/**
 * Last known contents of the favorites rails (SharedPreferences, JSON).
 *
 * Those rails come from Spotify search, so anything that makes search fail — no Wi-Fi at boot,
 * or a 429 rate limit that can lock the app out for several minutes — used to empty the Home
 * screen completely and leave a child staring at recent/liked only. The cache lets Home come up
 * with yesterday's rails immediately; a successful load overwrites it.
 */
object HomeCacheStore {
    private const val PREFS = "yomp_home_cache"
    private const val KEY = "rails"
    private val json = Json { ignoreUnknownKeys = true }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): List<CachedRail> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<CachedRail>>(raw) }.getOrDefault(emptyList())
    }

    fun save(context: Context, rails: List<CachedRail>) {
        prefs(context).edit().putString(KEY, json.encodeToString(rails)).apply()
    }
}
