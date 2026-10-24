# Внешний HTTP API TBox Monitor

Статус: **реализован v1** (сервер, локальный провайдер доступа на ГУ, pairing,
catalog/signals/invoke/run, ручной токен, `voiceAliasesRu`, клиенты `:voice` и `:mqtt`).

**Пользовательская инструкция (вкл. автоподключение на ГУ, curl / телефон / Tasker):**
[EXTERNAL_API_USER_GUIDE_RU.md](EXTERNAL_API_USER_GUIDE_RU.md).

Связанные документы:

| Документ | Роль |
|----------|------|
| [EXTERNAL_API_USER_GUIDE_RU.md](EXTERNAL_API_USER_GUIDE_RU.md) | Как включить, токен, примеры запросов |
| [MQTT_APK_RU.md](MQTT_APK_RU.md) | Клиент Home Assistant (**TBox MQTT**, `:mqtt`), локальный статус `127.0.0.1:8766/status` |
| [VOICE_APK_RU.md](VOICE_APK_RU.md) | Голосовой клиент (**VAD Voice**, `:voice`) |
| [AUTOMATIONS_RU.md](AUTOMATIONS_RU.md) | Каталог сигналов/действий, RunNow, безопасность CAN |
| [AUTOMATIONS_AI_JSON_GUIDE_RU.md](AUTOMATIONS_AI_JSON_GUIDE_RU.md) | Каноническая JSON-схема действий и сигналов |
| [TBOX_PROXY_RU.md](TBOX_PROXY_RU.md) | Legacy broadcast (`TboxBroadcastSender`) — **не** основа нового API |
| [BACKLOG.md](BACKLOG.md) | Связанные планы |

---

## 1. Цели

1. Дать внешним приложениям **чтение сигналов** и **выполнение действий** с тем же смыслом, что у
   автоматизаций (единый каталог).
2. Позволить **запускать сохранённые автоматизации** так же, как кнопка UI «Выполнить сейчас».
3. Поддержать клиентов **на ГУ и в телефоне** в той же LAN (не только `localhost`).
4. Сделать доступ **простым для пользователя**: автоподключение приложений-спутников на ГУ
   (`content://vad.dashing.tbox.localaccess`) с однократным подтверждением, HTTP-сопряжение с
   подтверждением на ГУ **или** ручная генерация токена в Настройки → API (телефон / Tasker / curl).
5. Не изменять поведение существующих правил автоматизаций (только аддитивные расширения каталога).

Вне scope v1:

- создание / редактирование / удаление правил с голоса или по HTTP;
- push-подписки / WebSocket / SSE (ассистенту достаточно единичных запросов);
- облако / аккаунт / OAuth провайдеров;
- TLS обязателен в v1 (см. §6; опционально позже).

---

## 2. Клиенты и сценарии

| Клиент | Типичный доступ |
|--------|-----------------|
| TBox MQTT / VAD Voice на том же ГУ | `http://127.0.0.1:<port>` + токен/порт через локальный провайдер `content://vad.dashing.tbox.localaccess` |
| Веб-панель / внешний клиент в LAN | `http://<ip_гу>:<port>` в той же Wi‑Fi / hotspot |

Типичный голосовой цикл (без подписки на поток):

```text
фраза пользователя
  → NLU (RU aliases + слоты; без LLM на старте)
    → один HTTP GET или POST
      → TTS ответ
```

Примеры:

- «Сколько градусов на улице?» → `GET /v1/signals?ids=outside_temperature&source=head_unit`
- «Включи обдув на стекло» → `POST /v1/actions/invoke` с `can_command`
- «Запусти прогрев» → `GET /v1/automations` → resolve по имени → `POST /v1/automations/{id}/run`

---

## 3. Принципы (автоматизации не ломаем)

1. **Единый каталог.** Идентификаторы сигналов и форма тел действий = `storageKey` /
   JSON из автоматизаций (`AutomationSignalId`, `AutomationAction`, `AutomationCanCatalog`,
   `AutomationBuiltinActionType`). Не вводить параллельные camelCase-имена вроде старого broadcast.
2. **Тот же executor.** `POST /v1/actions/invoke` → валидация как у правил →
   `AutomationActionExecutor`. Произвольные raw property id вне каталога — запрещены.
3. **Тот же RunNow.** `POST /v1/automations/{id}/run` → `AutomationEngine.requestRunNow`
   (семантика как UI «Выполнить сейчас»).
4. **Только аддитивный код.** Новый пакет (рабочее имя `externalapi/` или `localapi/`), без
   переписывания `AutomationEngine` / codec `formatVersion`.
5. **Отдельный interest sourceId** для внешних чтений HU-сигналов при необходимости
   (например `external-api`), не смешивать с `user-automations`.
6. **Legacy broadcast** (`TboxBroadcastSender` / `main_action`) **не развивается**; старых
   внешних клиентов нет. Удаление или deprecated — отдельным шагом после появления HTTP API.
7. Регрессия: существующие unit-тесты `automation/*` остаются зелёными; новые тесты — на API-слой
   и на новые сигналы каталога.

---

## 4. Транспорт и настройки сервера

| Параметр | v1 |
|----------|-----|
| Протокол | HTTP/1.1, JSON UTF-8 |
| Bind | **`0.0.0.0`** (LAN + localhost), не только loopback |
| Порт | **выбирается в настройках** (по умолчанию `8765`, допустимый диапазон `1024..65535`) |
| Сервер по умолчанию | **выключен** |
| Base path | `/v1` |
| UI | весь UX API — в разделе настроек **«API»** (см. §4.1) |

