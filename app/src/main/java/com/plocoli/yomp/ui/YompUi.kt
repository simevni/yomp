package com.plocoli.yomp.ui

import android.Manifest
import android.app.Activity
import android.content.Context
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.plocoli.yomp.BrowseSection
import com.plocoli.yomp.YompViewModel
import com.plocoli.yomp.auth.AuthState
import com.plocoli.yomp.data.LocalTrack
import com.plocoli.yomp.data.SettingsStore
import com.plocoli.yomp.kiosk.KioskManager
import com.plocoli.yomp.data.Track
import com.plocoli.yomp.player.NowPlaying
import com.plocoli.yomp.player.RepeatMode
import kotlinx.coroutines.delay

@Composable
fun YompApp(vm: YompViewModel) {
    // Every launch shows the branded intro first → then browsing.
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

// MARK: - Intro splash

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
    Surface(Modifier.fillMaxSize(), color = Color(0xFF2196F3)) {   // Yomp brand blue (matches the icon/splash)
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

// MARK: - Login

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

// MARK: - Main (Home/Search tabs + now-playing bar)

@Composable
private fun MainScreen(vm: YompViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    var showPlayer by remember { mutableStateOf(false) }
    val nowPlaying by vm.nowPlaying.collectAsState()

    // Refresh on every entry/resume (retry favorites if empty + refresh recent/liked).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.loadHome() }

    // Back: close the full-screen player/artist screen; ignore on Home (launcher-like — avoids restarting the app).
    BackHandler(enabled = true) {
        when {
            vm.showFavEditor -> vm.closeFavEditor()
            vm.showParent -> vm.closeParent()
            vm.artistTitle != null -> vm.closeArtist()
            showPlayer -> showPlayer = false
            else -> { /* ignore back on Home */ }
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

        if (vm.showParent) {
            ParentScreen(vm)
        }

        if (vm.showFavEditor) {
            FavoritesEditorScreen(vm)
        }
    }
}

// MARK: - Parent panel (PIN-gated)

@Composable
private fun ParentScreen(vm: YompViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity
    val isOwner = remember { KioskManager.isDeviceOwner(context) }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.closeParent() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "닫기", Modifier.size(30.dp))
                }
                Text(if (vm.parentAuthed) "부모 설정" else "부모 확인", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            if (!vm.parentAuthed) {
                PinGate(onSubmit = { vm.submitParentPin(it) })
            } else {
                ParentPanel(vm, activity, isOwner, context)
            }
        }
    }
}

@Composable
private fun PinGate(onSubmit: (String) -> Boolean) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    fun submit() { if (!onSubmit(pin)) { error = true; pin = "" } }
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("부모 PIN을 입력하세요", color = MaterialTheme.colorScheme.outline)
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it.filter(Char::isDigit).take(8); error = false },
            singleLine = true,
            isError = error,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { submit() }),
        )
        if (error) Text("PIN이 올바르지 않아요", color = MaterialTheme.colorScheme.error)
        Button(onClick = { submit() }) { Text("확인", modifier = Modifier.padding(4.dp)) }
    }
}

