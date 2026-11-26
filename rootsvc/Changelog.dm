# Changelog — Root-служба ГУ (`:rootsvc`, `vad.dashing.rootsvc`)

Версии APK: `versionName` / `versionCode` в `rootsvc/build.gradle.kts`.
При публикации поднять **versionCode** на 1 и править **versionName** (semver); сюда — блок с тем же номером сверху.
Имя APK: `rootsvc-<versionName>-<debug|release>.apk`.

1.0.2
1. Команды `status` и `admin.restart` для карточки Monitor; `SERVER_VERSION` 1.0.2.
2. `admin.restart` выходит с кодом 43 — супервизор сбрасывает счётчик падений и перезапускает без паузы (код 0 больше не считается крашем).
3. ENSURE с `force` обходит skip при crashloop (кнопка «Перезапустить» в Monitor, когда служба уже мертва).

1.0.1
1. На ГУ A9 (API 28) ENSURE снова может писать `/sdcard/vad_rootsvc_boot.sh`: в манифесте `READ/WRITE_EXTERNAL_STORAGE`, экран запрашивает право.
2. `supervisor.sh` в APK всегда с LF (`.gitattributes`, нормализация при сборке и при копировании из assets) — CRLF ломал `sh` на ГУ.
3. Клиент подключается к сокету без timed `connect` на API ≤28 (`UnsupportedOperationException` на A9).

1.0.0
1. Первая поставка общей root-службы для ГУ Android 9 (Mengbo): APK-сторож со спецвозможностью и `BOOT_COMPLETED`, супервизор и `app_process`-демон на абстрактном сокете `vad.dashing.rootsvc`, JSON-протокол v1, клиентская библиотека `shared/rootsvc` / модуль `:rootclient`, протокол захвата канала `mb_daemon`. A10 и миграция потребителей — отдельными PR.
