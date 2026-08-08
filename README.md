# 욤프 (Yomp)

A distraction-free, music-only Android player for young kids.

Yomp is a kid-safe music app built for a 6-year-old: it plays audio from Spotify (and local files) with **no music videos, no wandering to other apps**. It's designed to run as the device's home app on a dedicated tablet, so turning the tablet on goes straight into Yomp.

> Motivation: Spotify's own apps now surface music videos and other content. Yomp uses the Spotify **Web API** (metadata only) for browsing/search and the **App Remote SDK** (audio only) for playback, so there is no code path that can ever show a video.

## Features

- 🎵 **Music-only playback** via Spotify App Remote (audio only — no video)
- 🏠 **Home-app mode** — power on → Yomp, nothing else reachable via home/back
- ⭐ **Parent-curated favorites** shown as home rails, **editable in-app** (no rebuild)
- 🔎 Search + **artist deep-dive** (paginated)
- ❤️ In-app likes and an instantly-updating "recently played" rail (stored locally)
- 🙈 **Hide any song** with a long-press (per-track block)
- 📴 **Offline local audio** — plays MP3/M4A files stored on the device (no internet)
- 🦆 A cute duck floating along the progress bar
- 🎬 Branded intro splash

## Tech stack

Kotlin · Jetpack Compose (Material 3) · Spotify Web API (OAuth 2.0 Authorization Code + PKCE) · Spotify App Remote SDK · OkHttp · kotlinx.serialization · Coil 3 · MediaStore/MediaPlayer for local audio.

## Setup

This project needs a few things that are **not** committed (secrets / third-party SDK):

### 1. Spotify Developer app
1. Create an app at the [Spotify Developer Dashboard](https://developer.spotify.com/dashboard).
2. In its settings, enable **Web API** and **Android**.
3. Add redirect URI: `yomp://callback`
4. Under **Android Packages**, add:
   - Package name: `com.plocoli.yomp` (or your own `applicationId`)
   - SHA1 fingerprint of your debug keystore — get it with:
     ```bash
     keytool -list -v -keystore ~/.android/debug.keystore \
       -alias androiddebugkey -storepass android -keypass android | grep SHA1
     ```

### 2. Client ID → `local.properties`
Create/edit `local.properties` in the project root (it is git-ignored):
```properties
SPOTIFY_CLIENT_ID=your_client_id_here
SPOTIFY_REDIRECT_URI=yomp://callback
```
No client secret is needed (PKCE).

### 3. Spotify App Remote SDK (.aar)
The App Remote SDK is not redistributed here. Download it from
[spotify/android-sdk releases](https://github.com/spotify/android-sdk/releases) and place it at:
```
app/libs/spotify-app-remote-release-0.8.0.aar
```

### 4. On the device
- Install the **Spotify** app and sign in (App Remote streams audio through it).
- A **Spotify Premium** account is required for full-track playback.

### 5. Build & run
```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Keep the **debug** build — the debug keystore's SHA1 is what you registered with Spotify. A release build has a different SHA1 and App Remote will fail unless you register that too.

## Notes / limitations

Spotify restricts some Web API features for apps in **development mode**, which shaped a few design choices:
- Search `limit` is capped at 10 → artist deep-dive uses offset pagination.
- Recommendations / featured playlists / top-tracks are unavailable → "favorites" are parent-curated searches.
- `PUT /me/tracks` (save to library) is blocked → likes are stored **locally** on the device.

Curated favorites live in `app/src/main/java/com/plocoli/yomp/Favorites.kt` as defaults, and are editable in-app (stored on device).

## License

[MIT](LICENSE)
