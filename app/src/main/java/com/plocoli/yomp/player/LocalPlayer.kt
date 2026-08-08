package com.plocoli.yomp.player

import android.content.Context
import android.media.MediaPlayer
import com.plocoli.yomp.data.LocalTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 기기 로컬 오디오 파일 재생 (Spotify App Remote 와 별개).
 * android.media.MediaPlayer 로 단순 재생/일시정지.
 */
class LocalPlayer(private val context: Context) {

    private val _current = MutableStateFlow<LocalTrack?>(null)
    val current: StateFlow<LocalTrack?> = _current.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private var mp: MediaPlayer? = null

    fun play(track: LocalTrack) {
        mp?.release()
        mp = MediaPlayer.create(context, track.uri)?.apply {
            setOnCompletionListener { _isPlaying.value = false }
            start()
        }
        _current.value = track
        _isPlaying.value = mp != null
    }

    fun toggle() {
        val player = mp ?: return
        if (player.isPlaying) {
            player.pause(); _isPlaying.value = false
        } else {
            player.start(); _isPlaying.value = true
        }
    }

    fun stop() {
        mp?.release()
        mp = null
        _current.value = null
        _isPlaying.value = false
    }
}
