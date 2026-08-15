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
| `yomp_lyrics` | 가사 캐시(곡 ID → 가사, 못 찾은 것도 7일간 기억) |

## 가사 (LRCLIB + 직접 만든 .lrc)

전체화면 플레이어 우상단 **가사 아이콘**을 누르면 아트워크 자리에 가사가 뜬다. 싱크(LRC) 가사가 있으면 **부르는 줄이 크게 강조되고 자동으로 따라 스크롤**된다(가라오케). 아이용 기능이라 부모 PIN 게이트 **밖**에 둔다.

**왜 자체 구현인가:** **Spotify 는 가사를 공개 API 로 제공하지 않는다.** Spotify 앱 안의 가사는 Musixmatch 에서 라이선스로 받아 자사 앱에만 넣는 것이고, Web API·App Remote SDK 어디에도 가사 필드가 없다. 개발 모드 403 과 달리 **정식 모드로 승격해도 열리지 않는다.** 비공식 내부 엔드포인트(`spclient…/color-lyrics`)는 약관 위반 + 수시로 깨져서 쓰지 않는다.

**폴백 체인** (`data/LyricsRepository.kt`):

1. **로컬 파일** — `Android/data/com.plocoli.yomp/files/lyrics/` 의 `.lrc`(또는 `.txt`). **항상 먼저 확인하고 캐시하지 않으므로**, 파일을 넣으면 다음 재생부터 즉시 반영되고 인터넷 가사를 덮어쓴다. 파일 이름은 `가수 - 제목.lrc` 또는 `제목.lrc`(대소문자·공백·문장부호 무시).
2. **캐시** (`LyricsStore`) — 같은 곡을 반복 재생하는 아이 특성상 대부분 여기서 끝난다. 오프라인에서도 뜬다. 못 찾은 것도 캐시하되 7일 뒤 만료(LRCLIB 는 계속 커지므로).
3. **LRCLIB** (`LrclibSource`) — 무료·API 키 없음·레이트리밋 없음. `/api/get`(duration 이 ±2초 안에서 맞아야 함)을 먼저 시도하고, 404 면 `/api/search` 로 폴백해 길이가 가장 가까운 것을 고른다(싱크 가사 우선).

소스를 추가하려면 `LyricsSource` 를 구현해 `LyricsRepository` 의 `remoteSources` 에 넣기만 하면 된다. UI·ViewModel 은 안 건드려도 된다.

**제목 정규화가 핵심** (`TrackMatch`): Spotify 제목에는 `(feat. …)`, `- From "…"` 같은 꼬리표가 붙는데, 그대로 조회하면 **검색 결과가 0건**이 된다(실측: `Golden (feat. …) - From KPop Demon Hunters` → 0건, `Golden` → 20건). 아티스트도 첫 번째만 쓴다. 단위 테스트는 `app/src/test/.../LyricsTest.kt`.

**커버리지 (2026-08 실측, 검색 상위 20건 중 싱크 가사 보유):** K-pop 걸그룹은 거의 전부(BLACKPINK 20/20, IVE·aespa·NewJeans 19/20, (G)I-DLE·KATSEYE·BABYMONSTER 18/20), 영화·캐릭터는 준수(HUNTR/X 20/20, Encanto·Moana·KPop Demon Hunters 16/20, Kidz Bop 15/20, My Little Pony 10/20, Frozen 9/20), **일본 동요는 거의 없음**(ケロポンズ 1건, エビカニクス 2건, にじ 0건).

