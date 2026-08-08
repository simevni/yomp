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
import com.plocoli.yomp.data.SpotifyApi
import com.plocoli.yomp.data.Track
import com.plocoli.yomp.player.AppRemotePlayer
import com.plocoli.yomp.player.LocalPlayer
import com.plocoli.yomp.player.Player
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 레일 제목 탭 시 여는 딥다이브 종류 */
sealed interface DeepDive {
    data class Artist(val query: String) : DeepDive   // 아티스트 검색 페이지네이션
    data object Recent : DeepDive                      // 최근 들은 노래 더 많이(최대 50)
}

/** 홈 화면의 가로 레일 하나. deepDive!=null 이면 제목 탭 시 딥다이브 가능. */
data class BrowseSection(val title: String, val tracks: List<Track>, val deepDive: DeepDive? = null)

class YompViewModel(app: Application) : AndroidViewModel(app) {

    private val api = SpotifyApi { AuthManager.validAccessToken(getApplication()) }

    // 실제 재생: Spotify App Remote (설치된 Spotify 앱 필요). 에뮬레이터엔 Spotify 앱이 없어 연결 실패.
    private val appRemotePlayer = AppRemotePlayer(app)
    private val player: Player = appRemotePlayer

    // 로컬 오디오(기기 저장 파일) — Spotify 와 별개 엔진
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

    /** 지금 재생 중인 곡의 좋아요 상태 (전체화면 플레이어 하트) */
    var isCurrentLiked by mutableStateOf(false)
        private set

    // 아티스트 딥다이브 (레일 제목 탭 → 그 아티스트 곡 여러 페이지)
    var artistTitle by mutableStateOf<String?>(null)   // null = 닫힘
        private set
    var artistTracks by mutableStateOf<List<Track>>(emptyList())
        private set
    var isLoadingArtist by mutableStateOf(false)
        private set

    // 즐겨찾기 편집 (부모가 앱 안에서 추가/삭제 → 재빌드 없이 반영)
    var showFavEditor by mutableStateOf(false)
        private set
    var favEditList by mutableStateOf<List<Fav>>(emptyList())
        private set

    // 곡 숨기기 (길게 눌러 이 곡 숨김 → 모든 레일에서 제외)
    var blockedIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var blockCandidate by mutableStateOf<Track?>(null)   // null 아니면 "숨길까요?" 다이얼로그
        private set

    // 즐겨찾기는 성공(비어있지 않음)하면 캐시, 최근/좋아요는 갱신마다 다시 로드
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

    /** 홈 진입/복귀 시 호출: 즐겨찾기(비었으면 로드/재시도) + 최근/좋아요 갱신. */
    fun loadHome() {
        if (homeLoading) return
        viewModelScope.launch {
            homeLoading = true
            try {
                // 콜드부팅 직후엔 Wi-Fi 가 아직 안 올라와 검색이 다 실패할 수 있어, 성공할 때까지 재시도.
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

    /** 최근 들은/좋아요 레일만 다시 로드 (앱 복귀·좋아요 변경 후). */
    private suspend fun refreshDynamic() {
        // 최근: 로컬 기록 우선(즉시성). 비어있으면 Spotify 서버 기록으로 1회 시드.
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
            // 개인화(최근·좋아요)를 위로, 그다음 부모 큐레이션 즐겨찾기
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
                errorMessage = e.message ?: "검색 중 문제가 생겼어요."
            } finally {
                isSearching = false
            }
        }
    }

    fun play(track: Track) {
        localPlayer.stop()   // 로컬 재생 중지 (Spotify 와 동시 재생 방지)
        isCurrentLiked = LikeStore.isLiked(getApplication(), track.id)
        // 재생 즉시 최근기록에 올려 홈에 바로 반영
        RecentStore.add(getApplication(), track)
        recentSection = RecentStore.all(getApplication()).takeIf { it.isNotEmpty() }
            ?.let { BrowseSection("최근 들은 노래", it, DeepDive.Recent) }
        rebuildHome()
        viewModelScope.launch { player.play(track) }
    }

    // --- 로컬 오디오 ---
    fun loadLocal() {
        localTracks = runCatching { LocalAudio.query(getApplication()) }.getOrDefault(emptyList())
    }

    fun playLocal(track: LocalTrack) {
        player.pause()   // Spotify 일시정지 (동시 재생 방지)
        localPlayer.play(track)
    }

    fun toggleLocal() = localPlayer.toggle()

    /** 현재 곡 좋아요 토글(로컬 저장) → "좋아요 한 노래" 레일 즉시 갱신. */
    fun toggleLike() {
        val track = nowPlaying.value?.track ?: return
        isCurrentLiked = LikeStore.toggle(getApplication(), track)
        likedSection = LikeStore.all(getApplication()).takeIf { it.isNotEmpty() }
            ?.let { BrowseSection("좋아요 한 노래", it) }
        rebuildHome()
    }

    /** 앱 복귀 시 갱신 (loadHome 이 즐겨찾기 빈 경우 로드 + 최근/좋아요 갱신 처리). */
    fun refresh() = loadHome()

    /** 딥다이브 열기: 아티스트는 페이지네이션 검색, 최근은 recently-played 50곡. */
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

    // --- 즐겨찾기 편집 ---
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
