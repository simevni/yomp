package com.plocoli.yomp.data

import android.content.Context
import com.plocoli.yomp.Fav
import com.plocoli.yomp.Favorites
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 즐겨찾기 목록을 기기에 저장 (SharedPreferences). 부모가 앱 안에서 편집 → 재빌드 없이 반영.
 * 저장된 게 없으면 Favorites.items(코드 기본값)로 시드한다.
 */
object FavoritesStore {
    private const val PREFS = "yomp_favorites"
    private const val KEY = "list"
    private val json = Json { ignoreUnknownKeys = true }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): List<Fav> {
        val raw = prefs(context).getString(KEY, null)
            ?: return Favorites.items.also { save(context, it) }   // 최초: 기본값 시드
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
