package com.plocoli.yomp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.abs

@Serializable
private data class LrclibTrack(
    val id: Long? = null,
    val trackName: String = "",
    val artistName: String = "",
    val duration: Double? = null,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
)

/**
 * LRCLIB (https://lrclib.net) — a free, community-contributed synced-lyrics database.
 * No API key, no account, no rate limit; it only asks that clients identify themselves
 * with a User-Agent. Spotify has no public lyrics API (its lyrics are licensed from
 * Musixmatch for its own apps only), so this is what fills the gap.
 */
class LrclibSource(
    private val http: OkHttpClient = OkHttpClient(),
) : LyricsSource {

    override val name = "lrclib"

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun fetch(query: LyricsQuery): Lyrics? = withContext(Dispatchers.IO) {
        val title = TrackMatch.cleanTitle(query.title)
        val artist = TrackMatch.primaryArtist(query.artist)
        if (title.isBlank()) return@withContext null

        // 1) Exact lookup. Needs a duration that matches within a couple of seconds, so it only
        //    works when we know the track length — but when it hits, it is the right recording.
        if (query.durationMs > 0) {
            val exact = get(title, artist, query.album, query.durationMs / 1000)
            if (exact != null) return@withContext exact.toLyrics(name)
        }

        // 2) Otherwise search by name and pick the closest match.
        val results = search(title, artist)
        val best = results
            .filter { !it.plainLyrics.isNullOrBlank() || !it.syncedLyrics.isNullOrBlank() || it.instrumental }
            .minByOrNull { candidate ->
                val durPenalty = if (query.durationMs > 0 && candidate.duration != null) {
                    abs(candidate.duration * 1000 - query.durationMs)
                } else {
                    0.0
                }
                // Prefer synced lyrics: a 30s duration gap is still worth less than losing the karaoke sync.
                durPenalty + if (candidate.syncedLyrics.isNullOrBlank()) 30_000.0 else 0.0
            }
        best?.toLyrics(name)
    }

    private fun get(title: String, artist: String, album: String, durationSec: Long): LrclibTrack? {
        val url = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
            .apply { if (album.isNotBlank()) addQueryParameter("album_name", album) }
            .addQueryParameter("duration", durationSec.toString())
            .build()
        return call(url.toString())?.let { runCatching { json.decodeFromString<LrclibTrack>(it) }.getOrNull() }
    }

    private fun search(title: String, artist: String): List<LrclibTrack> {
        val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .apply { if (artist.isNotBlank()) addQueryParameter("artist_name", artist) }
            .build()
        val body = call(url.toString()) ?: return emptyList()
        return runCatching { json.decodeFromString<List<LrclibTrack>>(body) }.getOrDefault(emptyList())
    }

    /** Returns the response body, or null for 404 (no match) and any transport error. */
    private fun call(url: String): String? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        return runCatching {
            http.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        }.getOrNull()
    }

    private fun LrclibTrack.toLyrics(source: String): Lyrics? {
        val synced = syncedLyrics?.let { Lrc.parse(it) }.orEmpty()
        val plain = plainLyrics.orEmpty()
        if (!instrumental && synced.isEmpty() && plain.isBlank()) return null
        return Lyrics(plain = plain, lines = synced, instrumental = instrumental, source = source)
    }

    private companion object {
        // LRCLIB asks clients to identify themselves rather than send a generic agent.
        const val USER_AGENT = "Yomp/1.0 (https://github.com/simevni/yomp)"
    }
}
