package com.plocoli.yomp.player

import com.plocoli.yomp.data.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class NowPlaying(
    val track: Track,
    val isPlaying: Boolean,
)

enum class RepeatMode { OFF, ALL, ONE }

/**
 * Playback-engine abstraction. Actual audio is handled by the Spotify App Remote
 * implementation (requires a real device). Audio only — there is no video concept.
 * NoopPlayer is a silent stub for UI/flow development.
 */
interface Player {
    val state: StateFlow<NowPlaying?>
    val repeatMode: StateFlow<RepeatMode>
    val positionMs: StateFlow<Long>   // current playback position
    val durationMs: StateFlow<Long>   // track length
    suspend fun play(track: Track)
    fun toggle()
    fun pause()
    fun next()
    fun previous()
    fun cycleRepeat()
    fun seekTo(ms: Long)
    fun release()
}

/** Silent stub that only mimics state. For checking the UI on an emulator. */
class NoopPlayer : Player {
    private val _state = MutableStateFlow<NowPlaying?>(null)
    override val state: StateFlow<NowPlaying?> = _state

    private val _repeat = MutableStateFlow(RepeatMode.OFF)
    override val repeatMode: StateFlow<RepeatMode> = _repeat

    override val positionMs: StateFlow<Long> = MutableStateFlow(0L)
    override val durationMs: StateFlow<Long> = MutableStateFlow(0L)
    override fun seekTo(ms: Long) { /* stub */ }

    override suspend fun play(track: Track) {
        _state.value = NowPlaying(track, isPlaying = true)
    }

    override fun toggle() {
        _state.value = _state.value?.let { it.copy(isPlaying = !it.isPlaying) }
    }

    override fun pause() { _state.value = _state.value?.copy(isPlaying = false) }

    override fun next() { /* stub: no queue */ }
    override fun previous() { /* stub: no queue */ }

    override fun cycleRepeat() {
        _repeat.value = when (_repeat.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    override fun release() { _state.value = null }
}
