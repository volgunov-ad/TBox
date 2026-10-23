# AGENTS.md

## Cursor Cloud specific instructions

This is an Android application (**TBox Monitor** for Jetour Dashing, v1.1.0). There is no server backend, web frontend, or external database — it is a single-module Gradle project (`:app`) producing an APK. Vehicle data comes from the TBox module (UDP via **tbox-proxy**) and from the head unit CAN stack (**mbCAN** on Android 9 or **VHAL** on Android 10).

> **Naming:** «Android 10» in this project means the Adayo/VHAL HU product line. Stock factory UI may show `Build.VERSION.RELEASE` as 10 while the platform API level remains 28 — see [docs/CAN_BACKENDS_RU.md](docs/CAN_BACKENDS_RU.md).

### Key subsystems

| Area | Code (main) | Domain docs |
|------|-------------|-------------|
| **Trips** | `trip/` (`TripRepository`, `TripRules`, `TripFuelAccounting`), `BackgroundService`, `UiTripsTab` | [docs/Trips.md](docs/Trips.md) |
| **Refuels & fuel calibration** | `fuel/`, `fuellevelcalibration/`, `utils/CanFramesProcess.kt` | [docs/fuel-refuels-calibration.md](docs/fuel-refuels-calibration.md) |
| **Themes** (`.tboxtheme`) | `Theme*.kt`, `DriveModeThemeWatcher`, `ui/ThemesTabContent.kt` | [docs/Themes.md](docs/Themes.md) |
| **CAN backends** | `mbcan/UniversalCanRepository.kt`, `HeadUnitCanMode.kt` | [docs/CAN_BACKENDS_RU.md](docs/CAN_BACKENDS_RU.md), [docs/MBCAN_VHAL_PARAMETERS_RU.md](docs/MBCAN_VHAL_PARAMETERS_RU.md) |
| **Stock A9 apps** | (firmware reference) | [docs/STOCK_APPS_ANDROID9_MBCAN_RU.md](docs/STOCK_APPS_ANDROID9_MBCAN_RU.md) |
| **Stock A10 apps** | (firmware reference) | [docs/STOCK_APPS_ANDROID10_VHAL_RU.md](docs/STOCK_APPS_ANDROID10_VHAL_RU.md) |
| **A10 launcher window (ActivityView), AA/CarPlay in window** | `AdayoStockAppWindow.kt` (TBox side) | [docs/LAUNCHER_WINDOW_AA_CARPLAY_RU.md](docs/LAUNCHER_WINDOW_AA_CARPLAY_RU.md) — hardcoded 1327×865/1920×1080, `LAUNCH_APP` / `SourceType` / `com.adayo.launcher.FULLSCREEN` intents, AA/CarPlay touch & bars diagnosis, no-patch fullscreen fix, APK patch plan |
| **HU devices (A9/A10)** | `adb/HuRebootAdb`, `cacerts/`, `adb/LocalhostAdbSession` | [docs/HEAD_UNITS_DEVICE_NOTES_RU.md](docs/HEAD_UNITS_DEVICE_NOTES_RU.md) — profiles, root, boot, CPU load, eMMC/GPT layout, HU reboot |
| **HU USB audio (A10)** | `huaudio/` (`HuUsbAudioPolicyCodec`, `HuUsbAudioPolicyClient`), `ui/HuUsbAudioSettingsCard.kt`; permanent one-shot app `:usbaudio` | [docs/USB_AUDIO_A10_RU.md](docs/USB_AUDIO_A10_RU.md) — hub workaround for kernel FS-enum bug; TBox Monitor keeps the temporary bind-overlay variant with boot auto-apply; the permanent `/system` write with the eMMC write-protect dance (vendor file name removed, fallback config) lives in `:usbaudio`, mirroring `:dataresize` |
| **Phone app link (companion BLE / direct RFCOMM)** | `shared/.../phoneble/` (`PhoneBleCodec`, `PhoneDirectWire`), `phoneble/PhoneDirectHost`, `PhoneDirectServer`, `ui/PhoneDirectSettingsSection.kt`; phone side `:phone` (`PhoneLinkRouter`, `PhoneRadio`, `PhoneDirectLink`) | [docs/PHONE_COMPANION_BLE_PLAN_RU.md](docs/PHONE_COMPANION_BLE_PLAN_RU.md) §8 — A9 BLE advertising crashes the BT stack, so the direct path is RFCOMM with own UUID (SPP 0x1101 is nForeTek's); HU stores direct-path keys; phone path Auto / Direct / Companion |
| **Heading sources (DR) / phone location** | `location/MockHeadingSource`, `YawIntegrator`, `SteerHeadingIntegrator`, `drsensor/`, `vehicle/WheelPulseOdometer` | [docs/HEADING_SOURCES_PLAN_RU.md](docs/HEADING_SOURCES_PLAN_RU.md) — plan: A10 has no working gyro (no Navi DR service, ASM330 HAL activate EPERM, VHAL INS silent); new heading options from CAN wheel pulses, phone gyro, phone compass; phone as location source over direct RFCOMM |
| **Raw decode formulas** | `CanFramesProcess`, `BackgroundService.ans*`, `Android10VhalRepository`, `HvacClimateDomain` | [docs/RAW_VALUE_FORMULAS_RU.md](docs/RAW_VALUE_FORMULAS_RU.md) |
| **TBox / network** | `TboxRepository`, `BackgroundService`, `TboxProtocol` | [docs/TBOX_PROXY_RU.md](docs/TBOX_PROXY_RU.md), [docs/USER_GUIDE_RU.md](docs/USER_GUIDE_RU.md) |
| **Dashboard / widgets** | `ui/Dashboard*.kt`, `WidgetConfigCodec.kt` | [docs/PANELS_AND_WIDGETS_RU.md](docs/PANELS_AND_WIDGETS_RU.md) |
| **Boot / autostart** | `BootCompleteReceiver`, `MainScreenBootOpen.kt`, `BackgroundService` | [docs/BOOT_AUTOSTART_DEVICE_RU.md](docs/BOOT_AUTOSTART_DEVICE_RU.md) |
| **Wi-Fi SoftAP → ESP32 router** | (firmware research, `app_process` helper) | [docs/WIFI_SOFTAP_ESP32_ROUTER_RU.md](docs/WIFI_SOFTAP_ESP32_ROUTER_RU.md) (A9), [docs/WIFI_SOFTAP_A10_RU.md](docs/WIFI_SOFTAP_A10_RU.md) (A10) |

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

Debug and release APKs are signed with the repo keystore `keystore/debug.keystore` (the machine `~/.android/debug.keystore` from 2025-03-22). Cloud and local builds share this signature, so a cloud APK installs over an existing HU install. Do not replace this file.

### Testing

- **Unit tests**: `./gradlew testRuDebugUnitTest` (or `testEnDebugUnitTest`) — **~180 tests** in 44 suites covering trips, refuels, fuel calibration, themes, widgets, and related logic. All run in the cloud VM without a device.
- **Instrumented tests**: Require an Android device/emulator (API 28+) — not available in this cloud environment.
- When changing trip/refuel/fuel logic, prefer extending existing tests under `app/src/test/java/vad/dashing/tbox/` (e.g. `TripRepository*Test`, `TripFuelAccountingTest`, `RefuelRepositoryTest`, `FuelCalibrationJsonTest`, `Theme*Test`).

### Lint

- `./gradlew lintRuDebug` (or `lintEnDebug`) — must pass with zero errors; warnings (mostly `UnusedResources`) are tolerated. Lint needs the 4 GB Gradle heap from `gradle.properties`; a stale daemon started with less memory fails with `OutOfMemoryError` (run `./gradlew --stop`).

### Tools

- `tools/can_log_to_xlsx.py` — converts app CAN export (`.txt`) to Excel using the same decode rules as `CanFramesProcess.kt`. Requires Python deps from `requirements.txt`.
- `tools/app_log_mbcan_to_xlsx.py` — converts app / deep-diagnostic journals (`tbox_app_log_*.txt`) to Excel timeline of `MBCAN_TMP` / `CANDIAG_MBCAN` / `CANDIAG_VHAL` / optional `TripFuel` events, with known/unknown cfg ids vs `MbCanKnownVehiclePropertyId`. Same deps as `can_log_to_xlsx.py`.
- `tools/geo_debug_analyze.py` — summarizes geo-debug logs (`tbox_geo_debug_*.txt`): truth-loss windows, shadow/hardResync, reverse PRND, online yaw calib, session `integ.*` (CAN path / gyro / steer), rough `k_speed`, left/right turn scale. Stdlib only.
- `tools/osm_to_tboxroads.py` — GeoJSON / Overpass JSON / exact `--fetch-overpass-area` / synthetic → `.tboxroads` v1. Stdlib only; see `docs/TBOXROADS_FORMAT_RU.md`.
- `tools/build_road_map_packs.py` — build whole RU/BY regions into the synced `release/maps` Yandex Disk folder and refresh remote/bundled catalogs. See `docs/ROAD_MAPS_HOSTING_RU.md`.
- `tools/hu_perf/` — HU startup / CPU / window-switch benchmark over ADB (reboot, logs, `/proc/stat` sampling, `am start -W` + BACK cycles, `gfxinfo`) and side-by-side summary (`hu_perf_analyze.py`, stdlib). Used for before/after `cmd package compile -m speed`. Method and A9 results: [docs/HU_PERF_TESTS_RU.md](docs/HU_PERF_TESTS_RU.md).
- `tools/hu_rescue/hu_rescue.ps1` — A10 HU recovery over the debug UART (Windows PowerShell 5.1, CP210x on `COM21`, 921600): boot-stage watcher, diagnostics, ADB/Wi-Fi recovery, GPT repair (move `userdata`, rebuild one copy from the other, tables from the 14.10 dump), partition write from a USB stick, `-DryRun`. GPT is rebuilt from the tables on disk, as in `:dataresize`. See [docs/HEAD_UNITS_DEVICE_NOTES_RU.md](docs/HEAD_UNITS_DEVICE_NOTES_RU.md) §2.1, §6.4–6.5.
- `tools/tbox_external_api_pair.py` — pair with External HTTP API and smoke-check (`/v1/health`, catalog+`voiceAliasesRu`, signals, automations, safe `actions/invoke`, automations/run probe). Stdlib only. Default host `192.168.1.128:8765`. Optional `--run-automation <id|name>` for real RunNow. Manual token: Settings → API → «Создать токен», then put `accessToken` into `~/.tbox_external_api_token.json` and use `--check-only`. See [docs/EXTERNAL_API_USER_GUIDE_RU.md](docs/EXTERNAL_API_USER_GUIDE_RU.md).

### Voice APK (`:voice`)

Separate app **VAD Voice** (`vad.dashing.voice`). Plan: [docs/VOICE_APK_RU.md](docs/VOICE_APK_RU.md).

Piper TTS + Vosk STT models are **not** in git. `assemble*` downloads them via Gradle (no Python):

```
./gradlew :voice:fetchTtsModel :voice:fetchSttModel   # optional manual
./gradlew :voice:assembleDebug
./gradlew :voice:testDebugUnitTest
```

APK ships Irina RU int8 + vosk-model-small-ru (~45 MB); only `armeabi-v7a` / `arm64-v8a`.

### MQTT APK (`:mqtt`)

Separate app **TBox MQTT** (`vad.dashing.mqtt`): External API → user MQTT broker and optional Home Assistant discovery. Plan: [docs/MQTT_APK_RU.md](docs/MQTT_APK_RU.md).

```
./gradlew :mqtt:testDebugUnitTest
./gradlew :mqtt:assembleDebug
```
### Data Resize APK (`:dataresize`) and `:adbcore`

Separate app **Расширение /data** (`vad.dashing.dataresize`) for A10 HUs: moves `userdata` to the unpartitioned eMMC tail and grows it, with live progress. Root via localhost ADB + `su`. Design and bench results: [docs/HEAD_UNITS_DEVICE_NOTES_RU.md](docs/HEAD_UNITS_DEVICE_NOTES_RU.md) §6.6–6.8.

`:adbcore` is an Android library with the ADB client (`AdbProtocol`, `AdbConnection`, `AdbAuthKeys`, `AdbTransport`, `AdbEndpoints`, `AdbShellResults`; package `vad.dashing.tbox.adb`), shared by `:app`, `:dataresize`, `:usbaudio`, `:fwunlock`, and `:winpatch`.

```
./gradlew :adbcore:testDebugUnitTest
./gradlew :dataresize:testDebugUnitTest
./gradlew :dataresize:assembleDebug
```

### USB Audio APK (`:usbaudio`)

Separate app **USB-аудио ГУ** (`vad.dashing.usbaudio`) for A10 HUs: the permanent one-shot USB audio enablement (config with inline usb module written to `/system/etc/audio_policy_configuration.xml` with the eMMC write-protect dance from `:dataresize`, vendor file name removed). TBox Monitor keeps only the temporary bind-overlay variant with boot auto-apply. Design and history: [docs/USB_AUDIO_A10_RU.md](docs/USB_AUDIO_A10_RU.md).

```
./gradlew :usbaudio:testDebugUnitTest
./gradlew :usbaudio:assembleDebug
```

### Firewall Unlock APK (`:fwunlock`)

Separate app **Файрвол ГУ** (`vad.dashing.fwunlock`) for A10 HUs: permanent Adayo firewall unlock — rewrites `/system/etc/adayo/firewall/AdayoFirewallAppDef.json` (Gson whitelist `{"packageName": ...}` consumed by stock `CarNetworkService`; no wildcard support) with all third-party packages, via localhost ADB + su and the eMMC write-protect dance from `:dataresize`; restarts `com.android.car` to apply without reboot. TBox Monitor keeps the runtime iptables fix (`hufirewall/`) as the safety net for apps installed later. Design: [docs/HEAD_UNITS_DEVICE_NOTES_RU.md](docs/HEAD_UNITS_DEVICE_NOTES_RU.md) §5.2.

```
./gradlew :fwunlock:testDebugUnitTest
./gradlew :fwunlock:assembleDebug
```

### Window Patch APK (`:winpatch`)

Separate app **AA/CarPlay окна ГУ** (`vad.dashing.winpatch`) for A10 HUs: permanent install/rollback of the universal patched `AndroidAuto` p6 and `CarPlayView` p6 APKs into `/system/app/...` across all known A10 firmwares (`V00.03.07`, `V00.03.16`, `V00.05.09`, `V00.07.12`), with the eMMC write-protect dance from `:dataresize`, via localhost ADB + su (`RootShell` over `:adbcore`). Patched APKs (`AndroidAuto_p6.apk`, `CarPlayView_p6.apk`) and reference stock APKs (`assets/stock/*`) are bundled in assets with pinned MD5s; installer matches the firmware by `ro.build.version.incremental` / `/system` MD5, backs up and restores stock even if `/data/local/tmp` was wiped or a legacy patch (`p1..p5`) is installed, clears dalvik-cache/oat, and reports `TBOX_WINPATCH_*` markers. Design: [docs/LAUNCHER_WINDOW_AA_CARPLAY_RU.md](docs/LAUNCHER_WINDOW_AA_CARPLAY_RU.md) §7.8.

```
./gradlew :winpatch:testDebugUnitTest
./gradlew :winpatch:assembleDebug
```

### Mengbo Camera Preview Test APK (`:camview`)

Separate diagnostic/test app **Тест камер ГУ (Mengbo)** (`vad.dashing.camview`, `arm64-v8a`) for A9 HUs: live preview and pixel format decoding (`ARGB_8888`, `RGB_565`, `UYVY`, `YUYV`, `NV12`) from the Mengbo native SDKs (`MBAvm`, `MBOms`, `MBDms`) and direct shared-memory files (`/early_services/earlydata/*_sharecamera`), with AVM view switching (`nativeSendViewCtrol`) and a draggable/resizable in-app or system overlay window (`TYPE_APPLICATION_OVERLAY`).

```
./gradlew :camview:assembleDebug
```

### Git branches

Use **`preRelease`** for pre-release integration; merge to **`master`** when ready to ship. Feature branches branch off `preRelease`, not `master`. See [docs/BRANCHING.md](docs/BRANCHING.md).

### Caveats

- `local.properties` (with `sdk.dir=/opt/android-sdk`) is gitignored but must exist for Gradle to locate the SDK. The update script recreates it on each startup.
- Full end-to-end testing requires a physical Jetour Dashing head unit or hardware-mocking setup: TBox UDP, mbCAN/VHAL bind, and drive-mode CAN signals cannot be emulated here.
- There is no emulator or device available in the cloud VM; builds and unit tests can be verified, but APKs cannot be installed/run here.
- **CAN backend** (`mbCAN` vs `VHAL`) is a runtime head-unit choice, not a build flavor — see `docs/CAN_BACKENDS_RU.md` before changing `UniversalCanRepository` or bind logic.
- **A9 mbCAN JNI is not thread-safe.** Never call `canGet*` / `canSet*` / `getMbCanData` from the main thread; hop to `mbcan-state-apply` (same as `MbCanRepository.execute`). Do not poll mbCAN/VHAL faster than 30 s / 1.5 s burst. Mixer volume is OpenOS/SettingsSvc, not mbCAN — 500 ms max while UI observes. Fast main-thread audio get raced OEM `parseCanData` and SIGABRT’d the process. See [docs/MBCAN_JNI_THREADING_RU.md](docs/MBCAN_JNI_THREADING_RU.md).
- **mbCAN/VHAL parameters**: read/write ids, raw decode, and push/pull behavior for widgets and settings are documented in [docs/MBCAN_VHAL_PARAMETERS_RU.md](docs/MBCAN_VHAL_PARAMETERS_RU.md). Use it when investigating or implementing CAN-backed UI. When adding or changing widgets, toggles, or settings that read or write vehicle properties, **update that doc in the same change** and keep it aligned with `MbCanCommandRegistry`, `FirmwareVehicleJsonMapper`, and domain decoders (`*Domain.kt`, `MbCanSignalStateEngine`).
