# Agent Responsibilities

## Verification Expectations
- When you **modify code**, run the Gradle verification command before finalizing your work.
- Follow the test matrix below to decide which checks to run.
- After completing any **feature request**, always build an APK before finalizing (`./gradlew assembleDebug` unless told otherwise).

## Test Matrix (from GEMINI.md)
- `./gradlew test` — Run for JVM unit tests when you change Kotlin/Java logic, ViewModels, or non-UI business rules.
- `./gradlew lint` — Run when you change UI, resources, or Android framework integrations.
- `./gradlew connectedAndroidTest` — **Optional** instrumented tests; only run when explicitly requested.

## Environment Constraints
- Use **JDK 17** and **Android SDK 34**.
- If the Android SDK is missing, **report it** and include a **⚠️ warning** in the final message rather than failing silently.

## Known Good Local Verification Setup (Verified 2026-02-12)
- `local.properties` uses `sdk.dir=/home/ungern/Android/Sdk` and `platforms/android-34` is present.
- The default shell Java may be JDK 21; force JDK 17 before Gradle commands:
```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
```
- Verified commands in this repo:
```bash
./gradlew test
./gradlew lint
./gradlew assembleDebug
```
- Equivalent one-line forms:
```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64; export PATH="$JAVA_HOME/bin:$PATH"; ./gradlew test
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64; export PATH="$JAVA_HOME/bin:$PATH"; ./gradlew lint
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64; export PATH="$JAVA_HOME/bin:$PATH"; ./gradlew assembleDebug
```

## Testing Section Format (Final Response Reminder)
- Use a **Testing** section with bullet points.
- Prefix each command with **✅** (pass), **⚠️** (warning), or **❌** (fail).
