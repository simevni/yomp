package com.plocoli.yomp.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

/** 태블릿에 저장된 음원 파일 하나 */
data class LocalTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val uri: Uri,
    val durationMs: Long,
)

/** MediaStore 로 기기 내 오디오 파일 목록을 조회 */
object LocalAudio {
    fun query(context: Context): List<LocalTrack> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val result = mutableListOf<LocalTrack>()
        context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                result.add(
                    LocalTrack(
                        id = id,
                        title = c.getString(titleCol) ?: "제목 없음",
                        artist = c.getString(artistCol)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "",
                        uri = ContentUris.withAppendedId(collection, id),
                        durationMs = c.getLong(durCol),
                    )
                )
            }
        }
        return result
    }
}