일본 동요는 상업 DB(Musixmatch·プチリリ 등)에도 없다 — 차트 음악이 아니라서다. 그래서 **직접 `.lrc` 를 만들어 위 폴더에 넣는 것**이 정답이고, 만든 김에 [LRCLIB 에 업로드](https://lrclibup.boidu.dev/)하면(가입·키 불필요) 다음부터는 앱이 자동으로 받아온다. 단 **LRCLIB 는 등록한 가사의 삭제·교체를 지원하지 않는다**(영구).

부모 설정에 **"가사 다시 받기"**(캐시 비우기)와 위 폴더 경로 안내가 있다.

## 자주 하는 업데이트 방법

- **즐겨찾기 바꾸기(운영 중)**: 앱 홈 우상단 **연필(✏️)** → 추가/삭제. 재빌드 불필요. 기본값 자체를 바꾸려면 `Favorites.kt` 수정.
  - 검색어 팁: 표시 이름이 흔한 단어·다른 곡과 겹치면 `query` 를 더 구체적으로 지정(예: 제목 "Frozen" → query "Disney Frozen" 처럼 "Disney ○○", 또는 작곡가/대표 아티스트명). 비영어권 노래는 원어 표기를 query 로 넣어야 결과가 정확하고, 키워드가 엉뚱하게 걸리면(무섭거나 커버·연주 위주) 더 구체적인 아티스트명으로 바꾼다.
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
- 콜드부팅 직후 Wi-Fi 지연으로 즐겨찾기 검색이 실패할 수 있어 `loadHome` 에 재시도 로직 있음(1.5초 간격 6회 ≈ 7.5초).
- **그 재시도가 끝난 뒤 인터넷이 돌아오는 경우**를 위해 `YompViewModel` 이 `ConnectivityManager.NetworkCallback` 을 등록해 **네트워크 복귀 시 `loadHome()` 을 자동 호출**한다(매니페스트 `ACCESS_NETWORK_STATE`). 키오스크에서는 앱이 포그라운드를 안 떠나 `ON_RESUME` 이 몇 시간씩 안 걸릴 수 있어, 이게 없으면 Wi-Fi 가 돌아와도 홈이 계속 반쪽인 채로 남는다.

### 홈 레일이 최근·좋아요 둘만 나올 때 (2026-08-14)

최근·좋아요는 **로컬 저장**이라 오프라인에서도 뜨고, 즐겨찾기 레일은 **Spotify 검색**이라 인터넷이 없으면 전부 사라진다. 이 조합이 보이면 십중팔구 앱이 아니라 **네트워크 문제**다. 실제 사례: 아이가 빠른설정에서 비행기모드를 켜 둠(홈앱 이탈로 알림창이 열려 있던 그 시간에). 진단·복구:

```bash
adb -s <시리얼> shell dumpsys connectivity | grep 'Active default network'   # none 이면 네트워크 없음
adb -s <시리얼> shell settings get global airplane_mode_on                    # 1 이면 비행기모드
adb -s <시리얼> shell cmd connectivity airplane-mode disable
adb -s <시리얼> shell svc wifi enable
```

> 키오스크 중에는 빠른설정이 막혀 **태블릿에서 Wi-Fi 를 고칠 수 없다.** 현재는 PC+adb 가 유일한 방법(또는 부모 PIN 으로 잠금 해제 후 복구). 부모 패널에 Wi-Fi 바로가기를 넣는 건 미구현.

## 기기 잠금 (키오스크 / Device Owner)

욤프는 안드로이드 **Lock Task(키오스크) 모드**를 지원한다. 켜면 알림창·빠른설정·설정·데일리보드·다른 앱이 **전부 막히고**, 홈 우상단 연필(부모 설정) → **부모 PIN**으로만 빠져나온다. 이 기능은 앱이 기기의 **Device Owner** 일 때만 켜지고, 아니면 자동으로 비활성(그냥 일반 홈앱처럼 동작).

> 왜 필요? "홈앱 지정"만으로는 잠기지 않는다(알림창을 내려 설정에 들어갈 수 있음). 그리고 홈앱은 "최근 앱"에 안 떠서 일반 화면 고정(App pinning)도 못 건다. 그래서 Device Owner + Lock Task 가 유일한 native 완전 잠금 경로.

### 최초 1회 프로비저닝 (PC에서 adb)
Device Owner 는 **기기에 계정이 하나도 없을 때만** 설정된다.
1. 기기에서 **모든 계정 제거**: 설정 → 계정 및 백업 → 계정 관리 → 각 계정 → 제거. (Spotify 로그인은 Spotify 앱 내부 계정이라 영향 없음. Play 스토어 자동 업데이트만 막힘 — 전용 기기엔 무방)
2. 욤프가 설치된 상태에서 PC에서:
   ```bash
   adb shell dpm set-device-owner com.plocoli.yomp/.kiosk.YompDeviceAdminReceiver
   ```
   `Success` 나오면 완료. ("already some accounts on the device" 오류 → 1번 계정 제거를 다시 확인)
3. 욤프를 껐다 켜거나 재부팅 → 자동으로 잠금 모드 진입.

### 홈앱 지정은 반드시 "영구 지정"으로 (2026-08-14 사고)

**증상:** 아이가 태블릿을 껐다 켰더니 욤프가 아니라 삼성 One UI Home 이 떴다. 잠금이 통째로 무력화된 상태.

**원인 사슬:** 기본 홈 지정이 삼성 런처로 풀림 → 부팅 시 욤프가 **아예 실행되지 않음** → `MainActivity.onResume` 이 안 도니 `startLockTask()` 도 호출 안 됨 → Device Owner 도 `kioskEnabled=true` 도 멀쩡했는데 잠금이 안 걸림. **홈앱 지정이 잠금 사슬의 첫 고리**라는 점이 핵심이다.

**따라서 `adb shell cmd package set-home-activity` 로만 지정하면 안 된다.** 그건 일반 preferred activity라 언제든 풀릴 수 있다. Device Owner 권한으로 `addPersistentPreferredActivity`(ACTION_MAIN + CATEGORY_HOME/DEFAULT)를 걸어야 **재부팅·앱 재설치·설정 변경으로 안 풀린다**. `KioskManager.setAsPersistentHome()` 이 이걸 하고, `configureIfOwner()` 가 호출하며, `BootReceiver` 도 매 부팅 `configureIfOwner()` 를 부른다(액티비티 실행에 의존하지 않도록).

검증법: `adb shell cmd package set-home-activity <삼성런처>` 가 `Success` 로 떨어져도 `adb shell cmd package resolve-activity -c android.intent.category.HOME -a android.intent.action.MAIN` 이 계속 `com.plocoli.yomp` 를 가리키면 정상.

> ⚠️ `clearDeviceOwner()` 는 소유권을 포기하기 **전에** `clearPackagePersistentPreferredActivities` 를 먼저 부른다. 순서를 바꾸면 키오스크를 제거한 뒤에도 기기가 욤프 홈에 갇히고, Device Owner 가 없어져서 되돌릴 방법이 팩토리 리셋뿐이 된다.

### 동작
- `MainActivity.onResume` 에서 Device Owner + kiosk armed 상태면 `startLockTask()`.
- **나가기**: 홈 우상단 연필 → PIN(기본 **`1234`**) → "기기 잠금 해제하고 나가기".
- **다시 잠그기**: 부모 설정 → "지금 다시 잠그기". 재부팅하면 `BootReceiver` 가 자동 재잠금(kioskEnabled=true 리셋)이라, 부모의 임시 해제는 재시작으로 원복.
- **PIN 변경**: 부모 설정 → "PIN 변경"(숫자 4자리 이상).

### 되돌리기 (팩토리 리셋 없이)
- 앱 안: 부모 설정 → **"키오스크 기능 완전 제거"** (`clearDeviceOwnerApp`).
- adb: `adb shell dpm remove-active-admin com.plocoli.yomp/.kiosk.YompDeviceAdminReceiver`

### 코드
`kiosk/KioskManager.kt`(lock task 제어), `kiosk/YompDeviceAdminReceiver.kt`, `kiosk/BootReceiver.kt`, `data/SettingsStore.kt`(PIN·arm 상태), `res/xml/device_admin.xml`, 매니페스트의 receiver 등록. Spotify(`com.spotify.music`)도 lock task allowlist 에 포함(App Remote 바인딩용).

## 알려진 이슈 / 다음에 할 만한 것

- **[백로그] 재생바 캐릭터 선택** — 진행바 썸을 오리 말고 다른 동물(고양이/햄스터/강아지 등)로 바꾸는 옵션. **PIN 없이 아이가 직접** 고를 수 있게(부모 게이트 밖). 구현 메모: 현재 `DuckThumb()`(`ui/YompUi.kt`)를 여러 캐릭터 Canvas 로 확장 → 선택값을 로컬 저장(예: `SettingsStore` 에 `thumb_character`, PIN 무관) → 전체화면 플레이어 어딘가에 귀여운 "동물 고르기" 버튼(홈/플레이어에서 바로 접근). 부모 게이트와 무관한 아이용 설정이라는 점 주의.
- 부모 PIN 기본값 `1234` — 프로비저닝 후 부모 설정에서 바꾸는 걸 권장. 저장은 평문(SharedPreferences)이라 6세 방지 수준.
- 인트로 "두둥" **사운드 미구현** (res/raw 오디오 넣으면 됨).
- Device Owner 미설정 시엔 완전 잠금이 안 됨(연필은 PIN으로 막히지만 알림창 이탈은 가능) — 완전 잠금은 위 프로비저닝 필요.
- 임시 디버그 로그(`YompApi`) 제거 여지.
```
