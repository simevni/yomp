package com.plocoli.yomp.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Lyrics hand-placed on the device, in `Android/data/com.plocoli.yomp/files/lyrics/`.
 *
 * This is the escape hatch for songs no database has — Japanese children's songs, for
 * instance, are missing from LRCLIB *and* from the commercial databases, because they
 * are not chart music. A parent writes the .lrc once, drops it in over USB, and it wins
 * over anything fetched from the network.
 *
 * Matching is by file name, in this order:
 *   `<artist> - <title>.lrc`, `<title>.lrc`, or (local audio) `<audio file name>.lrc`.
 * Punctuation, spacing and case are ignored. `.txt` files are accepted as plain lyrics.
 */
class LocalLyricsSource(private val context: Context) : LyricsSource {

    override val name = "local"

    override suspend fun fetch(query: LyricsQuery): Lyrics? = withContext(Dispatchers.IO) {
        val dir = dir() ?: return@withContext null
        val files = dir.listFiles()?.filter { it.isFile && it.extension.lowercase() in EXTENSIONS }
        if (files.isNullOrEmpty()) return@withContext null

        val title = TrackMatch.cleanTitle(query.title)
        val artist = TrackMatch.primaryArtist(query.artist)
        val wanted = buildList {
            if (artist.isNotBlank()) add(TrackMatch.key(artist, title))
            add(TrackMatch.key(title))
            query.fileBaseName?.let { add(TrackMatch.key(it)) }
        }

        val match = wanted.firstNotNullOfOrNull { want ->
            files.firstOrNull { TrackMatch.key(it.nameWithoutExtension) == want }
        } ?: return@withContext null

        val raw = runCatching { match.readText() }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: return@withContext null
        val lines = Lrc.parse(raw)
        Lyrics(
            // Strip the timing tags so the plain view stays readable when there is no sync.
            plain = if (lines.isEmpty()) raw.trim() else lines.joinToString("\n") { it.text },
            lines = lines,
            source = match.name,
        )
    }

    /** Created on first use so the folder is visible over USB even before any file is added. */
    private fun dir(): File? {
        val base = context.getExternalFilesDir(null) ?: return null
        val dir = File(base, FOLDER)
        if (!dir.exists()) dir.mkdirs()
        return dir.takeIf { it.isDirectory }
    }

    companion object {
        const val FOLDER = "lyrics"
        private val EXTENSIONS = setOf("lrc", "txt")

        /** Shown in the parent panel so a parent knows where to copy files. */
        fun folderHint(context: Context): String =
            File(context.getExternalFilesDir(null), FOLDER).absolutePath
    }
}
