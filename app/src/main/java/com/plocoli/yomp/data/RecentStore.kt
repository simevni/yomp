package com.plocoli.yomp.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * "최근 들은 노래" 로컬 기록. 곡을 재생하는 순간 바로 맨 앞에 추가 → 홈에 즉시 반영.
 * (Spotify 서버 recently-played 는 반영 딜레이가 있고 앱 복귀 때만 가져와서, 로컬로 즉시 관리)
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

    /** 방금 재생한 곡을 맨 앞에 추가 (중복 제거, 최대 MAX개) */
    fun add(context: Context, track: Track) {
        val list = (listOf(track) + all(context)).distinctBy { it.id }.take(MAX)
        saveAll(context, list)
    }

    /** Spotify 서버 기록 등으로 초기 시드 */
    fun saveAll(context: Context, list: List<Track>) {
        prefs(context).edit().putString(KEY, json.encodeToString(list.take(MAX))).apply()
    }
}
