# Радио штатных ГУ: управление из TBox Monitor (A9 mbCAN / A10 VHAL)

Исследование штатного радио (FM/AM/DAB) на обеих прошивках Jetour Dashing — цель: добавить управление радио в виджет музыки TBox Monitor (как ранее Bluetooth).

Статус: **исследование завершено, интеграция реализована** (виджет музыки, виртуальный плеер `vad.dashing.tbox.radio`, `radio/RadioPlayer.kt`; масштаб частот и play/pause A9 остались бенч-пунктами §5). Главный вывод: на обеих прошивках управление возможно, причём на A9 гипотеза «радио через mbCAN» **подтверждена**.

Связанные доки: [STOCK_APPS_ANDROID9_MBCAN_RU.md](STOCK_APPS_ANDROID9_MBCAN_RU.md), [STOCK_APPS_ANDROID10_VHAL_RU.md](STOCK_APPS_ANDROID10_VHAL_RU.md), [MBCAN_VHAL_PARAMETERS_RU.md](MBCAN_VHAL_PARAMETERS_RU.md), [MBCAN_JNI_THREADING_RU.md](MBCAN_JNI_THREADING_RU.md).

## Кратко

| | A10 (VHAL, Adayo) | A9 (mbCAN, Mengbo/TINNOVE) |
|---|---|---|
| Основной API | Binder `IAdayoDAB` (`com.adayo.service.AdayoDABService`) | mbCAN: `canSetVehicleParam` (id 154/155/156/158/159/161) + `canRadioSet` (cmd 1..10) |
| Fallback | Broadcast `action.adayo.app.media.control` (только prev/next, только когда радио — активный источник) | нет |
| Состояние (частота и т.п.) | Колбэк `IProjectCallBack` (mode=1002), `getCurFreq()`/`getBand()`/`getPSName()`/`getRTInfo()` | RX `eMBCAN_RADIO_FREQUENCYINFO` (тип 19) / `eMBCAN_RADIO_PROGRAMSTATE` (тип 30) через `getMbCanData` |
| MediaSession | нет (grep по MediaService — 0) | нет |
| Декларации API в исходниках | `D:\Dashing\Android10-VHAL\MediaService\sources\com\adayo\project\aidl\IAdayoDAB.java` | `MBVehicleProperty.java`, `MBRadioCmdType.java` |

Виджет музыки TBox (`MediaControlService.kt`, `MediaSessionManager` + `OnActiveSessionsChangedListener`) радио **не видит** — нужен отдельный `RadioController` рядом с MediaSession-путём.

---

## 1. A10 (Android 10 VHAL / Adayo)

### 1.1 Binder IAdayoDAB — основной путь

- Сервис публикуется в `ServiceManager` под именем **`com.adayo.service.AdayoDABService`** (`ProjectGlobalDef.java:7`; `addService` в `AdayoRadioBinder.java:629` и `AdayoDABBinder.java:61`).
- Дескрипторы AIDL: интерфейс `com.adayo.project.aidl.IAdayoDAB`, колбэк `com.adayo.project.aidl.IProjectCallBack`.
- Образец клиента — `AdayoDABProxy.java`: рефлексия `ServiceManager.getService` + `Stub.asInterface`, **без bind и без разрешений**. TBox может повторить это со своими копиями AIDL.
- API (`IAdayoDAB`): `enterBand(band)` (**AM=1 / FM=2 / DAB=3**), `play()` / `pause()` (play = захват аудиофокуса + unmute, сам переключает источник — `notifyServiceAudioChange("ADAYO_SOURCE_RADIO")`), `prev()` / `next()` (seek-станция), `prevStep()` / `nextStep()` (шаг частоты), `getCurFreq()`, `getBand()`, `getPSName()`, `getRTInfo()`, `fullScan()`, `registerCallback(IProjectCallBack)`.
- Колбэк `IProjectCallBack`: `mode=1002`, `func`: 0 — band, 1 — mute/play-статус, 4 — scan, 30 — частота, 34 — stereo.

### 1.2 Broadcast — fallback

`action.adayo.app.media.control`, extra `control` = `previous` / `next`. Принимается в `AdayoDABService.java:71`. Ограничения: работает **только когда радио — активный аудиоисточник**, и только prev/next.

### 1.3 Прочее

