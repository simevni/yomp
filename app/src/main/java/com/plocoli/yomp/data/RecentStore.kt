package com.plocoli.yomp.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Local "recently played" history. A track is prepended the moment it starts playing,
 * so the Home rail updates instantly.
 * (Spotify's server-side recently-played has a reporting delay and is only fetched on
 * app resume, so we track it locally for immediacy.)
 */
object RecentStore {
    private const val PREFS = "yomp_recent"
    private const val KEY = "list"
    private const val MAX = 20
    private val json = Json { ignoreUnknownKeys = true }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(context: Context): List<Track> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<Track>>(raw) }.getOrDefault(emptyList())
    }

    /** Prepends the just-played track (de-duplicated, capped at MAX). */
    fun add(context: Context, track: Track) {
        val list = (listOf(track) + all(context)).distinctBy { it.id }.take(MAX)
        saveAll(context, list)
    }

    /** Initial seed, e.g. from Spotify's server-side history. */
    fun saveAll(context: Context, list: List<Track>) {
        prefs(context).edit().putString(KEY, json.encodeToString(list.take(MAX))).apply()
    }
}
