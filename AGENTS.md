# AGENTS.md

## Cursor Cloud specific instructions

This is an Android application (TBox Monitor for Jetour Dashing). There is no backend, no web frontend, no database — it is a single-module Gradle project producing an APK.

### Environment

- **JDK**: OpenJDK 21 (system-installed); compatible with AGP 8.11.x
- **Android SDK**: Installed at `/opt/android-sdk` (set via `ANDROID_HOME` in `~/.bashrc` and `local.properties`)
- **Gradle**: 8.13 via wrapper (`./gradlew`)

### Build commands

Build commands use the Gradle wrapper. Two product flavors exist: `ru` (Russian) and `en` (English). See `README.md` for the full list.

```
./gradlew assembleRuDebug    # Russian debug APK
./gradlew assembleEnDebug    # English debug APK
./gradlew assembleRuRelease  # Russian release APK
./gradlew assembleEnRelease  # English release APK
```

### Testing

- **Unit tests**: `./gradlew testRuDebugUnitTest` (or `testEnDebugUnitTest`)
- **Instrumented tests**: Require an Android device/emulator (API 28+) — not available in this cloud environment.
- The test source is minimal (one example unit test and one example instrumented test).

### Lint

- `./gradlew lintRuDebug` — the codebase has pre-existing lint errors (5 errors, 98 warnings as of this writing); lint will fail. This is a pre-existing condition, not caused by the dev environment.

### Caveats

- `local.properties` (with `sdk.dir=/opt/android-sdk`) is gitignored but must exist for Gradle to locate the SDK. The update script recreates it on each startup.
- Full end-to-end testing requires a physical Jetour Dashing head unit or hardware-mocking setup, since the app communicates with the vehicle TBox module.
- There is no emulator or device available in the cloud VM; builds can be verified but APKs cannot be installed/run here.
