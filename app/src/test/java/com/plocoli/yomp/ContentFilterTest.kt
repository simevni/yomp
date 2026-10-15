package com.plocoli.yomp

import com.plocoli.yomp.data.Album
import com.plocoli.yomp.data.Artist
import com.plocoli.yomp.data.ContentFilter
import com.plocoli.yomp.data.SpotifyLinks
import com.plocoli.yomp.data.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentFilterTest {

    private fun track(
        name: String = "Song",
        artists: List<String> = listOf("Singer"),
        albumId: String = "album1",
        albumName: String = "Album",
        explicit: Boolean = false,
    ) = Track(
        id = "t-$name",
        name = name,
        uri = "spotify:track:x",
        artists = artists.map { Artist(it) },
        album = Album(id = albumId, name = albumName),
        explicit = explicit,
    )

    @Test
    fun `allows everything by default, explicit included`() {
        assertTrue(ContentFilter().allows(track(explicit = true)))
    }

    @Test
    fun `blocks by track, album and any credited artist`() {
        val f = ContentFilter(trackIds = setOf("t-A"), albumIds = setOf("bad"), artists = setOf("Bad Singer"))
        assertFalse(f.allows(track(name = "A")))
        assertFalse(f.allows(track(albumId = "bad")))
        assertFalse(f.allows(track(artists = listOf("Nice", "bad singer"))))   // featured, other case
        assertTrue(f.allows(track(name = "B")))
    }

    /** Old likes/recents have no album ID; an empty ID must not match anything. */
    @Test
    fun `empty album id never matches`() {
        assertTrue(ContentFilter(albumIds = setOf("")).allows(track(albumId = "")))
    }

    @Test
    fun `latin words match whole words and plurals only`() {
        val f = ContentFilter(words = listOf("rock"))
        assertFalse(f.allows(track(name = "Let's Rock Tonight")))
        assertFalse(f.allows(track(albumName = "Big Rocks")))
        assertTrue(f.allows(track(name = "Rocket Song")))
        assertTrue(f.allows(track(name = "Bedrock")))
    }

    @Test
    fun `phrases tolerate extra spaces`() {
        val f = ContentFilter(words = listOf("blue moon"))
        assertFalse(f.allowsQuery("BLUE   moon"))
        assertTrue(f.allowsQuery("blue sky"))
    }

    @Test
    fun `non-latin words match anywhere`() {
        val f = ContentFilter(words = listOf("호랑이"))
        assertFalse(f.allowsQuery("호랑이송"))
        assertEquals("호랑이", f.matchedWord("이건 호랑이춤"))
        assertNull(f.matchedWord("곰 세 마리"))
    }

    @Test
    fun `album links parse from share urls and uris`() {
        val id = "4aawyAB9vmqN3uQ7FjRGTy"
        assertEquals(id, SpotifyLinks.albumId("https://open.spotify.com/album/$id?si=abc123"))
        assertEquals(id, SpotifyLinks.albumId("https://open.spotify.com/intl-ko/album/$id"))
        assertEquals(id, SpotifyLinks.albumId("spotify:album:$id"))
        assertNull(SpotifyLinks.albumId("https://open.spotify.com/track/$id"))
        assertNull(SpotifyLinks.albumId("TWICE"))
    }

    @Test
    fun `fav key separates an album from a singer of the same name`() {
        assertEquals("IVE", Fav("IVE").key)
        assertEquals("album:x", Fav("IVE", albumId = "x").key)
    }
}
