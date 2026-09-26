# Backlog — запросы пользователей

Сводный список задач, собранный из обращений. Статус по умолчанию: **не начато**.

Легенда сложности: **S** — точечное изменение, **M** — несколько подсистем, **L** — крупная фича / архитектурный сдвиг.

Статус в колонке «Сложн.»: без пометки — **open**; `*(частично)*` — сделано не всё; `*(реализовано)*` / `*(реализовано: …)*` — **done** (с кратким evidence).

---

## Виджеты и панели

| # | Задача | Источник | Область кода | Сложн. |
|---|--------|----------|--------------|--------|
| W-01 | **Выравнивание текста в виджетах** — выбор положения символов: по центру / слева / справа. Сейчас почти все плитки жёстко используют `TextAlign.Center`. | «…отображение символов было не по середине, а можно было выбрать с лева или с право…» | `WidgetConfigCodec`, `FloatingDashboardWidgetConfig`, `DashboardWidgetRenderer`, отдельные `Dashboard*Widget.kt` | M *(реализовано)* |
| W-02 | **Расстояние между плитками в панели** — настраиваемый зазор между ячейками сетки на всю панель сразу. Вкладка «Плитки»: default **8 dp**; плавающие панели и панели главного экрана: default **0 dp**. | «…настраиваемый отступ… в сетке плитки…» (уточнено: зазор между плитками, не padding иконки) | `FloatingDashboardConfig`, `MainScreenPanelConfig`, `DashboardPanelGrid`, `DashboardTab`, Settings | M *(реализовано)* |
| W-03 | **Виджет открытия багажника** — действие по тапу (mbCAN/VHAL: `DOOR_TRUNK`, remote trunk). | «…виджет открытия багажника» | `TrunkDoorDomain`, `TrunkDoorRepository`, `DashboardTrunkDoorWidget`, `MbCanCatalog` | M *(реализовано)* |
| W-04 | **Виджет наклона зеркал при задней передаче** — чтение/переключение `MIRROR_REVERSE_TURN` / `eVEHICLE_SET_MIRROR_REVERSE_TURN_LOC` (mbCAN/VHAL). Параметр есть в каталоге; отдельного виджета и wiring в `MbCanKnownVehiclePropertyId` / UI пока нет. | «…виджет наклона зеркал при включении задней передачи» | `MbCanCatalog`, `MBVehicleProperty`, виджет + политика команд | M |
| W-05 | **Кнопки регулировки зеркал и разблокировки дверей** — зеркала: виджеты режима регулировки (`mirrorAdjustModeWidget`) и складывания (`mirrorFoldWidget`) готовы. Отдельного action-виджета «разблокировать двери» нет (режимы auto-lock / ign-off unlock / driver unlock — в «Настройки авто», не плитка). | «С кнопкой регулировки зеркал и разблокировки дверей не получается пока?» | `DashboardMirrorWidgets`, `MirrorAdjustModeRepository`, `MirrorCanCommands`; двери — `CarSettingsTab` / mbCAN door props | M *(частично: зеркала готовы; unlock дверей — нет)* |
| W-06 | **Опция «получать данные через CAN» для остальных виджетов** — флаг `useMbCanVhal` в `WidgetsRepository.supportsUseMbCanVhal` уже для RPM, speed, odo, fuel %, ambient, gear (PRND/current/prepared), TPMS P/T, instant fuel, DTE, maintenance, PM2.5, steer. Остаток — точечные dataKey по мере появления HU-пути (не «только громкость медиа» — у media volume флага нет). | «добавить для других виджетов функционал «получать данные через can"» | `WidgetsRepository.supportsUseMbCanVhal`, `DataProvider`, `UniversalCanRepository`, `docs/CAN_BACKENDS_RU.md` §8 | L *(частично: широкий набор готов; остаток точечный)* |
| W-07 | **Копирование и вставка плавающих панелей** — duplicate целой панели (сетка плиток + chrome «Вся панель») без ручной перенастройки. Позиция/размер на экране в буфер не входят. | «…копирование и вставку уже настроенных плавающих панелей/плиток…» | `WidgetDialogClipboard` (`copyPanel` / `WholePanelClipboardSnapshot`), `WidgetSelectionDialogShared`, `docs/PANELS_AND_WIDGETS_RU.md` | M *(реализовано)* |
| W-08 | **Копирование и вставка настроек плитки** — из выбранной ячейки копировать **все** параметры `FloatingDashboardWidgetConfig` и вставить в другую плитку на любой поверхности (вкладка «Плитки», панель главного экрана, плавающая панель); есть «Вставить без типа плитки». | «сделать возможность копирования всех настроек, включая тип данных, выбранной плитки и вставки в другую плитку» | `WidgetDialogClipboard` (`copyTile` / `TileClipboardSnapshot`), `WidgetSelectionDialogShared`, `WidgetConfigCodec` | M *(реализовано)* |
| W-09 | **Настройка веса шрифта плитки** — в расширенных настройках: стандартный / средний / полужирный (`Normal` / `Medium` / `SemiBold`). По умолчанию — **средний** (`Medium`, как до фичи). | «добавить в настройки каждой плитки возможность выбора веса шрифта…» | `FloatingDashboardWidgetConfig`, `WidgetConfigCodec`, `calculateResponsiveTextStyle`, `DashboardWidgetRenderer`, UI настроек плитки | M *(реализовано)* |
| W-10 | **Положение заголовка плитки** — если у виджета есть заголовок (`showTitle`), выбор: **сверху** или **снизу** плитки. Default: сверху; для `appLauncherWidget` — снизу (как сейчас). | «дополнительная настройка каждой плитки — положение заголовка…» | `FloatingDashboardWidgetConfig`, `WidgetConfigCodec`, `DashboardWidgetTitleRowIfVisible`, виджеты с `showTitle`, UI настроек плитки | M *(реализовано)* |
| W-11 | **Сворачивание панели по свайпу в полоску** — для **любой** панели (плавающей и главного экрана) во вкладке «Вся панель»: (1) **область сворачивания** — *нет* / *слева* / *справа* / *снизу* / *сверху*; (2) **толщина** (default **32 dp**) — зона свайпа и полоска: снизу/сверху — ширина панели × толщина; слева/справа — толщина × высота панели; панель **сжимается к противоположному краю** (область «снизу» → полоска у верхней границы); (3) **цвет полоски свёрнутой** и **развёрнутой** зоны свайпа — отдельно light/dark (у развёрнутой по умолчанию полупрозрачный); (4) **сворачивать после одиночного или двойного тапа** по плитке (включая внутренние кнопки) с **задержкой 0–10 с** (default **1 с**; не в режиме редактирования; только если выбран край сворачивания). Анимация; плавающий overlay сжимается до полоски. **isCollapsed** — отдельный map panelId→bool в DataStore (**независимо от темы**, не в `runtime.json` / theme.json). Default: область *нет*, авто-сворачивание *выкл*. | «…свайп… полоска… анимация… состояние в DataStore независимо от темы… авто-сворачивание по тапу…» | `PanelCollapse.kt`, `PanelCollapseOverlay.kt`, `MainScreenPanelConfig`, `FloatingDashboardConfig`, `PanelCollapseStates`, `FloatingOverlayController`, `MainScreenDashboardPanel`, `FloatingDashboard`, вкладка «Вся панель» | L *(реализовано)* |

