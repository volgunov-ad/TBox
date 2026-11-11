# Режим «Работа в среде лаунчера DCL»

Кратко для разработчиков и ручной проверки на ГУ. Контракт API: [DCL_LAUNCHER_API_FOR_VENDOR_RU.md](DCL_LAUNCHER_API_FOR_VENDOR_RU.md), клиент: `vad.dashing.tbox.dcl.*`.

## Два независимых ключа

| Preference | Default | Владеет |
|------------|---------|---------|
| `vad.dashing.tbox.dcl_launcher_environment_enabled` | **false** | сторонние ярлыки, App List, music open, automation `LaunchApplication` |
| `vad.dashing.tbox.launch_main_in_stock_app_window` | **true** | все пути открытия MainActivity |

Settings: блок типа ГУ / лаунчер. First-run `VehicleFeatureSetupDialog` показывает только DCL-env (default OFF). Main-switch также виден на A9, если DCL API available или env включён.

## v1 — маршрутизация при env ON

| Путь | Поведение |
|------|-----------|
| Ярлык «Обычный» (`fullscreen`) | DCL `launchApp(..., mode=app_area)` |
| Ярлык «На весь экран (DCL)» (`dcl_fullscreen`) | DCL `mode=fullscreen` |
| App List Open | только DCL `app_area` (кнопка stock-window скрыта) |
| Automation `LaunchApplication` | parity с ярлыком |
| Freeform / VD / stock | UI disable для **нового** выбора; сохранённые конфиги работают |
| API Unavailable / missing capability / `unsupported`/`internal` | raw `startActivity` + toast/journal; toggle **не** сбрасывается |
| `busy` / `not_installed` / target errors | toast only (no raw fallback) |
| Main ON | DCL `app_area` → иначе Adayo stock → иначе fullscreen |
| Main OFF | fullscreen |

Bulk-convert в Settings: freeform/VD/stock на ярлыках (legacy dashboard + main panels + floating) и в `LaunchApplication` → «Обычный». Не трогает Main, App List, уже-Обычный / DCL fullscreen.

Код: `DclLauncherEnvironmentRouting`, `DclAppLaunchHelper`, `DashboardAppNavigation`, `MainActivityIntentHelper`, `AutomationActionExecutor`.

## v2 — сигналы автоматизаций

| Signal | Тип | Источник |
|--------|-----|----------|
| `dcl_adas_panel_visible` | STATE on/off | `uiState.adasPanelVisible` |
| `dcl_top_bar_visible` | STATE on/off | `topBarVisible` |
| `dcl_bottom_bar_visible` | STATE on/off | `bottomBarVisible` |
| `dcl_screen_mode` | STATE home/single/fullscreen/split | `screenMode` |

Через существующие `state_equals` / `state`. Пока снимка DCL нет — `Unavailable`.

## Вне scope (v3)

Геометрия панелей / insets / auto-geometry freeform относительно `appAreaBounds`.