mDNS (`_tboxmonitor._tcp`) — опционально после MVP.

### 4.1. UI: раздел «API» в меню «Настройки»

В левом меню пункт **«Настройки»** содержит горизонтальный раздел/вкладку **`API`**.

Весь пользовательский UX внешнего API сосредоточен **только здесь** (не размазывать по
экспертному режиму и прочим экранам):

| Блок на экране «API» | Содержание |
|----------------------|------------|
| **Состояние** | сервер вкл/выкл; тумблер «Простая веб-панель»; кратко: слушает порт / ошибка bind; `apiVersion` / `catalogVersion` |
| **Сеть** | порт (`1024..65535`, по умолчанию `8765`); готовые URL: `http://127.0.0.1:<port>/v1/…` и `http://<lan-ipv4>:<port>/v1/…` (и URL веб-панели при включённом тумблере) |
| **Сопряжение** | кнопка **«Подключить приложение»** (режим HTTP pairing на `120 с` + ручное выкл.); список входящих `pair/request` с кнопками «Разрешить» / «Отклонить» |
| **Токен вручную** | имя клиента + **«Создать токен»**; токен показывается один раз (копирование токена или `Bearer …`); для телефона/Tasker/curl без pairing |
| **Доверенные клиенты** | список локальных (`local:<package>`), сопряжённых и ручных клиентов (имя, `clientId`, дата последнего использования); кнопка **«Отозвать»** |
| **Опасные команды** | тумблер разрешения ADB / `dangerous` (по умолчанию выкл.) |
| **Справка** | коротко: для чего API; подробности — [EXTERNAL_API_USER_GUIDE_RU.md](EXTERNAL_API_USER_GUIDE_RU.md) |

Диалог подтверждения для локальных приложений на ГУ (`ExternalApiLocalConsentActivity`) открывается
поверх любого экрана автоматически при запросе через `ContentProvider`, а **ручной вход в режим
HTTP pairing и управление доверенными клиентами** находятся в разделе «API».

---

## 5. Сопряжение и токены — механизмы доступа

Три способа получить Bearer-токен:

1. **Локальный провайдер на ГУ (`content://vad.dashing.tbox.localaccess`)** — основной
   автоматический механизм для приложений-спутников на том же ГУ (**TBox MQTT**, **VAD Voice**).
   Не требует включения режима pairing в настройках и ручного копирования токена.
2. **HTTP Pairing** — для клиентов, которые обращаются по HTTP (`POST /v1/pair/request` →
   `GET /v1/pair/status`): веб-панель управления, PC-скрипт `tools/tbox_external_api_pair.py`,
   а также запасной путь, если локальный провайдер недоступен.
3. **Ручной токен** — пользователь создаёт токен в **Настройки → API** (телефон, Tasker,
   curl, Shortcuts). Токен показывается один раз; на устройстве хранится только SHA-256 hash.

### 5.1. Автоподключение приложений на ГУ (`content://vad.dashing.tbox.localaccess`)

Реализовано в `ExternalApiLocalAccessProvider`, `ExternalApiLocalAccess`,
`ExternalApiLocalConsentActivity` (`:app`) и общем клиентском модуле `shared/localaccess/`
(`LocalAccessContract`, `LocalAccessClient`).

1. **Верификация вызывающего пакета через Binder:**
   - Приложение-спутник вызывает `ContentResolver.call(Uri.parse("content://vad.dashing.tbox.localaccess"), method, null, extras)`.
   - `ExternalApiLocalAccessProvider` определяет вызывающий пакет через `callingPackage` и проверяет, что он принадлежит `Binder.getCallingUid()` (`PackageManager.getPackagesForUid`). В отличие от поля `clientName` в HTTP-запросе, подделать имя пакета нельзя.
   - Разрешены только известные пакеты из `ExternalApiLocalClients`:
     - `vad.dashing.mqtt` → имя `"TBox MQTT"`, `clientId = "local:vad.dashing.mqtt"`;
     - `vad.dashing.voice` → имя `"VAD Voice"`, `clientId = "local:vad.dashing.voice"`.
   - Для пакета вычисляется SHA-256 сертификата подписи APK (`PackageManager.GET_SIGNING_CERTIFICATES` → `certSha256`).
2. **Однократное подтверждение пользователем (`getAccess`, `interactive = true`):**
   - При первом открытии TBox MQTT или VAD Voice без сохранённого токена (либо по кнопке «Отправить запрос») клиент вызывает `LocalAccessClient.awaitAccess(context)`: сначала `getAccess(interactive = true)`, затем опрос `getAccess(interactive = false)` раз в `1 с` (до `120 с`), пока статус равен `"pending"`.
   - Если клиент `local:<packageName>` с этим `certSha256` ещё не в списке доверенных, Monitor запускает прозрачную `ExternalApiLocalConsentActivity` поверх текущего экрана с диалогом подтверждения («Разрешить приложению „TBox MQTT“ (`vad.dashing.mqtt`) доступ к API?») — включать режим pairing в «Настройки → API» не нужно.
   - При нажатии **«Разрешить»** (`approveLocalConsent`) Monitor регистрирует доверенного клиента `local:<packageName>` с привязкой к `packageName` и `certSha256`. При **«Отклонить»** пакет помечается отклонённым (`status = "denied"`) до следующей интерактивной попытки; при закрытии окна без ответа возвращается `status = "consent_required"`.
