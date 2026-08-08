package com.plocoli.yomp.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.plocoli.yomp.BrowseSection
import com.plocoli.yomp.YompViewModel
import com.plocoli.yomp.auth.AuthState
import com.plocoli.yomp.data.LocalTrack
import com.plocoli.yomp.data.Track
import com.plocoli.yomp.player.NowPlaying
import com.plocoli.yomp.player.RepeatMode
import kotlinx.coroutines.delay

@Composable
fun YompApp(vm: YompViewModel) {
    // 켤 때마다 "욤프 대문"(인트로) 먼저 → 브라우징으로
    var showIntro by remember { mutableStateOf(true) }
    if (showIntro) {
        IntroScreen(onDone = { showIntro = false })
        return
    }

    val authState by vm.authState.collectAsState()

    if (vm.errorMessage != null) {
        AlertDialog(
            onDismissRequest = { vm.clearError() },
            confirmButton = { TextButton(onClick = { vm.clearError() }) { Text("확인") } },
            title = { Text("이런!") },
            text = { Text(vm.errorMessage ?: "") },
        )
    }

    vm.blockCandidate?.let { t ->
        AlertDialog(
            onDismissRequest = { vm.cancelBlock() },
            confirmButton = { TextButton(onClick = { vm.confirmBlock() }) { Text("숨기기") } },
            dismissButton = { TextButton(onClick = { vm.cancelBlock() }) { Text("취소") } },
            title = { Text("이 곡 숨기기") },
            text = { Text("\"${t.name}\"을(를) 목록에서 숨길까요?") },
        )
    }

    when (authState) {
        AuthState.AUTHENTICATED -> MainScreen(vm)
        else -> LoginScreen(vm, authState)
    }
}

// MARK: - 욤프 대문 (인트로 스플래시)

@Composable
private fun IntroScreen(onDone: () -> Unit) {
    var started by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (started) 1f else 0.5f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "introScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "introAlpha",
    )
    LaunchedEffect(Unit) {
        started = true
        delay(1900)
        onDone()
    }
    Surface(Modifier.fillMaxSize(), color = Color(0xFF2196F3)) {   // 욤프 브랜드 파랑 (아이콘/스플래시와 통일)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    scaleX = scale; scaleY = scale; this.alpha = alpha
                },
            ) {
                Icon(Icons.Filled.MusicNote, null, Modifier.size(120.dp), tint = Color.White)
                Spacer(Modifier.size(16.dp))
                Text("욤프", fontSize = 64.sp, fontWeight = FontWeight.Black, color = Color.White)
            }
        }
    }
}

// MARK: - 로그인

@Composable
private fun LoginScreen(vm: YompViewModel, state: AuthState) {
    val context = LocalContext.current
    val error by vm.authError.collectAsState()

    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Icon(Icons.Filled.MusicNote, null, Modifier.size(96.dp), tint = MaterialTheme.colorScheme.primary)
            Text("욤프", fontSize = 48.sp, fontWeight = FontWeight.Black)
            if (state == AuthState.AUTHENTICATING) {
                CircularProgressIndicator()
            } else {
                Button(onClick = { vm.login(context) }) {
                    Text("시작하기", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
                }
            }
            if (state == AuthState.ERROR && error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 16.sp)
            }
        }
    }
}

// MARK: - 메인 (홈/찾기 탭 + 재생 바)

