# Общая root-служба A9 (`rootsvc`) — инструкция для DCL

**Аудитория:** разработчик Dashing Club Launcher (`ras.dashing.tbox.launcher`).  
**Цель:** перевести root-действия DCL и окна (бывший `dclwm`) на общую службу `vad.dashing.rootsvc`, с безопасным fallback на текущий путь.  
**Язык:** русский; имена пакетов, команд и полей JSON — как в коде.

Контракт и код в репозитории TBox (`preRelease`): модуль `:rootsvc`, клиент `shared/rootsvc/java/vad/dashing/rootsvc/client/`. Обзор службы: [ROOT_SERVICE_A9_RU.md](ROOT_SERVICE_A9_RU.md). Окна до миграции: [DCL_WINDOW_HELPER_RU.md](DCL_WINDOW_HELPER_RU.md).

Платформа: **только Android 9 / mbCAN (Mengbo)**. На A10 службы в v1 нет.

Документ описывает **фактическое** поведение текущего кода, без обещаний плана, которые ещё не реализованы.

---

## 0. Нужно ли что-то менять в DCL?

| Вердикт | Что это значит |
|---------|----------------|
| **Переход со стороны службы возможен** | Root USB unlock, окна (`wm.*`), `helper.ensure`, `settings.a11y.*`, `pkg.appops`, `script.run` уже в allowlist DCL |
| **Fallback обязателен** | Если сокета нет — DCL работает как сейчас (`RootOrchestrator` + `dclwm`) |
| **Старый путь тоже правится** | Даже без службы: захват канала `mb_daemon` (§6.2) и хвост `ArmScript` без безусловного `rm`/`setprop ''` |

Итого: DCL сначала ходит в службу; при `UNAVAILABLE` / `unsupported` / `proto` — старый путь. При `forbidden` / `bad_args` / `unknown_cmd` — **не** падать на старый путь. При `failed` — **не** повторять legacy (действие уже было).

---

## 1. Кто есть кто

| Компонент | Пакет | Роль |
|-----------|--------|------|
| **Root-служба (`rootsvc`)** | `vad.dashing.rootsvc` | APK-сторож + root-демон `app_process`; абстрактный сокет |
| **DCL** | `ras.dashing.tbox.launcher` | Клиент службы; uid узнаётся по `SO_PEERCRED` → пакеты |
| **`dclwm` (legacy)** | dex в файлах лаунчера | Отдельный root-помощник окон на сокете `dclwm`; остаётся fallback и переходный путь |
| **TBox Monitor / camd / dcl_install** | `vad.dashing.*` | Другие клиенты той же службы (не тема этой инструкции) |

Служба узнаёт DCL **только по имени пакета** `ras.dashing.tbox.launcher`. Подпись не сверяется. Другое приложение под этим именем может встать только если DCL не установлен (имя пакета на устройстве уникально).

---

## 2. Подключение

| Параметр | Значение |
|----------|----------|
| Сокет | абстрактный Unix: `vad.dashing.rootsvc` |
| API | `LocalSocket` + `LocalSocketAddress(name, Namespace.ABSTRACT)` |
| Формат | JSON-строки, протокол **v1**, одна строка ≤ 16 КиБ |
| Сессия | одно соединение = один запрос: запрос → `ACK` → результат → закрытие |
| Поток | **не** с главного потока UI |

Пакет сторожа: `vad.dashing.rootsvc`. Явный broadcast поднятия: action `vad.dashing.rootsvc.ENSURE`, package `vad.dashing.rootsvc` (exported). Добавить пакет в `<queries>`.

### 2.1. Клиентская библиотека

Скопировать как есть (чистая Java):

```
shared/rootsvc/java/vad/dashing/rootsvc/client/*.java
```

Ключевые классы:

| Класс | Назначение |
|-------|------------|
| `RootSvcClient` | `call(cmd, args, timeoutMs)`, `isUp()`, `kick(Context)` |
| `RootSvcResult` | `Kind`: `OK` / `FAILED` / `REJECTED` / `UNKNOWN_OUTCOME` / `UNAVAILABLE` |
| `FallbackContract` | `shouldFallback(result, cmd)`, таймауты результата |
| `RootSvcProtocol` | константы сокета, ошибок, команд, таймаутов (`BOOT_WAIT_MS` / `BOOT_WINDOW_MS`) |
| `Allowlist` | справочно: `PKG_DCL`, набор caps (на стороне службы) |
| `MbDaemonChannel` | протокол захвата `mb_daemon` для legacy-пути |