3. **Выдача и автоматическая ротация токена (`getAccess`):**
   - Когда пакет уже подтверждён (`ExternalApiAuth.isLocalClientTrusted`), любой вызов `getAccess` (в том числе фоновый с `interactive = false`) сразу генерирует **новый** `accessToken`, обновляет `tokenHash` у `local:<packageName>` (прежний токен перестаёт действовать) и возвращает `Bundle`:
     - `status = "granted"`
     - `accessToken = "<hex>"`
     - `port = <текущий порт API в настройках Monitor>`
     - `serverEnabled = <включён ли сервер API>`
   - Клиент сохраняет `accessToken` и `port` и дальше обращается к `http://127.0.0.1:<port>/v1/...` с `Authorization: Bearer <accessToken>`.
4. **Бесшовное восстановление при `401` и смене порта (`getStatus`):**
   - Если во время работы `MqttBridgeService` (или `VoiceHomeViewModel`) получает `401 Unauthorized` (например, токен был удалён при очистке неактивных или перевыпущен), клиент вызывает `LocalAccessClient.getAccess(context, interactive = false)` (в `:mqtt` — не чаще раза в `30 с`, `RENEW_PERIOD_MS`) и автоматически получает свежий токен и порт без участия пользователя.
   - Если клиент был отозван пользователем в **Настройки → API → Доверенные клиенты → «Отозвать»**, фоновый `getAccess(interactive = false)` вернёт `consent_required`, и доступ возобновится только после нового интерактивного подтверждения пользователем.
   - Если HTTP `/v1/health` не отвечает, `MqttBridgeService` вызывает метод `getStatus` у провайдера:
     - `status == "unavailable"` → фоновый сервис Monitor не запущен;
     - `serverEnabled == false` → HTTP API выключен в «Настройки → API»;
     - `port != settings.apiPort` → порт API в Monitor был изменён: `:mqtt` **автоматически сохраняет новый порт** и сразу переподключается к нему.
5. **Откат на HTTP Pairing:**
   - Если провайдер не найден (`status == "not_supported"`), по кнопке «Отправить запрос» `:mqtt` автоматически переходит на стандартный обмен `POST /v1/pair/request` → `GET /v1/pair/status`.

### 5.2. HTTP Pairing

Пользовательский поток (веб-панель, PC-скрипт, внешние клиенты):

1. **Настройки → API → «Подключить приложение»** → режим pairing **вкл.** (таймер **`120 с`**, `PAIRING_TIMEOUT_MS`, либо ручное выключение на том же экране).
2. Клиент: `POST /v1/pair/request` с именем клиента `clientName`, стабильным `clientId` и опциональным `clientKind` (до `10` ожидающих заявок одновременно, `MAX_PENDING_PAIR_REQUESTS`).
3. Клиент опрашивает `GET /v1/pair/status?requestId=<uuid>`. Пока заявка не рассмотрена, возвращается `{"status": "pending"}`.
4. На ГУ в разделе «API» нажимается «Разрешить» (с подтверждением в диалоге) или «Отклонить».
5. При **Разрешить** Monitor генерирует `accessToken`, сохраняет клиента в доверенных и **автоматически выключает** режим pairing (`stopPairing()`). Пока режим выключен, новые `pair/request` получают `403 pairing_inactive`.
6. Следующий опрос `GET /v1/pair/status?requestId=<uuid>` возвращает `status: "approved"` и `accessToken` **ровно один раз** (`takeRequestForPoll` удаляет решённую заявку при выдаче; повторный запрос с тем же `requestId` вернёт `404 not_found`). Невостребованный результат `approved` / `denied` хранится `10 минут` (`PAIR_RESULT_TTL_MS`).
7. Дальнейшие вызовы API: заголовок `Authorization: Bearer <accessToken>`.
8. Отозвать доступ: **Настройки → API → Доверенные клиенты → «Отозвать»**.

Хранение на стороне Monitor (`ExternalApiPairedClient`): **SHA-256 hash** токена (`tokenHash`) + метаданные клиента (`clientId`, `clientName`, `createdAtEpochMs`, `lastUsedAtEpochMs`, а для локальных клиентов — `packageName` и `certSha256`), без plaintext токена на диске и в логах.

### 5.3. Ручной токен

1. **Настройки → API → «Создать токен»** (имя клиента, напр. «Телефон»).
2. Диалог показывает `accessToken` **один раз** (кнопки копирования токена и заголовка `Bearer …`).
3. Клиент с `clientId` вида `manual-<uuid>` попадает в доверенные; отзыв работает так же, как для остальных клиентов.
4. Дальше те же вызовы с `Authorization: Bearer <accessToken>`.

Практика и curl: [EXTERNAL_API_USER_GUIDE_RU.md](EXTERNAL_API_USER_GUIDE_RU.md).

### Уровни доступа (v1)

| Уровень | Содержание |
|---------|------------|
| **Базовый** (после подтверждения) | `catalog`, чтение сигналов, `actions/invoke` для действий уровня `safe` / `confirm` по политике, список и `run` автоматизаций |
| **Опасные команды** | Отдельный **тумблер в Настройки → API** (не галочки в диалоге pairing): ADB, `adb_set_tcp`, `adb_shell`, `adb_force_stop`. По умолчанию **выкл.** |

