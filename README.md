# NeuralCast

NeuralCast is a single-module Android radio app built with Kotlin, Jetpack Compose Material 3, Navigation Compose, DataStore, and AndroidX Media3. It streams two curated stations, keeps playback alive through a foreground media service, and exposes the same content through the phone UI, media controls, and Android Auto browse integration.

## What The App Does

- Streams the two built-in stations, NeuralCast and NeuralForge.
- Shows now-playing metadata, recent playback history, listener counts, and a sleep timer.
- Provides station schedule screens with day-by-day navigation.
- Includes Settings for theme selection and admin/host-orchestrator configuration.
- Includes an Admin Console for AzuraCast skip-track controls and host-side orchestration workflows.
- Supports background playback, media notifications, lock-screen controls, and Android Auto/media library browsing.

## Tech Stack

- Kotlin
- Jetpack Compose Material 3
- Navigation Compose
- DataStore Preferences
- AndroidX Media3 (`ExoPlayer`, `MediaSessionService`, `MediaLibraryService`)
- Min SDK 24
- Compile SDK 36
- Target SDK 36
- Java/Kotlin 17

## Key Areas Of The Codebase

- `app/src/main/java/com/neuralcast/radioplayer/MainActivity.kt` wires theme, notification permission, and navigation.
- `app/src/main/java/com/neuralcast/radioplayer/ui/` contains the main screens: radio, settings, schedule, and admin console.
- `app/src/main/java/com/neuralcast/radioplayer/playback/` contains the Media3 service, notification provider, metadata handling, and now-playing store.
- `app/src/main/java/com/neuralcast/radioplayer/data/` contains repositories for settings, station data, schedules, requests, admin, and host orchestration.
- `app/src/main/java/com/neuralcast/radioplayer/model/` contains the app's immutable state and domain models.

## Station Streams

- NeuralCast: `https://neuralcast.duckdns.org/listen/neuralcast/radio.mp3`
- NeuralForge: `https://neuralcast.duckdns.org/listen/neuralforge/radio.mp3`

## Setup

### Prerequisites

- Android Studio
- JDK 17
- Android SDK 36

### Run Locally

1. Open the project in Android Studio.
2. Let Gradle sync finish.
3. Choose a device or emulator.
4. Run the `app` configuration.

## Permissions

The app declares:

- `INTERNET`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK`
- `POST_NOTIFICATIONS`

## Verification

- `./gradlew assembleDebug`

If you change Kotlin logic or UI later, also run `./gradlew test` and/or `./gradlew lint` as appropriate. This repo currently does not include committed automated tests.

## Notes For Contributors

Keep this README as the main source of truth for architecture and setup. `GEMINI.md` and `IMPLEMENTATION_SPEC.md` were removed because they duplicated older, stale project assumptions.