---

## Отображение данных (CAN / TBox)

| # | Задача | Источник | Область кода | Сложн. |
|---|--------|----------|--------------|--------|
| D-01 | **Уличная и салонная температура: задержка перед обнулением при null** — как для давления шин: не сбрасывать значение сразу при невалидном CAN, держать последнее **5 минут**. Без сохранения на диск. | «…температура за бортом…»; «задержку на null, как для давления шин» | `InOutTemperatureNullDebounce`, `CanDataRepository`, `CanFramesProcess` | S *(реализовано)* |
| D-02 | **КПП: режим + передача** — номер передачи только в **D**; плитка `gearBoxModeCurrentGear` без температуры масла. | «…p, r, n, d… номера передачи нет» | `GearBoxDisplayFormat`, `DashboardGearBoxWidget`, `DataProvider`, `ViewModels` | S *(реализовано)* |
| D-03 | **Давление и температура шин через mbCAN/VHAL** — HU TPMS P/T + null-debounce давления; persist давления в отдельные ключи `wheel*_pressure_last_hu` (не смешиваются с TBox); виджеты с `useMbCanVhal`. Температура — live с шины без disk persist (как на TBox-пути). | «давление шин и температуру шин получать через mbCAN/vhal и сохранять…» | `TirePressureDomain`, `UniversalCanRepository` / `MbCanRepository` / `Android10VhalRepository`, `AppDataManager`, `DashboardWheelsPressure*Widget` | L *(реализовано)* |
| D-04 | **Перевести поездки и заправки на mbCAN/VHAL** — уменьшить зависимость от TBox UDP для одометра, RPM, топлива и связанной логики учёта. | «перевести работу функций поездок и заправок на mbCAN/vhal» | `BackgroundService`, `TripRepository`, `fuel/`, `UniversalCanRepository`, `docs/Trips.md`, `docs/fuel-refuels-calibration.md` | L *(реализовано: `TripTelemetryRepository`, dwell 15 с, useMbCanVhal для odo/fuel/ambient; CDR = только TBox)* |

---

## Поездки

| # | Задача | Источник | Область кода | Сложн. |
|---|--------|----------|--------------|--------|
| T-01 | **Ручной сброс счётчика пробега** — накопительная «суточная» поездка без автосброса; сброс только вручную (архив в историю + новая живая). | «…сброс … пробега, по моему желанию, а не по истечению времени простоя» | `TripRepository`, `BackgroundService`, UI поездок/виджеты | M *(реализовано)* |

---

## Заправки