Диалог подтверждения без набора галочек — проще для пользователя. В v1 достаточно пары «доверенный клиент» + тумблер «Опасные команды».

### Запросы без токена

| Endpoint | Без Bearer |
|----------|------------|
| `GET /v1/health` | всегда отдаёт полный JSON состояния сервера (`ok`, `apiVersion`, `catalogVersion`, `serverEnabled`, `webPanelEnabled`, `pairingActive`, `appVersion`, `headUnit`, `climateControlType`), **без** секретов и телеметрии |
| `POST /v1/pair/request` | только при активном режиме pairing (иначе `403 pairing_inactive`) |
| `GET /v1/pair/status?requestId=` | опрос статуса заявки pairing (`pending` / `approved` / `denied`); финальный ответ выдаётся один раз |
| `GET /` и `GET /panel` | только если в Настройки → API включена **простая веб-панель**: HTML управления автомобилем, без телеметрии. Пять страниц с нижним меню: Подключение, Климат-контроль, Сиденья, Музыка, «Окна, люк и шторка» (окна по одному и все сразу, люк 0/20/50/80/100 % и «Откинуть», шторка 0/20/50/80/100 %). Сверху статус и температура на улице и в салоне. Последняя страница запоминается в браузере; без токена открывается «Подключение». Каждая страница читает только свои сигналы. Язык страницы — язык сборки (`ru` / `en`). Кнопка на странице вызывает тот же `POST /v1/pair/request` и поллит `GET /v1/pair/status`. Дальше команды идут в `/v1` с полученным Bearer |
| Остальные | `401` |

### Клиент для проверки с ПК

В репозитории: `tools/tbox_external_api_pair.py` (только stdlib). По умолчанию хост
`192.168.1.128`, порт `8765`.

```bash
# На ГУ: Настройки → API → включить сервер → «Подключить приложение»
python3 tools/tbox_external_api_pair.py
python3 tools/tbox_external_api_pair.py --health-only
python3 tools/tbox_external_api_pair.py --check-only   # уже есть токен в ~/.tbox_external_api_token.json
python3 tools/tbox_external_api_pair.py --check-only --run-automation климат
```

Скрипт: `POST /v1/pair/request` → poll `GET /v1/pair/status` → сохраняет Bearer →
проверяет `GET /v1/catalog` (в т.ч. `voiceAliasesRu`), несколько `GET /v1/signals`,
`GET /v1/automations`, safe `POST /v1/actions/invoke` (`show_toast`), probe
`POST /v1/automations/.../run`. Реальный RunNow — только с `--run-automation <id|имя>`.

---

## 6. Безопасность (сводка)

- Открытый bind в LAN **обязательно** сочетается с авторизацией по токену; без сопряжённого токена
  телеметрия и команды недоступны.
- Локальный провайдер (`content://vad.dashing.tbox.localaccess`) проверяет вызывающий пакет через
  `Binder.getCallingUid()`, разрешает только `vad.dashing.mqtt` и `vad.dashing.voice`, требует
  однократного подтверждения пользователем на ГУ и фиксирует SHA-256 сертификата подписи (`certSha256`).
- Лимиты HTTP-сервера (`ExternalApiConstants` / `ExternalApiHttpServer`): пул до `8` параллельных
  клиентов (`MAX_CONCURRENT_CLIENTS = 8`), сокет-таймаут `30 с`, стартовая строка до `8 КБ`
  (`431 line_too_long`), заголовки до `32 КБ` и не более `100` штук (`431 headers_too_large`),
  тело запроса до `256 КБ` (`413 payload_too_large`).
- Audit: краткие строки в журнал приложения (local access approve/deny, pair approve/deny,
  manual token created, token idle sweep) без полного тела опасных команд и без секретов.
- TLS: в v1 не блокирует релиз (типичная LAN ГУ). Позже — опциональный HTTPS / pinning.
- Подтверждение на ГУ для отдельных `confirm`-действий в v1 **не требуется**, если клиент уже
  доверенный; класс `dangerous` режется отдельным тумблером в «Настройки → API».

---

## 7. Контракт HTTP v1

Общие соглашения:

- `Content-Type: application/json; charset=utf-8`
- Ошибки: `{ "error": { "code": "...", "message": "..." } }` с подходящим HTTP-статусом
- Успех invoke/run: массив/объект результатов с `success` / `message` (как дух
  `AutomationActionResult`)

### 7.1. `GET /v1/health`

Отдаёт одинаковый ответ как без токена, так и с токеном (без секретов и телеметрии):

```json
{
  "ok": true,
  "apiVersion": 1,
  "catalogVersion": 5,
  "serverEnabled": true,
  "webPanelEnabled": true,
  "pairingActive": false,
  "appVersion": "1.0.0",
  "headUnit": "android9",
  "climateControlType": "dual_zone"
}
```

`headUnit` — `android9` (mbCAN) или `android10` (VHAL). От него зависит набор команд стёкол: на A9 — `close` / `vent` (20 %) / `comfort_open` (80 %) / `open`, на A10 — `close` / `vent` / `open`.

`climateControlType` — глобальный вид климата из Настроек (`ordinary_ac` / `single_zone` / `dual_zone`); веб-панель скрывает Auto / SYNC / пассажирскую зону соответственно.

