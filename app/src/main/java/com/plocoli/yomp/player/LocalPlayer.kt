package com.plocoli.yomp.player

import android.content.Context
import android.media.MediaPlayer
import com.plocoli.yomp.data.LocalTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Plays local audio files on the device (separate from Spotify App Remote).
 * Simple play/pause via android.media.MediaPlayer.
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
