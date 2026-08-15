package com.plocoli.yomp.data

import kotlinx.serialization.Serializable

/** One line of synced lyrics: the moment it starts, and the text to show. */
@Serializable
data class LyricLine(val timeMs: Long, val text: String)

/**
 * Lyrics for a single track.
 * [lines] is non-empty when synced (karaoke) lyrics exist; otherwise only [plain] is available.
 */
@Serializable
data class Lyrics(
    val plain: String = "",
    val lines: List<LyricLine> = emptyList(),
    val instrumental: Boolean = false,
    /** Where these came from ("lrclib", a file name, …) — shown nowhere, kept for debugging. */
    val source: String = "",
) {
    val isSynced: Boolean get() = lines.isNotEmpty()
    val isBlank: Boolean get() = !instrumental && plain.isBlank() && lines.isEmpty()

    /**
     * Index of the line to highlight at [positionMs], or -1 before the first line starts.
     * Binary search — this runs on every progress tick (~500ms).
     */
    fun indexAt(positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var lo = 0
        var hi = lines.lastIndex
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (lines[mid].timeMs <= positionMs) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return found
    }
}

/** What we know about the track we want lyrics for. Every source takes this. */
data class LyricsQuery(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    /** File name (without extension) for local audio — lets a sidecar .lrc match by name. */
    val fileBaseName: String? = null,
)

/**
 * One place lyrics can come from. Sources are tried in order by [LyricsRepository];
 * returning null just means "not here, try the next one".
 */
interface LyricsSource {
    val name: String
    suspend fun fetch(query: LyricsQuery): Lyrics?
}

/** Parses the `[mm:ss.xx] text` LRC format. */
object Lrc {
    private val TIME_TAG = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    /**
     * Returns the lines sorted by time. Lines with no time tag (LRC metadata like `[ar:…]`) are skipped.
     * A tag with empty text is kept — it marks an instrumental gap, so the highlight clears there.
     */
    fun parse(raw: String): List<LyricLine> {
        val out = mutableListOf<LyricLine>()
        raw.lineSequence().forEach { line ->
            val tags = TIME_TAG.findAll(line).toList()
            if (tags.isEmpty()) return@forEach
            val text = line.substring(tags.last().range.last + 1).trim()
            tags.forEach { m ->
                val min = m.groupValues[1].toLongOrNull() ?: return@forEach
                val sec = m.groupValues[2].toLongOrNull() ?: return@forEach
                // Fractions are usually hundredths, but tenths and milliseconds both show up in the wild.
                val fracRaw = m.groupValues[3]
                val frac = when (fracRaw.length) {
                    1 -> fracRaw.toLong() * 100
                    2 -> fracRaw.toLong() * 10
                    3 -> fracRaw.toLong()
                    else -> 0L
                }
                out += LyricLine(min * 60_000 + sec * 1_000 + frac, text)
            }
        }
        return out.sortedBy { it.timeMs }
    }
}

/**
 * Title/artist cleanup before querying a lyrics database.
 * Spotify titles carry decorations ("(feat. …)", "- From \"Frozen\"") that make lyrics
 * lookups miss entirely — a search for the decorated title returns zero hits.
 */
object TrackMatch {
    private val BRACKETED = Regex("""\s*[(\[][^)\]]*[)\]]""")
    private val DASH_SUFFIX = Regex("""\s+-\s+.*$""")
    private val KEEP = Regex("""[^\p{L}\p{N}]""")

    /** "Golden (feat. X) - From KPop Demon Hunters" → "Golden" */
    fun cleanTitle(title: String): String {
        val noBrackets = title.replace(BRACKETED, "").trim()
        val noSuffix = noBrackets.replace(DASH_SUFFIX, "").trim()
        // Never return an empty title (e.g. a title that is entirely bracketed).
        return noSuffix.ifBlank { noBrackets.ifBlank { title.trim() } }
    }

    /** Spotify joins every credited artist; lyrics databases index the first one. */
    fun primaryArtist(artist: String): String =
        artist.substringBefore(",").substringBefore(" & ").trim()

    /** Loose key for filename matching: letters/digits only, lowercased (keeps Hangul/Kana/Kanji). */
    fun key(vararg parts: String): String =
        parts.joinToString("") { it }.lowercase().replace(KEEP, "")
}
