# SystemSettings — Audio / Display CERT

Источник: штатные `SystemSettings` A9 (`AudioFragment` / `DisplayFragment`) и A10 (`VoiceFragment` / `MeterLightFragment` / …).  
Критерий CERT: UI на **обеих** линейках, известные R/W id, явные значения, связка A9↔A10.

См. также [GUARANTEED_STOCK_PARAMETERS_RU.md](GUARANTEED_STOCK_PARAMETERS_RU.md), [MBCAN_VHAL_PARAMETERS_RU.md](MBCAN_VHAL_PARAMETERS_RU.md).

## Треки API

| Трек | Примеры |
|------|---------|
| **mbCAN audio** | `canGet/SetAudioParam(MBAudioProperty)` |
| **mbCAN vehicle** | `canGet/SetVehicleParam` (ICM) |
| **VHAL** | `CarPropertyManager` int props |
| **Platform** | OpenOS AudioManager, Adayo `SettingsSvcIfManager`, `SettingsManager`, `Settings.System` — **не** Car Settings CAN |

## Уже в TBox

| Функция | Где | Заметка |
|---------|-----|---------|
| Media / phone / navi / voice volume | Car Settings → Аудио, виджет медиа | Platform mixer: A9 OpenOS groups (usage 1/2/12/16), A10 SettingsSvc streams **3/6/7/9** |
| Headrest speaker | Car Settings → Аудио | A9 audio **37** (0/1/2); A10 SettingsSvc **1/2/3** |
| Volume vs speed | Car Settings → Аудио | A10 **1–4**; A9 **0–3** ↔ shared UI 1–4 |
| Day/night панелей | Themes (`.tboxtheme`) | ≠ SystemSettings theme |
| Экран ГУ: яркость / авто / day-night | Car Settings → HUD | A9 `screen_brightness` 10…100 и `auto_bright` 2/1; A10 runtime Binder `adayo.setting.v2.0`: `get/setSysBacklight`, `get/setDayNightMode` (1 auto, 4 manual) |

## CERT для Car Settings (CAN/VHAL)

| Pri | Control | A9 | A10 | Статус |
|-----|---------|----|-----|--------|
| P1 | ICM brightness | Vehicle **209** (1…10) | Write **289415087**, read **289414939** | ✅ |
| P1 | ICM auto mode | Vehicle **208** (0=auto, 1=manual) | **289415088** | ✅ |
| P2 | Volume vs speed A9 map | Audio **13** raw **0–3** | VHAL **557849227** **1–4** | ✅ shared UI 1–4 |
| P3 | EQ mode + bands | Audio **10**, **5/6/7** | Platform `SettingsSvc` (не VHAL UI) | ✅ A9; A10 controls unavailable |
| P3 | Balance/fader | Audio **3/4** | Platform sound field | ✅ A9; A10 controls unavailable |
| P4 | Key tone / radar | Audio **17** / **11** | Platform streams | ✅ A9; A10 controls unavailable |

## Platform-only (отдельный трек — не Car Settings CAN)

- Громкости phone / nav / TTS на A10 и A9 — platform mixer (Car Settings → Аудио)
- Platform settings остаются отдельными от CAN ICM/HUD; экран ГУ реализован через Settings/Adayo service.
- SystemSettings day/night (`NIGHT_MODE_AUTO` / `auto_skin`) — не смешивать с TBox Themes
- Boot volume, screensaver, time format, GPS sync

## Порядок внедрения

1. Media volume row в Audio Settings
2. EQ / balance A9-first
3. Platform backlog только по явному запросу продукта
