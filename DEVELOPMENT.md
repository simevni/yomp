# 개발 문서 (Yomp)

나중에 업데이트할 때 참고하는 문서. (셋업 방법은 [README](README.md) 참고)

## 한눈에 보기 — 아키텍처

- **메타데이터(검색·브라우징)** = Spotify **Web API** (REST, OkHttp). 오디오 안 다룸.
- **실제 재생(오디오)** = Spotify **App Remote SDK** — 설치된 Spotify 앱에 붙어 소리만 냄. **영상 경로가 코드에 아예 없음 = 뮤비 완전 차단.**
- **오프라인 재생** = 기기 로컬 파일(MediaStore + MediaPlayer), 인터넷 불필요.
- **로그인** = OAuth 2.0 Authorization Code + PKCE (Custom Tabs). Secret 불필요.
- **UI** = Jetpack Compose (Material 3). 상태는 `YompViewModel` 하나로.

## 프로젝트 구조

```
com.plocoli.yomp/
  MainActivity.kt          앱 진입 (Compose setContent → YompApp)
  YompApplication.kt       Coil3 이미지로더에 OkHttp 네트워크 페처 등록
  YompViewModel.kt         모든 화면 상태 + 로직 (검색/홈/재생/좋아요/숨김/편집)
  Favorites.kt             즐겨찾기 "기본값"(Fav = title+query). 앱 첫 실행 시 시드
  auth/
    SpotifyAuth.kt         PKCE, authorize URL, 토큰 교환/갱신, 스코프
    AuthManager.kt         로그인 상태 싱글턴 (Custom Tab 실행, 콜백 처리, 토큰 갱신)
    AuthCallbackActivity.kt  yomp://callback 수신 → 토큰 교환
    TokenStore.kt          토큰 SharedPreferences 저장
  data/
    Models.kt              Spotify Track/Album 등 DTO (kotlinx.serialization)
    SpotifyApi.kt          Web API 호출 (검색, 페이지네이션 딥다이브, 최근 재생 등)
    LocalAudio.kt          MediaStore 로 기기 오디오 조회 (LocalTrack)
    FavoritesStore.kt      즐겨찾기 기기 저장(편집용). 없으면 Favorites.items 로 시드
    LikeStore.kt           인앱 좋아요(로컬) 저장
    RecentStore.kt         최근 들은 노래(로컬) — 재생 즉시 prepend
    BlockStore.kt          숨긴 곡 ID 집합
  player/
    Player.kt              재생 엔진 인터페이스 + NoopPlayer(무음 스텁, 에뮬용)
    AppRemotePlayer.kt     실제 재생(App Remote). 위치 보간 틱커, seek, 반복
    LocalPlayer.kt         로컬 파일 재생(MediaPlayer)
  ui/
    YompUi.kt              모든 Composable (인트로/로그인/홈/검색/내음악/플레이어/편집)
    theme/                 Compose 테마(기본 생성물)
```

## 핵심 설계 결정과 이유

- **왜 Spotify인가, iOS 아님?** 처음 iOS + Apple MusicKit 로 만들었으나 MusicKit capability 가 **유료 Apple Developer($99/년)** 필요 → 폐기. 안드로이드는 자기 앱 무료 사이드로딩 + 보유 Spotify Premium 재사용 가능.
- **왜 App Remote, Web Playback SDK 아님?** Web Playback SDK 는 iOS WKWebView 에서 DRM·백그라운드 오디오 문제. App Remote 는 네이티브·안정적이고 **오디오 전용(영상 경로 없음)** → "뮤비 완전 차단" 보장의 핵심.
- **왜 홈앱(런처) 방식, 삼성 키즈 아님?** 삼성 키즈는 "텅 빈 화면에 앱 하나" 레이아웃이라 아이에게 '다른 앱 더' 욕구 유발. 욤프를 홈앱으로 두면 켜자마자 음악 화면 → 그 욕구 자체가 없음.
- **왜 debug 빌드 유지?** debug 키스토어의 SHA1 을 Spotify 대시보드에 등록해둠. **release 빌드는 SHA1 이 달라 App Remote 가 깨짐.** debug APK 는 만료 안 됨.

## Spotify 개발 모드 제약 (중요 — 기능 설계에 큰 영향)

앱이 대시보드 "development mode"라 아래가 막혀 있음. 우회책도 함께.

