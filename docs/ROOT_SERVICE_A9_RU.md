# Общая root-служба A9 (`vad.dashing.rootsvc`)

A9-only в v1. План и решения Андрея (26.11.2026): Project store `docs/shared-root-service-plan.md`. Код: модуль `:rootsvc` (APK-сторож + dex демона), клиент `shared/rootsvc/` и `:rootclient`. Хвост `tools/dclwm` (`ArmScript` / `Starter`) исправлен под протокол захвата канала в том же PR.

## Архитектура

```
 Клиенты (Monitor, camd, DCL, dcl_install, …)
        │  абстрактный сокет "vad.dashing.rootsvc" (SO_PEERCRED → пакеты)
        ▼
 rootsvc (app_process, uid 0)  ← supervisor.sh (root, цикл перезапуска)
        ▲                                   ▲
        │ PING / HELLO                      │ один взвод за загрузку
 APK-сторож vad.dashing.rootsvc ────────────┘ A9: mb_daemon (/sdcard/autorun.sh)
   • AccessibilityService (ранний старт)
   • BOOT_COMPLETED / MY_PACKAGE_REPLACED / ENSURE
```

- Подпись: репозиторный `keystore/debug.keystore` (как у остальных программ).
- Режим дилера (`/sdcard/dcl_dealer.flag`): служба **продолжает работать**.
- A10 в v1 нет.

## Сборка

```
./gradlew :rootsvc:assembleDebug
./gradlew :rootsvc:assembleRelease
./gradlew :rootsvc:testDebugUnitTest
./gradlew :rootclient:assembleDebug
```

APK: `rootsvc/build/outputs/apk/…/rootsvc-<version>-<debug|release>.apk`. Dex демона собирается задачей `buildDaemonDex` и кладётся в `rootsvc/src/main/assets/rootsvc.dex` (gitignore: не коммитить, если попадёт в дерево — артефакт сборки; в git только исходники и `supervisor.sh`).

## Клиентский API

Пакет `vad.dashing.rootsvc.client` (чистая Java + `LocalSocket`).

| Класс | Назначение |
|---|---|
| `RootSvcClient` | `call(cmd, args, timeoutMs)`, `isUp()`, `kick(Context)` |
| `RootSvcResult` | `Kind`: OK / FAILED / REJECTED / UNKNOWN_OUTCOME / UNAVAILABLE |
| `FallbackContract` | когда идти старым путём (`shouldFallback`) |
| `MbDaemonChannel` | протокол захвата канала §3.4 (`vad.dashing.mbdaemon.lock`) |
| `Allowlist` | пакеты и возможности (только имя пакета, без подписи) |
| `WireCodec` | JSON-строки v1 |

Gradle-модули: `implementation(project(":rootclient"))` или `java.srcDir("../shared/rootsvc/java")` как у `shared/localaccess`.

```java
RootSvcResult r = RootSvcClient.call("svc.ctl",
    new JSONObject().put("action", "stop").put("name", "AVMapp"), 5_000);
if (FallbackContract.shouldFallback(r, "svc.ctl")) {
    RootSvcClient.kick(context);
    // legacy mb_daemon arm with MbDaemonChannel …
}
```

С главного потока не вызывать. `RootSvcClient.waitBootWindow()` / `callWithBootWait(...)` ждут сокет до 5 с в первые 60 с после boot (`BOOT_WAIT_MS` / `BOOT_WINDOW_MS`). camd, camdbench и dcl_install используют это; DCL может звать те же методы из скопированного клиента.

### Ошибки и fallback (кратко)

| Исход | Действие клиента |
|---|---|
| нет сокета / нет ACK за 1 с → `UNAVAILABLE` | старый путь + `kick` |
| `unsupported` / `proto` → `REJECTED` | старый путь |
| `forbidden` / `bad_args` / `unknown_cmd` | **не** старый путь |
| `failed` | **не** повторять старым путём |
| ACK есть, сбой IO / таймаут результата / слишком длинная строка → `UNKNOWN_OUTCOME` | старый путь только для идемпотентных (**не** для `script.run`) |
| `busy` (очередь рабочих потоков полна) → `REJECTED` | `shouldFallback(busy)` = false — свой retry |