@Composable
private fun ParentPanel(vm: YompViewModel, activity: Activity?, isOwner: Boolean, context: Context) {
    var showPinChange by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = { vm.openFavEditor() }, modifier = Modifier.fillMaxWidth()) {
            Text("즐겨찾기 편집", modifier = Modifier.padding(4.dp))
        }

        if (isOwner && activity != null) {
            Button(
                onClick = {
                    SettingsStore.setKioskEnabled(context, false)
                    KioskManager.exitKiosk(activity)
                    vm.closeParent()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("기기 잠금 해제하고 나가기", modifier = Modifier.padding(4.dp)) }

            Button(
                onClick = {
                    SettingsStore.setKioskEnabled(context, true)
                    KioskManager.configureIfOwner(context)
                    KioskManager.enterKiosk(activity)
                    vm.closeParent()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("지금 다시 잠그기", modifier = Modifier.padding(4.dp)) }
        } else {
            Text(
                "완전 잠금(키오스크)은 아직 설정되지 않았어요. 설정 방법은 DEVELOPMENT.md의 '기기 잠금' 섹션을 참고하세요.",
                color = MaterialTheme.colorScheme.outline,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        TextButton(onClick = { showPinChange = !showPinChange }) { Text("PIN 변경") }

        if (isOwner && activity != null) {
            var confirmClear by remember { mutableStateOf(false) }
            TextButton(onClick = { confirmClear = true }) {
                Text("키오스크 기능 완전 제거", color = MaterialTheme.colorScheme.error)
            }
            if (confirmClear) {
                AlertDialog(
                    onDismissRequest = { confirmClear = false },
                    confirmButton = {
                        TextButton(onClick = {
                            SettingsStore.setKioskEnabled(context, false)
                            KioskManager.exitKiosk(activity)
                            KioskManager.clearDeviceOwner(context)
                            confirmClear = false
                            vm.closeParent()
                        }) { Text("제거") }
                    },
                    dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("취소") } },
                    title = { Text("키오스크 완전 제거") },
                    text = { Text("완전 잠금 기능을 끄고 욤프를 일반 앱으로 되돌릴까요? (다시 켜려면 PC에서 재설정 필요)") },
                )
            }
        }

        if (showPinChange) {
            var newPin by remember { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = it.filter(Char::isDigit).take(8) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("새 PIN (숫자 4자리 이상)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                Button(onClick = {
                    if (newPin.length >= 4) { vm.changeParentPin(newPin); newPin = ""; showPinChange = false }
                }) { Text("저장") }
            }
        }
    }
}

// MARK: - Favorites editor (parent)

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

// MARK: - Artist deep-dive

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

// MARK: - Full-screen player

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

            // Playback progress bar (shows time + lets you seek).
            val maxV = dur.coerceAtLeast(1L).toFloat()
            val sliderValue = (if (dragging) dragValue else posRaw.toFloat()).coerceIn(0f, maxV)
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Slider(
                    value = sliderValue,
                    onValueChange = { dragging = true; dragValue = it },
                    onValueChangeFinished = { vm.seekTo(dragValue.toLong()); dragging = false },
                    valueRange = 0f..maxV,
                    // 🦆 A yellow duck floats along the stream to show progress (facing right).
                    thumb = { DuckThumb() },
                    track = { sliderState ->
                        SliderDefaults.Track(
                            sliderState = sliderState,
                            colors = SliderDefaults.colors(
                                activeTrackColor = Color(0xFF4FC3F7),     // stream already passed (deeper blue)
                                inactiveTrackColor = Color(0xFFB3E5FC),   // stream ahead (lighter blue)
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

// MARK: - Home (browse rails)

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
        // Parent entry point: opens the PIN-gated parent panel (top-right).
        IconButton(
            onClick = { vm.openParent() },
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
        ) {
            Icon(Icons.Filled.Edit, "부모 설정", tint = MaterialTheme.colorScheme.outline)
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

// MARK: - Search

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

// MARK: - Shared track tile (whole tile is tappable)

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

// MARK: - My Music (local audio)

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

// MARK: - Now-playing bar

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

/** ms → "m:ss" (e.g. 3:07). */
private fun fmtTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

/** Slider thumb: a yellow duck floating along the stream (facing right). */
@Composable
private fun DuckThumb() {
    val body = Color(0xFFFFD21E)   // yellow
    val wing = Color(0xFFF3B500)   // slightly deeper yellow (wing)
    val beak = Color(0xFFFF8A00)   // orange beak
    val eye = Color(0xFF3A2A00)
    Canvas(Modifier.size(36.dp)) {
        val w = size.width
        val h = size.height
        // body
        drawOval(body, topLeft = Offset(w * 0.04f, h * 0.44f), size = Size(w * 0.66f, h * 0.40f))
        // wing
        drawOval(wing, topLeft = Offset(w * 0.20f, h * 0.52f), size = Size(w * 0.32f, h * 0.20f))
        // head (upper right)
        drawCircle(body, radius = h * 0.23f, center = Offset(w * 0.70f, h * 0.40f))
        // beak (triangle pointing right)
        drawPath(
            Path().apply {
                moveTo(w * 0.85f, h * 0.34f)
                lineTo(w * 0.99f, h * 0.42f)
                lineTo(w * 0.85f, h * 0.50f)
                close()
            },
            beak,
        )
        // eye
        drawCircle(eye, radius = h * 0.035f, center = Offset(w * 0.77f, h * 0.34f))
    }
}
