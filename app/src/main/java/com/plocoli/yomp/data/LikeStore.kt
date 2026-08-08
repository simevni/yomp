package com.plocoli.yomp.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 인앱 "좋아요" 로컬 저장소 (SharedPreferences 에 Track 목록 JSON).
 * Spotify 라이브러리 저장(PUT /me/tracks)은 2026-02 개발 모드에서 403(폐지)이라, 기기 로컬로 관리한다.
 * 아이 전용 기기라 로컬 저장이 오히려 자연스럽다.
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

    /** 좋아요 토글. 반환값 = 토글 후 좋아요 상태(true=좋아요됨). 최근 좋아요가 앞에 오도록 저장. */
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