```java
RootSvcResult r = RootSvcClient.call("usb.unlock", null, 5_000);
if (FallbackContract.shouldFallback(r, "usb.unlock")) {
    RootSvcClient.kick(context);
    // legacy RootOrchestrator / Starter.ensure + MbDaemonChannel …
}
```

`RootSvcClient.waitBootWindow()` / `callWithBootWait(...)` ждут сокет до 5 с в первые 60 с после boot. Предпочитайте их на старте / `BOOT_COMPLETED`, прежде чем уходить в legacy (см. §6.1).

---

## 3. Протокол обмена

### 3.1. Каркас

```
→ {"v":1,"id":"d01","cmd":"hello","client":"ras.dashing.tbox.launcher","clientVersion":"0.8.5"}
← {"id":"d01","ack":true}
← {"id":"d01","ok":true,"server":"1.0.0","proto":1,"hu":"A9","uid":10098,
    "caps":["usb.unlock","wm.list","wm.resize","wm.minimize","wm.dismiss",
            "helper.ensure","settings.a11y.add","settings.a11y.remove",
            "pkg.appops","script.run"]}

→ {"v":1,"id":"d02","cmd":"usb.unlock"}
← {"id":"d02","ack":true}
← {"id":"d02","ok":true,"exit":0,"ms":180}
```

Поле `client` в `hello` — для журнала; авторизация идёт по uid сокета, не по этой строке.

### 3.2. Коды ошибок (`err`)

| `err` | Смысл | Fallback на legacy? |
|-------|--------|---------------------|
| `forbidden` | нет cap / чужой пакет / задача лаунчера в `wm.*` | **нет** — ошибка интеграции |
| `bad_args` | кривые аргументы | **нет** |
| `unknown_cmd` | неизвестная команда | **нет** |
| `unsupported` | команды нет на этом ГУ / в этой сборке | **да** |
| `proto` | несовместимая версия протокола | **да** |
| `busy` | очередь рабочих потоков полна (ёмкость 8) | `FallbackContract.shouldFallback(busy)` = `false` — нужен свой retry (×3), не legacy |
| `timeout` | команда не уложилась на стороне службы | по контракту клиента |
| `failed` | выполнилось с ошибкой (`exit` / `stderr` / `msg`) | **нет** — действие уже было |

Клиентское `UNAVAILABLE` (нет сокета / нет `ACK` за 1 с) в провод не попадает: его выставляет `RootSvcClient`. `ACK` шлёт поток accept до постановки в очередь; при полной очереди после `ACK` приходит `busy`. Сбой **после** `ACK` (в т.ч. ответ длиннее 16 КиБ) — `UNKNOWN_OUTCOME`, не `UNAVAILABLE` (иначе legacy даст двойное выполнение).

### 3.3. Таймауты

| Этап | Значение |
|------|----------|
| Connect | ≤ 300 мс |
| `ACK` | 1 с |
| Результат по умолчанию | 5 с |
| `helper.ensure` / тяжёлые | 15 с |
| `script.run` | задаёт клиент, до 120 с |
| Окно загрузки | **рекомендация:** первые 60 с после boot ждать сокет до 5 с; в общем клиенте **не реализовано** — делать в DCL |
| Кэш «службы нет» | 30 с |

---

## 4. Allowlist DCL (возможности)

Служба: `SO_PEERCRED` → uid → пакеты через **`pm list packages -U`** (не Binder `getPackagesForUid`). Разрешение только если **все** пакеты этого uid есть в таблице клиентов (защита от `sharedUserId`). uid 0 — всегда.

Для `ras.dashing.tbox.launcher` в allowlist есть:

