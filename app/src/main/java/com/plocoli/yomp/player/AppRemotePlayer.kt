package com.plocoli.yomp.player

import android.content.Context
import android.os.SystemClock
import com.plocoli.yomp.BuildConfig
import com.plocoli.yomp.data.Track
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Real playback: connects to the installed Spotify app via App Remote and controls audio.
 * (Audio only — there is no video code path.)
 * The displayed metadata (title/artist/artwork) comes from the Track the user tapped
 * (Web API, https artwork); play/pause and repeat state are synced by subscribing to
 * App Remote's playerState.
 */
class AppRemotePlayer(private val appContext: Context) : Player {

    private val _state = MutableStateFlow<NowPlaying?>(null)
    override val state: StateFlow<NowPlaying?> = _state.asStateFlow()

    private val _repeat = MutableStateFlow(RepeatMode.OFF)
    override val repeatMode: StateFlow<RepeatMode> = _repeat.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    override val positionMs: StateFlow<Long> = _positionMs.asStateFlow()
    private val _durationMs = MutableStateFlow(0L)
    override val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    // playerState events are infrequent, so interpolate from the last position + elapsed time for a smooth tick.
    private var lastPos = 0L
    private var lastTickAt = SystemClock.elapsedRealtime()
    private var paused = true
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        scope.launch {
            while (true) {
                if (!paused) {
                    val elapsed = SystemClock.elapsedRealtime() - lastTickAt
                    val dur = _durationMs.value
                    _positionMs.value = (lastPos + elapsed).let { if (dur > 0) it.coerceAtMost(dur) else it }
                }
                delay(500)
            }
        }
    }

    /** Errors to surface to the user, e.g. connection failures. */
    var onError: ((String) -> Unit)? = null

    private var remote: SpotifyAppRemote? = null
    private var repeatInitialized = false   // repeat is forced off once, on the first connection

    private val params = ConnectionParams.Builder(BuildConfig.SPOTIFY_CLIENT_ID)
        .setRedirectUri(BuildConfig.SPOTIFY_REDIRECT_URI)
        .showAuthView(true)
        .build()

    private fun ensureConnected(onReady: (SpotifyAppRemote) -> Unit) {
        val r = remote
        if (r != null && r.isConnected) {
            onReady(r); return
        }
        SpotifyAppRemote.connect(appContext, params, object : Connector.ConnectionListener {
            override fun onConnected(spotifyAppRemote: SpotifyAppRemote) {
                remote = spotifyAppRemote
                subscribe(spotifyAppRemote)
                // Start every launch un-repeated. Spotify remembers the repeat mode across sessions,
                // so a repeat switched on days ago would keep looping one song with no visible cause.
                // The toggle still works — this only sets the starting state, once per launch.
                if (!repeatInitialized) {
                    repeatInitialized = true
                    spotifyAppRemote.playerApi.setRepeat(0)
                    _repeat.value = RepeatMode.OFF
                }
                onReady(spotifyAppRemote)
            }
            override fun onFailure(error: Throwable) {
                onError?.invoke("Spotify 연결에 실패했어요: ${error.message}")   // user-facing: "Failed to connect to Spotify"
            }
        })
    }

    private fun subscribe(r: SpotifyAppRemote) {
        r.playerApi.subscribeToPlayerState().setEventCallback { ps ->
            _state.value = _state.value?.copy(isPlaying = !ps.isPaused)
            _repeat.value = when (ps.playbackOptions.repeatMode) {
                1 -> RepeatMode.ONE   // track
                2 -> RepeatMode.ALL   // context
                else -> RepeatMode.OFF
            }
            // Sync position/length for the progress bar (the ticker interpolates from these).
            paused = ps.isPaused
            lastPos = ps.playbackPosition
            lastTickAt = SystemClock.elapsedRealtime()
            _durationMs.value = ps.track?.duration ?: 0L
            _positionMs.value = ps.playbackPosition
        }
    }

    override suspend fun play(track: Track) {
        _state.value = NowPlaying(track, isPlaying = true)   // update the display immediately
        ensureConnected { it.playerApi.play(track.uri) }
    }

    override fun toggle() {
        val r = remote ?: return
        r.playerApi.playerState.setResultCallback { ps ->
            if (ps.isPaused) r.playerApi.resume() else r.playerApi.pause()
        }
    }

    override fun pause() { remote?.playerApi?.pause() }
    override fun next() { remote?.playerApi?.skipNext() }
    override fun previous() { remote?.playerApi?.skipPrevious() }

    override fun seekTo(ms: Long) {
        remote?.playerApi?.seekTo(ms)
        lastPos = ms
        lastTickAt = SystemClock.elapsedRealtime()
        _positionMs.value = ms
    }

    override fun cycleRepeat() {
        val next = when (_repeat.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        val code = when (next) {
            RepeatMode.OFF -> 0
            RepeatMode.ONE -> 1
            RepeatMode.ALL -> 2
        }
        remote?.playerApi?.setRepeat(code)
        _repeat.value = next
    }

    override fun release() {
        scope.cancel()
        remote?.let { SpotifyAppRemote.disconnect(it) }
        remote = null
        _state.value = null
    }
}