### 7.2. `POST /v1/pair/request` и `GET /v1/pair/status`

Запрос на сопряжение (`POST /v1/pair/request`, доступен только при `pairingActive = true`, лимит очереди `MAX_PENDING_PAIR_REQUESTS = 10`):

```json
{
  "clientId": "voice-phone-uuid-or-stable-id",
  "clientName": "TBox Voice (телефон)",
  "clientKind": "voice"
}
```

Ответ при ожидании подтверждения в UI: `202 Accepted` + `{ "status": "pending", "requestId": "..." }`.

Клиент опрашивает `GET /v1/pair/status?requestId=<requestId>`.
Пока пользователь не нажал кнопку на ГУ, возвращается `200 OK` + `{ "status": "pending" }`.

При подтверждении («Разрешить» на ГУ) режим pairing автоматически выключается, а следующий опрос `GET /v1/pair/status?requestId=<requestId>` возвращает:

```json
{
  "status": "approved",
  "accessToken": "...",
  "clientId": "voice-phone-uuid-or-stable-id",
  "expiresAt": null
}
```

`expiresAt: null` — бессрочно до ручного отзыва или истечения 6 месяцев неактивности (см. §12). При отклонении: `{ "status": "denied" }`.

Финальный ответ (`approved` или `denied`) выдаётся методом `takeRequestForPoll` **ровно один раз** и сразу удаляется из памяти: повторный запрос с тем же `requestId` вернёт `404 not_found`. Невостребованный финальный результат хранится `10 минут` (`PAIR_RESULT_TTL_MS`) после окончания окна pairing (`120 с`, `PAIRING_TIMEOUT_MS`).

### 7.3. `GET /v1/catalog`

Требует Bearer. Машиночитаемое описание возможностей:

- сигналы: `id`, `valueType` (`number`\|`state`\|`position`), `sources` (с учётом backend ГУ:
  на Android 9 у `engine_temperature` и `target_gear`, на Android 10 у `steering_speed` только
  `tbox`; первым идёт источник по умолчанию; `GET /v1/signals?source=head_unit` для них
  отвечает значением TBox), `unit`, `label`,
  `stateOptions` / `namedValues`, `typicalRange`, **`voiceAliasesRu`** (массив RU-фраз
  lowercase; минимум — нормализованный `label`, плюс разговорные синонимы для типовых
  вопросов),
- действия (`actionTypes`): все `builtin` (с `actionType`, `safety`, `voiceAliasesRu` и опциональным `write`), все `can_command` (с `bus`, `propertyId`, `label`, `safety`, `operations`, `signalId`, `write`, `voiceAliasesRu`), а также 4 общих типа: `launch_application` (`confirm`), `open_main_screen` (`safe`), `http_request` (`confirm`), `delay` (`safe`),
- `catalogVersion` (сейчас **5** — read-only обогащение гео/модема отдельными signal id;
  **4** — у `can_command` добавлены `operations`, `signalId` и `write`, а у builtin `set_media_volume` — `write`).

`write` — схема записи для сопряжённого клиента (TBox MQTT). Поле аддитивное: голосовой клиент его не читает.

| `write.kind` | Смысл |
|--------------|--------|
| `binary` | пары `off`/`on` → значение invoke |
| `options` | `state` как у сигнала, `value` — тело команды (ключ или целое) |
| `number` | `min`/`max`/`step`/`unit` и пары «число сигнала → value» (для builtin `set_media_volume` — диапазон `0..31`, `step = 1`, `options = []`) |
| `pulse` | кнопки `open`/`close`, без `signalId` |

Если для `can_command` пару надёжно построить нельзя, `write` = `null`, а `signalId` всё равно указывает сигнал-подтверждение, когда он один. `catalogVersion` **3** остаётся в истории как aliases + `start_vad_voice`.

Источник истины для id — те же каталоги, что UI автоматизаций и
[AUTOMATIONS_AI_JSON_GUIDE_RU.md](AUTOMATIONS_AI_JSON_GUIDE_RU.md). Поле `voiceAliasesRu` —
обогащение для NLU в `ExternalApiVoiceAliasesRu`, не меняет `storageKey`.

### 7.4. `GET /v1/signals`

Требует Bearer. Единичный snapshot, без потока событий. Для `source=head_unit` чтение регистрирует интерес CAN этих сигналов (`sourceId = "external-api"`) и сразу делает pull, поэтому значение есть и без виджета на экране ГУ:

```
GET /v1/signals?ids=outside_temperature,fuel_level_percent&source=head_unit
```

Параметры:

| Query | Смысл |
|-------|--------|
| `ids` | список signal id через запятую (обязателен; до `50` id за запрос, `MAX_SIGNAL_IDS_PER_REQUEST = 50`) |
| `source` | один источник на запрос: `head_unit` \| `tbox` \| `app` (как в автоматизациях). Если сигнал не поддерживает source — элемент с `available: false` |

Пример ответа:

```json
{
  "observedAtElapsedMillis": 123456789,
  "signals": [
    {
      "id": "outside_temperature",
      "source": "head_unit",
      "valueType": "number",
      "value": 12.5,
      "available": true
    },
    {
      "id": "fuel_level_percent",
      "source": "head_unit",
      "valueType": "number",
      "value": null,
      "available": false
    }
  ]
}
```

