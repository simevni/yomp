package com.plocoli.yomp.data

/**
 * What a parent has blocked, and the rule that decides whether a track gets through.
 *
 * Everything is allowed unless a parent blocked it — the explicit flag is deliberately not a
 * criterion. Plenty of songs a child loves are marked explicit, and plenty that cross the line
 * are not, so the parent draws the line by blocking specific songs, albums, singers or words.
 *
 * Pure Kotlin (no Android types) so the matching rules can be unit tested.
 */
data class ContentFilter(
    val trackIds: Set<String> = emptySet(),
    val albumIds: Set<String> = emptySet(),
    /** Singer names as entered; compared case-insensitively. */
    val artists: Set<String> = emptySet(),
    val words: List<String> = emptyList(),
) {
    private val artistKeys = artists.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
    private val wordMatchers = words.mapNotNull(::matcherFor)

    fun allows(track: Track): Boolean {
        if (track.id in trackIds) return false
        val albumId = track.album?.id.orEmpty()
        if (albumId.isNotEmpty() && albumId in albumIds) return false
        // Any credited artist counts, so a featured appearance is blocked too.
        if (track.artists.any { it.name.trim().lowercase() in artistKeys }) return false
        val text = listOf(track.name, track.album?.name.orEmpty(), track.artistName).joinToString("\n")
        return matchedWord(text) == null
    }

    /** A search for a blocked word shows nothing — a child often types a phrase heard somewhere else. */
    fun allowsQuery(query: String): Boolean = matchedWord(query) == null

    fun matchedWord(text: String): String? {
        val normalized = text.lowercase()
        return wordMatchers.firstOrNull { (_, re) -> re.containsMatchIn(normalized) }?.first
    }

    private companion object {
        private val LATIN = Regex("^[a-z0-9 '\\-]+$")

        /**
         * Latin words match as whole words (plus a plural ending), so "rock" catches "rocks" but
         * not "rocket". Other scripts (Korean, Japanese) have no reliable word boundary, so they
         * match anywhere.
         */
        fun matcherFor(word: String): Pair<String, Regex>? {
            val w = word.trim().lowercase().replace(Regex("\\s+"), " ")
            if (w.isEmpty()) return null
            val re = if (LATIN.matches(w)) {
                val body = w.split(' ').joinToString("\\s+") { Regex.escape(it) }
                Regex("(?<![a-z0-9])$body(?:s|es)?(?![a-z0-9])")
            } else {
                Regex(Regex.escape(w))
            }
            return word.trim() to re
        }
    }
}

/** Spotify album links as a parent would paste them from the Spotify app's "Share" menu. */
object SpotifyLinks {
    private val ALBUM = Regex("(?:open\\.spotify\\.com/(?:intl-[a-zA-Z-]+/)?album/|spotify:album:)([A-Za-z0-9]{22})")

    fun albumId(input: String): String? = ALBUM.find(input)?.groupValues?.get(1)
}
