# Внешний HTTP API TBox Monitor

Статус: **реализован v1** (сервер, pairing, catalog/signals/invoke/run, ручной токен,
`voiceAliasesRu`). Голосовой клиент (Voice APK) — отдельный шаг.

**Пользовательская инструкция (вкл. curl / телефон / Tasker):**
[EXTERNAL_API_USER_GUIDE_RU.md](EXTERNAL_API_USER_GUIDE_RU.md).

Связанные документы:

| Документ | Роль |
|----------|------|
| [EXTERNAL_API_USER_GUIDE_RU.md](EXTERNAL_API_USER_GUIDE_RU.md) | Как включить, токен, примеры запросов |
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
4. Сделать доступ **простым для пользователя**: сопряжение с подтверждением на ГУ **или**
   ручная генерация токена в Настройки → API (телефон / Tasker / curl).
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
| Voice APK на том же ГУ | `http://127.0.0.1:<port>` или IP ГУ |
| Голосовой ассистент в телефоне | `http://<ip_гу>:<port>` в той же Wi‑Fi / hotspot |

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
| Порт | **выбирается в настройках** (значение по умолчанию зафиксировать при реализации, напр. `8765`) |
| Сервер по умолчанию | **выключен** |
| Base path | `/v1` |
| UI | весь UX API — в новом разделе настроек **«API»** (см. §4.1) |

mDNS (`_tboxmonitor._tcp`) — опционально после MVP.

### 4.1. UI: раздел «API» в меню «Настройки»

В левом меню пункт **«Настройки»** получает **новый горизонтальный раздел/вкладку `API`**
(рядом с существующими вроде «Автомобиль», «Поездки», «Интерфейс», «Система» — точное место
в ряду вкладок зафиксировать при реализации; логично ближе к «Система»).

Весь пользовательский UX внешнего API сосредоточен **только здесь** (не размазывать по
экспертному режиму и прочим экранам):

| Блок на экране «API» | Содержание |
|----------------------|------------|
| **Состояние** | сервер вкл/выкл; кратко: слушает / ошибка bind; `apiVersion` / `catalogVersion` |
| **Сеть** | порт (редактируемый); готовые URL для копирования: `http://127.0.0.1:<port>/v1/…` и `http://<lan-ipv4>:<port>/v1/…`; подсказка для телефона в той же Wi‑Fi |
| **Сопряжение** | кнопка/тумблер **«Подключить приложение»** (режим pairing + таймер/ручное выкл.); индикатор «ожидается подтверждение…»; диалог Да/Нет при входящем `pair/request` |
| **Токен вручную** | имя клиента + **«Создать токен»**; токен показывается один раз (копирование); для телефона/Tasker/curl без pairing |
| **Доверенные клиенты** | список сопряжённых / ручных клиентов (имя, id); **отозвать** доступ |
| **Опасные команды** | тумблер разрешения ADB / `dangerous` (по умолчанию выкл.) |
| **Справка** | коротко: для чего API; подробности — [EXTERNAL_API_USER_GUIDE_RU.md](EXTERNAL_API_USER_GUIDE_RU.md) |

Диалог подтверждения сопряжения может быть системным/overlay поверх текущего экрана, но
**вход в режим pairing и управление клиентами** — только из раздела «API».

---

## 5. Сопряжение и токены — механизмы доступа

Два равноправных способа получить Bearer:

1. **Pairing** — клиент умеет `pair/request` (Voice APK, PC-скрипт).
2. **Ручной токен** — пользователь создаёт токен в **Настройки → API** (телефон, Tasker,
   curl, Shortcuts). Токен показывается один раз; на устройстве хранится только hash.

### 5.1. Pairing

Пользовательский поток:

1. **Настройки → API → «Подключить приложение»** → режим pairing **вкл.** (таймер, ориентир
   **60–120 с**, либо ручное выключение на том же экране).
2. Клиент: `POST /v1/pair/request` с именем клиента и стабильным `clientId`.
3. На ГУ диалог: «Разрешить „&lt;clientName&gt;“?» → Да / Нет.
4. При **Да** Monitor выдаёт `accessToken`; клиент сохраняет его локально.
5. Режим pairing **выключается** (по таймеру или вручную). Пока он выкл., новые
   `pair/request` → `403`.
6. Дальнейшие вызовы API: заголовок `Authorization: Bearer <accessToken>`.
7. Отозвать доступ: **Настройки → API → доверенные клиенты → удалить**.

