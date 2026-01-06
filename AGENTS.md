# Agent Responsibilities

## Verification Expectations
- When you **modify code**, run the Gradle verification command before finalizing your work.
- Follow the test matrix below to decide which checks to run.

## Test Matrix (from GEMINI.md)
- `./gradlew test` — Run for JVM unit tests when you change Kotlin/Java logic, ViewModels, or non-UI business rules.
- `./gradlew lint` — Run when you change UI, resources, or Android framework integrations.
- `./gradlew connectedAndroidTest` — **Optional** instrumented tests; only run when explicitly requested.

## Environment Constraints
- Use **JDK 17** and **Android SDK 34**.
- If the Android SDK is missing, **report it** and include a **⚠️ warning** in the final message rather than failing silently.

## Testing Section Format (Final Response Reminder)
- Use a **Testing** section with bullet points.
- Prefix each command with **✅** (pass), **⚠️** (warning), or **❌** (fail).