## Команды v1

`ping`, `hello`, `admin.upgrade`, `pkg.*`, `settings.*` (в т.ч. `a11y.add/remove`), `svc.ctl` (`AVMapp`), `usb.unlock`, `wallpaper.set`, `helper.ensure`, `wm.list/resize/minimize/dismiss`, `power.reboot`, `script.run`.

Авторизация: **только имя пакета** (uid → `pm list packages -U`, кэш 60 с; для `ping` lookup не делается). uid 0 — всегда. `script.run` — у всех пакетов из allowlist, включая DCL (`ras.dashing.tbox.launcher`); на службе сериализован (один одновременно). `stdout`/`stderr` в ответе обрезаются до 4 КиБ с хвоста.

Каталог `/data/local/vad-rootsvc` — `0711`, `state` и `helpers/*.ver` — `0644` (сторож читает crashloop, camd — версию граббера).

Сторож (`EnsureDaemon`) армит только на A9 Mengbo (`RELEASE=9` + `ro.wt.solution.provider=MB`). При замене APK сравнивает `hello.server` с `SERVER_VERSION` и вызывает `admin.upgrade`.

## Протокол захвата `mb_daemon`

`MbDaemonChannel`: bind `vad.dashing.mbdaemon.lock` → ждать чужой `autorun.sh` → тело в свой файл → `autorun.sh` = `exec sh <тело>` через rename → флаг + `setprop` → ждать nonce в маркере. **Nonce в маркер пишет само тело скрипта** (`echo … > mark`); канал только ждёт. Скрипт **не** удаляет `$0` / общий флаг / не сбрасывает свойство.

Авторизация пакетов на службе: `pm list packages -U` (не Binder `getPackagesForUid`). Ограничения DCL «appops/a11y/helper только для себя» в v1 **не проверяются**. `wm.*` не проверяет коды выхода `am`.

## Потребители

| Клиент | Статус |
|---|---|
| `:camd` / `:camdbench` | переведены: rootsvc primary + `MbDaemonChannel` fallback (`CamdRootChannel` / `RootChannel`) |
| `:camd` (0.9.15+) | запасной `usb.unlock` при загрузке, если SystemUI скрыт и флешка не смонтирована через 10 с; только служба, без `mb_daemon` |
| `dcl_install` | ставит APK из assets, выдаёт storage + a11y; бут-фикс и короткие скрипты через службу |
| Dashing Store | follow-up: карточка каталога |
| Monitor | карточка Root-служба в Настройки → Система |
| UsbMount | удалён (`tools/usb_mount/`); `dcl_install` делает `pm uninstall vad.dashing.usbmount` |

Явный broadcast: `vad.dashing.rootsvc.ENSURE` (exported). Пакет в `<queries>` у клиентов.

Сторожу нужно `WRITE_EXTERNAL_STORAGE` (A9 / API 28), иначе не записать `/sdcard/vad_rootsvc_boot.sh`. Установщик (`dcl_install` / Store follow-up) или `pm grant vad.dashing.rootsvc android.permission.WRITE_EXTERNAL_STORAGE`; экран приложения тоже запрашивает право.

---

## Раздел для разработчика DCL

Полная инструкция вендору: **[DCL_ROOT_SERVICE_FOR_VENDOR_RU.md](DCL_ROOT_SERVICE_FOR_VENDOR_RU.md)** (протокол, `wm.*`, fallback, allowlist). Окна legacy: [DCL_WINDOW_HELPER_RU.md](DCL_WINDOW_HELPER_RU.md).

Кратко: если службы нет — DCL работает как сейчас. Пакет `ras.dashing.tbox.launcher` — по имени (без подписи). Клиент: скопировать `shared/rootsvc/java/vad/dashing/rootsvc/client/*.java`. При живой службе — `usb.unlock` + `wm.*`, `/sdcard/autorun.sh` и отдельный `dclwm` в этой загрузке не трогать. Legacy — с `MbDaemonChannel` (без безусловного `rm -f "$0"` / `setprop ''`).