Хранение на стороне Monitor: **hash** токена + метаданные клиента (`clientId`, имя, createdAt,
scopes), не plaintext токена в логах.

### 5.2. Ручной токен

1. **Настройки → API → «Создать токен»** (имя клиента, напр. «Телефон»).
2. Диалог показывает `accessToken` **один раз** (копировать токен / `Bearer …`).
3. Клиент с `clientId` вида `manual-<uuid>` попадает в доверенные; revoke как обычно.
4. Дальше те же вызовы с `Authorization: Bearer <accessToken>`.

Практика и curl: [EXTERNAL_API_USER_GUIDE_RU.md](EXTERNAL_API_USER_GUIDE_RU.md).

### Уровни доступа (v1)

| Уровень | Содержание |
|---------|------------|
| **Базовый** (после «Да») | `catalog`, чтение сигналов, `actions/invoke` для действий уровня `safe` / `confirm` по политике, список и `run` автоматизаций |
| **Опасные команды** | Отдельный **тумблер в Настройки → API** (не галочки в диалоге pairing): ADB, `adb_shell`, `adb_force_stop`, прочие `dangerous`. По умолчанию **выкл.** |

Диалог pairing без набора галочек — проще для пользователя. При необходимости позже можно
добавить scopes; в v1 достаточно пары «доверенный клиент» + «разрешить опасные».

### Запросы без токена

| Endpoint | Без Bearer |
|----------|------------|
| `GET /v1/health` | допускается урезанный ответ (alive / версия API / pairingActive / webPanelEnabled / climateControlType), **без** секретов и телеметрии |
| `POST /v1/pair/request` | только при активном pairing |
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

- Открытый bind в LAN **обязательно** сочетается с pairing; без сопряжённого токена телеметрия и
  команды недоступны.
- Rate limit на клиент/IP (реализационная константа).
- Audit: краткие строки в журнал приложения (pair approve/deny, invoke, run automation) без
  полного тела опасных команд.
- TLS: в v1 не блокирует релиз (типичная LAN ГУ). Позже — опциональный HTTPS / pinning.
- Подтверждение на ГУ для отдельных `confirm`-действий в v1 **не обязательно**, если клиент уже
  сопряжён; класс `dangerous` режется тумблером. Уточняется при реализации UI.

---

## 7. Контракт HTTP v1

Общие соглашения:

- `Content-Type: application/json; charset=utf-8`
- Ошибки: `{ "error": { "code": "...", "message": "..." } }` с подходящим HTTP-статусом
- Успех invoke/run: массив/объект результатов с `success` / `message` (как дух
  `AutomationActionResult`)

### 7.1. `GET /v1/health`

Без токена (публичный минимум) или с токеном (расширенный):