@Composable
private fun MainScreen(vm: YompViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    var showPlayer by remember { mutableStateOf(false) }
    val nowPlaying by vm.nowPlaying.collectAsState()

    // 앱에 들어오거나 복귀할 때마다 갱신 (즐겨찾기 비었으면 재시도 + 최근/좋아요)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.loadHome() }

    // 뒤로가기: 전체화면 플레이어/아티스트 화면은 닫고, 홈에선 무시(런처처럼 — 앱 재시작 방지)
    BackHandler(enabled = true) {
        when {
            vm.showFavEditor -> vm.closeFavEditor()
            vm.artistTitle != null -> vm.closeArtist()
            showPlayer -> showPlayer = false
            else -> { /* 홈에서 뒤로가기 무시 */ }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                Column {
                    nowPlaying?.let { NowPlayingBar(it, vm, onOpen = { showPlayer = true }) }
                    NavigationBar {
                        NavigationBarItem(
                            selected = tab == 0,
                            onClick = { tab = 0 },
                            icon = { Icon(Icons.Filled.Home, "홈") },
                            label = { Text("홈") },
                        )
                        NavigationBarItem(
                            selected = tab == 1,
                            onClick = { tab = 1 },
                            icon = { Icon(Icons.Filled.Search, "찾기") },
                            label = { Text("찾기") },
                        )
                        NavigationBarItem(
                            selected = tab == 2,
                            onClick = { tab = 2 },
                            icon = { Icon(Icons.Filled.LibraryMusic, "내 음악") },
                            label = { Text("내 음악") },
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    0 -> HomeContent(vm)
                    1 -> SearchContent(vm)
                    else -> LocalContent(vm)
                }
            }
        }

        val artist = vm.artistTitle
        if (artist != null) {
            ArtistScreen(vm, artist, onBack = { vm.closeArtist() })
        }

        val np = nowPlaying
        if (showPlayer && np != null) {
            FullPlayerScreen(vm, np, onClose = { showPlayer = false })
        }

        if (vm.showFavEditor) {
            FavoritesEditorScreen(vm)
        }
    }
}

// MARK: - 즐겨찾기 편집 (부모)

@Composable
private fun FavoritesEditorScreen(vm: YompViewModel) {
    var newTerm by remember { mutableStateOf("") }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.closeFavEditor() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "닫기", Modifier.size(30.dp))
                }
                Text("즐겨찾기 편집", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                "가수·영화 이름을 넣으면 홈에 그 노래 줄이 생겨요.",
                color = MaterialTheme.colorScheme.outline,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = newTerm,
                    onValueChange = { newTerm = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("예: TWICE, 뽀로로") },
                    singleLine = true,
                )
                Button(onClick = { vm.addFavorite(newTerm, newTerm); newTerm = "" }) { Text("추가") }
            }
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(vm.favEditList) { index, fav ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(fav.title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { vm.removeFavorite(index) }) {
                            Icon(Icons.Filled.Close, "삭제", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            TextButton(onClick = { vm.resetFavorites() }, modifier = Modifier.padding(8.dp)) {
                Text("기본값으로 초기화")
            }
        }
    }
}

// MARK: - 아티스트 딥다이브

@Composable
private fun ArtistScreen(vm: YompViewModel, title: String, onBack: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", Modifier.size(30.dp))
                }
                Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
            when {
                vm.isLoadingArtist ->
                    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                vm.artistTracks.isEmpty() ->
                    CenteredHint("곡을 불러오지 못했어요")
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(vm.artistTracks.filter { it.id !in vm.blockedIds }, key = { it.id }) { track ->
                        TrackTile(track, { vm.play(it) }, Modifier.fillMaxWidth(), onLongPress = { vm.askBlock(track) })
                    }
                }
            }
        }
    }
}

