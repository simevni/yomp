package com.plocoli.yomp.data

import android.content.Context

/**
 * Resolves lyrics through a fallback chain:
 *
 *   1. **Local files** — always checked first, and never cached, so a .lrc a parent drops in
 *      today takes effect on the very next play (and overrides a wrong database match).
 *   2. **Cache** — including remembered misses, so replays stay offline and quiet.
 *   3. **Network sources** — LRCLIB today; add another [LyricsSource] to the list and nothing
 *      else in the app has to change.
 *
 * Returns null when nothing has lyrics for this track.
 */
class LyricsRepository(
    private val context: Context,
    private val localSource: LyricsSource = LocalLyricsSource(context),
    private val remoteSources: List<LyricsSource> = listOf(LrclibSource()),
) {
    suspend fun load(query: LyricsQuery): Lyrics? {
        runCatching { localSource.fetch(query) }.getOrNull()?.let { return it }

        LyricsStore.get(context, query.trackId)?.let { return it.getOrNull() }

        val fetched = remoteSources.firstNotNullOfOrNull { source ->
            runCatching { source.fetch(query) }.getOrNull()?.takeIf { !it.isBlank || it.instrumental }
        }
        LyricsStore.put(context, query.trackId, fetched)
        return fetched
    }
}