| Команда | Что делает служба |
|---------|-------------------|
| `ping` / `hello` | проверка и caps |
| `usb.unlock` | `setLockScreenShown(false)` + vold secure-keyguard off |
| `wm.list` / `wm.resize` / `wm.minimize` / `wm.dismiss` | окна (поглощение `dclwm`) |
| `helper.ensure` | запуск/проверка помощника по `path` + SHA-256 + версия |
| `settings.a11y.add` / `settings.a11y.remove` | дописать/убрать component в `enabled_accessibility_services` |
| `pkg.appops` | `appops set <package> <op> <mode>` |
| `script.run` | произвольный скрипт (тело, таймаут, `stdout`/`stderr`/`exit`) |

Всё остальное (`pkg.hide`, `svc.ctl`, `wallpaper.set`, …) → `forbidden`. Чужой пакет может только `ping` (проверка «служба есть»).

**Ограничения «только для себя» в v1 не проверяются.** Служба не сверяет:

- `pkg.appops` — любой `package`, подходящий под regex имени;
- `settings.a11y.*` — любой `component` с `/` (логика add/remove чужие строки не затирает, но чужой компонент принять можно);
- `helper.ensure` — любой существующий файл с верным SHA-256, не только `files/` вызывающего пакета.

DCL с `script.run` и так имеет полный root; это не повышает права относительно статус-кво, но **не** стоит полагаться на «служба сама ограничит scope». Передавайте свои пути/пакет/компонент сами.

---

## 5. Команды окон `wm.*`

Переносят сценарий `dclwm` (`LIST` / `RESIZE` / `MINIMIZE` / `DISMISS`). Семантика окон, freeform и кнопка «домой» — [DCL_WINDOW_HELPER_RU.md](DCL_WINDOW_HELPER_RU.md). Свои задачи пакета `ras.dashing.tbox.launcher` служба **не** двигает → `"err":"forbidden"` (msg про launcher).

Порядок кнопки «домой» **не меняется**:

1. `wm.list`, затем `wm.minimize` каждой чужой задачи;
2. свой `startActivity` на домашний экран лаунчера (`LauncherHomeActivity`).

`am start` от root **не** переносить в службу: запуск от uid 0 приходит в чужое приложение как вызов от root.

### 5.1. `wm.list`

```
→ {"v":1,"id":"d03","cmd":"wm.list"}
← {"id":"d03","ack":true}
← {"id":"d03","ok":true,"tasks":[
     {"id":406,"stack":4,"pkg":"vad.dashing.tbox",
      "activity":"vad.dashing.tbox.MainActivity","mode":"freeform",
      "left":344,"top":37,"right":1920,"bottom":1000,"visible":true}
   ]}
```

### 5.2. `wm.resize` / `wm.minimize` / `wm.dismiss`

Аргумент задачи — поле **`taskId`** (не `task`).

```
→ {"v":1,"id":"d04","cmd":"wm.resize","args":{"taskId":406,"left":344,"top":37,"right":1920,"bottom":1000}}
← {"id":"d04","ack":true}
← {"id":"d04","ok":true,"exit":0,"ms":…}

→ {"v":1,"id":"d05","cmd":"wm.minimize","args":{"taskId":406}}
← {"id":"d05","ack":true}
← {"id":"d05","ok":true,"exit":0,"ms":…}

→ {"v":1,"id":"d06","cmd":"wm.dismiss","args":{"taskId":406}}
← {"id":"d06","ack":true}
← {"id":"d06","ok":true,"exit":0,"ms":…}
```

Фактические ответы (не обещания плана):

| Ситуация | Ответ rootsvc |
|----------|----------------|
| Нет задачи | `"ok":false,"err":"failed","msg":"not_found"` → у клиента `FAILED` |
| Задача лаунчера | `"err":"forbidden"` (msg про launcher) |
| Коды выхода `am stack/task resize/remove` | **игнорируются**; при найденной чужой задаче ответ почти всегда `"ok":true` (в отличие от `dclwm`, где был `ERR am …`) |
| `wm.resize` с кривыми bounds / без нужных полей | `"err":"bad_args"` |
| `wm.minimize` / `wm.dismiss` без `taskId` | `taskId` читается как −1 → как «нет задачи»: `failed` / `not_found`, **не** `bad_args` |

