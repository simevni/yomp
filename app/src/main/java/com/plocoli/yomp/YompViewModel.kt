package com.plocoli.yomp

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.plocoli.yomp.auth.AuthManager
import com.plocoli.yomp.data.BlockStore
import com.plocoli.yomp.data.CachedRail
import com.plocoli.yomp.data.ContentFilter
import com.plocoli.yomp.data.FavoritesStore
import com.plocoli.yomp.data.HomeCacheStore
import com.plocoli.yomp.data.LikeStore
import com.plocoli.yomp.data.LocalAudio
import com.plocoli.yomp.data.LocalTrack
import com.plocoli.yomp.data.Lyrics
import com.plocoli.yomp.data.LyricsQuery
import com.plocoli.yomp.data.LyricsRepository
import com.plocoli.yomp.data.LyricsStore
import com.plocoli.yomp.data.RateLimitedException
import com.plocoli.yomp.data.RecentStore
import com.plocoli.yomp.data.SettingsStore
import com.plocoli.yomp.data.SpotifyApi
import com.plocoli.yomp.data.SpotifyLinks
import com.plocoli.yomp.data.Track
import com.plocoli.yomp.data.TrackMatch
import com.plocoli.yomp.player.AppRemotePlayer
import com.plocoli.yomp.player.LocalPlayer
import com.plocoli.yomp.player.Player
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Kind of deep-dive opened when a rail title is tapped. */
sealed interface DeepDive {
    data class Artist(val query: String) : DeepDive   // paginated artist search
    data class Album(val albumId: String) : DeepDive  // every track on the album
    data object Recent : DeepDive                      // more recently-played (up to 50)
}

/** How much a parent blocks from the long-press dialog. Album and singer need the parent PIN. */
enum class BlockScope { TRACK, ALBUM, ARTIST }

/**
 * One horizontal rail on the Home screen. If deepDive != null, tapping the title opens a deep-dive.
 * [fav] is set on favorites rails — the ones that can be taken off Home.
 */
data class BrowseSection(
    val title: String,
    val tracks: List<Track>,
    val deepDive: DeepDive? = null,
    val fav: Fav? = null,
)