| # | Задача | Источник | Область кода | Сложн. |
|---|--------|----------|--------------|--------|
| F-01 | **Кнопка «Обновить данные по заправке»** — в меню «Заправки», когда появился интернет (повторная подтяжка цен АЗС / пересчёт оценок). | «…кнопку - обновить данные по заправке в меню Заправки, когда интернет появился» | `UiRefuelsTab` (иконка Refresh / `ACTION_REFRESH_REFUEL_PRICES`), `BackgroundService.refreshRefuelPrice`, `docs/fuel-refuels-calibration.md` | S *(реализовано)* |

---

## Настройки и TBox

| # | Задача | Источник | Область кода | Сложн. |
|---|--------|----------|--------------|--------|
| S-01 | **Переключатель «Не подключаться к TBox»** — работа только через mbCAN/VHAL без UDP к tbox-proxy. | «сделать в Насти переключатель «не подключаться к tbox"» | `Settings.kt`, `TboxRepository`, `BackgroundService`, `docs/TBOX_PROXY_RU.md` | M *(реализовано)* |
| S-02 | **Wi‑Fi модем как источник сети** — опрос внешнего 4G MiFi/роутера по HTTP API (модель, IP, логин/пароль); данные для виджета сигнала и вкладки «Модем» (аналог `LocationSource`). В коде VERIFIED: **Olax F95** (`reqproc`), **ZTE MF79U** (`goform`), **Huawei E3372** (HiLink). Кандидаты дальше: [WIFI_MODEM_CANDIDATES_RU.md](./WIFI_MODEM_CANDIDATES_RU.md) (Olax M100 / ZLT `reqproc`, HiLink E5573…, Alcatel `/jrd/webapi`, TP-Link). | «источником данных может служить 4G модем… Olax F95»; HAR ZTE MF79U | `wifimodem/` (`WifiModemModel`, poller/UI), `BackgroundService`, вкладка Модем, `docs/WIFI_MODEM_*_RU.md` | L *(частично: Olax F95 + ZTE MF79U + Huawei E3372 VERIFIED; поллер/UI подключены)* |

---

## Автоматизации (крупная фича)

| # | Задача | Источник | Область кода | Сложн. |
|---|--------|----------|--------------|--------|
| A-01 | **Пользовательские автоматизации (триггер → действия)** — правила с условиями, задержками и действиями: всё, что уже умеет приложение (включая действия виджетов: климат, режим вождения, HTTP, ярлыки и т.д.). | «добавить функционал пользовательских автоматизаций (триггер - действия)…» | `automation/` (`AutomationEngine`, `AutomationActionExecutor`, codec/UI), `BackgroundService`, `docs/AUTOMATIONS_RU.md` | L *(реализовано)* |

---

## Рекомендуемый порядок (черновик)

Актуально на открытые / частичные пункты (готовые строки выше помечены `*(реализовано)*`).

1. **Новые / дожать action-виджеты:** W-04 (наклон зеркал на R), W-05 хвост (разблокировка дверей как плитка, если нужен remote unlock)  
2. **CAN-флаг точечно:** W-06 (добавлять dataKey в `supportsUseMbCanVhal` по мере HU-пути)  
3. **Wi‑Fi модемы:** S-02 (следующие модели из [WIFI_MODEM_CANDIDATES_RU.md](./WIFI_MODEM_CANDIDATES_RU.md))

---

## Вне таблицы backlog (связанные планы)

Не дублировать сюда как новые ID — это отдельные design/plan docs, не запросы из «источников» таблицы выше:

| Тема | Doc |
|------|-----|
| Компас компаньона (калибровка / DR `COMPASS` / `GYRO_COMPASS`, фазы 2–3) | [COMPASS_HEADING_PLAN_RU.md](./COMPASS_HEADING_PLAN_RU.md) |
| Map-matching polish (along-track catch-up, полевые проверки) | [MAP_MATCHING_PLAN_RU.md](./MAP_MATCHING_PLAN_RU.md) |
| Пилот СКДФ maxspeed (tools, пакет НН) | [SKDF_SPEED_LIMITS_NIZHNY_RU.md](./SKDF_SPEED_LIMITS_NIZHNY_RU.md) |
| CERT HUD/ICM без полной связки UI | [GUARANTEED_STOCK_PARAMETERS_RU.md](./GUARANTEED_STOCK_PARAMETERS_RU.md) § Backlog CERT |
| Беспроводная зарядка на Android 10 (pull/push не wired) | [MBCAN_VHAL_PARAMETERS_RU.md](./MBCAN_VHAL_PARAMETERS_RU.md) |

---

## Открытые вопросы

- **W-05:** нужен ли отдельный виджет remote unlock/lock дверей (как багажник), или достаточно настроек auto-lock / unlock mode в «Настройки авто»? На машине пользователя раньше не срабатывали зеркала и/или двери — уточнить, что ещё ломается после появления виджетов зеркал.
- **W-04:** подтвердить property id / raw values `eVEHICLE_SET_MIRROR_REVERSE_TURN_LOC` на A9 и A10 перед виджетом.
