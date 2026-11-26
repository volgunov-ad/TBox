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

С главного потока не вызывать. В первые 60 с после загрузки, если сторож установлен, клиент может ждать сокет до 5 с перед fallback (`RootSvcProtocol.BOOT_WAIT_MS` / `BOOT_WINDOW_MS`).

### Ошибки и fallback (кратко)

| Исход | Действие клиента |
|---|---|
| нет сокета / нет ACK за 1 с → `UNAVAILABLE` | старый путь + `kick` |
| `unsupported` / `proto` → `REJECTED` | старый путь |
| `forbidden` / `bad_args` / `unknown_cmd` | **не** старый путь |
| `failed` | **не** повторять старым путём |
| ACK есть, результата нет → `UNKNOWN_OUTCOME` | старый путь только для идемпотентных |

## Команды v1

`ping`, `hello`, `admin.upgrade`, `pkg.*`, `settings.*` (в т.ч. `a11y.add/remove`), `svc.ctl` (`AVMapp`), `usb.unlock`, `wallpaper.set`, `helper.ensure`, `wm.list/resize/minimize/dismiss`, `power.reboot`, `script.run`.

Авторизация: **только имя пакета** (uid → `pm list packages -U`). uid 0 — всегда. `script.run` — у наших пакетов (`vad.dashing.*` из allowlist), не у DCL.

## Протокол захвата `mb_daemon`

`MbDaemonChannel`: bind `vad.dashing.mbdaemon.lock` → ждать чужой `autorun.sh` → тело в свой файл → `autorun.sh` = `exec sh <тело>` через rename → флаг + `setprop` → ждать nonce. Скрипт **не** удаляет `$0` / общий флаг / не сбрасывает свойство.

## Установка (хуки для follow-up)

| Канал | Статус в этом PR |
|---|---|
| `dcl_install` | не менялся — follow-up: ставить APK + выдать a11y |
| Dashing Store | не менялся — follow-up: карточка каталога |
| Monitor | не менялся — follow-up: карточка статуса |
| UsbMount | не удалялся — follow-up (DCL 0.8.2+ уже разблокирует USB) |

Явный broadcast: `vad.dashing.rootsvc.ENSURE` (exported). Пакет в `<queries>` у клиентов.

---

## Раздел для разработчика DCL

Вводить можно независимо: если службы нет, DCL работает как сейчас.

### Что известно

- Пакет сторожа: `vad.dashing.rootsvc`. Сокет: абстрактный `vad.dashing.rootsvc`, `LocalSocket` + `LocalSocketAddress(name, Namespace.ABSTRACT)`.
- Вызывать не с главного потока. Пакет `ras.dashing.tbox.launcher` проверяется по имени (подпись не сверяется). Представиться лаунчером другое приложение не может: uid из ядра.
- Клиент: скопировать `shared/rootsvc/java/vad/dashing/rootsvc/client/*.java` как есть.

### Обмен

```
→ {"v":1,"id":"d01","cmd":"hello","client":"ras.dashing.tbox.launcher","clientVersion":"0.8.5"}
← {"id":"d01","ack":true}
← {"id":"d01","ok":true,"server":"1.0.0","proto":1,"hu":"A9","caps":["usb.unlock","helper.ensure","settings.a11y.add","wm.list",…]}

→ {"v":1,"id":"d02","cmd":"usb.unlock"}
← {"id":"d02","ack":true}
← {"id":"d02","ok":true,"exit":0,"ms":180}

→ {"v":1,"id":"d03","cmd":"helper.ensure","args":{"name":"dclwm","path":"/data/data/ras.dashing.tbox.launcher/files/dclwm.dex","sha256":"…","version":"3","kind":"dex","mainClass":"vad.dashing.dclwm.Helper"}}
← {"id":"d03","ack":true}
← {"id":"d03","ok":true,"state":"started","pid":4321}
```

### Порядок в DCL

1. При старте процесса и на `BOOT_COMPLETED` (не с главного потока): `hello`. В первые 60 с после загрузки ждать сокет до 5 с.
2. Служба ответила и в `caps` есть нужное → `usb.unlock`, `helper.ensure dclwm`. **`/sdcard/autorun.sh` в этой загрузке не трогать.** Окна: `wm.*` на сокете службы **или** пока ещё сокет `dclwm`, пока миграция не завершена.
3. Нет сокета / нет ACK / `unsupported`/`proto` → прежний `RootOrchestrator` и `Starter.ensure`, но с `MbDaemonChannel` (без безусловного `rm -f "$0"` / `setprop ''`). Можно послать `vad.dashing.rootsvc.ENSURE`.
4. `forbidden`/`bad_args` — не падать на старый путь, записать в журнал.
5. `failed` — действие уже выполнялось; старым путём не повторять.

### Что DCL нужно исправить в любом случае

Хвост скрипта `dclwm` (`ArmScript`) не должен удалять `autorun.sh`, общий флаг и сбрасывать свойство; `Starter.ensure` должен ждать чужой скрипт и брать блокировку канала (`MbDaemonChannel` / `vad.dashing.mbdaemon.lock`). Правка в `tools/dclwm/` — follow-up; DCL заберёт новые файлы.