Для state-сигналов `value` — строка канонического состояния (`"D"`, `"on"`, …).  
У стёкол (`window_front_left`, `window_front_right`, `window_rear_left`, `window_rear_right`) `value` — `"0%"`, `"20%"`, `"80%"`, `"100%"` или `"open"` (на A9 `−1` между остановками → `"open"`; на A10 статусный enum `0..4` декодируется в `"0%"` для `1` и `"open"` для `0/2/3/4`), и есть поле `"open": true/false` — открыто ли стекло в любом положении.  
Для `geo_position` — объект `{ "latitude", "longitude" }` (Unavailable при NONE/LOST).
Дополнительно (отдельные id, source `app`, без изменения `geo_position` / `locate_status`):

| id | Тип | Примечание |
|----|-----|------------|
| `geo_altitude_m` | number | м; Unavailable без LIVE/RETAINING |
| `geo_speed_kmh` | number | км/ч; Unavailable без LIVE/RETAINING |
| `geo_speed_source` | state | `gnss` / `can` / `retention` / `none` |
| `geo_bearing_deg` | number | °; Unavailable без bearing |
| `geo_satellites_visible` / `geo_satellites_used` | number | Unavailable без LIVE/RETAINING |
| `geo_fix_valid` | state | on/off — `gnssTruthful` |
| `geo_source` | state | `tbox` / `companion` / `android` / `usb` |
| `modem_rssi_dbm` | number | signalDbm или CSQ→dBm; без IMEI/ICCID/IMSI |
| `modem_registered` | state | on/off (домашняя/роуминг) |
| `modem_operator` | state | строка оператора |
| `modem_source` | state | `tbox` / `wifi_http` |
| `modem_throughput_down_kbps` / `modem_throughput_up_kbps` | number | только Wi‑Fi HTTP |

Не дублируют уже существующие `modem_net_type`, `modem_sim_status`, `modem_signal_level`,
`modem_mobile_data`. Write/actions не расширялись.

**Подписки на поток в v1 нет.** Клиент при необходимости повторяет GET.

### 7.5. `POST /v1/actions/invoke`

Требует Bearer. Тело — массив `actions` (от `1` до `20` действий за запрос, `MAX_ACTIONS_PER_REQUEST = 20`):

```json
{
  "actions": [
    {
      "type": "can_command",
      "bus": "vehicle",
      "propertyId": 188,
      "operation": "set",
      "value": "on"
    }
  ]
}
```

Форма каждого action — как в JSON автоматизаций (см. AI guide). Ответ:

```json
{
  "results": [
    { "success": true, "message": "" }
  ]
}
```

Относительные голосовые фразы («потеплее», «тише») клиент закрывает сам: GET текущего →
вычислить абсолют → invoke. Отдельные `adjust_+1` в каталоге в v1 не обязательны.

Действия класса `dangerous` при выключенном тумблере → ошибка `403` / `dangerous_disabled`.

### 7.6. `GET /v1/automations`

Требует Bearer. Краткий список без секретов (не отдавать сырой HTTP YAML с паролями):

```json
{
  "automations": [
    {
      "id": "5dd72c87-ef90-4a77-a16d-d18a1955dc7e",
      "name": "Прогрев",
      "enabled": true
    }
  ]
}
```

### 7.7. `POST /v1/automations/{id}/run`

Требует Bearer. Эквивалент UI **«Выполнить сейчас»**:

- триггеры и общие условия **не** проверяются;
- вложенные `if_then_else` считаются как обычно;
- пауза 2 с и глобальные guard’ы движка действуют;
- **выключенное** правило можно запустить (как в текущем UI RunNow);
- ошибки ручного запуска правило не отключают (как в UI).

Ответ: `202 Accepted` с `{ "accepted": true, "message": "" }` или `400 Bad Request` с `{ "accepted": false, "message": "..." }` (например, «не найдена», ошибки валидатора).

Голосовой клиент резолвит фразу по **имени** правила из списка; при неоднозначности — уточняет
у пользователя (логика клиента, не Monitor).

---

## 8. Расширение каталога автоматизаций (G1–G7 и последующие сигналы)

Добавляются **в единый каталог автоматизаций** (сигналы + provider + AI guide + тесты), чтобы
UI правил и HTTP API получали их одновременно. Существующие JSON правил без новых полей не меняются.

| ID | Signal id | Назначение | Статус |
|----|-----------|------------|--------|
| G1 | `tbox_connected` | TBox на связи (on/off), source `app` | **в каталоге** |
| G2 | `modem_signal_level` | уровень сигнала модема (число), `app` | **в каталоге** |
| G3 | `locate_status` | GPS-фикс GeoDisplay (on/off), `app` | **в каталоге** |
| G4 | `fuel_level_percent_filtered`, `fuel_level_liters` | фильтр % / калибр. литры, `app` | **в каталоге** |
| G5 | `gear_box_oil_temperature` | температура масла КПП, `tbox` | **в каталоге** |
| G6 | `active_trip_distance_km`, `active_trip_avg_fuel_l100km`, `active_trip_duration_s`, `active_trip_motor_hours`, `motor_hours` | поездка / моточасы, `app` | **в каталоге** |
| G7 | `media_title`, `media_artist`, `media_playing`, `media_position_ms`, `media_duration_ms` | активная медиасессия (трек, артист, статус воспроизведения, позиция и длительность), `app` | **в каталоге** |
| Дорога | `road_highway_class`, `road_speed_limit_kmh`, `road_tunnel` | OSM map-matching: класс дороги, ограничение скорости, признак тоннеля (on/off), `app` | **в каталоге** |
| Свет / кузов | `hazard`, `drl`, `high_beam`, `rain_detected`, `wiper_sts` | аварийка, ДХО, дальний свет, датчик дождя, режим дворников, `head_unit` | **в каталоге** |

