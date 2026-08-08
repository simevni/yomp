package com.plocoli.yomp.data

import android.content.Context
import com.plocoli.yomp.Fav
import com.plocoli.yomp.Favorites
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persists the favorites list on the device (SharedPreferences). A parent can edit it
 * in-app, so changes take effect without a rebuild.
 * If nothing is stored yet, it seeds from Favorites.items (the in-code defaults).
 */
object FavoritesStore {
    private const val PREFS = "yomp_favorites"
    private const val KEY = "list"
    private val json = Json { ignoreUnknownKeys = true }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): List<Fav> {
        val raw = prefs(context).getString(KEY, null)
            ?: return Favorites.items.also { save(context, it) }   // first run: seed with defaults
        return runCatching { json.decodeFromString<List<Fav>>(raw) }
            .getOrNull()?.takeIf { it.isNotEmpty() }
            ?: Favorites.items
    }

    fun save(context: Context, list: List<Fav>) {
        prefs(context).edit().putString(KEY, json.encodeToString(list)).apply()
    }

    fun resetToDefault(context: Context): List<Fav> {
        save(context, Favorites.items)
        return Favorites.items
    }
}
