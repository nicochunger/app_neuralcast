# Android Web Radio Player - Implementation Spec

## Goal
Build a simple Android app that plays two specific web radio MP3 streams. Each station has its own play button. Only one station can play at a time. The app should feel modern and native, using Material Design 3 and standard Android media behavior.

## Executive Decisions
- Language: Kotlin
- UI: Jetpack Compose with Material 3 components
- Architecture: Single-activity, MVVM with a small state holder and unidirectional data flow
- Playback: AndroidX Media3 (ExoPlayer) for streaming and media session integration
- Min SDK: 24 (Android 7.0) or higher
- Target SDK: latest stable at implementation time (currently 34)

## Functional Requirements
- Two stations with name and stream URL (placeholders for now).
- Tap a station's play button to start playback.
- If another station is playing, it stops and the new station starts.
- Playback state is reflected in the UI (idle, buffering, playing, error).
- Show the current song title when stream metadata is available.
- Audio focus is handled correctly (pause/duck when needed).
- Media controls appear on lock screen and in the notification shade.

## Non-Goals
- User account, favorites, playlists, downloads.
- Advanced audio effects or equalizer.
- Custom streaming protocol support beyond MP3 HTTP streams.

## Data Model
- `RadioStation`
  - `id: String`
  - `name: String`
  - `streamUrl: String`
  - `description: String?` (optional)
- `PlaybackState`
  - `Idle | Buffering | Playing | Error`
- `UiState`
  - `stations: List<RadioStation>`
  - `activeStationId: String?`
  - `playbackState: PlaybackState`
  - `nowPlaying: String?`
  - `errorMessage: String?`

## Architecture Overview
- `MainActivity` hosts a single Compose screen.
- `RadioPlayer` wraps Media3 `ExoPlayer`, exposes play/stop, and reports state changes.
- `RadioPlayerViewModel` owns `UiState`, routes user actions, and coordinates playback.
- Media session service holds the player for background playback and OS integration.

## UI/UX
- Material 3 theme, dynamic color on Android 12+.
- Top app bar with app name.
- Two station cards, each with title and a primary play button.
- Play button toggles to a stop icon when that station is active.
- Status line under each card for current playback state.
- Now playing line shows current song title when available.
- Snackbar for errors (network failure, stream unavailable).

## Playback Behavior
- Create `ExoPlayer` with Media3 and set audio attributes for media playback.
- Use a `MediaSession` and `MediaSessionService` for system integration.
- When user taps play:
  - If the same station is playing, stop.
  - Otherwise, stop current playback, set new media item, prepare, play.
- Request ICY metadata (`Icy-MetaData: 1`) so the stream can deliver current song titles.
- Handle "audio becoming noisy" and audio focus automatically.
- Release player when service is destroyed.

## Key APIs and Patterns (Reference-Level)
- Compose Material 3 theme scaffold:
  - Use `MaterialTheme` as the root of the UI tree.
- Media3 playback setup:
  - `ExoPlayer.Builder(context).build()`
  - `MediaItem.fromUri(streamUrl)`
  - `player.setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ true)`
  - `player.setMediaItem(mediaItem)` then `player.prepare()` and `player.play()`
  - `player.stop()` and `player.release()` on teardown
- Playback state:
  - `Player.Listener` to observe state and errors for `UiState` updates
- Media session integration:
  - `MediaSession` bound to the player inside a `MediaSessionService`
  - Notification controls through Media3 session and UI components

## Permissions and Manifest
- `INTERNET`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (Android 14+)
- `POST_NOTIFICATIONS` runtime permission on Android 13+ for media notification

## Dependencies (Gradle)
- Jetpack Compose BOM
- `androidx.compose.material3:material3`
- `androidx.activity:activity-compose`
- `androidx.lifecycle:lifecycle-viewmodel-compose`
- `androidx.media3:media3-exoplayer`
- `androidx.media3:media3-session`
- `androidx.media3:media3-ui` (for notification manager)

## Edge Cases and Error Handling
- Stream fails to load: show a snackbar and reset to `Idle`.
- Network loss: transition to error state and stop playback.
- Tap play repeatedly: debounce or ignore until state changes from buffering.
- App background: keep playback via service and update notification state.

## Implementation Phases and Tasks

### Phase 1 - Project Setup
- [x] Create project structure (Compose single-activity app).
- [x] Set Kotlin, Compose compiler, and target SDK to latest stable.
- [x] Add dependencies listed above.
- [x] Configure `AndroidManifest.xml` with permissions and service declaration.
- [x] Add station list with real URLs.

### Phase 2 - Core Playback Layer
- [x] Implement `PlaybackService` with Media3 `ExoPlayer`.
- [x] Add audio attributes and audio focus handling.
- [x] Implement play/stop and state callbacks through `MediaController`.
- [x] Add `MediaSessionService` and `MediaSession` binding.
- [x] Create notification integration for playback controls.

### Phase 3 - UI and State Management
- [x] Build Compose UI with Material 3 components.
- [x] Implement `UiState` and `RadioPlayerViewModel`.
- [x] Wire play/stop actions to Media3 controller.
- [x] Reflect playback state in the UI and show errors via snackbar.
- [x] Ensure only one station can play at a time.
- [x] Display current song metadata when available.

### Phase 4 - Polish and Integration
- [x] Add dynamic color support and theming defaults.
- [x] Add icon and accessibility labels for controls.
- [x] Handle app lifecycle and release resources cleanly.
- [ ] Verify behavior with screen off, Bluetooth, and audio focus transitions.

### Phase 5 - Testing and Validation
- [ ] Manual test: start/stop, station switching, error handling, background playback.
- [ ] Optional unit tests for ViewModel state transitions.
- [ ] Smoke test on Android 12+ and Android 9 or 10.

## Placeholder URLs
## Station List
- NeuralCast: `https://neuralcast.duckdns.org/listen/neuralcast/radio.mp3`
- NeuralForge: `https://neuralcast.duckdns.org/listen/neuralforge/radio.mp3`

## Notes and References
- Jetpack Compose and Material 3 are used for modern UI.
- Media3 provides ExoPlayer and MediaSession APIs for reliable streaming and OS media integration.
- API notes above align with official Compose and Media3 documentation.
