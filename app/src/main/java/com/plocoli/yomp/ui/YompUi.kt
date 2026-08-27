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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.plocoli.yomp.BrowseSection
import com.plocoli.yomp.DeepDive
import com.plocoli.yomp.YompViewModel
import com.plocoli.yomp.auth.AuthState
import com.plocoli.yomp.data.LocalLyricsSource
import com.plocoli.yomp.data.LocalTrack
import com.plocoli.yomp.data.batteryStatusFlow
import com.plocoli.yomp.data.SettingsStore
import com.plocoli.yomp.data.TrackMatch
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

    vm.favRemoveCandidate?.let { title ->
        AlertDialog(
            onDismissRequest = { vm.cancelRemoveFavorite() },
            confirmButton = { TextButton(onClick = { vm.confirmRemoveFavorite() }) { Text("빼기") } },
            dismissButton = { TextButton(onClick = { vm.cancelRemoveFavorite() }) { Text("취소") } },
            title = { Text("홈에서 빼기") },
            text = { Text("\"$title\" 줄을 홈에서 뺄까요? 나중에 다시 넣을 수 있어요.") },
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
            showPlayer && vm.showLyrics -> vm.closeLyrics()   // lyrics view first, then the player itself
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

        // Lyrics: where to drop hand-made .lrc files, and a way to re-check the database.
        Text(
            "가사 파일(.lrc)을 넣는 곳:\n${LocalLyricsSource.folderHint(context)}\n" +
                "여기 넣은 파일이 인터넷에서 받은 가사보다 우선해요. 파일 이름은 \"가수 - 제목.lrc\" 또는 \"제목.lrc\".",
            color = MaterialTheme.colorScheme.outline,
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = { vm.clearLyricsCache() }) { Text("가사 다시 받기") }

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
            var confirmPin by remember { mutableStateOf("") }
            var msg by remember { mutableStateOf<String?>(null) }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = it.filter(Char::isDigit).take(8); msg = null },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("새 PIN (숫자 4자리 이상)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { confirmPin = it.filter(Char::isDigit).take(8); msg = null },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("새 PIN 다시 입력") },
                    singleLine = true,
                    isError = msg != null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                msg?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                Button(
                    onClick = {
                        when {
                            newPin.length < 4 -> msg = "숫자 4자리 이상 입력하세요"
                            newPin != confirmPin -> msg = "두 번 입력한 PIN이 서로 달라요"
                            else -> { vm.changeParentPin(newPin); newPin = ""; confirmPin = ""; showPinChange = false }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("저장", modifier = Modifier.padding(4.dp)) }
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
                Spacer(Modifier.weight(1f))
                // Same star as on search results, for the artist as a whole.
                val starred = vm.isFavorite(title)
                IconButton(onClick = { vm.toggleFavorite(title) }) {
                    Icon(
                        if (starred) Icons.Filled.Star else Icons.Filled.StarBorder,
                        if (starred) "홈에서 빼기" else "홈에 추가",
                        Modifier.size(30.dp),
                        tint = if (starred) Color(0xFFFFC107) else MaterialTheme.colorScheme.outline,
                    )
                }
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

@Composable
private fun FullPlayerScreen(vm: YompViewModel, np: NowPlaying, onClose: () -> Unit) {
    val posRaw by vm.positionMs.collectAsState()

    Surface(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(24.dp)) {
            // The tablet gets held both ways. Landscape leaves only ~600dp of height — the portrait
            // stack (artwork + title + progress + transport + repeat) does not fit and the bottom
            // controls used to be cut off the screen, repeat button first. So in landscape the
            // artwork moves beside the controls instead of above them.
            val landscape = maxWidth > maxHeight
            val artSize = if (landscape) {
                (minOf(maxHeight - 72.dp, maxWidth / 2)).coerceIn(160.dp, 320.dp)
            } else {
                320.dp
            }

            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                PlayerTopBar(vm, onClose)

                if (landscape) {
                    Row(
                        Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            PlayerArt(vm, np, posRaw, artSize)
                        }
                        Column(
                            Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            PlayerTitle(np)
                            Spacer(Modifier.size(16.dp))
                            PlayerProgress(vm, posRaw, Modifier.fillMaxWidth())
                            Spacer(Modifier.size(8.dp))
                            PlayerTransport(vm, np)
                            Spacer(Modifier.size(8.dp))
                            RepeatButton(vm)
                        }
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                    PlayerArt(vm, np, posRaw, artSize)
                    Spacer(Modifier.size(28.dp))
                    PlayerTitle(np)
                    Spacer(Modifier.weight(1f))
                    PlayerProgress(vm, posRaw, Modifier.fillMaxWidth().padding(horizontal = 24.dp))
                    Spacer(Modifier.size(16.dp))
                    PlayerTransport(vm, np)
                    Spacer(Modifier.size(24.dp))
                    RepeatButton(vm)
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PlayerTopBar(vm: YompViewModel, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.KeyboardArrowDown, "닫기", Modifier.size(32.dp))
        }
        Spacer(Modifier.weight(1f))
        // Lyrics toggle — a kid's control, so no parent PIN in front of it.
        IconButton(onClick = { vm.toggleLyrics() }) {
            Icon(
                Icons.Filled.Lyrics,
                "가사 보기",
                Modifier.size(32.dp),
                tint = if (vm.showLyrics) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = { vm.toggleLike() }) {
            Icon(
                if (vm.isCurrentLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                "좋아요",
                Modifier.size(32.dp),
                tint = if (vm.isCurrentLiked) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Artwork, or the lyrics in its place — same footprint either way, so nothing jumps on toggle. */
@Composable
private fun PlayerArt(vm: YompViewModel, np: NowPlaying, positionMs: Long, size: Dp) {
    if (vm.showLyrics) {
        LyricsPane(vm, positionMs, Modifier.width(size).height(size))
    } else {
        Box(Modifier.size(size).clip(RoundedCornerShape(28.dp))) {
            Artwork(np.track.thumbnailUrl, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun PlayerTitle(np: NowPlaying) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            np.track.name,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (np.track.artistName.isNotEmpty()) {
            Text(np.track.artistName, fontSize = 18.sp, color = MaterialTheme.colorScheme.outline)
        }
    }
}

/** Playback progress bar (shows time + lets you seek). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerProgress(vm: YompViewModel, positionMs: Long, modifier: Modifier = Modifier) {
    val dur by vm.durationMs.collectAsState()
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    val maxV = dur.coerceAtLeast(1L).toFloat()
    val sliderValue = (if (dragging) dragValue else positionMs.toFloat()).coerceIn(0f, maxV)
    Column(modifier) {
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
}

@Composable
private fun PlayerTransport(vm: YompViewModel, np: NowPlaying) {
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
}

@Composable
private fun RepeatButton(vm: YompViewModel) {
    val repeat by vm.repeatMode.collectAsState()
    IconButton(onClick = { vm.cycleRepeat() }, modifier = Modifier.size(56.dp)) {
        Icon(
            if (repeat == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
            "반복 재생",
            Modifier.size(30.dp),
            tint = if (repeat == RepeatMode.OFF) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
        )
    }
}

// MARK: - Lyrics

/**
 * Lyrics for the current track, in place of the artwork.
 *
 * With synced (LRC) lyrics this is a karaoke view: the line being sung is large and
 * highlighted, the rest are dimmed, and the list follows along. [positionMs] comes from the
 * same interpolated ticker that drives the duck on the progress bar, so it updates smoothly
 * even though App Remote only reports its position occasionally.
 */
@Composable
private fun LyricsPane(vm: YompViewModel, positionMs: Long, modifier: Modifier = Modifier) {
    val lyrics = vm.lyrics

    Box(modifier, contentAlignment = Alignment.Center) {
        when {
            vm.isLoadingLyrics && lyrics == null -> CircularProgressIndicator()

            lyrics == null -> LyricsHint("이 노래는 가사가 없어요")

            lyrics.instrumental -> LyricsHint("가사가 없는 연주곡이에요 🎵")

            lyrics.isSynced -> {
                val current = lyrics.indexAt(positionMs)
                val listState = rememberLazyListState()
                // Keep the sung line near the top-middle of the pane as the song moves on.
                LaunchedEffect(current) {
                    if (current >= 0) listState.animateScrollToItem((current - 1).coerceAtLeast(0))
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(lyrics.lines) { index, line ->
                        if (line.text.isBlank()) {
                            Spacer(Modifier.size(8.dp))
                        } else {
                            val isCurrent = index == current
                            Text(
                                line.text,
                                Modifier.fillMaxWidth(),
                                fontSize = if (isCurrent) 24.sp else 18.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            // No timing information — just show the words.
            else -> Text(
                lyrics.plain,
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LyricsHint(text: String) {
    Text(text, color = MaterialTheme.colorScheme.outline, fontSize = 18.sp, textAlign = TextAlign.Center)
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
                            // Only favorites rails can be removed; "recently played" and
                            // "liked" are generated, not curated.
                            onRemoveRail = if (section.deepDive is DeepDive.Artist) {
                                { vm.askRemoveFavorite(section.title) }
                            } else {
                                null
                            },
                        )
                    }
                }
        }
        Row(
            Modifier.align(Alignment.TopEnd).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Kiosk hides the status bar, so this is the only battery reading on the device.
            BatteryIndicator()
            // Rebuild the rails on demand. The favorites rails come from Spotify search, so a
            // network hiccup empties them until something triggers another load — in kiosk mode
            // that may never happen on its own. No PIN: refreshing can only help.
            IconButton(onClick = { vm.refreshNow() }, enabled = !vm.isLoadingHome) {
                if (vm.isLoadingHome) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Refresh, "새로고침", tint = MaterialTheme.colorScheme.outline)
                }
            }
            // Parent entry point: opens the PIN-gated parent panel.
            IconButton(onClick = { vm.openParent() }) {
                Icon(Icons.Filled.Edit, "부모 설정", tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

/**
 * Battery level, drawn as a little battery + percentage.
 *
 * Lock Task mode hides the system status bar, so without this there is no way to tell how
 * much charge is left — short of unlocking the device with the parent PIN. Colour carries
 * the meaning for a child who cannot read the number yet: green while charging, orange
 * when low, red when nearly empty.
 */
@Composable
private fun BatteryIndicator(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val flow = remember(context) { batteryStatusFlow(context) }
    val status by flow.collectAsState(initial = null)
    val battery = status ?: return   // nothing to show until the first reading arrives

    val color = when {
        battery.isCharging -> Color(0xFF43A047)          // charging
        battery.percent <= 15 -> Color(0xFFE53935)       // almost empty
        battery.percent <= 30 -> Color(0xFFFB8C00)       // getting low
        else -> MaterialTheme.colorScheme.outline
    }

    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (battery.isCharging) {
            Icon(Icons.Filled.Bolt, "충전 중", Modifier.size(16.dp), tint = color)
        }
        Canvas(Modifier.size(width = 30.dp, height = 15.dp)) {
            val nubWidth = size.width * 0.08f
            val bodyWidth = size.width - nubWidth
            val stroke = size.height * 0.12f
            val radius = CornerRadius(size.height * 0.25f, size.height * 0.25f)

            // Outline
            drawRoundRect(
                color = color,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(bodyWidth - stroke, size.height - stroke),
                cornerRadius = radius,
                style = Stroke(width = stroke),
            )
            // Fill, proportional to the charge left
            val pad = stroke * 2f
            val fillWidth = (bodyWidth - pad * 2) * (battery.percent / 100f)
            if (fillWidth > 0f) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(pad, pad),
                    size = Size(fillWidth, size.height - pad * 2),
                    cornerRadius = CornerRadius(stroke, stroke),
                )
            }
            // Positive terminal nub on the right
            drawRoundRect(
                color = color,
                topLeft = Offset(bodyWidth, size.height * 0.32f),
                size = Size(nubWidth, size.height * 0.36f),
                cornerRadius = CornerRadius(nubWidth / 2, nubWidth / 2),
            )
        }
        Text(
            "${battery.percent}%",
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BrowseRail(
    section: BrowseSection,
    onTap: (Track) -> Unit,
    onOpenArtist: (() -> Unit)?,
    blocked: Set<String>,
    onLongPress: (Track) -> Unit,
    onRemoveRail: (() -> Unit)? = null,
) {
    val tracks = section.tracks.filter { it.id !in blocked }
    if (tracks.isEmpty()) return
    Column(Modifier.padding(bottom = 24.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                // Tap the title to open the artist, long-press it to take the rail off Home.
                .then(
                    if (onOpenArtist != null || onRemoveRail != null) {
                        Modifier.combinedClickable(
                            onClick = { onOpenArtist?.invoke() },
                            onLongClick = onRemoveRail,
                        )
                    } else {
                        Modifier
                    }
                )
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
                    val artist = TrackMatch.primaryArtist(track.artistName)
                    TrackTile(
                        track,
                        { vm.play(it) },
                        Modifier.fillMaxWidth(),
                        onLongPress = { vm.askBlock(track) },
                        isFavorite = artist.isNotEmpty() && vm.isFavorite(artist),
                        onToggleFavorite = if (artist.isNotEmpty()) ({ vm.toggleFavorite(artist) }) else null,
                    )
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
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = { onTap(track) }, onLongClick = onLongPress)
            .padding(4.dp)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp))) {
            Artwork(track.thumbnailUrl, Modifier.fillMaxSize())
            // Star = "put this singer on my Home screen". Sits on the artwork so the child
            // can add a rail straight from the song they just found.
            if (onToggleFavorite != null) {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Icon(
                        if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        if (isFavorite) "홈에서 빼기" else "홈에 추가",
                        Modifier.size(30.dp),
                        tint = if (isFavorite) Color(0xFFFFC107) else Color.White,
                    )
                }
            }
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
