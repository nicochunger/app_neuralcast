# NeuralCast

**NeuralCast** is a modern Android application designed for streaming specific web radio stations. It is built using **Kotlin** and **Jetpack Compose**, leveraging **Material Design 3** for a sleek, native UI. The app uses **AndroidX Media3 (ExoPlayer)** for robust media playback and seamless system integration (background playback, notifications).

## Project Overview

*   **Type:** Android Application
*   **Language:** Kotlin
*   **UI Framework:** Jetpack Compose (Material 3)
*   **Architecture:** MVVM (Model-View-ViewModel) with Unidirectional Data Flow (UDF)
*   **Media Engine:** AndroidX Media3 (ExoPlayer) with MediaSessionService
*   **Minimum SDK:** 24 (Android 7.0)
*   **Target SDK:** 34 (Android 14)

### Key Features
*   **Dual Station Support:** Pre-configured for "NeuralCast" and "NeuralForge" stations.
*   **Modern Playback Controls:** Play/Stop toggles, persistent bottom bar volume slider.
*   **Visualizers:** Animated waveform indicator when active.
*   **Metadata:** Real-time "Now Playing" updates via ICY metadata.
*   **Sleep Timer:** Configurable timer (15/30/45/60 mins) to auto-stop playback.
*   **History:** Tracks recently played songs locally.
*   **Background Playback:** Continues playing when the app is backgrounded or screen is off.

## Building and Running

The project is primarily built and managed using **Android Studio**.

### Agent Testing Guidance

When changes touch Kotlin, Jetpack Compose UI, or Gradle files, run the following from the repo root:

*   `./gradlew :app:assembleDebug` (compile/app build check)
*   `./gradlew :app:testDebugUnitTest` (unit tests only)
*   Optional: `./gradlew :app:lintDebug` (static analysis)

Android Studio is still required for emulator/instrumented tests. Skip instrumented tests unless explicitly requested; CI-style Gradle checks are expected for agent changes.

### Prerequisites
*   Android Studio (latest stable version).
*   JDK 17 or higher.
*   Android SDK 34 (compile and target).

## Development Conventions

### Architecture
*   **ViewModel (`RadioPlayerViewModel.kt`):** The single source of truth for the UI. It exposes a `UiState` via `StateFlow`.
*   **UI (`RadioScreen.kt`):** A stateless Composable that renders the `UiState` and delegates events (Play, Volume, Timer) back to the ViewModel.
*   **Service (`PlaybackService.kt`):** Hosts the `ExoPlayer` and `MediaSession`. It runs as a foreground service to ensure playback continuity.
*   **Models (`RadioModels.kt`):** Immutable data classes defining the state and station structures.

### Style & Patterns
*   **Compose:** Use `MaterialTheme` from `Theme.kt`. Prefer `Scaffold` for top-level screen structure.
*   **State:** UI state is immutable (`data class UiState`). Updates are done via `_uiState.update { ... }` in the ViewModel.
*   **Resources:**
    *   **Icons:** Use `androidx.compose.material.icons`. Use `AutoMirrored` icons (e.g., `Icons.AutoMirrored.Filled.VolumeUp`) for RTL support.
    *   **Images:** stored in `app/src/main/res/drawable` (e.g., station backgrounds).

## Key Files
*   `app/src/main/java/com/neuralcast/radioplayer/ui/RadioScreen.kt`: Main UI composition.
*   `app/src/main/java/com/neuralcast/radioplayer/ui/RadioPlayerViewModel.kt`: Business logic and state management.
*   `app/src/main/java/com/neuralcast/radioplayer/playback/PlaybackService.kt`: Media3 Service implementation.
*   `app/src/main/AndroidManifest.xml`: Permissions and Service declarations.