| 막힌 것 | 우회 |
|---|---|
| 검색 `limit` 최대 10 (11+ → 400) | 아티스트 딥다이브는 **offset 페이지네이션**(`searchTracksDeep`) |
| 추천/Featured/장르시드/artist top-tracks → 403 | "추천" 대신 **부모 큐레이션 검색 레일**(Favorites) |
| `PUT /me/tracks`(좋아요 저장) → 403 | 좋아요를 **로컬 저장**(`LikeStore`) |
| `/me/player/recently-played` 반영 딜레이 + 앱 복귀 때만 갱신 | **로컬 `RecentStore`** 로 재생 즉시 갱신(첫 로드만 서버로 시드) |

> 이 제약을 풀려면 Spotify 에 "quota extension"(정식 모드) 신청이 필요(심사·개인정보처리방침 등). 개인용이면 현재 방식 유지 권장.

## 로컬 저장소 (SharedPreferences)

| 파일명 | 내용 |
|---|---|
| `yomp_auth` | OAuth 토큰(access/refresh/만료) |
| `yomp_favorites` | 즐겨찾기 목록(편집 반영) |
| `yomp_likes` | 인앱 좋아요 |
| `yomp_recent` | 최근 들은 노래 |
| `yomp_blocked` | 숨긴 곡 ID |

## 자주 하는 업데이트 방법

- **즐겨찾기 바꾸기(운영 중)**: 앱 홈 우상단 **연필(✏️)** → 추가/삭제. 재빌드 불필요. 기본값 자체를 바꾸려면 `Favorites.kt` 수정.
  - 검색어 팁: 이름이 흔한 단어와 겹치면(예: "IVE"↔"I've", "Frozen"↔마돈나, "にじ 童謡"↔무서운 곡) `query` 를 더 구체적으로(한글명/작곡가명/"Disney X"/대표 키즈 아티스트명). 일본 동요는 `ケロポンズ` 가 다양+깨끗.
- **곡 하나 빼기**: 그 곡 **길게 누르기** → 숨기기 (`BlockStore`).
- **새 기능 추가**: 상태·로직은 `YompViewModel`, 화면은 `ui/YompUi.kt`, 데이터는 `data/`.
- **App Remote 재생 제어 손보기**: `player/AppRemotePlayer.kt` (play/pause/next/previous/seek/setRepeat 는 `playerApi`).

## 빌드 & 설치

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd ~/AndroidStudioProjects/Yomp
./gradlew :app:assembleDebug
# 태블릿(USB 디버깅) 연결 후
adb devices
adb -s <시리얼> install -r app/build/outputs/apk/debug/app-debug.apk
```
- SDK 커맨드라인툴/adb: `~/Library/Android/sdk/...`
- 태블릿을 홈앱으로: `adb shell cmd package set-home-activity com.plocoli.yomp/.MainActivity`

## 디버깅 팁

- 저장된 토큰 열람(디버그 빌드): `adb -s <시리얼> shell run-as com.plocoli.yomp cat shared_prefs/yomp_auth.xml`
- Web API 를 직접 찔러 확인: 위 토큰으로 `curl -H "Authorization: Bearer <token>" "https://api.spotify.com/v1/search?q=...&type=track&limit=10"`
- 검색 로그: logcat 태그 `YompApi` (임시 디버그 로그 — 정리해도 됨).
- **에뮬레이터엔 Spotify 앱이 없어 재생 불가** → 브라우징/UI 만 확인용. 실제 재생은 실기기.

## 기기 세팅 시 주의 (삼성 갤럭시탭)

- USB 디버깅 토글이 비활성이면 **설정→보안 및 개인정보→자동 차단(Auto Blocker) OFF**.
- 개발자 옵션: 설정→태블릿 정보→소프트웨어 정보→**빌드번호 7번 탭**.
- 콜드부팅 직후 Wi-Fi 지연으로 즐겨찾기 검색이 실패할 수 있어 `loadHome` 에 재시도 로직 있음.

## 알려진 이슈 / 다음에 할 만한 것

- 인앱 편집(연필)에 **PIN 게이트 없음** — 아이가 열 수 있음(초기화로 복구 가능). 부모 잠금 추가 여지.
- 인트로 "두둥" **사운드 미구현** (res/raw 오디오 넣으면 됨).
- recents 버튼/알림 shade 로 이탈 가능(홈앱만으론 못 막음) — 완전 잠금 원하면 화면 고정/키오스크.
- 임시 디버그 로그(`YompApi`) 제거 여지.
```