```json
{
  "ok": true,
  "apiVersion": 1,
  "catalogVersion": 4,
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

### 7.2. `POST /v1/pair/request`

Тело:

```json
{
  "clientId": "voice-phone-uuid-or-stable-id",
  "clientName": "TBox Voice (телефон)",
  "clientKind": "voice"
}
```

Ответ при ожидании UI: `202` + `{ "status": "pending", "requestId": "..." }`  
(клиент поллит `GET /v1/pair/status?requestId=` **или** долгий ответ с таймаутом — выбрать при
реализации; предпочтительно pending + poll, чтобы не держать соединение на диалоге).

При approve:

```json
{
  "status": "approved",
  "accessToken": "...",
  "clientId": "voice-phone-uuid-or-stable-id",
  "expiresAt": null
}
```

`expiresAt: null` — бессрочно до revoke (v1). При deny / timeout: `status: "denied"`.

### 7.3. `GET /v1/catalog`

Требует Bearer. Машиночитаемое описание возможностей:

- сигналы: `id`, `valueType` (`number`\|`state`\|`position`), `sources` (с учётом backend ГУ:
  на Android 9 у `engine_temperature` и `target_gear`, на Android 10 у `steering_speed` только
  `tbox`; первым идёт источник по умолчанию; `GET /v1/signals?source=head_unit` для них
  отвечает значением TBox), `unit`, `label`,
  `stateOptions` / `namedValues`, `typicalRange`, **`voiceAliasesRu`** (массив RU-фраз
  lowercase; минимум — нормализованный `label`, плюс разговорные синонимы для типовых
  вопросов),
- действия: типы из automations (`can_command`, `builtin`, `launch_application`, …) +
  `safety` (`safe`\|`confirm`\|`dangerous`) + **`voiceAliasesRu`**,
- `catalogVersion` (сейчас **4** — у `can_command` добавлены `operations`, `signalId` и `write`).

`write` — схема записи для сопряжённого клиента (TBox MQTT). Поле аддитивное: голосовой клиент его не читает.

| `write.kind` | Смысл |
|--------------|--------|
| `binary` | пары `off`/`on` → значение invoke |
| `options` | `state` как у сигнала, `value` — тело команды (ключ или целое) |
| `number` | `min`/`max`/`step`/`unit` и пары «число сигнала → value» |
| `pulse` | кнопки `open`/`close`, без `signalId` |

Если пару надёжно построить нельзя, `write` = `null`, а `signalId` всё равно указывает сигнал-подтверждение, когда он один. `catalogVersion` **3** остаётся в истории как aliases + `start_vad_voice`.

Источник истины для id — те же каталоги, что UI автоматизаций и
[AUTOMATIONS_AI_JSON_GUIDE_RU.md](AUTOMATIONS_AI_JSON_GUIDE_RU.md). Поле `voiceAliasesRu` —
обогащение для NLU в `ExternalApiVoiceAliasesRu`, не меняет `storageKey`.

### 7.4. `GET /v1/signals`

Требует Bearer. Единичный snapshot, без потока событий. Для `source=head_unit` чтение регистрирует интерес CAN этих сигналов и сразу делает pull, поэтому значение есть и без виджета на экране ГУ:

```
GET /v1/signals?ids=outside_temperature,fuel_level_percent&source=head_unit
```

Параметры:

| Query | Смысл |
|-------|--------|
| `ids` | список signal id через запятую (обязателен; лимит длины при реализации) |
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
У стёкол (`window_front_left`, `window_front_right`, `window_rear_left`, `window_rear_right`) `value` — `"0%"`, `"20%"`, `"80%"`, `"100%"` или `"open"` (стекло между остановками, A9 отдаёт −1), и есть поле `"open": true/false` — открыто ли стекло в любом положении.  
Для `geo_position` — объект координат по схеме, согласованной с автоматизациями (уточнить в
реализации; не сырой byte dump).

**Подписки на поток в v1 нет.** Клиент при необходимости повторяет GET.

### 7.5. `POST /v1/actions/invoke`

Требует Bearer. Тело — один action или массив (лимит N при реализации, ориентир ≤ 20):

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

Ответ: accepted / rejected + `message` (например, «не найдена», ошибки валидатора).

Голосовой клиент резолвит фразу по **имени** правила из списка; при неоднозначности — уточняет
у пользователя (логика клиента, не Monitor).

---

## 8. Расширение каталога автоматизаций (G1–G7)

Нужны для типовых голосовых вопросов; добавляются **в каталог автоматизаций** (сигналы +
provider + AI guide + тесты), чтобы UI правил и HTTP API получили их одновременно.
Существующие JSON правил без новых полей не меняются.

| ID | Signal id | Назначение | Статус |
|----|-----------|------------|--------|
| G1 | `tbox_connected` | TBox на связи (on/off), source `app` | **в каталоге** |
| G2 | `modem_signal_level` | уровень сигнала модема (число), `app` | **в каталоге** |
| G3 | `locate_status` | GPS-фикс GeoDisplay (on/off), `app` | **в каталоге** |
| G4 | `fuel_level_percent_filtered`, `fuel_level_liters` | фильтр % / калибр. литры, `app` | **в каталоге** |
| G5 | `gear_box_oil_temperature` | температура масла КПП, `tbox` | **в каталоге** |
| G6 | `active_trip_distance_km`, `active_trip_avg_fuel_l100km`, `active_trip_duration_s`, `active_trip_motor_hours`, `motor_hours` | поездка / моточасы, `app` | **в каталоге** |
| G7 | `media_title`, `media_artist` | now playing, `app` | **в каталоге** |

Дополнительно для голоса (не отдельные signal id): таблица **`voiceAliasesRu`** в выдаче
`/v1/catalog` (код: `ExternalApiVoiceAliasesRu` — label + curated RU-фразы для сигналов и
действий). `catalogVersion = 3`.

### Не в блокирующем списке v1

- наклон зеркал на R (backlog W-04);
- remote lock/unlock дверей как отдельная команда (уточнение W-05);
- сырые CAN-кадры по id как в legacy broadcast.

---

## 9. Политика классов действий

| Класс | Примеры | Поведение API |
|-------|---------|----------------|
| `safe` | чтение; климат set; медиа play/pause; громкость; toast | invoke при базовом токене |
| `confirm` | окна, люк, багажник pulse, Wi‑Fi off, restart TBox, run automation | в v1 разрешены базовому клиенту; при необходимости ужесточить позже |
| `dangerous` | `adb_set_tcp`, `adb_shell`, `adb_force_stop`; иные по решению реализации | только если в Monitor включён тумблер опасных команд |

Точная разметка `safety` по каждому builtin/can_command — таблица в коде + поле в `/v1/catalog`.

---

## 10. Голосовой клиент (вне этого документа как код)

Отдельное приложение (ГУ и/или телефон):

| Слой | v1 |
|------|-----|
| STT | офлайн RU (Vosk MVP; Sherpa опционально позже) |
| NLU | aliases + слоты по `/v1/catalog`; без LLM |
| Транспорт | единичные HTTP-запросы после pairing / ручного токена |
| TTS | **встроенный** офлайн (Piper/Sherpa); системный TTS ГУ **не** используем |

План Voice APK: [VOICE_APK_RU.md](VOICE_APK_RU.md) (**VAD Voice**, `vad.dashing.voice`;
модуль `:voice`; Piper TTS; Vosk; активация UI / wake-word opt-in / руль opt-in /
Intent из Monitor).

Именованные сценарии пользователя → `run` автоматизации по имени.  
Свободные команды машины → `invoke` по каталогу.

MQTT-клиент для Home Assistant — отдельное приложение на том же API:
[MQTT_APK_RU.md](MQTT_APK_RU.md) (**TBox MQTT**, `vad.dashing.mqtt`, модуль `:mqtt`).
Схема записи команд — `catalogVersion` 4 (`operations`, `signalId`, `write`).

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
| 6 | `GET /catalog`, `GET /signals` | Snapshot совпадает с каталогом автоматизаций | **сделано** (код) |
| 7 | `POST /actions/invoke` → validator + executor; тумблер dangerous в разделе API | Safe-команды работают; dangerous закрыты | **сделано** (код) |
| 8 | `GET /automations`, `POST .../run` → `requestRunNow` | Паритет с кнопкой UI | **сделано** (код) |
| 8a | PC smoke-клиент `tools/tbox_external_api_pair.py` | Сопряжение + health/catalog/signals/automations/invoke/run с LAN | **сделано** |
| 9 | `voiceAliasesRu` в catalog | Voice APK может матчить RU-фразы | **сделано** (`catalogVersion` 3) |
| 10 | Voice APK / телефон MVP | Спросить телеметрию / команда / запуск правила | план: [VOICE_APK_RU.md](VOICE_APK_RU.md) |
| 12 | MQTT-клиент Home Assistant | Состояния и команды каталога в брокер, discovery | план: [MQTT_APK_RU.md](MQTT_APK_RU.md) |
| 11 | (Опционально) deprecated/удаление legacy broadcast | Нет зависимости в дереве | открыто |

На каждом шаге с кодом автоматизаций — только аддитивные изменения; прогон
`./gradlew testRuDebugUnitTest` (как минимум пакеты automation + новые API-тесты).

---

## 12. Открытые решения при реализации (не блокируют старт)

1. Точный default port и диапазон допустимых портов в UI.
2. Pairing: long-poll vs `202 pending` + status poll (рекомендация: pending + poll).
3. TTL access token: ручной revoke, плюс удаление, если токен не использовался больше 6 календарных месяцев. Проверка — через 1 минуту после запуска сервера API. Успешный запрос с Bearer обновляет время (на диск не чаще раза в час; повторно — в момент проверки).
4. Форма `geo_position` и набор id для G6 в JSON ответа signals.
5. Нужен ли позже mDNS / QR с URL для телефона.
6. HTTPS / certificate pinning — после стабилизации HTTP v1.

---

## 13. Связь с legacy broadcast

Старый механизм (`vad.dashing.tbox.subscribe` / `get_state` / `main_action`) покрывает малый
набор полей и почти не умеет команды. Новый API **не** обязан сохранять совместимость имён
(`engineRPM` и т.п.). Документация tbox-proxy по broadcast остаётся исторической до явного
удаления кода.