private fun Fav.deepDive(): DeepDive = albumId?.let { DeepDive.Album(it) } ?: DeepDive.Artist(query)
private fun Fav.section(tracks: List<Track>) = BrowseSection(title, tracks, deepDive(), this)

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

    // Lyrics for the current track. A kid's feature (learning to read along), so it sits
    // outside the parent PIN gate — the child toggles it from the full-screen player.
    private val lyricsRepo = LyricsRepository(app)
    var lyrics by mutableStateOf<Lyrics?>(null)
        private set
    var isLoadingLyrics by mutableStateOf(false)
        private set
    var showLyrics by mutableStateOf(false)
        private set
    private var lyricsJob: Job? = null

    private val connectivity = app.getSystemService(ConnectivityManager::class.java)

    /**
     * Refills the Home rails as soon as the device gets back online.
     *
     * The favorites rails come from Spotify search, so a device that is offline at launch
     * shows only the local rails (recent/liked). [loadHome] retries for a few seconds and
     * then gives up until something calls it again — and in kiosk mode the app never leaves
     * the foreground, so ON_RESUME may not fire for hours. Without this, Wi-Fi coming back
     * would not repair the Home screen on its own. (2026-08-14: exactly what happened.)
     */
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            viewModelScope.launch { loadHome() }
        }
    }

    // Artist deep-dive (tap a rail title → several pages of that artist's tracks).
    var artistTitle by mutableStateOf<String?>(null)   // null = closed
        private set
    var artistTracks by mutableStateOf<List<Track>>(emptyList())
        private set
    var isLoadingArtist by mutableStateOf(false)
        private set
    var artistDive by mutableStateOf<DeepDive?>(null)   // what the open deep-dive shows (drives its star)
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

    // The child curates the same list directly — a star on a search result adds that artist
    // as a Home rail, a long-press on a rail title removes it. No PIN: the Search tab already
    // reaches all of Spotify, so this gate never controlled *what* could be played, only how
    // Home was arranged — which is the child's own screen.
    var favTitles by mutableStateOf<Set<String>>(emptySet())   // lowercase singer titles, for star state
        private set
    var favAlbumIds by mutableStateOf<Set<String>>(emptySet())  // album favorites, for the player's album button
        private set
    var favRemoveCandidate by mutableStateOf<Fav?>(null)       // non-null shows "remove from Home?"
        private set

    // Blocking (long-press a song → hide it, or with the PIN its whole album/singer; plus blocked
    // words in the parent panel). Applied to every rail, search, and deep-dive.
    var contentFilter by mutableStateOf(ContentFilter())
        private set
    var blockCandidate by mutableStateOf<Track?>(null)   // non-null shows the "hide this?" dialog
        private set
    var showBlockEditor by mutableStateOf(false)
        private set
    /** True when the last search was for a blocked word — the Search tab then shows nothing. */
    var searchBlocked by mutableStateOf(false)
        private set

    // Favorites are cached once a non-empty load succeeds; recent/liked are reloaded on every refresh.
    private var favSections: List<BrowseSection> = emptyList()
    private var recentSection: BrowseSection? = null
    private var likedSection: BrowseSection? = null
    private var homeJob: Job? = null

    init {
        appRemotePlayer.onError = { msg -> errorMessage = msg }
        contentFilter = BlockStore.filter(getApplication())
        refreshFavTitles()
        AuthManager.init(getApplication())

        // Follow whatever is actually playing — the track also changes on next/previous,
        // which come back through the App Remote subscription rather than through play().
        viewModelScope.launch {
            player.state
                .map { it?.track }
                .distinctUntilChanged { a, b -> a?.id == b?.id }
                .collect { track -> loadLyrics(track) }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching { connectivity?.registerNetworkCallback(request, networkCallback) }
    }

    fun askBlock(track: Track) { blockCandidate = track }
    fun cancelBlock() { blockCandidate = null }

    /**
     * Blocks the candidate song, its album, or its singer(s). A single song needs no PIN — that is
     * how it has always worked, and one song is easy to undo. A whole album or singer can take a
     * favorite rail off Home, so it needs [pin]. Returns false if the PIN is wrong.
     */
    fun confirmBlock(scope: BlockScope, pin: String? = null): Boolean {
        val track = blockCandidate ?: return true
        if (scope != BlockScope.TRACK && !SettingsStore.checkPin(getApplication(), pin.orEmpty())) return false
        val ctx = getApplication<Application>()
        when (scope) {
            BlockScope.TRACK -> BlockStore.add(ctx, track.id)
            BlockScope.ALBUM -> track.album?.takeIf { it.id.isNotEmpty() }?.let { BlockStore.addAlbum(ctx, it.id, it.name) }
            BlockScope.ARTIST -> track.artists.forEach { BlockStore.addArtist(ctx, it.name) }
        }
        blockCandidate = null
        applyFilter()
        return true
    }

    private fun applyFilter() {
        contentFilter = BlockStore.filter(getApplication())
        // Don't let a song that was just blocked keep playing.
        nowPlaying.value?.track?.let { if (!contentFilter.allows(it)) player.pause() }
    }

    // --- Block list (parent panel) ---
    fun openBlockEditor() { showBlockEditor = true }
    fun closeBlockEditor() { showBlockEditor = false }

    fun blockedAlbums(): Map<String, String> = BlockStore.albums(getApplication())
    fun blockedArtists(): List<String> = BlockStore.artists(getApplication()).sortedBy { it.lowercase() }
    fun blockedTrackCount(): Int = contentFilter.trackIds.size

    fun addBlockedWord(word: String) {
        word.trim().takeIf { it.isNotEmpty() }?.let { BlockStore.addWord(getApplication(), it) }
        applyFilter()
    }
    fun removeBlockedWord(word: String) { BlockStore.removeWord(getApplication(), word); applyFilter() }
    fun addBlockedArtist(name: String) {
        name.trim().takeIf { it.isNotEmpty() }?.let { BlockStore.addArtist(getApplication(), it) }
        applyFilter()
    }
    fun removeBlockedArtist(name: String) { BlockStore.removeArtist(getApplication(), name); applyFilter() }
    fun removeBlockedAlbum(id: String) { BlockStore.removeAlbum(getApplication(), id); applyFilter() }
    fun clearBlockedTracks() { BlockStore.clearTracks(getApplication()); applyFilter() }

    /**
     * Explicit songs keep their cover out of sight unless they are from a singer or album on
     * Home: covers of explicit songs are what looked wrong at a glance, before anything played.
     * The song itself still plays — blocking stays the parent's call (see [ContentFilter]).
     */
    fun showsCover(track: Track): Boolean =
        !track.explicit ||
            isFavorite(TrackMatch.primaryArtist(track.artistName)) ||
            track.album?.id?.let { it in favAlbumIds } == true

    /**
     * Called on Home entry/resume: loads/retries favorites (if empty) + refreshes recent/liked.
     *
     * [force] reloads the favorites even when rails are already showing, and restarts a load that
     * is still running. The refresh button and every favorites edit use it: without it a load that
     * is already in flight would swallow the new call and leave Home showing the old (or an empty)
     * set of rails until something else happened to trigger a reload — which in kiosk mode, where
     * ON_RESUME rarely fires, could be hours.
     */
    fun loadHome(force: Boolean = false) {
        if (homeJob?.isActive == true) {
            if (!force) return
            homeJob?.cancel()
        }
        homeJob = viewModelScope.launch {
            try {
                if (force || favSections.isEmpty()) {
                    // Drives the spinner on the refresh button; HomeContent only shows the
                    // full-screen spinner while there is nothing on screen yet.
                    isLoadingHome = true
                    val favs = FavoritesStore.load(getApplication())

                    // Start from the last known rails, so Home is filled in immediately and stays
                    // filled even if every search fails (offline, or rate limited for minutes).
                    val rails = HomeCacheStore.load(getApplication())
                        .map { Fav(it.title, it.query, it.albumId) to it.tracks }
                        .associate { (fav, tracks) -> fav.key to fav.section(tracks) }
                        .toMutableMap()
                    // Favorites order wins, and a favorite removed meanwhile drops out of the cache too.
                    fun publish() {
                        favSections = favs.mapNotNull { rails[it.key] }
                        rebuildHome()
                    }
                    publish()

                    var pending = favs
                    var attempt = 0
                    var rateLimitedFor = 0L   // seconds Spotify asked us to wait, if it did
                    // Retry only the favorites that came back empty — right after a cold boot Wi-Fi
                    // may not be up yet, and re-running the whole set would spend quota on rails that
                    // already loaded. Backing off matters: hammering is what earns a 429 in the first place.
                    while (pending.isNotEmpty() && attempt < 3) {
                        if (attempt > 0) delay(3000L * attempt)
                        val results = coroutineScope {
                            pending.map { fav ->
                                fav to async {
                                    runCatching { fav.albumId?.let { api.albumTracks(it) } ?: api.searchTracks(fav.query) }
                                }
                            }.map { (fav, d) -> fav to d.await() }
                        }
                        results.forEach { (fav, result) ->
                            val tracks = result.getOrNull().orEmpty()
                            if (tracks.isNotEmpty()) {
                                rails[fav.key] = fav.section(tracks)
                            }
                            (result.exceptionOrNull() as? RateLimitedException)?.let {
                                rateLimitedFor = maxOf(rateLimitedFor, it.retryAfterSeconds)
                            }
                        }
                        pending = results.filter { it.second.getOrNull().isNullOrEmpty() }.map { it.first }
                        publish()   // show rails as they arrive, rather than after the last retry
                        attempt++
                    }

                    // A refresh that changes nothing looks broken. Say why — being rate limited can
                    // last several minutes, and no amount of pressing the button shortens it.
                    if (force && pending.isNotEmpty() && rateLimitedFor > 0) {
                        val minutes = ((rateLimitedFor + 59) / 60).coerceAtLeast(1)
                        errorMessage = "Spotify가 잠시 요청을 막고 있어요. ${minutes}분쯤 뒤에 다시 새로고침해 주세요."
                    }

                    HomeCacheStore.save(
                        getApplication(),
                        favs.mapNotNull { fav -> rails[fav.key]?.let { CachedRail(it.title, fav.query, it.tracks, fav.albumId) } },
                    )
                }
                refreshDynamic()
            } finally {
                isLoadingHome = false
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
        searchBlocked = false
        if (q.isEmpty()) {
            searchResults = emptyList()
            return
        }
        if (!contentFilter.allowsQuery(q)) {
            searchResults = emptyList()
            searchBlocked = true
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

    // --- Lyrics ---

    /** Loads lyrics for [track] through the fallback chain (local file → cache → LRCLIB). */
    private fun loadLyrics(track: Track?) {
        lyricsJob?.cancel()
        lyrics = null
        if (track == null) {
            isLoadingLyrics = false
            return
        }
        lyricsJob = viewModelScope.launch {
            isLoadingLyrics = true
            try {
                lyrics = lyricsRepo.load(
                    LyricsQuery(
                        trackId = track.id,
                        title = track.name,
                        artist = track.artistName,
                        album = track.album?.name.orEmpty(),
                        durationMs = track.durationMs,
                    )
                )
            } finally {
                isLoadingLyrics = false
            }
        }
    }

    fun toggleLyrics() { showLyrics = !showLyrics }
    fun closeLyrics() { showLyrics = false }

    /** Parent panel: drop the cache so newly added .lrc files / new LRCLIB entries are picked up. */
    fun clearLyricsCache() {
        LyricsStore.clear(getApplication())
        loadLyrics(nowPlaying.value?.track)
    }

    /** Refresh on app resume (loadHome handles loading favorites if empty + refreshing recent/liked). */
    fun refresh() = loadHome()

    /** The refresh button on Home: rebuild every rail now, even ones that are already showing. */
    fun refreshNow() = loadHome(force = true)

    /** Opens a deep-dive: artist = paginated search; recent = 50 recently-played tracks. */
    fun openDeepDive(title: String, dive: DeepDive) {
        artistTitle = title
        artistDive = dive
        artistTracks = emptyList()
        isLoadingArtist = true
        viewModelScope.launch {
            artistTracks = when (dive) {
                is DeepDive.Artist -> runCatching { api.searchTracksDeep(dive.query, pages = 5) }.getOrDefault(emptyList())
                is DeepDive.Album -> runCatching { api.albumTracks(dive.albumId) }.getOrDefault(emptyList())
                DeepDive.Recent -> runCatching { api.recentlyPlayed(limit = 50) }.getOrNull()?.distinctBy { it.id }.orEmpty()
            }
            isLoadingArtist = false
        }
    }

    fun closeArtist() {
        artistTitle = null
        artistDive = null
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

    /** Adds a singer/movie search rail — or, if [title] is a Spotify album link, that album. */
    fun addFavorite(title: String, query: String) {
        val t = title.trim()
        val q = query.trim().ifEmpty { t }
        if (t.isEmpty()) return
        val albumId = SpotifyLinks.albumId(t)
        if (albumId != null) {
            addAlbumFromLink(albumId)
            return
        }
        favEditList = favEditList + Fav(t, q)
        persistFavorites()
    }

    private fun addAlbumFromLink(albumId: String) {
        if (favEditList.any { it.albumId == albumId }) return
        viewModelScope.launch {
            // The link carries only the ID; the rail needs the album's name.
            val name = runCatching { api.albumTracks(albumId) }.getOrNull()
                ?.firstNotNullOfOrNull { it.album?.name?.takeIf(String::isNotEmpty) }
            if (name == null) {
                errorMessage = "앨범을 찾지 못했어요. 링크를 다시 확인해 주세요."
                return@launch
            }
            favEditList = FavoritesStore.load(getApplication()) + Fav(name, name, albumId)
            persistFavorites()
        }
    }

    fun removeFavorite(index: Int) {
        favEditList = favEditList.filterIndexed { i, _ -> i != index }
        persistFavorites()
    }

    fun resetFavorites() {
        favEditList = FavoritesStore.resetToDefault(getApplication())
        refreshFavTitles()
        reloadFavorites()
    }

    // --- Favorites the child manages (star on search results / long-press a rail title) ---

    fun isFavorite(name: String): Boolean = name.trim().lowercase() in favTitles

    /** Adds the artist as a Home rail, or removes it if already there. */
    fun toggleFavorite(name: String) {
        val title = name.trim()
        if (title.isEmpty()) return
        val list = FavoritesStore.load(getApplication())
        val existing = list.firstOrNull { it.albumId == null && it.title.equals(title, ignoreCase = true) }
        favEditList = if (existing != null) list - existing else list + Fav(title)
        persistFavorites()
    }

    fun isFavoriteAlbum(albumId: String): Boolean = albumId in favAlbumIds

    /** Adds the track's album as a Home rail (the player's album button), or removes it. */
    fun toggleFavoriteAlbum(album: com.plocoli.yomp.data.Album) {
        if (album.id.isEmpty()) return
        val list = FavoritesStore.load(getApplication())
        val existing = list.firstOrNull { it.albumId == album.id }
        favEditList = if (existing != null) list - existing else list + Fav(album.name, album.name, album.id)
        persistFavorites()
    }

    // Removing is confirmed first: a long-press is easy to trigger by accident, and a rail
    // the parent curated should not vanish on one stray touch.
    fun askRemoveFavorite(fav: Fav) { favRemoveCandidate = fav }
    fun cancelRemoveFavorite() { favRemoveCandidate = null }
    fun confirmRemoveFavorite() {
        favRemoveCandidate?.let { fav ->
            favEditList = FavoritesStore.load(getApplication()).filterNot { it.key == fav.key }
            persistFavorites()
        }
        favRemoveCandidate = null
    }

    private fun refreshFavTitles() {
        val favs = FavoritesStore.load(getApplication())
        favTitles = favs.filter { it.albumId == null }.map { it.title.lowercase() }.toSet()
        favAlbumIds = favs.mapNotNull { it.albumId }.toSet()
    }

    private fun persistFavorites() {
        FavoritesStore.save(getApplication(), favEditList)
        refreshFavTitles()
        reloadFavorites()
    }

    private fun reloadFavorites() {
        favSections = emptyList()
        loadHome(force = true)
    }

    fun toggle() = player.toggle()
    fun next() = player.next()
    fun previous() = player.previous()
    fun cycleRepeat() = player.cycleRepeat()
    fun seekTo(ms: Long) = player.seekTo(ms)

    fun clearError() { errorMessage = null }

    override fun onCleared() {
        runCatching { connectivity?.unregisterNetworkCallback(networkCallback) }
        player.release()
        localPlayer.stop()
    }
}
