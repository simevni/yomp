package com.plocoli.yomp.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Local store for in-app "likes" (a JSON list of Tracks in SharedPreferences).
 * Saving to the Spotify library (PUT /me/tracks) returns 403 in development mode
 * (deprecated as of 2026-02), so likes are kept on the device instead.
 * On a dedicated kids' device, local storage is a natural fit anyway.
 */
object LikeStore {
    private const val PREFS = "yomp_likes"
    private const val KEY = "liked"
    private val json = Json { ignoreUnknownKeys = true }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(context: Context): List<Track> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<Track>>(raw) }.getOrDefault(emptyList())
    }

    fun isLiked(context: Context, id: String): Boolean = all(context).any { it.id == id }

    /** Toggles a like. Returns the resulting state (true = now liked). Newest likes are stored first. */
    fun toggle(context: Context, track: Track): Boolean {
        val current = all(context).toMutableList()
        val idx = current.indexOfFirst { it.id == track.id }
        val nowLiked = if (idx >= 0) {
            current.removeAt(idx); false
        } else {
            current.add(0, track); true
        }
        prefs(context).edit().putString(KEY, json.encodeToString(current)).apply()
        return nowLiked
    }
}