// MARK: - 전체화면 플레이어

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullPlayerScreen(vm: YompViewModel, np: NowPlaying, onClose: () -> Unit) {
    val repeat by vm.repeatMode.collectAsState()
    val posRaw by vm.positionMs.collectAsState()
    val dur by vm.durationMs.collectAsState()
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.KeyboardArrowDown, "닫기", Modifier.size(32.dp))
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { vm.toggleLike() }) {
                    Icon(
                        if (vm.isCurrentLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        "좋아요",
                        Modifier.size(32.dp),
                        tint = if (vm.isCurrentLiked) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Box(Modifier.size(320.dp).clip(RoundedCornerShape(28.dp))) {
                Artwork(np.track.thumbnailUrl, Modifier.fillMaxSize())
            }

            Spacer(Modifier.size(28.dp))
            Text(
                np.track.name,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (np.track.artistName.isNotEmpty()) {
                Text(np.track.artistName, fontSize = 18.sp, color = MaterialTheme.colorScheme.outline)
            }

            Spacer(Modifier.weight(1f))

            // 재생 진행바 (남은 시간 표시 + 원하는 위치로 이동)
            val maxV = dur.coerceAtLeast(1L).toFloat()
            val sliderValue = (if (dragging) dragValue else posRaw.toFloat()).coerceIn(0f, maxV)
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Slider(
                    value = sliderValue,
                    onValueChange = { dragging = true; dragValue = it },
                    onValueChangeFinished = { vm.seekTo(dragValue.toLong()); dragging = false },
                    valueRange = 0f..maxV,
                    // 🦆 노란 오리가 냇물 위를 떠가는 진행 표시 (오른쪽 보고 있음)
                    thumb = { DuckThumb() },
                    track = { sliderState ->
                        SliderDefaults.Track(
                            sliderState = sliderState,
                            colors = SliderDefaults.colors(
                                activeTrackColor = Color(0xFF4FC3F7),     // 지나온 냇물 (진한 물색)
                                inactiveTrackColor = Color(0xFFB3E5FC),   // 남은 냇물 (연한 물색)
                            ),
                        )
                    },
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(fmtTime(sliderValue.toLong()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.weight(1f))
                    Text(fmtTime(dur), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                }
            }

            Spacer(Modifier.size(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.previous() }, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Filled.SkipPrevious, "이전", Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { vm.toggle() }, modifier = Modifier.size(96.dp)) {
                    Icon(
                        if (np.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        "재생/일시정지",
                        Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = { vm.next() }, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Filled.SkipNext, "다음", Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.size(24.dp))

            IconButton(onClick = { vm.cycleRepeat() }, modifier = Modifier.size(56.dp)) {
                Icon(
                    if (repeat == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    "반복 재생",
                    Modifier.size(30.dp),
                    tint = if (repeat == RepeatMode.OFF) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.weight(1f))
        }
    }
}

// MARK: - 홈 (브라우징 레일)

@Composable
private fun HomeContent(vm: YompViewModel) {
    Box(Modifier.fillMaxSize()) {
        when {
            vm.homeSections.isEmpty() && vm.isLoadingHome ->
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            vm.homeSections.isEmpty() ->
                CenteredHint("찾기 탭에서 노래를 검색해 보세요")
            else ->
                LazyColumn(contentPadding = PaddingValues(vertical = 16.dp)) {
                    items(vm.homeSections) { section ->
                        BrowseRail(
                            section = section,
                            onTap = { vm.play(it) },
                            onOpenArtist = section.deepDive?.let { dd -> { vm.openDeepDive(section.title, dd) } },
                            blocked = vm.blockedIds,
                            onLongPress = { vm.askBlock(it) },
                        )
                    }
                }
        }
        // 부모용: 즐겨찾기 편집 진입 (우상단)
        IconButton(
            onClick = { vm.openFavEditor() },
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
        ) {
            Icon(Icons.Filled.Edit, "즐겨찾기 편집", tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun BrowseRail(
    section: BrowseSection,
    onTap: (Track) -> Unit,
    onOpenArtist: (() -> Unit)?,
    blocked: Set<String>,
    onLongPress: (Track) -> Unit,
) {
    val tracks = section.tracks.filter { it.id !in blocked }
    if (tracks.isEmpty()) return
    Column(Modifier.padding(bottom = 24.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onOpenArtist != null) Modifier.clickable { onOpenArtist() } else Modifier)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(section.title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (onOpenArtist != null) {
                Icon(
                    Icons.Filled.KeyboardArrowRight,
                    "더 보기",
                    Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(tracks, key = { it.id }) { track ->
                TrackTile(track, onTap, Modifier.width(150.dp), onLongPress = { onLongPress(track) })
            }
        }
    }
}

// MARK: - 찾기

@Composable
private fun SearchContent(vm: YompViewModel) {
    var query by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("노래 찾기") },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge,
            trailingIcon = {
                IconButton(onClick = { vm.search(query) }) { Icon(Icons.Filled.Search, "검색") }
            },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { vm.search(query) }),
        )
        when {
            vm.isSearching -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            vm.searchResults.isEmpty() -> CenteredHint("듣고 싶은 노래를 찾아봐요")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(vm.searchResults.filter { it.id !in vm.blockedIds }, key = { it.id }) { track ->
                    TrackTile(track, { vm.play(it) }, Modifier.fillMaxWidth(), onLongPress = { vm.askBlock(track) })
                }
            }
        }
    }
}

// MARK: - 공통 타일 (전체가 탭 영역)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackTile(
    track: Track,
    onTap: (Track) -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = { onTap(track) }, onLongClick = onLongPress)
            .padding(4.dp)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp))) {
            Artwork(track.thumbnailUrl, Modifier.fillMaxSize())
        }
        Spacer(Modifier.size(6.dp))
        Text(track.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (track.artistName.isNotEmpty()) {
            Text(
                track.artistName,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun Artwork(url: String?, modifier: Modifier = Modifier) {
    if (url != null) {
        AsyncImage(model = url, contentDescription = null, modifier = modifier)
    } else {
        Surface(modifier, color = MaterialTheme.colorScheme.surfaceVariant) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.MusicNote, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

// MARK: - 내 음악 (로컬 오디오)

@Composable
private fun LocalContent(vm: YompViewModel) {
    val context = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= 33)
        Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
    }
    LaunchedEffect(granted) { if (granted) vm.loadLocal() }

    val localNow by vm.localNowPlaying.collectAsState()
    val localPlaying by vm.localIsPlaying.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when {
                !granted -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Filled.LibraryMusic, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.outline)
                        Text("기기에 저장된 음악을 보려면 권한이 필요해요", fontSize = 18.sp, color = MaterialTheme.colorScheme.outline)
                        Button(onClick = { launcher.launch(permission) }) { Text("권한 허용", modifier = Modifier.padding(4.dp)) }
                    }
                }
                vm.localTracks.isEmpty() -> CenteredHint("기기에 저장된 음악이 없어요")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(vm.localTracks, key = { it.id }) { track ->
                        LocalRow(track) { vm.playLocal(it) }
                    }
                }
            }
        }
        localNow?.let { LocalNowBar(it, localPlaying) { vm.toggleLocal() } }
    }
}

