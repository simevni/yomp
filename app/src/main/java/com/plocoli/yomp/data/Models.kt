package com.plocoli.yomp.data

import kotlinx.serialization.Serializable

// Spotify Web API search response (only the fields we need; the rest are ignored).

@Serializable
data class SearchResponse(val tracks: TrackPage? = null)

@Serializable
data class TrackPage(val items: List<Track> = emptyList())

/** Responses shaped like items[].track, e.g. /me/tracks and /me/player/recently-played. */
@Serializable
data class SavedTracksResponse(val items: List<TrackHolder> = emptyList())

@Serializable
data class TrackHolder(val track: Track? = null)

@Serializable
data class Track(
    val id: String,
    val name: String,
    val uri: String,
    val artists: List<Artist> = emptyList(),
    val album: Album? = null,
) {
    val artistName: String get() = artists.joinToString(", ") { it.name }
    /** Prefer the largest thumbnail; fall back to any available image. */
    val thumbnailUrl: String? get() = album?.images?.firstOrNull()?.url
}

@Serializable
data class Artist(val name: String)

@Serializable
data class Album(
    val name: String = "",
    val images: List<SpotifyImage> = emptyList(),
)

@Serializable
data class SpotifyImage(
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
)
