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

С главного потока не вызывать. Константы `BOOT_WAIT_MS` / `BOOT_WINDOW_MS` (5 с в первые 60 с) есть в протоколе, но `RootSvcClient` окно загрузки **сам не ждёт** — клиент (DCL и др.) реализует ожидание сам; см. [DCL_ROOT_SERVICE_FOR_VENDOR_RU.md](DCL_ROOT_SERVICE_FOR_VENDOR_RU.md).

### Ошибки и fallback (кратко)

| Исход | Действие клиента |
|---|---|
| нет сокета / нет ACK за 1 с → `UNAVAILABLE` | старый путь + `kick` |
| `unsupported` / `proto` → `REJECTED` | старый путь |
| `forbidden` / `bad_args` / `unknown_cmd` | **не** старый путь |
| `failed` | **не** повторять старым путём |
| ACK есть, результата нет → `UNKNOWN_OUTCOME` | старый путь только для идемпотентных |
| `busy` | в текущей службе **не отдаётся** (пул без отказа); `shouldFallback(busy)` = false |

## Команды v1

`ping`, `hello`, `admin.upgrade`, `pkg.*`, `settings.*` (в т.ч. `a11y.add/remove`), `svc.ctl` (`AVMapp`), `usb.unlock`, `wallpaper.set`, `helper.ensure`, `wm.list/resize/minimize/dismiss`, `power.reboot`, `script.run`.

Авторизация: **только имя пакета** (uid → `pm list packages -U`). uid 0 — всегда. `script.run` — у всех пакетов из allowlist, включая DCL (`ras.dashing.tbox.launcher`).

## Протокол захвата `mb_daemon`

`MbDaemonChannel`: bind `vad.dashing.mbdaemon.lock` → ждать чужой `autorun.sh` → тело в свой файл → `autorun.sh` = `exec sh <тело>` через rename → флаг + `setprop` → ждать nonce в маркере. **Nonce в маркер пишет само тело скрипта** (`echo … > mark`); канал только ждёт. Скрипт **не** удаляет `$0` / общий флаг / не сбрасывает свойство.

Авторизация пакетов на службе: `pm list packages -U` (не Binder `getPackagesForUid`). Ограничения DCL «appops/a11y/helper только для себя» в v1 **не проверяются**. `script.run` не сериализован; `wm.*` не проверяет коды выхода `am`.

## Установка (хуки для follow-up)

| Канал | Статус в этом PR |
|---|---|
| `dcl_install` | не менялся — follow-up: ставить APK + выдать a11y |
| Dashing Store | не менялся — follow-up: карточка каталога |
| Monitor | не менялся — follow-up: карточка статуса |
| UsbMount | не удалялся — follow-up (DCL 0.8.2+ уже разблокирует USB) |

Явный broadcast: `vad.dashing.rootsvc.ENSURE` (exported). Пакет в `<queries>` у клиентов.

Сторожу нужно `WRITE_EXTERNAL_STORAGE` (A9 / API 28), иначе не записать `/sdcard/vad_rootsvc_boot.sh`. Установщик (`dcl_install` / Store follow-up) или `pm grant vad.dashing.rootsvc android.permission.WRITE_EXTERNAL_STORAGE`; экран приложения тоже запрашивает право.

---

## Раздел для разработчика DCL

Полная инструкция вендору: **[DCL_ROOT_SERVICE_FOR_VENDOR_RU.md](DCL_ROOT_SERVICE_FOR_VENDOR_RU.md)** (протокол, `wm.*`, fallback, allowlist). Окна legacy: [DCL_WINDOW_HELPER_RU.md](DCL_WINDOW_HELPER_RU.md).

Кратко: если службы нет — DCL работает как сейчас. Пакет `ras.dashing.tbox.launcher` — по имени (без подписи). Клиент: скопировать `shared/rootsvc/java/vad/dashing/rootsvc/client/*.java`. При живой службе — `usb.unlock` + `wm.*`, `/sdcard/autorun.sh` и отдельный `dclwm` в этой загрузке не трогать. Legacy — с `MbDaemonChannel` (без безусловного `rm -f "$0"` / `setprop ''`).
