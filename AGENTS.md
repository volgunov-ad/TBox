# AGENTS.md

## Cursor Cloud specific instructions

This is an Android application (**TBox Monitor** for Jetour Dashing, v0.16.1). There is no server backend, web frontend, or external database — it is a single-module Gradle project (`:app`) producing an APK. Vehicle data comes from the TBox module (UDP via **tbox-proxy**) and from the head unit CAN stack (**mbCAN** on Android 9 or **VHAL** on Android 10).

> **Naming:** «Android 10» in this project means the Adayo/VHAL HU product line. Stock factory UI may show `Build.VERSION.RELEASE` as 10 while the platform API level remains 28 — see [docs/CAN_BACKENDS_RU.md](docs/CAN_BACKENDS_RU.md).

### Key subsystems

| Area | Code (main) | Domain docs |
|------|-------------|-------------|
| **Trips** | `trip/` (`TripRepository`, `TripRules`, `TripFuelAccounting`), `BackgroundService`, `UiTripsTab` | [docs/Trips.md](docs/Trips.md) |
| **Refuels & fuel calibration** | `fuel/`, `fuellevelcalibration/`, `utils/CanFramesProcess.kt` | [docs/fuel-refuels-calibration.md](docs/fuel-refuels-calibration.md) |
| **Themes** (`.tboxtheme`) | `Theme*.kt`, `DriveModeThemeWatcher`, `ui/ThemesTabContent.kt` | [docs/Themes.md](docs/Themes.md) |
| **CAN backends** | `mbcan/UniversalCanRepository.kt`, `HeadUnitCanMode.kt` | [docs/CAN_BACKENDS_RU.md](docs/CAN_BACKENDS_RU.md), [docs/MBCAN_VHAL_PARAMETERS_RU.md](docs/MBCAN_VHAL_PARAMETERS_RU.md) |
| **TBox / network** | `TboxRepository`, `BackgroundService`, `TboxProtocol` | [docs/TBOX_PROXY_RU.md](docs/TBOX_PROXY_RU.md), [docs/USER_GUIDE_RU.md](docs/USER_GUIDE_RU.md) |
| **Dashboard / widgets** | `ui/Dashboard*.kt`, `WidgetConfigCodec.kt` | [docs/PANELS_AND_WIDGETS_RU.md](docs/PANELS_AND_WIDGETS_RU.md) |

Trips and refuels are tightly coupled: filtered fuel % and calibrated liters are computed **only during an active trip** (`CanFramesProcess` gate); refuel records are created inside `BackgroundService.applyActiveTripFuelStep`.

### Environment

- **JDK**: OpenJDK 21 (system-installed); compatible with AGP 8.11.x
- **Android SDK**: Installed at `/opt/android-sdk` (set via `ANDROID_HOME` in `~/.bashrc` and `local.properties`)
- **Gradle**: 8.13 via wrapper (`./gradlew`)
- **Python** (optional, for `tools/`): `pip install -r requirements.txt` (`openpyxl`, `tqdm`)

### Build commands

Build commands use the Gradle wrapper. Two product flavors exist: `ru` (Russian) and `en` (English). See `README.md` for the full list.

```
./gradlew assembleRuDebug    # Russian debug APK
./gradlew assembleEnDebug    # English debug APK
./gradlew assembleRuRelease  # Russian release APK
./gradlew assembleEnRelease  # English release APK
```

### Testing

- **Unit tests**: `./gradlew testRuDebugUnitTest` (or `testEnDebugUnitTest`) — **~180 tests** in 44 suites covering trips, refuels, fuel calibration, themes, widgets, and related logic. All run in the cloud VM without a device.
- **Instrumented tests**: Require an Android device/emulator (API 28+) — not available in this cloud environment.
- When changing trip/refuel/fuel logic, prefer extending existing tests under `app/src/test/java/vad/dashing/tbox/` (e.g. `TripRepository*Test`, `TripFuelAccountingTest`, `RefuelRepositoryTest`, `FuelCalibrationJsonTest`, `Theme*Test`).

### Lint

- `./gradlew lintRuDebug` — the codebase has pre-existing lint errors; lint will fail. This is a pre-existing condition, not caused by the dev environment.

### Tools

- `tools/can_log_to_xlsx.py` — converts app CAN export (`.txt`) to Excel using the same decode rules as `CanFramesProcess.kt`. Requires Python deps from `requirements.txt`.

### Git branches

Use **`preRelease`** for pre-release integration; merge to **`master`** when ready to ship. Feature branches branch off `preRelease`, not `master`. See [docs/BRANCHING.md](docs/BRANCHING.md).

### Caveats

- `local.properties` (with `sdk.dir=/opt/android-sdk`) is gitignored but must exist for Gradle to locate the SDK. The update script recreates it on each startup.
- Full end-to-end testing requires a physical Jetour Dashing head unit or hardware-mocking setup: TBox UDP, mbCAN/VHAL bind, and drive-mode CAN signals cannot be emulated here.
- There is no emulator or device available in the cloud VM; builds and unit tests can be verified, but APKs cannot be installed/run here.
- **CAN backend** (`mbCAN` vs `VHAL`) is a runtime head-unit choice, not a build flavor — see `docs/CAN_BACKENDS_RU.md` before changing `UniversalCanRepository` or bind logic.
- **mbCAN/VHAL parameters**: read/write ids, raw decode, and push/pull behavior for widgets and settings are documented in [docs/MBCAN_VHAL_PARAMETERS_RU.md](docs/MBCAN_VHAL_PARAMETERS_RU.md). Use it when investigating or implementing CAN-backed UI. When adding or changing widgets, toggles, or settings that read or write vehicle properties, **update that doc in the same change** and keep it aligned with `MbCanCommandRegistry`, `FirmwareVehicleJsonMapper`, and domain decoders (`*Domain.kt`, `MbCanSignalStateEngine`).
