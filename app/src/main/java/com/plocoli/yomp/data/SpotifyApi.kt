package com.plocoli.yomp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Spotify answered 429: too many requests. [retryAfterSeconds] comes from the Retry-After header. */
class RateLimitedException(val retryAfterSeconds: Long) : Exception("rate limited (retry after ${retryAfterSeconds}s)")

private const val DEFAULT_RETRY_WAIT_SECONDS = 3L
private const val MAX_RETRY_WAIT_SECONDS = 20L

/**
 * Spotify Web API calls (search, etc.). Metadata only — never touches audio or video.
 * accessTokenProvider supplies a valid token per call for the Authorization header.
 */
class SpotifyApi(
    private val accessTokenProvider: suspend () -> String?,
) {
    private val http = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Caps how many searches are in flight at once.
     *
     * Home fires one search per favorite — over two dozen — and firing them all at once made
     * Spotify answer 429 QUOTA_EXCEEDED for most of them, which is what empties the favorites
     * rails. A development-mode app has a small quota, so the requests are queued instead.
     */
    private val searchGate = Semaphore(4)

    /**
     * When Spotify's 429 penalty runs out. Retry-After can be several minutes, and every request
     * sent inside that window is both refused and counted, which extends the penalty. So while it
     * lasts, requests are refused here without touching the network.
     */
    @Volatile
    private var rateLimitedUntilMs = 0L

    // Development-mode apps cap the search limit at 10 (higher → 400 "Invalid limit"). Offset pagination still works.
    suspend fun searchTracks(query: String, limit: Int = 10, offset: Int = 0): List<Track> =
        searchGate.withPermit {
            val cooldownMs = rateLimitedUntilMs - System.currentTimeMillis()
            if (cooldownMs > 0) throw RateLimitedException(cooldownMs / 1000)
            try {
                search(query, limit, offset)
            } catch (rate: RateLimitedException) {
                // 429 comes with a Retry-After. Waiting it out beats failing the rail — but only
                // if the wait is short; a long one means the quota is spent and retrying now would
                // just deepen the hole.
                val wait = rate.retryAfterSeconds
                if (wait !in 1..MAX_RETRY_WAIT_SECONDS) throw rate
                android.util.Log.w("YompApi", "rate limited, retrying in ${wait}s: $query")
                delay(wait * 1000)
                search(query, limit, offset)
            }
        }

    private suspend fun search(query: String, limit: Int, offset: Int): List<Track> = withContext(Dispatchers.IO) {
        val token = accessTokenProvider() ?: error("로그인이 필요해요.")   // user-facing: "Please sign in."
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
            if (resp.code == 429) {
                val retryAfter = resp.header("Retry-After")?.toLongOrNull() ?: DEFAULT_RETRY_WAIT_SECONDS
                rateLimitedUntilMs = System.currentTimeMillis() + retryAfter * 1000
                android.util.Log.e("YompApi", "search rate limited (retry after ${retryAfter}s): $query")
                throw RateLimitedException(retryAfter)
            }
            if (!resp.isSuccessful) {
                android.util.Log.e("YompApi", "search failed ${resp.code}: $text")
                error("검색 실패 (${resp.code})")   // user-facing: "Search failed (code)"
            }
            json.decodeFromString<SearchResponse>(text).tracks?.items ?: emptyList()
        }
    }

    /** Artist deep-dive: fetches several offset pages in parallel and de-duplicates. Works around the limit-of-10 cap. */
    suspend fun searchTracksDeep(query: String, pages: Int = 5): List<Track> = coroutineScope {
        (0 until pages)
            .map { p -> async { runCatching { searchTracks(query, limit = 10, offset = p * 10) }.getOrDefault(emptyList()) } }
            .flatMap { it.await() }
            .distinctBy { it.id }
    }

    /** Recently played tracks (fills up as songs are played). */
    suspend fun recentlyPlayed(limit: Int = 10): List<Track> =
        savedTracks("https://api.spotify.com/v1/me/player/recently-played?limit=$limit")

    /** Liked/saved tracks (whatever the parent saved in Spotify). */
    suspend fun likedTracks(limit: Int = 10): List<Track> =
        savedTracks("https://api.spotify.com/v1/me/tracks?limit=$limit")

    // Note: in-app likes are handled by LikeStore (local). Spotify PUT /me/tracks returns 403 in development mode (deprecated), so it's unused.

    private suspend fun savedTracks(url: String): List<Track> = withContext(Dispatchers.IO) {
        val token = accessTokenProvider() ?: return@withContext emptyList()
        val req = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                android.util.Log.e("YompApi", "request failed ${resp.code} ($url): $text")
                return@withContext emptyList()
            }
            json.decodeFromString<SavedTracksResponse>(text).items.mapNotNull { it.track }
        }
    }
}