- Радио A10 **не создаёт MediaSession** (grep по `MediaService` — ноль).
- Клиент в штатном Launcher: `RadioManager extends ServiceConnection`, системный сервис **`adayo.radio.v2.0`** (`ServiceConstants.SERVICE_NAME_RADIO_V2_0`, подключение тоже через рефлексию `ServiceManager.getService`) — второй известный binder-сервис.
- Низкоуровневый тюнер-сервис: `com.adayo.service.radio`, intent-filter `com.adayo.radio.service.radioinfo` (`RadioService.apk` в `firmware_analysis\extracted\system\app\RadioService\`).

---

## 2. A9 (Android 9 mbCAN / Mengbo) — гипотеза подтверждена

### 2.1 Write-путь: mbCAN свойства (ГУ → RRM-тюнер)

Определения: `MB_AIService\sources\com\mengbo\mbCan\defines\MBVehicleProperty.java` (в копии TBox — `app/src/main/java/com/mengbo/mbCan/defines/MBVehicleProperty.java`). Значения полей — из заводской таблицы `FactoryMode\resources\assets\RRM1_RRM2.csv` (это декодер тех же CAN-сигналов).

| id | Имя | Значения (RRM1_RRM2.csv) | Биты |
|---:|-----|--------------------------|------|
| 154 | `eVEHICLE_SET_RADIO_FREQUANCE_MODE` | 0 Not_Active, **1 FM_Mode, 2 AM_Mode** | 0..1, 2 бита |
| 155 | `eVEHICLE_SET_RADIO_RESEARCH_STS` | 0 Not_Active, 1 Normal, 2 AST, 3 Not_Active | 2..3, 2 бита |
| 156 | `eVEHICLE_SET_RADIO_SOURCE_STATION_MODE` | 0 Not_Active, **1 Radio**, 2 USB, 3 Online_Music, 4 Online_Radio, **5 Bluetooth**, 6 iPOD, 7 LocalStorage | 4..6, 3 бита |
| 158 | `eVEHICLE_FMRADIO_FREQUANCE_VALUE` | диапазон примеров **65..108** (16 бит @ bit 16) | — |
| 159 | `eVEHICLE_AMRADIO_FREQUANCE_VALUE` | диапазон примеров **520..1710** (16 бит @ bit 32) | — |
| 161 | `eVEHICLE_SET_FREQUENCY_STS` | «0x1:PlayOn / 0x0:PlayOff», комментарий «当前收音机频率» (1 бит @ bit 51) — семантика сомнительная, низкая достоверность | — |

Также есть `eSOURCE_STATION_MODE(127)` (`MBVehicleProperty.java:133`) — отдельный id выбора источника (в аудио-домене), назначение на реальном ГУ не проверено.

**Механизм доставки (подтверждён дампом MCU, `mcu_analysis/`)**: Android `canSet(propertyId, value)` → UART-кадр в MCU S32K144 → `X50_Uart_Recv_Data` → `CAN_Msg_Tx_Set_Process` → `IlPutTxIHU_2_FMRadioFrequanceValue` (`0x54CF5`, id 158) / `IlPutTxIHU_2_AMRadioFrequanceValue` (`0x54D29`, id 159) → TX в CAN (`mcu_to_mbcan_map.csv:31-32`, `IlPutTx.csv:44-45`, `SUMMARY.md:120-121`). То есть 154–161 — это именно **управляющие сигналы ГУ→тюнер** (RRM), тот же транспорт, что у остальных `canSetVehicleParam` (климат, сиденья и т.д.), которые в TBox уже работают.

Единицы частоты — **открытый вопрос**: FM-примеры 65..108 намекают на целые МГц с поддержкой диапазона OIRT (65,9–74,0) + CCIR (87,5–108), но шаг 0,1/0,05 МГц требует другого масштаба; AM 520..1710 — почти наверняка кГц. Проверить на машине (см. §5).

### 2.2 Командный API: `canRadioSet`

- `MBCanEngine.canRadioSet(cmdType, value)` → JNI `nativeCanRadioSet` (`MB_AIService\sources\com\mengbo\mbCan\MBCanEngine.java:1662`; в копии TBox — `app/src/main/java/com/mengbo/mbCan/MBCanEngine.java:1553`, native — `MBCanClient.java:41`).
- Команды `MBRadioCmdType` (`app/src/main/java/com/mengbo/mbCan/defines/MBRadioCmdType.java`): 1 BAND, **2 SEEK_UP, 3 SEEK_DOWN**, 4 SEEK_CANCEL, 5 SEEK_AUTO, 6 SEEK_AUTO_CANCEL, **7 STEP_UP, 8 STEP_DOWN**, 9 FREQUENCY_CHECK, 10 PARAM.
- Семантика `value` для команд не задокументирована (вероятно 0/импульс; для BAND — номер диапазона) — проверить на машине.
- Отправитель в штатных приложениях: **`WT_RadioService` (`com.tinnove.radioservice`) в локальной распаковке `D:\Dashing\Android9-mbCan\` отсутствует** (нет ни в `app\`, ни в `pulled_*`) — его внутренности не разобраны. `MB_AIService` свои радио-команды наружу не отдаёт: FM-стек `com.openos.radio.LocalRadioManager` (AOSP `RadioManager`/`RadioTuner`) живёт локально внутри AIS, binder наружу не торчит.
- Заводской отправитель: `FactoryMode` `BaseCanFragment.java:122` — `modular == 3` → `canRadioSet(canID, value)` с описанием из `MBRadioCmdType`. Но строки RRM CSV создаются конструктором `TXEntity` с жёстким `nModular = 2` (`TXEntity.java`), т.е. завод шлёт радио-свойства 154–161 через **`canSetVehicleParam`**; путь `canRadioSet` используется фрагментами с ручным `setnModular(getItemType())` (`TestFragment.java:24`, парсер CSVUtil не декомпилировался).

### 2.3 RX: состояние радио

- `eMBCAN_RADIO_FREQUENCYINFO` (**тип 19**) → `MBCanRadioFrequencyInfo{int nFrequency, byte nStatus, byte nValid, byte nLevel, byte nRev}` (в копии TBox — `app/src/main/java/com/mengbo/mbCan/entity/MBCanRadioFrequencyInfo.java`).
- `eMBCAN_RADIO_PROGRAMSTATE` (**тип 30**) → `MBRadioProgramState{level, radioType, noise, multiPath, freDeviation}` (`entity/MBRadioProgramState.java`).
- Движок подписан на оба типа (`MBCanSubscribeBase` в `MBCanEngine`), но в dispatch-ветках **не раздаёт их никому** (пустые ветки: копия AIS `MBCanEngine.java:401-403` и `586-588`; копия TBox `:362` и `:502`). PUSH-колбэка нет; `registCMDListener` хранит слушателей только для CFG_* типов.
- Чтение — **поллинг `getMbCanData(19, MBCanRadioFrequencyInfo.class)` / `getMbCanData(30, MBRadioProgramState.class)`** из нативного кэша (тот же механизм, что уже используют read-методы `MbCanEngineFacade`). Заводской режим радио через `canGet*` не читает (в `BaseCanFragment` для modular 3 get-ветка отсутствует).
- RDS-текст (PS/RT) над mbCAN не виден — сущности нет; на A9 метаданные ограничены частотой/уровнем/стерео.

### 2.4 Потоки (важно)

A9 mbCAN JNI **не потокобезопасен**: `canRadioSet`, `getMbCanData`, `canSetVehicleParam` — только на рабочем потоке `mbcan-state-apply` (как `MbCanRepository.execute`), никогда с main и не внутри колбэков (re-entrancy → SIGABRT). См. [MBCAN_JNI_THREADING_RU.md](MBCAN_JNI_THREADING_RU.md).

### 2.5 Что уже есть в TBox

- `nativeCanRadioSet` в bundled-копии `MBCanClient.java:41` и `MBCanEngine.java:1553` — доступен через рефлексию, надо только добавить метод в `MbCanEngineFacade` (по образцу `canSetVehicleParam`, `MbCanEngineFacade.kt:133,227`).
- Сущности 19/30 и `MBRadioCmdType` уже в `app/src/main/java/com/mengbo/mbCan/`.
- `DeepDiagnosticsCatalog.kt:284,326` уже знает имена типов 19/30 — deep-диагностика на машине может сразу показать поток `MBCanRadioFrequencyInfo`.

### 2.6 Открытый вопрос: DAB на A9

В каталоге доки [STOCK_APPS_ANDROID9_MBCAN_RU.md](STOCK_APPS_ANDROID9_MBCAN_RU.md) числятся `MB_DABService` (`com.adayo.service.dab`) и `WT_RadioService`, но в локальной распаковке `D:\Dashing\Android9-mbCan\` их APK нет. Если на реальной ГУ DAB-сервис есть — возможно, он принимает тот же Adayo-протокол, что на A10 (§1.1). Для анализа извлечь APK из `system.img` или снять с ГУ.

---

## 3. План интеграции в TBox Monitor

Общая точка — `MediaControlService.kt`: рядом с MediaSession-путём добавить `RadioController`; виджет показывает радио-режим, когда источник — радио (или по явному выбору пользователя).

### A10
1. Свои копии AIDL `IAdayoDAB` / `IProjectCallBack` с теми же дескрипторами (`com.adayo.project.aidl.*`).
2. Подключение: рефлексия `ServiceManager.getService("com.adayo.service.AdayoDABService")` + `Stub.asInterface` (по образцу `AdayoDABProxy`).
3. Кнопки: play/pause → `play()`/`pause()`, prev/next → `prev()`/`next()`; переключение FM/AM/DAB → `enterBand`.
4. Метаданные: частота + PS/RT через колбэк mode=1002 или poll `getCurFreq()`/`getPSName()`/`getRTInfo()`.
5. Fallback: broadcast `action.adayo.app.media.control` (только prev/next и только когда радио — активный источник).

### A9
1. `MbCanEngineFacade`: добавить `canRadioSetMethod` (рефлексия `canRadioSet`) и read-хелперы `getMbCanData(19/30)`.
2. Кнопки (через `MbCanRepository` на `mbcan-state-apply`):
   - prev/next (seek): `canRadioSet(2/3, 0)`; шаг: `canRadioSet(7/8, 0)`; диапазон FM/AM: `canSetVehicleParam(154, 1|2)` (или `canRadioSet(1, band)` — уточнить).
   - play/pause: `canSetVehicleParam(161, 2|1)` — низкая достоверность, проверить.
3. Состояние: поллинг `getMbCanData(19)` (частота, valid, level, stereo) + `getMbCanData(30)` (radioType, noise) в существующем цикле mbCAN-состояния.
4. Метаданные: только частота/уровень (RDS-текста над mbCAN нет).
5. При реализации параметров 154–161 обновить [MBCAN_VHAL_PARAMETERS_RU.md](MBCAN_VHAL_PARAMETERS_RU.md) в том же изменении (требование AGENTS.md).

---

## 4. Ссылки на источники (A9)

| Факт | Файл |
|------|------|
| Радио-id 154–161, 127 | `app/src/main/java/com/mengbo/mbCan/defines/MBVehicleProperty.java:133,162,165-168`; дубль `MB_AIService\...\MBVehicleProperty.java:168-175` |
| Значения полей 154–161 | `FactoryMode\resources\assets\RRM1_RRM2.csv:153-203` |
| Команды радио 1..10 | `app/src/main/java/com/mengbo/mbCan/defines/MBRadioCmdType.java` |
| `canRadioSet` → native | `app/src/main/java/com/mengbo/mbCan/MBCanEngine.java:1553`; `MBCanClient.java:41` |
| Заводской вызов `canRadioSet` (modular 3) | `FactoryMode\sources\com\mengbo\factory\fragment\BaseCanFragment.java:122` |
| nModular=2 по умолчанию (CSV → `canSetVehicleParam`) | `FactoryMode\sources\com\mengbo\factory\model\TXEntity.java` |
| MCU: 158/159 → IlPutTx (TX-направление) | `mcu_analysis\mcu_to_mbcan_map.csv:31-32`, `IlPutTx.csv:44-45`, `SUMMARY.md:120-121` |
| UART: propertyId == mbCAN id | `mcu_analysis\UART_PROTOCOL.md`, `SUMMARY.md` |
| RX-сущности 19/30 и пустые dispatch-ветки | `app/src/main/java/com/mengbo/mbCan/entity/MBCanRadioFrequencyInfo.java`, `MBRadioProgramState.java`; `MBCanEngine.java:362,502` (копия TBox), `:401,:586` (копия AIS) |
| Сущности/типы уже в TBox | `app/src/main/java/com/mengbo/mbCan/entity/*`, `DeepDiagnosticsCatalog.kt:284,326` |

## 5. Что проверить на машине (бенч)

1. Масштаб частоты: FM 158 (МГц vs 0,1 МГц, OIRT) и AM 159 (кГц); поле `nFrequency` типа 19.
2. Реакция тюнера на `canRadioSet(2/3/7/8, 0)` vs `canSetVehicleParam(154/155/156, ...)` — что реально двигает частоту.
3. 161 PlayOn/PlayOff — реально ли mute радио.
4. Приложение радио на машине: появляется ли у `com.tinnove.radioservice`/`com.adayo.service.dab` (A9) и пишет ли оно те же id — сверить CAN-лог (deep-диагностика `CANDIAG_MBCAN`, типы 19/30 уже в каталоге).
