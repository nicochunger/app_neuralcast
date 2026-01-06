# NeuralCast

NeuralCast is a modern Android application for streaming curated web radio stations. Built with Kotlin, Jetpack Compose, and AndroidX Media3, it delivers a clean Material 3 interface with reliable background playback and system media integration.

## Features

- **Dual station playback** with one-tap play/stop controls (only one station plays at a time).
- **Now Playing metadata** via ICY stream tags.
- **Animated visualizer** for active playback.
- **Sleep timer** (15/30/45/60 minutes).
- **Listening history** for recently played tracks.
- **Background playback** with MediaSession notifications and lock screen controls.

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose (Material 3)
- **Architecture:** MVVM + Unidirectional Data Flow
- **Playback:** AndroidX Media3 (ExoPlayer + MediaSessionService)
- **Min SDK:** 24 (Android 7.0)
- **Target SDK:** 34 (Android 14)

## Station Streams

- **NeuralCast:** `https://neuralcast.duckdns.org/listen/neuralcast/radio.mp3`
- **NeuralForge:** `https://neuralcast.duckdns.org/listen/neuralforge/radio.mp3`

## Project Structure

```
app/
  src/main/java/com/neuralcast/radioplayer/
    playback/PlaybackService.kt   # Media3 service + ExoPlayer
    ui/RadioScreen.kt             # Compose UI
    ui/RadioPlayerViewModel.kt    # UI state + actions
    model/RadioModels.kt          # Station + UI state models
```

## Getting Started

### Prerequisites

- **Android Studio** (latest stable)
- **JDK 17+**
- **Android SDK 34**

### Run in Android Studio

1. Open the project directory in Android Studio.
2. Let Gradle sync finish.
3. Select a device or emulator.
4. Click **Run**.

## Permissions

The app requires the following permissions:

- `INTERNET`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (Android 14+)
- `POST_NOTIFICATIONS` (Android 13+ runtime permission)

## Architecture Notes

- **RadioPlayerViewModel** is the single source of truth for UI state and playback actions.
- **PlaybackService** hosts the Media3 player + MediaSession for background playback.
- **RadioScreen** is a stateless composable that renders `UiState` and emits user intents.

## Build Notes

This project is intended to be built and run through **Android Studio**. If you use Gradle directly, ensure you have the Android SDK and JDK 17 configured.

## Contributing

1. Create a feature branch.
2. Make changes with clear, focused commits.
3. Open a pull request with a concise summary and test notes.

## License

This project is provided as-is for internal/demo use. Add a formal license file if you plan to distribute it.