**Обогащение гео/модема** (отдельные id, `catalogVersion` **5**, read-only): см. таблицу в §7.4
(`geo_altitude_m` … `geo_source`, `modem_rssi_dbm` … `modem_throughput_*_kbps`). Без PII
(IMEI/ICCID/IMSI) и без debug NMEA/DR.

Дополнительно для голоса (не отдельные signal id): таблица **`voiceAliasesRu`** в выдаче
`/v1/catalog` (код: `ExternalApiVoiceAliasesRu` — label + curated RU-фразы для сигналов и
действий). `catalogVersion = 3` (+ aliases); схема write — 4; гео/модем enrichment — **5**.

### Не в блокирующем списке v1

- наклон зеркал на R (backlog W-04);
- remote lock/unlock дверей как отдельная команда (уточнение W-05);
- сырые CAN-кадры по id как в legacy broadcast.

---

## 9. Политика классов действий

| Класс | Примеры | Поведение API |
|-------|---------|----------------|
| `safe` | чтение; медиа play/pause; `set_media_volume`; `show_toast`; `open_main_screen`; `delay` | invoke при базовом токене |
| `confirm` | `can_command` (климат, сиденья, окна, люк, багажник pulse и др.), `launch_application`, `http_request`, круиз, Wi‑Fi, restart TBox, run automation | в v1 разрешены базовому клиенту; при необходимости ужесточить позже |
| `dangerous` | `adb_set_tcp`, `adb_shell`, `adb_force_stop` | только если в Monitor включён тумблер опасных команд |

Точная разметка `safety` по каждому builtin/can_command — в `ExternalApiActionSafety.kt` + поле `safety` в `/v1/catalog`.

---

## 10. Клиенты-спутники на ГУ: VAD Voice (`:voice`) и TBox MQTT (`:mqtt`)

Оба приложения работают на том же ГУ, подключаются к `http://127.0.0.1:<port>/v1` и автоматически получают токен и актуальный порт API через локальный провайдер `content://vad.dashing.tbox.localaccess` (см. §5.1).

### 10.1. Голосовой клиент VAD Voice (`vad.dashing.voice`, модуль `:voice`)

| Слой | Реализация |
|------|------------|
| STT | офлайн RU Vosk (`vosk-model-small-ru`) |
| NLU | `voiceAliasesRu` + имена автоматизаций по `/v1/catalog` и `/v1/automations`; без LLM |
| Транспорт | единичные HTTP-запросы к `127.0.0.1:<port>/v1` + автообновление токена через `LocalAccessClient` |
| TTS | встроенный офлайн Piper (`irina-medium-int8` / Sherpa-ONNX); системный TTS ГУ не используется |

Подробности: [VOICE_APK_RU.md](VOICE_APK_RU.md) (активация из UI, wake-word opt-in, кнопка руля opt-in, Intent `vad.dashing.voice.action.LISTEN` и builtin `start_vad_voice` из Monitor).

### 10.2. MQTT-мост для Home Assistant (`vad.dashing.mqtt`, модуль `:mqtt`)

Приложение **TBox MQTT** читает каталог (`catalogVersion` 4+ со схемой `write`), опрашивает выбранные сигналы пачками до 50 id по источникам, публикует состояния и MQTT Discovery в брокер (напрямую или через встроенный userspace-туннель WireGuard) и выполняет входящие команды через `POST /v1/actions/invoke` и `POST /v1/automations/{id}/run`. Подробности: [MQTT_APK_RU.md](MQTT_APK_RU.md).

#### Обратный мониторинг статуса `:mqtt` в TBox Monitor (`127.0.0.1:8766/status`)

Чтобы пользователь видел состояние MQTT-моста прямо в TBox Monitor, работает обратный локальный HTTP-канал:

- Пока запущен foreground-сервис `MqttBridgeService`, он поднимает легковесный сервер `MqttStatusHttpServer` на **`127.0.0.1:8766`** (`MqttLocalStatus.PORT = 8766`, только loopback, без авторизации).
- Эндпоинт `GET http://127.0.0.1:8766/status` возвращает JSON со снимком состояния моста (`MqttLocalStatus.encode` / `MqttApkStatus.decode`):

```json
{
  "serviceRunning": true,
  "monitorUp": true,
  "brokerUp": false,
  "wireguardEnabled": true,
  "tunnelError": "",
  "brokerError": "timeout",
  "monitorError": "",
  "lastError": "",
  "availability": "offline"
}
```

