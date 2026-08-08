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
 * 재생 엔진 추상화. 실제 소리는 Spotify App Remote 구현이 담당한다(태블릿 필요).
 * 지금은 UI/흐름 개발용 무음 스텁(NoopPlayer)만 있음. 오디오 전용 — 영상 개념 없음.
 */
interface Player {
    val state: StateFlow<NowPlaying?>
    val repeatMode: StateFlow<RepeatMode>
    val positionMs: StateFlow<Long>   // 현재 재생 위치
    val durationMs: StateFlow<Long>   // 곡 길이
    suspend fun play(track: Track)
    fun toggle()
    fun pause()
    fun next()
    fun previous()
    fun cycleRepeat()
    fun seekTo(ms: Long)
    fun release()
}

/** 소리 없이 상태만 흉내내는 스텁. 에뮬레이터에서 UI 확인용. */
class NoopPlayer : Player {
    private val _state = MutableStateFlow<NowPlaying?>(null)
    override val state: StateFlow<NowPlaying?> = _state

    private val _repeat = MutableStateFlow(RepeatMode.OFF)
    override val repeatMode: StateFlow<RepeatMode> = _repeat

    override val positionMs: StateFlow<Long> = MutableStateFlow(0L)
    override val durationMs: StateFlow<Long> = MutableStateFlow(0L)
    override fun seekTo(ms: Long) { /* 스텁 */ }

    override suspend fun play(track: Track) {
        _state.value = NowPlaying(track, isPlaying = true)
    }

    override fun toggle() {
        _state.value = _state.value?.let { it.copy(isPlaying = !it.isPlaying) }
    }

    override fun pause() { _state.value = _state.value?.copy(isPlaying = false) }

    override fun next() { /* 스텁: 큐 없음 */ }
    override fun previous() { /* 스텁: 큐 없음 */ }

    override fun cycleRepeat() {
        _repeat.value = when (_repeat.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    override fun release() { _state.value = null }
}
