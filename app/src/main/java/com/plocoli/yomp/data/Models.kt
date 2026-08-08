package com.plocoli.yomp.data

import kotlinx.serialization.Serializable

// Spotify Web API 검색 응답 (필요한 필드만; 나머지는 무시)

@Serializable
data class SearchResponse(val tracks: TrackPage? = null)

@Serializable
data class TrackPage(val items: List<Track> = emptyList())

/** /me/tracks, /me/player/recently-played 처럼 items[].track 형태의 응답 */
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
    /** 큰 썸네일 우선, 없으면 아무 이미지나 */
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
