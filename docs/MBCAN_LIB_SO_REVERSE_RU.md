# Разбор libmbCan.so / libmbcanclient.so

Статический анализ OEM-библиотек из `mbcan/` (aarch64, stripped). Цель — понять, где живут CAN/property id и что можно вытащить без полного Ghidra-декомпилята.

## Состав

| Файл | Роль |
|------|------|
| `libmbCan.so` | JNI-обёртка Java `com.mengbo.mbCan.*`: `parseCanData`, кэш `jfieldID`, вызовы в client |
| `libmbcanclient.so` | IPC к mbCAN-сервису ГУ: `MBCan_Vehicle_Get/Set`, `MBCan_Audio_*`, subscribe |
| `libmbutils.so` | Утилиты / system properties; к каталогу vehicle/audio id почти не относится |

`MB_FactoryMode.apk` и `DashingCruise 1.6.1.apk` содержат **тот же** `libmbCan.so` (md5 совпадает с `mbcan/`). `com.mengbo.tboxservice` — другая (более старая) ревизия.

## Архитектура get/set

```
App (TBox Monitor)
  → MBCanEngine.canGetVehicleParam(id)   // Java
    → libmbCan.so JNI
      → MBCan_Vehicle_Get(id, &value)    // libmbcanclient.so
         1. id ∈ [1..324]?
         2. nItem = table[id - 1]        // .data VA 0x20280
         3. DBDeal_GetProperty(nModular=2, nItem, buf)
         4. распаковка int/byte из ответа
```

**Таблицы «property id → CAN frame 0xXXX» в этих `.so` нет.**  
Числовой property id мапится на внутренний `nItem` modular API; дальше кадры CAN обрабатывает сервис/MCU на ГУ.

Логи клиента:

- `Get nModular = %d, nItem = %d, Ret = %d`
- `Set nModular = %d nItem = %d, Ret = %d`

## Таблица Vehicle (`MBCan_Vehicle_Get` / `Set`)

- VA **`0x20280`** в `.data` (file offset `0x10280`)
- **324 × uint32 LE**, index = `id - 1`
- Проверка диапазона: `id - 1 <= 0x143` → id **1…324**
- Sentinel «нет свойства»: bit31 (в текущей сборке все 324 слота валидны)
- Значения `nItem` ≈ **800…** (почти уникальные; несколько id делят один opcode)

Особые ветки get:

- часть id в диапазоне около `0x3E…` — отказ для string/special props (`-2`)
- id `0x2F` (47) — отдельный отказ в int-get
- id `0x37…0x3A` — разбор отдельного байта ответа (sbfx)

`MBCan_Vehicle_Set` использует **ту же** таблицу и упаковывает значение в буфер перед `DBDeal_SetProperty`.

Полная выгрузка id / enum / nItem / покрытие: [MBCAN_OEM_FULL_CATALOG_RU.md](MBCAN_OEM_FULL_CATALOG_RU.md).

## Таблица Audio (`MBCan_Audio_Get` / `Set`)

- VA **`0x20198`**, **37 × uint32** (проверка `id - 1 <= 0x24` → id **1…37**)
- Рабочие `nItem` ≈ **513…548**, плюс **902** для id 35
- Id **14, 15, 16** = `0xFFFFFFFF` (нет native mapping)
- Java `eAUDIO_PROPERTY_COUNT=38` (max property id **37**); id **14–16** в таблице = `0xFFFFFFFF`

## `libmbCan.so`: что полезно

1. **Каталог property id** — в Java `MBVehicleProperty` / `MBAudioProperty`, не в `.so`.
2. **Имена полей push-структур** — да (`nFCM_2_*`, `nFRM_3_*`, `nICM_*`, `nBCM_*`, …): [MBCAN_JNI_PUSH_FIELDS_RU.md](MBCAN_JNI_PUSH_FIELDS_RU.md).
3. **JNI API surface** — `MBCan_Vehicle_*`, `MBCan_Audio_*`, subscribe/parse callbacks.

Полный интерактивный декомпилят Ghidra для stripped aarch64 в CI-среде не требовался: для таблицы id→nItem достаточно `objdump` / `radare2` + дампа `.data`.

## Ограничения

- Нет DBC / матрицы «property → frame/signal».
- `oem_only_unverified` ≠ «нет на машине»: часто просто не вызывалось из TBox Monitor.
- Имена OEM enum могут расходиться с фактическим смыслом на Dashing (HMA через id **19**, не 130; id **12** в приложении = люк tilt).
- Артефакт jadx `eVEHICLE_AVH_SWITCH(BuildConfig.VERSION_CODE)` исправлен на **142**.

## Инструмент

```bash
python3 tools/mbcan_so_catalog.py
```
