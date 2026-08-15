# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

욤프(Yomp) = 6세 아이 전용 안드로이드 음악 **전용** 플레이어. 전용 태블릿의 **홈앱(런처)** 으로 동작해 켜면 바로 음악 화면이 뜨고, 뮤직비디오나 다른 앱으로 새어나갈 경로가 없다.

먼저 읽을 문서: **`README.md`**(셋업 — Spotify 앱 등록·Client ID·.aar), **`DEVELOPMENT.md`**(아키텍처 상세, Spotify 개발 모드 제약표, 키오스크 프로비저닝 절차, 기기 세팅 함정, 백로그). 자잘하지 않은 변경 전엔 `DEVELOPMENT.md` 를 볼 것. 아래는 그 문서와 코드에서 뽑은, 작업 전에 알아야 할 것들.

## 빌드 · 설치 · 테스트

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"   # 시스템 JDK 로는 빌드 안 됨
./gradlew :app:assembleDebug                      # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest                  # 단위 테스트 (현재 템플릿 테스트만 있음)
./gradlew :app:testDebugUnitTest --tests 'com.plocoli.yomp.ExampleUnitTest'   # 단일 테스트
./gradlew :app:lint                               # Android Lint

adb -s R8YL41Z0TRB install -r app/build/outputs/apk/debug/app-debug.apk   # 갤럭시탭 A9 (실기기)
adb -s R8YL41Z0TRB logcat | grep -i yomp
```

- adb/emulator 는 `~/Library/Android/sdk/{platform-tools,emulator}/`. 에뮬레이터 AVD 이름 `Yomp_Tablet`.
- **에뮬레이터엔 Spotify 앱이 없어 재생이 안 된다** — UI·로그인·검색·브라우징만 확인용. 재생 관련 변경은 반드시 실기기로 검증.
- `local.properties` 의 `SPOTIFY_CLIENT_ID` 가 `BuildConfig` 로 주입된다(깃 제외). PKCE 라 secret 불필요.

## 절대 깨면 안 되는 제약

- **release 빌드로 바꾸지 말 것.** Spotify 대시보드에 등록된 SHA1 이 이 Mac 의 debug 키스토어(`B3:9A:34:...:73:D2`) 것이라, release 키로 서명하면 **App Remote 연결이 실패해 재생이 죽는다.** debug APK 는 만료되지 않는다.
- **영상 경로를 만들지 말 것.** 메타데이터는 Web API(REST), 오디오는 App Remote — 코드에 비디오 개념이 아예 없는 것이 이 앱의 존재 이유다.
- **Spotify 개발 모드 제약**(`DEVELOPMENT.md` 의 표 참고): 검색 `limit` 최대 10, 추천/Featured/top-tracks/`PUT /me/tracks` 는 403. 그래서 좋아요·최근 재생·즐겨찾기는 전부 **기기 로컬(SharedPreferences)** 이 진짜 소스다. 새 기능을 붙일 때 "Spotify 가 해주겠지"라고 가정하지 말고 먼저 이 표를 확인할 것.
- **부모용 기능은 PIN 게이트 뒤**(홈 우상단 연필 → `PinGate`), 아이용 설정은 게이트 밖. 이 구분을 지킬 것.

## 아키텍처 — 여러 파일을 읽어야 보이는 것들

**상태는 `YompViewModel` 하나뿐.** DI·리포지토리 계층 없음. 화면 상태는 `mutableStateOf`(+ `private set`), 재생 상태만 `StateFlow`(플레이어에서 흘러옴). 새 화면 상태를 추가할 곳도 여기다.

**홈 레일 조립 (`loadHome` / `refreshDynamic` / `rebuildHome`)** — 홈은 세 뭉치를 합쳐 만든다:
- `favSections` — 부모 큐레이션 즐겨찾기. `FavoritesStore`(로컬) 의 각 `Fav(title, query)` 를 **검색해서** 레일로 만든다(추천 API 가 403이라 검색이 추천을 대신함). 한 번 성공하면 캐시. **콜드부팅 직후엔 Wi-Fi 가 안 올라와 전부 실패하므로 1.5초 간격 최대 6회 재시도** 하는 로직이 있다 — 이 재시도를 없애면 전원 켠 직후 홈이 빈다.
- `recentSection` / `likedSection` — `RecentStore` / `LikeStore`(로컬)에서 매 resume 마다 갱신. 최근 재생은 서버 기록이 느려서 **재생 즉시 로컬에 prepend** 하고, 로컬이 비었을 때만 서버 기록으로 1회 시드한다.
- 순서는 `rebuildHome()` 이 정한다(최근 → 좋아요 → 즐겨찾기). `blockedIds`(`BlockStore`) 는 모든 레일·검색·딥다이브에서 필터링된다.

**재생 엔진 둘, 상호배타.** `player/Player.kt` 인터페이스를 `AppRemotePlayer`(Spotify, 실제 재생)와 에뮬용 `NoopPlayer`(무음 스텁, 현재 미사용) 가 구현한다. 기기 로컬 파일 재생은 별개 경로인 `LocalPlayer`(MediaPlayer). **둘이 동시에 소리 내지 않도록** `play()` 는 `localPlayer.stop()`, `playLocal()` 은 `player.pause()` 를 먼저 부른다 — 재생 진입점을 추가하면 이 규칙을 지킬 것. App Remote 는 이벤트가 드물게 와서 위치 표시는 500ms 틱커로 보간한다.

**UI 는 `ui/YompUi.kt` 한 파일**(~1000줄, Compose). `MainScreen` 이 Scaffold(홈/찾기/내 음악 탭 + 재생 바) 위에 오버레이(`ArtistScreen` → `FullPlayerScreen` → `ParentScreen` → `FavoritesEditorScreen`)를 겹치는 구조다. **홈앱이라 뒤로가기를 그냥 두면 앱이 재시작되어 인트로가 다시 뜨고 재생 바가 사라진다** — `MainScreen` 의 `BackHandler` 가 오버레이만 닫고 홈에선 뒤로가기를 무시한다. **오버레이를 추가하면 이 `BackHandler` 의 `when` 에도 반드시 케이스를 넣을 것.**

**아티스트 딥다이브** — 레일 제목 탭 → `searchTracksDeep(query, pages=5)` 가 offset 0/10/20/30/40 을 병렬 요청해 합친다(검색 limit 10 제약 우회). `BrowseSection.deepDive` 가 `DeepDive.Artist(query)` 면 이 경로, `DeepDive.Recent` 면 최근 재생 전체, `null` 이면 제목이 클릭 불가.

**키오스크(Device Owner + Lock Task)** 는 `kiosk/`. `MainActivity.onResume` 에서 Device Owner **이면서** `SettingsStore.kioskEnabled` 일 때만 `startLockTask()`, 아니면 조용히 일반 홈앱으로 동작한다. 프로비저닝은 기기에 계정이 하나도 없어야 가능(절차는 `DEVELOPMENT.md`). 재부팅 시 `BootReceiver` 가 재잠금하므로 부모의 임시 해제는 재시작으로 원복된다.

## 어디를 고칠까

| 하고 싶은 것 | 건드릴 곳 |
|---|---|
| 화면·위젯 | `ui/YompUi.kt` (오버레이면 `BackHandler` 도) |
| 상태·로직 | `YompViewModel.kt` |
| Spotify 호출 | `data/SpotifyApi.kt` (+ DTO 는 `data/Models.kt`) |
| 가사 | `data/Lyrics*.kt` — 소스 추가는 `LyricsSource` 구현 후 `LyricsRepository` 에 등록 |
| 재생 제어 | `player/AppRemotePlayer.kt` (`playerApi.play/pause/skipNext/seekTo/setRepeat`) |
| 즐겨찾기 **기본값** | `Favorites.kt` — 운영 중 변경은 앱 안 연필로(재빌드 불필요) |
| 새 로컬 저장 | `data/*Store.kt` 패턴(SharedPreferences + kotlinx.serialization JSON) |

UI 문구는 한국어(아이가 읽는다), 코드 주석은 영어. 즐겨찾기 검색어는 결과가 엉뚱하거나 무서운 곡이 섞이면 더 구체적인 아티스트/작곡가명으로 바꾸는 게 정석이다(예: `Frozen` → `Disney Frozen`, 일본 동요 → `ケロポンズ`). 케이팝은 걸그룹만.
