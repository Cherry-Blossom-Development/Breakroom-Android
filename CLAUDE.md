# Android Project - Claude Context

## Project Overview
Native Kotlin Android app for Breakroom — a multi-platform music session recorder.
Same backend API as the web app at https://www.prosaurus.com/api.

## Build & Install

```bash
# Always use --rerun-tasks (incremental build and clean both fail due to Gradle caching)
cd "C:/Users/dalla/Repo/Android"
./gradlew.bat assembleDebug --rerun-tasks
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> Use forward slashes in `cd` commands — backslash paths fail in the bash tool on Windows.

## Build Variants

| Variant | BASE_URL | Purpose |
|---------|----------|---------|
| `debug` | `http://10.0.2.2:3001/` (env override) | Local dev via emulator loopback |
| `dev` | `https://test.dev.prosaurus.com/` | Dev environment |
| `productionTest` | `https://test.prosaurus.com/` | Pre-release testing |
| `release` | `https://www.prosaurus.com/` | Production |

URL set via `BuildConfig.BASE_URL` in `app/build.gradle.kts`. Active environment overridden by `environments/active.properties` (set by `switch-env.ps1`, not committed).

## Compose / Material3 Notes

**Compose BOM version**: `2023.08.00` (older)
- No `Icons.AutoMirrored.*` — use `Icons.Default.*` or `Icons.Outlined.*`
- No `HorizontalDivider` — use `Divider()`
- `testTagsAsResourceId` is `@OptIn(ExperimentalComposeUiApi::class)`

## Architecture

- **ViewModel**: `SessionsViewModel`, `ChatViewModel`, etc.
- **Repository**: `SessionsRepository`, `ChatRepository` — wraps API calls
- **Network**: Retrofit + OkHttp via `RetrofitClient` / `BreakroomApiService`
- **Auth**: JWT stored in `EncryptedSharedPreferences` via `TokenManager`
- **Real-time**: Socket.IO (`io.socket:socket.io-client:2.1.0`) in `SocketManager`
- **Chat notifications**: `ChatService` foreground service with two channels:
  - `chat_service_channel` — silent, keeps service alive
  - `chat_message_channel` — high importance, system sound, for incoming messages

## Sessions Feature

### Audio Playback
- `NowPlayingBar` routes by `mime_type`: WAV → `NowPlayingBarWav`, everything else → `NowPlayingBarExo`
- `NowPlayingBarWav`: downloads WAV via OkHttp with Bearer auth, parses RIFF header, resamples if needed, plays via `AudioTrack` at 44100Hz `MODE_STREAM`
- `NowPlayingBarExo`: ExoPlayer + OkHttp interceptor for Bearer auth — handles M4A and other compressed formats
- `nowPlayingMimeType` tracked in `SessionsViewModel`

### WAV Playback Pipeline (NowPlayingBarWav)
1. Download from `api/sessions/{id}/stream` with `Authorization: Bearer {token}`
2. Validate RIFF header (bytes 0-3 must be `RIFF`)
3. Parse sample rate (bytes 24-27) and channels (bytes 22-23)
4. Find `data` chunk starting at byte 12
5. Resample to 44100Hz if needed using `resample16MonoPcm()` (linear interpolation) — handles pre-existing 48kHz files
6. Play via `AudioTrack` at 44100Hz, `CHANNEL_OUT_MONO` or `CHANNEL_OUT_STEREO`, `ENCODING_PCM_16BIT`, `MODE_STREAM`

> **Note**: Server-side normalization (EBU R128, 44100Hz output) is planned. Once deployed, new uploads will arrive pre-normalized and the resampling path will never trigger for new files — kept as fallback for old files.

### Upload
- `SessionsRepository.uploadSession()` — multipart POST with `audio` file part + metadata
- Supported formats from web: MP3, WAV, AAC, OGG, FLAC, M4A, WebM, Opus
- Server converts all uploads to normalized WAV (planned — see Audio Normalization section)

## Audio Normalization (Planned)

Server-side FFmpeg pipeline converts all uploads to: **44100Hz, 16-bit PCM WAV, EBU R128 -14 LUFS**.
- Fixes cross-device volume inconsistency (recordings sound very quiet on some devices)
- Enables future mixing (all files at same spec)
- Android client normalization code will be removed once server pipeline is deployed

## Appium E2E Testing Notes

- Selectors: use `Modifier.testTag("id")` + `testTagsAsResourceId = true` at root `Surface`
- `$('android=new UiSelector().resourceId("tag-name")')` — no package prefix
- Session reset between tests: `mobile: clearApp` before `activateApp`
- Test backend runs on port 3001 (Docker test environment)
- Test credentials: `testuser/TestPass123`, `testadmin/TestPass123`
- Use `127.0.0.1` not `localhost` in wdio config (Windows IPv6 issue)
- uiautomator2 version must be `3.9.2` (v7.x incompatible with Node 24)

## Key Files

| File | Purpose |
|------|---------|
| `app/build.gradle.kts` | Dependencies, build variants, signing config |
| `app/src/main/java/.../ui/screens/SessionsScreen.kt` | Sessions UI + WAV/ExoPlayer playback |
| `app/src/main/java/.../ui/screens/SessionsViewModel.kt` | Sessions state (nowPlayingId, mimeType, etc.) |
| `app/src/main/java/.../data/SessionsRepository.kt` | API calls for sessions, bands, instruments |
| `app/src/main/java/.../network/SocketManager.kt` | Socket.IO connection and events |
| `app/src/main/java/.../service/ChatService.kt` | Foreground service + message notifications |
| `app/src/main/java/.../network/RetrofitClient.kt` | Retrofit setup, BASE_URL from BuildConfig |

## Dependencies (Notable)

- `androidx.media3:media3-exoplayer:1.2.1` — ExoPlayer for compressed audio
- `androidx.media3:media3-datasource-okhttp:1.2.1` — OkHttp data source for ExoPlayer
- `io.socket:socket.io-client:2.1.0` — Socket.IO (exclude `org.json:json`)
- `io.coil-kt:coil-compose:2.5.0` — image loading
- `androidx.security:security-crypto:1.1.0-alpha06` — EncryptedSharedPreferences

## Known Issues / Notes

- `2023.08.00` BOM doesn't have newer Material3 APIs — check memory before using any new icon or component
- Gradle caching is aggressive — always use `--rerun-tasks`, never `clean` alone
- `AudioTrack` at native rate (44100Hz) bypasses AudioFlinger resampler which was producing silence on test device