Координаты: −100…4000; для `MINIMIZE` off-screen rect `2000,2000–2200,2200` подставляет служба.

### 5.3. Переход с сокета `dclwm`

Целевой режим после успешного `hello` с `wm.*` в `caps`:

- окна только через `wm.*` на сокете `vad.dashing.rootsvc`;
- **в этой загрузке** `/sdcard/autorun.sh` не трогать и отдельный `dclwm` **не** поднимать.

Если служба поднялась уже после того, как DCL успел запустить legacy `dclwm`: окна переводить на `wm.*`, процесс `dclwm` оставить доживать до перезагрузки (не обязательно убивать).

---

## 6. Fallback и порядок в DCL

| Ситуация | `RootSvcResult.Kind` | Действие DCL |
|----------|----------------------|--------------|
| Сокета нет / `ACK` нет за 1 с | `UNAVAILABLE` | legacy + `kick` (`ENSURE`); кэш «нет» 30 с |
| `unsupported` / `proto` | `REJECTED` | legacy |
| `forbidden` / `bad_args` / `unknown_cmd` | `REJECTED` | **не** legacy; журнал; сообщить нам |
| `failed` | `FAILED` | **не** повторять legacy |
| `ACK` есть, результата нет | `UNKNOWN_OUTCOME` | legacy только для идемпотентных: `usb.unlock`, `pkg.appops`, `helper.ensure`, `settings.a11y.*` |
| `busy` | `REJECTED` | **сейчас не приходит**; если появится — `shouldFallback` = false, нужен свой retry; не полагаться на общий пример §2.1 |

### 6.1. Рекомендуемый порядок

1. При старте процесса и на `BOOT_COMPLETED` (**не** с главного потока): в первые 60 с после загрузки **сами** ждать сокет до ~5 с, затем `hello`.
2. Служба ответила и в `caps` есть нужное → `usb.unlock`, окна через `wm.*`, разовые сценарии — `script.run` / `helper.ensure`. **`autorun.sh` и `dclwm` в этой загрузке не трогать.**
3. Нет сокета / нет `ACK` / `unsupported`/`proto` → прежний `RootOrchestrator` и `Starter.ensure` + сокет `dclwm`, но с протоколом захвата канала (§6.2). Послать `vad.dashing.rootsvc.ENSURE` и через ~30 с снова `hello`. Если служба поднялась — окна на `wm.*`.
4. `forbidden`/`bad_args` — не падать на старый путь.
5. `failed` — действие уже выполнялось; legacy не повторять.

### 6.2. Протокол захвата `mb_daemon` (обязателен для legacy)

Даже без службы: общий канал `/sdcard/autorun.sh` нельзя писать «в лоб». Использовать `MbDaemonChannel` / те же правила:

1. Захват: `LocalServerSocket("vad.dashing.mbdaemon.lock")`; не вышло — ждать 100 мс, до 15 с.
2. Под блокировкой: ждать исчезновения чужого `autorun.sh` (до 10 с).
3. Тело — в свой файл; `autorun.sh` = одна строка `exec sh <тело>` через `rename`.
4. Флаг (не пересоздавать чужой) + `setprop vendor.autorun.sdcard.logfile` + ждать свой nonce.
5. Скрипт в конце **ничего общего не трогает** (ни `$0`, ни `/sdcard/autorun.flag`, ни свойство). Убирает `mb_daemon`.
6. Отпустить блокировку после nonce или таймаута.

**Nonce пишет сам скрипт.** `MbDaemonChannel.arm(..., nonce)` только ждёт, пока в файле-маркере (для rootsvc по умолчанию `/sdcard/vad_rootsvc_arm`) появится строка с `nonce`. Канал **не** дописывает nonce в тело. Без `echo … > "$MARK"` (как в `ArmScript` / `EnsureDaemon.bootScript`) `arm()` ждёт ~8 с и бросает исключение. Для legacy DCL маркер `dclwm` — `/data/local/tmp/dclwm.arm` (`Protocol.ARM_MARKER`); в теле уже есть запись nonce.

Хвост `ArmScript` / `Starter.ensure` в `tools/dclwm/` уже без безусловного `rm`/`setprop ''`; DCL забирает обновлённые файлы из репозитория.

