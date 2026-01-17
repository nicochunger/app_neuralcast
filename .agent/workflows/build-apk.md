---
description: Build the debug APK for the application
---
# Build APK Workflow

Use this workflow to build a new debug APK after making changes to the codebase.

1. Ensure all changes are saved.
// turbo
2. Run the Gradle assemble task to build the APK:
   `./gradlew :app:assembleDebug`
3. Verify the APK location:
   `app/build/outputs/apk/debug/app-debug.apk`
