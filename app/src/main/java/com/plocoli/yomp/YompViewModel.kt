package com.plocoli.yomp

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.plocoli.yomp.auth.AuthManager
import com.plocoli.yomp.data.BlockStore
import com.plocoli.yomp.data.FavoritesStore
import com.plocoli.yomp.data.LikeStore
import com.plocoli.yomp.data.LocalAudio
import com.plocoli.yomp.data.LocalTrack
import com.plocoli.yomp.data.RecentStore
import com.plocoli.yomp.data.SettingsStore
import com.plocoli.yomp.data.SpotifyApi
import com.plocoli.yomp.data.Track
import com.plocoli.yomp.player.AppRemotePlayer
import com.plocoli.yomp.player.LocalPlayer
import com.plocoli.yomp.player.Player
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Kind of deep-dive opened when a rail title is tapped. */
sealed interface DeepDive {
    data class Artist(val query: String) : DeepDive   // paginated artist search
    data object Recent : DeepDive                      // more recently-played (up to 50)
}

/** One horizontal rail on the Home screen. If deepDive != null, tapping the title opens a deep-dive. */
data class BrowseSection(val title: String, val tracks: List<Track>, val deepDive: DeepDive? = null)

class YompViewModel(app: Application) : AndroidViewModel(app) {

    private val api = SpotifyApi { AuthManager.validAccessToken(getApplication()) }

    // Real playback: Spotify App Remote (requires the installed Spotify app). Emulators have no Spotify app, so connection fails there.
    private val appRemotePlayer = AppRemotePlayer(app)
    private val player: Player = appRemotePlayer

    // Local audio (files on the device) — a separate engine from Spotify.
    private val localPlayer = LocalPlayer(app)
    val localNowPlaying = localPlayer.current
    val localIsPlaying = localPlayer.isPlaying
    var localTracks by mutableStateOf<List<LocalTrack>>(emptyList())
        private set

    val authState = AuthManager.state
    val authError = AuthManager.error
    val nowPlaying = player.state
    val repeatMode = player.repeatMode
    val positionMs = player.positionMs
    val durationMs = player.durationMs

