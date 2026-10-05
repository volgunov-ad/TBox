# A9 mbCAN JNI: потоки и частота poll

Инцидент (A9 ГУ): `canGetAudioParam(37)` (подголовник) с **main** каждые **350 ms** одновременно с OEM `parseCanData` на `mbcan-state-apply` → `stack corruption` / SIGABRT (`-fstack-protector`). Mixer громкости при этом **не** mbCAN (OpenOS / `AudioManager`).

## Запрещено

- Вызывать OEM JNI с **main**: `canGet/SetVehicleParam`, `canGet/SetAudioParam`, `getMbCanData` / `read*`.
- Poll mbCAN/VHAL чаще, чем `MbCanJobManager` / A10 VHAL: **30 s**, после записи burst **1.5 s** на 15 s.
- Крутить mbCAN get в том же цикле, что и mixer (OpenOS/SettingsSvc).
- В OEM push-callback снова звать `getMbCanData` / `read*` (re-entrant binder ломает push/CFG).

## Обязательно

- Native get/set только на `mbcan-state-apply` (как `refreshSignal`, `MbCanRepository.execute` / `setAudioVolume`, подголовник A9 в `PlatformAudioRepository`).
- Car Settings / виджеты могут звать `execute` с Main — A9 hop внутри репозитория; VHAL с Main допустим (`CarPropertyManager`).
- Mixer: poll **только пока UI подписан**, не чаще **500 ms**. Это не mbCAN.
- Предупреждение `OEM get/set on main thread` в logcat — регрессия, чинить до релиза.

Подробности API: [CAN_BACKENDS_RU.md](CAN_BACKENDS_RU.md) §3. Сигналы: [MBCAN_VHAL_PARAMETERS_RU.md](MBCAN_VHAL_PARAMETERS_RU.md).