- На стороне TBox Monitor (`:app`) в `AndroidManifest.xml` объявлен `<queries><package android:name="vad.dashing.mqtt" /></queries>`.
- На вкладке **«Информация»** (`MqttStatusInfoSection.kt` + `MqttApkStatus.kt`), если пакет `vad.dashing.mqtt` установлен, Monitor каждые **2 секунды** (`MqttApkStatus.POLL_MS = 2_000L`) опрашивает `http://127.0.0.1:8766/status` и отображает блок **«TBox MQTT»**:
  - запущен ли сервис (при `Connection refused` пакет установлен, но `MqttBridgeService` остановлен);
  - связь с MQTT-брокером (и текст ошибки `brokerError`);
  - связь моста с TBox Monitor API (и `monitorError`);
  - состояние WireGuard (`выкл` · `вкл, туннель поднят` · `ошибка туннеля` + `tunnelError`).

---

## 11. Порядок реализации

| # | Шаг | Критерий готовности | Статус |
|---|-----|---------------------|--------|
| 1 | Этот документ + ссылки из README / backlog | Согласован контракт | **сделано** |
| 2 | Сигналы **G1–G7** в automations (+ тесты, AI guide) | Старые правила грузятся; новые id в UI | **сделано** |
| 3 | Раздел **«Настройки → API»** (каркас UI): вкл сервера, порт, URL/IP, pairing/клиенты | Раздел виден в меню настроек | **сделано** |
| 4 | HTTP server skeleton: bind `0.0.0.0`, порт из раздела API, `GET /health` | Ручная проверка с телефона в LAN | **сделано** (код) |
| 5 | Pairing + token store + revoke **в разделе API** | Чужой без pairing не читает сигналы | **сделано** (код) |
| 5a | Ручная генерация токена в UI | Телефон/Tasker без pair/request | **сделано** |
| 5b | Пользовательская инструкция | `docs/EXTERNAL_API_USER_GUIDE_RU.md` | **сделано** |
| 5c | Локальный провайдер `content://vad.dashing.tbox.localaccess` | Автоподключение `:mqtt` и `:voice` на ГУ с 1 окном подтверждения и автообновлением токена/порта | **сделано** |
| 6 | `GET /catalog`, `GET /signals` | Snapshot совпадает с каталогом автоматизаций | **сделано** (код) |
| 7 | `POST /actions/invoke` → validator + executor; тумблер dangerous в разделе API | Safe-команды работают; dangerous закрыты | **сделано** (код) |
| 8 | `GET /automations`, `POST .../run` → `requestRunNow` | Паритет с кнопкой UI | **сделано** (код) |
| 8a | PC smoke-клиент `tools/tbox_external_api_pair.py` | Сопряжение + health/catalog/signals/automations/invoke/run с LAN | **сделано** |
| 9 | `voiceAliasesRu` в catalog | Voice APK может матчить RU-фразы | **сделано** (`catalogVersion` 3) |
| 9a | Гео/модем enrichment (отдельные read-only id) | Каталог + provider + aliases + тесты | **сделано** (`catalogVersion` 5) |
| 10 | Voice APK (`:voice`) | Офлайн STT/TTS/NLU, локальный доступ к Monitor | **сделано** ([VOICE_APK_RU.md](VOICE_APK_RU.md)) |
| 12 | MQTT-клиент Home Assistant (`:mqtt`) + статус `127.0.0.1:8766/status` | Состояния и команды каталога в брокер, discovery, WireGuard, блок на вкладке «Информация» | **сделано** ([MQTT_APK_RU.md](MQTT_APK_RU.md)) |
| 11 | (Опционально) deprecated/удаление legacy broadcast | Нет зависимости в дереве | открыто |

На каждом шаге с кодом автоматизаций — только аддитивные изменения; прогон
`./gradlew testRuDebugUnitTest` (как минимум пакеты automation + новые API-тесты).

---

## 12. Принятые решения при реализации

1. ~~Точный default port и диапазон допустимых портов в UI~~ — по умолчанию `8765`, допустимый диапазон `1024..65535` (`ExternalApiConstants`).
2. ~~Pairing: long-poll vs `202 pending` + status poll~~ — реализован `202 Accepted` (`pending`) + опрос `GET /v1/pair/status?requestId=`. Финальный ответ (`approved`/`denied`) выдаётся один раз (`takeRequestForPoll`) и хранится до `10 минут` (`PAIR_RESULT_TTL_MS`). Для приложений на самом ГУ (`:mqtt`, `:voice`) основным сделан локальный провайдер `content://vad.dashing.tbox.localaccess` (см. §5.1).
3. TTL access token: ручной revoke, плюс удаление, если токен не использовался больше 6 календарных месяцев (`TOKEN_IDLE_MONTHS = 6`). Проверка — через 1 минуту после запуска сервера API (`TOKEN_EXPIRY_CHECK_DELAY_MS = 60_000L`). Успешный запрос с Bearer обновляет время в памяти сразу, а на диск — не чаще раза в час (`TOKEN_USAGE_PERSIST_INTERVAL_MS`); локальные приложения на ГУ при `401` автоматически получают новый токен через `localaccess`.
4. ~~Форма `geo_position`~~ — `{latitude,longitude}` + enrichment id в §7.4; G6 — в каталоге.
5. Нужен ли позже mDNS / QR с URL для телефона — открыто (опционально).
6. HTTPS / certificate pinning — после стабилизации HTTP v1.

---

## 13. Связь с legacy broadcast

Старый механизм (`vad.dashing.tbox.subscribe` / `get_state` / `main_action`) покрывает малый
набор полей и почти не умеет команды. Новый API **не** обязан сохранять совместимость имён
(`engineRPM` и т.п.). Документация tbox-proxy по broadcast остаётся исторической до явного
удаления кода.
