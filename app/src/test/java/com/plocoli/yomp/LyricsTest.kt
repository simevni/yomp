package com.plocoli.yomp

import com.plocoli.yomp.data.Lrc
import com.plocoli.yomp.data.Lyrics
import com.plocoli.yomp.data.TrackMatch
import org.junit.Assert.assertEquals
import org.junit.Test

class LrcParseTest {

    /** The shape LRCLIB returns: `[mm:ss.xx]` with a space before the text. */
    @Test
    fun `parses timestamps into milliseconds`() {
        val lines = Lrc.parse(
            """
            [00:07.78] first
            [01:02.50] second
            [10:00.05] third
            """.trimIndent()
        )
        assertEquals(3, lines.size)
        assertEquals(7_780L, lines[0].timeMs)
        assertEquals("first", lines[0].text)
        assertEquals(62_500L, lines[1].timeMs)
        assertEquals(600_050L, lines[2].timeMs)
    }

    @Test
    fun `handles tenths and milliseconds and a missing fraction`() {
        val lines = Lrc.parse("[00:01.5] a\n[00:02.345] b\n[00:03] c")
        assertEquals(listOf(1_500L, 2_345L, 3_000L), lines.map { it.timeMs })
    }

    @Test
    fun `skips metadata tags and keeps blank lines as gaps`() {
        val lines = Lrc.parse("[ar:Someone]\n[ti:A Song]\n[00:05.00]\n[00:06.00] words")
        assertEquals(2, lines.size)
        assertEquals("", lines[0].text)
        assertEquals("words", lines[1].text)
    }

    /** One text line can carry several timestamps (a repeated chorus). */
    @Test
    fun `expands repeated timestamps and sorts by time`() {
        val lines = Lrc.parse("[00:30.00][00:10.00] chorus")
        assertEquals(listOf(10_000L, 30_000L), lines.map { it.timeMs })
        assertEquals(listOf("chorus", "chorus"), lines.map { it.text })
    }
}

class LyricsIndexTest {

    private val lyrics = Lyrics(
        lines = Lrc.parse("[00:05.00] one\n[00:10.00] two\n[00:15.00] three")
    )

    @Test
    fun `highlights nothing before the first line`() {
        assertEquals(-1, lyrics.indexAt(0))
        assertEquals(-1, lyrics.indexAt(4_999))
    }

    @Test
    fun `highlights the line that has started`() {
        assertEquals(0, lyrics.indexAt(5_000))
        assertEquals(0, lyrics.indexAt(9_999))
        assertEquals(1, lyrics.indexAt(10_000))
        assertEquals(2, lyrics.indexAt(15_001))
    }

    @Test
    fun `stays on the last line past the end`() {
        assertEquals(2, lyrics.indexAt(9_999_999))
    }

    @Test
    fun `empty lyrics never highlight`() {
        assertEquals(-1, Lyrics().indexAt(1_000))
    }
}

class TrackMatchTest {

    /**
     * Spotify decorates titles; searching a lyrics database with the decorated form
     * returns zero hits, so this cleanup is what makes lookups land.
     */
    @Test
    fun `strips brackets and dash suffixes`() {
        assertEquals("Golden", TrackMatch.cleanTitle("Golden (feat. Someone)"))
        assertEquals("Golden", TrackMatch.cleanTitle("Golden - From \"A Movie\""))
        assertEquals("Let It Go", TrackMatch.cleanTitle("Let It Go [Remastered 2020]"))
        assertEquals("Song", TrackMatch.cleanTitle("Song (Live) - 2011 Remaster"))
    }

    @Test
    fun `never returns an empty title`() {
        assertEquals("(Instrumental)", TrackMatch.cleanTitle("(Instrumental)"))
        assertEquals("にじ", TrackMatch.cleanTitle("にじ"))
    }

    @Test
    fun `takes the first credited artist`() {
        assertEquals("IVE", TrackMatch.primaryArtist("IVE, Someone Else"))
        assertEquals("HUNTR/X", TrackMatch.primaryArtist("HUNTR/X & Another"))
        assertEquals("BLACKPINK", TrackMatch.primaryArtist("BLACKPINK"))
    }

    /** File-name matching ignores case, spacing and punctuation, and keeps CJK intact. */
    @Test
    fun `key normalizes for filename matching`() {
        assertEquals(TrackMatch.key("Kero Ponzu", "Song!"), TrackMatch.key("keroponzu", " song "))
        assertEquals(TrackMatch.key("ケロポンズ"), TrackMatch.key("ケロポンズ "))
        assertEquals(TrackMatch.key("아이브"), TrackMatch.key("아이브"))
    }
}
