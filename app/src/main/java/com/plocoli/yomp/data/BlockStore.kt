package com.plocoli.yomp.data

import android.content.Context

/**
 * 부모가 "숨긴" 곡들의 Spotify 트랙 ID 집합 (로컬 저장).
 * 모든 레일/검색/딥다이브에서 이 ID 들은 걸러진다.
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
