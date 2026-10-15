package com.plocoli.yomp.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * What a parent has blocked (stored locally): single songs, whole albums, singers, and words.
 * [filter] turns it into the [ContentFilter] applied to every rail, search, and deep-dive.
 */
object BlockStore {
    private const val PREFS = "yomp_blocked"
    private const val KEY = "ids"            // track IDs (name kept from before albums/singers existed)
    private const val KEY_ALBUMS = "albums"  // JSON map: album ID → album name, for the parent's list
    private const val KEY_ARTISTS = "artists"
    private const val KEY_WORDS = "words"
    private val json = Json { ignoreUnknownKeys = true }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun filter(context: Context) = ContentFilter(
        trackIds = all(context),
        albumIds = albums(context).keys,
        artists = artists(context),
        words = words(context),
    )

    fun all(context: Context): Set<String> =
        prefs(context).getStringSet(KEY, emptySet())?.toSet() ?: emptySet()

    fun add(context: Context, id: String) = editSet(context, KEY) { add(id) }

    /** Un-hides every single song. Single songs are stored by ID only, so they can't be listed one by one. */
    fun clearTracks(context: Context) {
        prefs(context).edit().remove(KEY).apply()
    }

    fun albums(context: Context): Map<String, String> {
        val raw = prefs(context).getString(KEY_ALBUMS, null) ?: return emptyMap()
        return runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())
    }

    fun addAlbum(context: Context, id: String, name: String) = saveAlbums(context, albums(context) + (id to name))
    fun removeAlbum(context: Context, id: String) = saveAlbums(context, albums(context) - id)

    private fun saveAlbums(context: Context, map: Map<String, String>) {
        prefs(context).edit().putString(KEY_ALBUMS, json.encodeToString(map)).apply()
    }

    fun artists(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_ARTISTS, emptySet())?.toSet() ?: emptySet()

    fun addArtist(context: Context, name: String) = editSet(context, KEY_ARTISTS) {
        if (none { it.equals(name, ignoreCase = true) }) add(name)
    }

    fun removeArtist(context: Context, name: String) = editSet(context, KEY_ARTISTS) { remove(name) }

    fun words(context: Context): List<String> =
        prefs(context).getStringSet(KEY_WORDS, emptySet())?.sorted() ?: emptyList()

    fun addWord(context: Context, word: String) = editSet(context, KEY_WORDS) {
        if (none { it.equals(word, ignoreCase = true) }) add(word)
    }

    fun removeWord(context: Context, word: String) = editSet(context, KEY_WORDS) { remove(word) }

    private fun editSet(context: Context, key: String, change: MutableSet<String>.() -> Unit) {
        // Copy: the set getStringSet returns must not be modified.
        val updated = (prefs(context).getStringSet(key, emptySet()) ?: emptySet()).toMutableSet().apply(change)
        prefs(context).edit().putStringSet(key, updated).apply()
    }
}
