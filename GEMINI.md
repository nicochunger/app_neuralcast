# NeuralCast Radio

**NeuralCast Radio** is a modern Android application designed for streaming specific web radio stations. It is built using **Kotlin** and **Jetpack Compose**, leveraging **Material Design 3** for a sleek, native UI. The app uses **AndroidX Media3 (ExoPlayer)** for robust media playback and seamless system integration (background playback, notifications).

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

The project uses Gradle with Kotlin DSL scripts (`.kts`).

### Prerequisites
*   JDK 17 or higher.
*   Android SDK (Command line tools or Android Studio).
*   Valid `local.properties` file with `sdk.dir` (if not set in env).

### Common Commands

*   **Build Debug APK:**
    ```bash
    ./gradlew assembleDebug
    ```
    *Output:* `app/build/outputs/apk/debug/app-debug.apk`

*   **Run Unit Tests:**
    ```bash
    ./gradlew testDebugUnitTest
    ```

*   **Run Lint Checks:**
    ```bash
    ./gradlew lintDebug
    ```

*   **Clean Project:**
    ```bash
    ./gradlew clean
    ```

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