    var searchResults by mutableStateOf<List<Track>>(emptyList())
        private set
    var isSearching by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)

    var homeSections by mutableStateOf<List<BrowseSection>>(emptyList())
        private set
    var isLoadingHome by mutableStateOf(false)
        private set

    /** Like state of the currently playing track (heart in the full-screen player). */
    var isCurrentLiked by mutableStateOf(false)
        private set

    // Artist deep-dive (tap a rail title → several pages of that artist's tracks).
    var artistTitle by mutableStateOf<String?>(null)   // null = closed
        private set
    var artistTracks by mutableStateOf<List<Track>>(emptyList())
        private set
    var isLoadingArtist by mutableStateOf(false)
        private set

    // Parent gate: the pencil opens a PIN prompt; a correct PIN reveals the parent panel
    // (favorites editor, kiosk unlock, PIN change). Keeps a child out of parent settings.
    var showParent by mutableStateOf(false)
        private set
    var parentAuthed by mutableStateOf(false)
        private set

    // Favorites editing (a parent adds/removes in-app → applied without a rebuild).
    var showFavEditor by mutableStateOf(false)
        private set
    var favEditList by mutableStateOf<List<Fav>>(emptyList())
        private set

    // Hiding a song (long-press to hide → excluded from every rail).
    var blockedIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var blockCandidate by mutableStateOf<Track?>(null)   // non-null shows the "hide this?" dialog
        private set

    // Favorites are cached once a non-empty load succeeds; recent/liked are reloaded on every refresh.
    private var favSections: List<BrowseSection> = emptyList()
    private var recentSection: BrowseSection? = null
    private var likedSection: BrowseSection? = null
    private var homeLoading = false

    init {
        appRemotePlayer.onError = { msg -> errorMessage = msg }
        blockedIds = BlockStore.all(getApplication())
        AuthManager.init(getApplication())
    }

    fun askBlock(track: Track) { blockCandidate = track }
    fun cancelBlock() { blockCandidate = null }
    fun confirmBlock() {
        blockCandidate?.let {
            BlockStore.add(getApplication(), it.id)
            blockedIds = BlockStore.all(getApplication())
        }
        blockCandidate = null
    }

    /** Called on Home entry/resume: loads/retries favorites (if empty) + refreshes recent/liked. */
    fun loadHome() {
        if (homeLoading) return
        viewModelScope.launch {
            homeLoading = true
            try {
                // Right after a cold boot, Wi-Fi may not be up yet and every search can fail, so retry until one succeeds.
                if (favSections.isEmpty()) {
                    isLoadingHome = homeSections.isEmpty()
                    val favs = FavoritesStore.load(getApplication())
                    var attempt = 0
                    while (favSections.isEmpty() && attempt < 6) {
                        if (attempt > 0) delay(1500)
                        favSections = coroutineScope {
                            favs.map { fav ->
                                fav to async { runCatching { api.searchTracks(fav.query) }.getOrNull().orEmpty() }
                            }.mapNotNull { (fav, d) ->
                                d.await().takeIf { it.isNotEmpty() }?.let { BrowseSection(fav.title, it, DeepDive.Artist(fav.query)) }
                            }
                        }
                        rebuildHome()
                        attempt++
                    }
                }
                refreshDynamic()
            } finally {
                isLoadingHome = false
                homeLoading = false
            }
        }
    }

    /** Reloads only the recent/liked rails (after app resume or a like change). */
    private suspend fun refreshDynamic() {
        // Recent: prefer the local history (immediacy). If empty, seed once from Spotify's server history.
        var recent = RecentStore.all(getApplication())
        if (recent.isEmpty()) {
            recent = runCatching { api.recentlyPlayed() }.getOrNull()?.dedupById().orEmpty()
            if (recent.isNotEmpty()) RecentStore.saveAll(getApplication(), recent)
        }
        recentSection = recent.takeIf { it.isNotEmpty() }?.let { BrowseSection("최근 들은 노래", it, DeepDive.Recent) }
        likedSection = LikeStore.all(getApplication()).takeIf { it.isNotEmpty() }?.let { BrowseSection("좋아요 한 노래", it) }
        rebuildHome()
    }

    private fun rebuildHome() {
        homeSections = buildList {
            // Personalized rails (recent/liked) first, then the parent-curated favorites.
            recentSection?.let(::add)
            likedSection?.let(::add)
            addAll(favSections)
        }
    }

    private fun List<Track>.dedupById(): List<Track> = distinctBy { it.id }

    fun login(context: Context) = AuthManager.launchLogin(context)
    fun logout() = AuthManager.logout(getApplication())

    fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty()) {
            searchResults = emptyList()
            return
        }
        viewModelScope.launch {
            isSearching = true
            try {
                searchResults = api.searchTracks(q)
            } catch (e: Exception) {
                errorMessage = e.message ?: "검색 중 문제가 생겼어요."   // user-facing: "Something went wrong while searching."
            } finally {
                isSearching = false
            }
        }
    }

    fun play(track: Track) {
        localPlayer.stop()   // stop local playback (avoid playing alongside Spotify)
        isCurrentLiked = LikeStore.isLiked(getApplication(), track.id)
        // Add to the recent history immediately so Home reflects it right away.
        RecentStore.add(getApplication(), track)
        recentSection = RecentStore.all(getApplication()).takeIf { it.isNotEmpty() }
            ?.let { BrowseSection("최근 들은 노래", it, DeepDive.Recent) }
        rebuildHome()
        viewModelScope.launch { player.play(track) }
    }

    // --- Local audio ---
    fun loadLocal() {
        localTracks = runCatching { LocalAudio.query(getApplication()) }.getOrDefault(emptyList())
    }

    fun playLocal(track: LocalTrack) {
        player.pause()   // pause Spotify (avoid simultaneous playback)
        localPlayer.play(track)
    }

    fun toggleLocal() = localPlayer.toggle()

    /** Toggles the current track's like (stored locally) → refreshes the "liked" rail immediately. */
    fun toggleLike() {
        val track = nowPlaying.value?.track ?: return
        isCurrentLiked = LikeStore.toggle(getApplication(), track)
        likedSection = LikeStore.all(getApplication()).takeIf { it.isNotEmpty() }
            ?.let { BrowseSection("좋아요 한 노래", it) }
        rebuildHome()
    }

    /** Refresh on app resume (loadHome handles loading favorites if empty + refreshing recent/liked). */
    fun refresh() = loadHome()

    /** Opens a deep-dive: artist = paginated search; recent = 50 recently-played tracks. */
    fun openDeepDive(title: String, dive: DeepDive) {
        artistTitle = title
        artistTracks = emptyList()
        isLoadingArtist = true
        viewModelScope.launch {
            artistTracks = when (dive) {
                is DeepDive.Artist -> runCatching { api.searchTracksDeep(dive.query, pages = 5) }.getOrDefault(emptyList())
                DeepDive.Recent -> runCatching { api.recentlyPlayed(limit = 50) }.getOrNull()?.distinctBy { it.id }.orEmpty()
            }
            isLoadingArtist = false
        }
    }

    fun closeArtist() {
        artistTitle = null
        artistTracks = emptyList()
    }

    // --- Parent gate ---
    fun openParent() { showParent = true }
    fun closeParent() { showParent = false; parentAuthed = false }

    /** Returns true if the PIN is correct (and unlocks the parent panel). */
    fun submitParentPin(pin: String): Boolean {
        val ok = SettingsStore.checkPin(getApplication(), pin)
        if (ok) parentAuthed = true
        return ok
    }

    fun changeParentPin(newPin: String) = SettingsStore.setPin(getApplication(), newPin)

    // --- Favorites editing ---
    fun openFavEditor() {
        favEditList = FavoritesStore.load(getApplication())
        showFavEditor = true
    }
    fun closeFavEditor() { showFavEditor = false }

    fun addFavorite(title: String, query: String) {
        val t = title.trim()
        val q = query.trim().ifEmpty { t }
        if (t.isEmpty()) return
        favEditList = favEditList + Fav(t, q)
        persistFavorites()
    }

    fun removeFavorite(index: Int) {
        favEditList = favEditList.filterIndexed { i, _ -> i != index }
        persistFavorites()
    }

    fun resetFavorites() {
        favEditList = FavoritesStore.resetToDefault(getApplication())
        reloadFavorites()
    }

    private fun persistFavorites() {
        FavoritesStore.save(getApplication(), favEditList)
        reloadFavorites()
    }

    private fun reloadFavorites() {
        favSections = emptyList()
        loadHome()
    }

    fun toggle() = player.toggle()
    fun next() = player.next()
    fun previous() = player.previous()
    fun cycleRepeat() = player.cycleRepeat()
    fun seekTo(ms: Long) = player.seekTo(ms)

    fun clearError() { errorMessage = null }

    override fun onCleared() {
        player.release()
        localPlayer.stop()
    }
}