@Composable
private fun LocalMusicIcon(size: Int) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.MusicNote, null, Modifier.size((size * 0.55).dp), tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun LocalRow(track: LocalTrack, onTap: (LocalTrack) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onTap(track) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LocalMusicIcon(52)
        Column(Modifier.weight(1f)) {
            Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (track.artist.isNotEmpty()) {
                Text(track.artist, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun LocalNowBar(track: LocalTrack, isPlaying: Boolean, onToggle: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LocalMusicIcon(52)
            Column(Modifier.weight(1f)) {
                Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (track.artist.isNotEmpty()) {
                    Text(track.artist, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                }
            }
            ControlButton(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "재생/일시정지", big = true) { onToggle() }
        }
    }
}

@Composable
private fun CenteredHint(text: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Filled.MusicNote, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.outline)
            Text(text, color = MaterialTheme.colorScheme.outline, fontSize = 18.sp)
        }
    }
}

// MARK: - 하단 재생 바

@Composable
private fun NowPlayingBar(np: NowPlaying, vm: YompViewModel, onOpen: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.weight(1f).clickable { onOpen() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))) {
                    Artwork(np.track.thumbnailUrl, Modifier.fillMaxSize())
                }
                Column(Modifier.weight(1f)) {
                    Text(np.track.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (np.track.artistName.isNotEmpty()) {
                        Text(
                            np.track.artistName,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            ControlButton(Icons.Filled.SkipPrevious, "이전") { vm.previous() }
            ControlButton(if (np.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "재생/일시정지", big = true) { vm.toggle() }
            ControlButton(Icons.Filled.SkipNext, "다음") { vm.next() }
        }
    }
}

@Composable
private fun ControlButton(icon: ImageVector, desc: String, big: Boolean = false, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(if (big) 56.dp else 44.dp)) {
        Icon(icon, desc, Modifier.size(if (big) 40.dp else 30.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

/** ms → "분:초" (예: 3:07) */
private fun fmtTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

/** 진행바 썸: 냇물 위를 떠가는 노란 오리 (오른쪽을 바라봄) */
@Composable
private fun DuckThumb() {
    val body = Color(0xFFFFD21E)   // 노랑
    val wing = Color(0xFFF3B500)   // 살짝 진한 노랑 (날개)
    val beak = Color(0xFFFF8A00)   // 주황 부리
    val eye = Color(0xFF3A2A00)
    Canvas(Modifier.size(36.dp)) {
        val w = size.width
        val h = size.height
        // 몸통
        drawOval(body, topLeft = Offset(w * 0.04f, h * 0.44f), size = Size(w * 0.66f, h * 0.40f))
        // 날개
        drawOval(wing, topLeft = Offset(w * 0.20f, h * 0.52f), size = Size(w * 0.32f, h * 0.20f))
        // 머리 (오른쪽 위)
        drawCircle(body, radius = h * 0.23f, center = Offset(w * 0.70f, h * 0.40f))
        // 부리 (오른쪽 방향 삼각형)
        drawPath(
            Path().apply {
                moveTo(w * 0.85f, h * 0.34f)
                lineTo(w * 0.99f, h * 0.42f)
                lineTo(w * 0.85f, h * 0.50f)
                close()
            },
            beak,
        )
        // 눈
        drawCircle(eye, radius = h * 0.035f, center = Offset(w * 0.77f, h * 0.34f))
    }
}
