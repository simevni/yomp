package com.plocoli.yomp.data

import android.content.Context

/**
 * Set of Spotify track IDs a parent has "hidden" (stored locally).
 * These IDs are filtered out of every rail, search, and deep-dive.
 */
object BlockStore {
    private const val PREFS = "yomp_blocked"
    private const val KEY = "ids"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(context: Context): Set<String> =
        prefs(context).getStringSet(KEY, emptySet())?.toSet() ?: emptySet()

    fun add(context: Context, id: String) {
        val updated = all(context).toMutableSet().apply { add(id) }
        prefs(context).edit().putStringSet(KEY, updated).apply()
    }
}
