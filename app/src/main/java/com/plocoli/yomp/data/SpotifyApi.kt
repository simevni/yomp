package com.plocoli.yomp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Spotify Web API 호출 (검색 등). 오디오/영상은 안 다룸 — 메타데이터 전용.
 * accessTokenProvider 로 매 호출마다 유효 토큰을 받아 Authorization 헤더에 넣는다.
 */
class SpotifyApi(
    private val accessTokenProvider: suspend () -> String?,
) {
    private val http = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    // Spotify 개발 모드 앱은 검색 limit 상한이 10 (그 이상은 400 "Invalid limit"). offset 페이지네이션은 됨.
    suspend fun searchTracks(query: String, limit: Int = 10, offset: Int = 0): List<Track> = withContext(Dispatchers.IO) {
        val token = accessTokenProvider() ?: error("로그인이 필요해요.")
        val url = "https://api.spotify.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("type", "track")
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("offset", offset.toString())
            .build()
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()
        android.util.Log.d("YompApi", "GET $url (token len=${token.length})")
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                android.util.Log.e("YompApi", "검색 실패 ${resp.code}: $text")
                error("검색 실패 (${resp.code})")
            }
            json.decodeFromString<SearchResponse>(text).tracks?.items ?: emptyList()
        }
    }

    /** 아티스트 딥다이브: 여러 페이지(offset)를 병렬로 모아 중복 제거. limit 10 제약 우회. */
    suspend fun searchTracksDeep(query: String, pages: Int = 5): List<Track> = coroutineScope {
        (0 until pages)
            .map { p -> async { runCatching { searchTracks(query, limit = 10, offset = p * 10) }.getOrDefault(emptyList()) } }
            .flatMap { it.await() }
            .distinctBy { it.id }
    }

    /** 최근 들은 노래 (재생할수록 채워짐) */
    suspend fun recentlyPlayed(limit: Int = 10): List<Track> =
        savedTracks("https://api.spotify.com/v1/me/player/recently-played?limit=$limit")

    /** 좋아요 표시한 곡 (부모가 Spotify 에서 담아둠) */
    suspend fun likedTracks(limit: Int = 10): List<Track> =
        savedTracks("https://api.spotify.com/v1/me/tracks?limit=$limit")

    // 참고: 인앱 좋아요는 LikeStore(로컬)로 처리. Spotify PUT /me/tracks 는 개발 모드에서 403(폐지)이라 안 씀.

    private suspend fun savedTracks(url: String): List<Track> = withContext(Dispatchers.IO) {
        val token = accessTokenProvider() ?: return@withContext emptyList()
        val req = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                android.util.Log.e("YompApi", "조회 실패 ${resp.code} ($url): $text")
                return@withContext emptyList()
            }
            json.decodeFromString<SavedTracksResponse>(text).items.mapNotNull { it.track }
        }
    }
}