---

## 7. Прочие команды DCL

### 7.1. `usb.unlock`

Без аргументов. Идемпотентно; при `UNKNOWN_OUTCOME` можно повторить legacy.

### 7.2. `helper.ensure`

Запуск/проверка помощника: `name`, `path`, `sha256` (64 hex), `version`, `kind` (`exec`|`dex`), для dex — `mainClass`. Путь **не** ограничивается каталогом `files/` вызывающего пакета — только «файл есть» и SHA совпал. Рекомендуемый пример (свой dex):

```
→ {"v":1,"id":"d07","cmd":"helper.ensure",
   "args":{"name":"dclwm",
           "path":"/data/data/ras.dashing.tbox.launcher/files/dclwm.dex",
           "sha256":"…","version":"3","kind":"dex",
           "mainClass":"vad.dashing.dclwm.Helper"}}
```

В целевом режиме с `wm.*` отдельный `dclwm` через `helper.ensure` **не** нужен. Команда остаётся для переходных сборок и своих будущих помощников.

### 7.3. `settings.a11y.add` / `settings.a11y.remove`

Аргумент: `component` (`pkg/Class`). Add/remove не затирает чужие записи в списке, но **не** проверяет, что component принадлежит вызывающему пакету. Передавайте только свой.

### 7.4. `pkg.appops`

Аргументы: `package`, `op`, `mode`. Пакет **не** сверяется с uid вызывающего — передавайте свой.

### 7.5. `script.run`

Тело скрипта, таймаут, ответ с `exit` / `stdout` / `stderr`. Для разовых сценариев, которых нет в закрытом списке.

Фактически в v1:

- **параллельность:** один `script.run` одновременно (семафор); остальные команды — пул из 4 потоков с очередью 8 (`busy` при переполнении);
- **журнал службы:** пишутся uid, команда, `ok`/`err` — **тело скрипта в лог не пишется**;
- служба обрезает `stdout`/`stderr` до 4 КиБ с хвоста; клиентский `LINE_LIMIT` = 16 КиБ. Болтливый вывод всё же лучше перенаправлять в файл на устройстве.


---

## 8. Чеклист внедрения в лаунчер

1. Скопировать `shared/rootsvc/.../client/*.java` в исходники DCL (тот же пакет `vad.dashing.rootsvc.client`).
2. В манифест: `<queries>` на `vad.dashing.rootsvc`.
3. На старте / `BOOT_COMPLETED`: `RootSvcClient.waitBootWindow()` или `callWithBootWait` → `hello` → при успехе `usb.unlock` + `wm.*`; иначе legacy с `MbDaemonChannel` (nonce в теле скрипта).
4. Кнопку «домой» вести через `wm.list`/`wm.minimize` + свой `startActivity` (как в [DCL_WINDOW_HELPER_RU.md](DCL_WINDOW_HELPER_RU.md)). Не полагаться на коды выхода `am` в ответе `wm.*`.
5. Убрать безусловный `rm -f "$0"` / сброс свойства в хвосте legacy-скрипта; брать блокировку канала.
6. При `forbidden`/`bad_args` писать в журнал DCL и не маскировать legacy-путём.
7. На `busy` — свой retry (×3); не рассчитывать на серверную проверку «только свой пакет/путь».

---

## 9. Ссылки

| Документ | Зачем |
|----------|--------|
| [ROOT_SERVICE_A9_RU.md](ROOT_SERVICE_A9_RU.md) | архитектура службы, сборка, установка |
| [DCL_WINDOW_HELPER_RU.md](DCL_WINDOW_HELPER_RU.md) | семантика окон / freeform / «домой» (legacy `dclwm`) |
| [HEAD_UNITS_DEVICE_NOTES_RU.md](HEAD_UNITS_DEVICE_NOTES_RU.md) §10 | `mb_daemon` на A9 |
| Код клиента | `shared/rootsvc/java/vad/dashing/rootsvc/client/` |
| Код `wm.*` | `rootsvc/daemon/.../WmCommands.java` |
| Legacy `dclwm` | `tools/dclwm/` |
