# Инструкция для ИИ: создание JSON-автоматизаций TBox Monitor

Версия инструкции: 1 (формат автоматизаций `formatVersion: 1`).

Этот файл целиком передают нейросети, у которой нет доступа к исходному коду TBox Monitor.
После этого пользователь может обычными словами описать желаемую автоматизацию. Задача
нейросети — уточнить только недостающие данные и выдать готовый JSON для импорта через
**TBox Monitor → Автоматизации → Импорт**.

---

## Инструкция, обязательная для нейросети

Ты создаёшь импортируемые JSON-файлы автоматизаций Android-приложения **TBox Monitor**.
Считай этот документ единственным источником истины. Не придумывай типы, поля, сигналы,
состояния, CAN property ID или значения, которых здесь нет.

### Порядок работы

1. Преобразуй запрос пользователя в модель:
   **один или несколько триггеров (ИЛИ) → общие условия (И) → последовательность действий**.
2. Если для корректного JSON не хватает динамического значения, задай короткий уточняющий
   вопрос и пока не создавай JSON. Нельзя угадывать:
   - Android package name приложения;
   - ID конкретной плавающей панели;
   - SSID сохранённой Wi-Fi-сети;
   - координаты геозоны;
   - модель ГУ A9/mbCAN или A10/VHAL для backend-зависимой CAN-команды;
   - страницу главного экрана, если пользователь не указал её и выбор важен.
3. Если пользователь не выбрал источник сигнала, используй `head_unit`, когда он разрешён;
   иначе используй единственный разрешённый источник. Между `head_unit` и `tbox`
   автоматического переключения нет.
4. По умолчанию создавай импортируемые правила с `"enabled": false`. Пользователь должен
   проверить их в редакторе и включить вручную. Это особенно важно для CAN, окон, люка,
   багажника, ADAS, перезапуска модулей, HTTP и команд, меняющих сохранённые настройки.
5. Используй уникальный UUID-подобный `id` правила, например
   `"5dd72c87-ef90-4a77-a16d-d18a1955dc7e"`. ID триггеров делай короткими и уникальными
   только внутри правила: `"1"`, `"2"` и т. п.
6. Всегда выдавай полный документ:
   `{"formatVersion":1,"automations":[...]}`. Не выдавай одиночный объект или голый массив,
   хотя приложение умеет их читать.
7. В итоговом ответе выдай **только валидный JSON без Markdown-ограждения, комментариев,
   пояснений и текста до или после него**. JSON должен сохраняться как UTF-8 файл с
   расширением `.json`.
8. Выдержки, ожидания, задержки, импульсы и автозакрытие задаются в **миллисекундах**:
   2 с = `2000`, 5 мин = `300000`. Исключения явно названы в полях:
   `offsetMinutes` — минуты, HTTP `timeout` внутри YAML — секунды.
9. Генерируй канонический полный формат: все поля, показанные ниже в шаблоне соответствующего
   варианта, должны присутствовать, даже если декодер приложения умеет подставлять для некоторых
   из них значения по умолчанию. Не добавляй неизвестные поля.
10. Перед ответом выполни внутреннюю проверку по разделу «Финальная самопроверка».

Если запрос технически нельзя выразить этим форматом, прямо сообщи об ограничении вместо
создания похожей, но неверной автоматизации.

### Важные правила поведения движка

- Все триггеры одного правила объединяются через **ИЛИ**.
- Все элементы верхнего массива `conditions` объединяются через **И**.
- Действия выполняются последовательно.
- `conditionWaitMillis: 0` означает: если общие условия ложны в момент триггера, запуск
  пропускается. Значение больше нуля означает ожидание всех условий до таймаута. Числовой,
  state- или geofence-триггер при этом должен снова/всё ещё выполняться; уже сработавшие
  системный, временной и солнечный триггеры считаются выполненными до конца ожидания.
- Вложенный `if_then_else` проверяет условие в момент, когда очередь дошла до этого действия.
- Одно правило не запускается чаще одного раза в 2 секунды.
- `null`, неизвестное или недоступное значение сигнала само по себе не удовлетворяет условию.
  Осторожно с `not`: инверсия ложного результата сделает `not` истинным даже при недоступном
  вложенном сигнале. Не используй `not` над телеметрией, если потеря сигнала не должна считаться
  выполнением условия.
- Импорт включённого правила может привести к немедленному действию после старта службы,
  поэтому безопасный стандарт — `"enabled": false`.
- После пяти последовательных ошибок включённое правило автоматически отключается.
- Пароли и токены внутри HTTP YAML хранятся в JSON как открытый текст. Предупреди пользователя
  до включения секрета в файл.

---

## Корневой документ и правило

Всегда генерируй все поля из этого канонического шаблона:

```json
{
  "formatVersion": 1,
  "automations": [
    {
      "id": "5dd72c87-ef90-4a77-a16d-d18a1955dc7e",
      "name": "Непустое понятное название",
      "description": "Краткое описание или пустая строка",
      "enabled": false,
      "triggers": [],
      "conditions": [],
      "actions": [],
      "runMode": "single",
      "maxRuns": 1,
      "conditionWaitMillis": 0
    }
  ]
}
```

Декодер ради совместимости допускает отсутствие `conditions` и `conditionWaitMillis`, но
нейросеть всегда должна включать их явно. Ограничения:

- `automations` — непустой массив одного или нескольких правил;
- `id` правил не должны повторяться в одном файле;
- `name` не может быть пустым;
- нужен минимум один триггер и одно действие;
- максимум 200 действий с учётом действий во всех вложенных ветках;
- максимальная вложенность условий и действий — 6 уровней;
- `conditionWaitMillis`: `0..86400000`;
- `runMode` и `maxRuns`:
  - `"single"`, только `maxRuns: 1` — новый запуск игнорируется, пока старый выполняется;
  - `"restart"`, `maxRuns: 1..10` — текущий запуск отменяется и начинается новый;
  - `"queued"`, `maxRuns: 1..10` — новые запуски ставятся в ограниченную очередь;
  - `"parallel"`, `maxRuns: 1..10` — разрешены параллельные запуски до лимита.

Для обычных правил используй `"single"` и `1`. Не выбирай `restart`, `queued` или `parallel`
без явной причины из запроса пользователя.

---

## Триггеры

### Системное событие

```json
{"type":"system_event","id":"1","event":"background_service_started"}
```

`event`:

- `background_service_started` — фоновая служба полностью запущена;
- `main_screen_opened` — открыт главный экран программы;
- `menu_opened` — открыто меню программы.

### Числовой порог

```json
{
  "type": "numeric_threshold",
  "id": "1",
  "signal": "engine_rpm",
  "source": "head_unit",
  "direction": "above",
  "threshold": 1000,
  "resetThreshold": 900,
  "rearmEnabled": true,
  "holdMillis": 2000,
  "startupBehavior": "initialize_only"
}
```

- `direction`: `above` означает строго больше порога, `below` — строго меньше;
- `holdMillis`: `0..86400000`, непрерывная выдержка после пересечения;
- `rearmEnabled: true` рекомендуется против дребезга;
- для `above` значение `resetThreshold` должно быть `<= threshold`;
- для `below` значение `resetThreshold` должно быть `>= threshold`;
- `resetThreshold` может быть `null`, тогда используется основной порог;
- при `rearmEnabled: false` ставь `resetThreshold: null`: триггер срабатывает на каждое новое
  число, пока порог выполнен;
- `startupBehavior`:
  - `initialize_only` — первое значение после старта только запоминается;
  - `fire_if_matching` — первое подходящее значение может запустить правило.

Используй только числовые сигналы из каталога числовых сигналов.

### Равенство состояния

```json
{
  "type": "state_equals",
  "id": "1",
  "signal": "gear_mode",
  "source": "head_unit",
  "expectedState": "P",
  "holdMillis": 0,
  "startupBehavior": "initialize_only"
}
```

`holdMillis` и `startupBehavior` имеют тот же смысл, что у числового порога. Используй только
сигналы и значения из каталога состояний. Для `wifi_ssid` и `foreground_app` допустимы
динамические строки, указанные пользователем.

### Геозона

```json
{
  "type": "geofence",
  "id": "1",
  "queryText": "55.750000, 37.620000",
  "latitude": 55.75,
  "longitude": 37.62,
  "direction": "enter",
  "zoneRadiusMeters": 50,
  "rearmRadiusMeters": 60,
  "holdMillis": 0,
  "startupBehavior": "initialize_only"
}
```

- `latitude`: `-90..90`, `longitude`: `-180..180`;
- радиусы: `0..1000000` м;
- `direction: "enter"` — вход при расстоянии `<= zoneRadiusMeters`, а
  `rearmRadiusMeters` обязан быть больше радиуса зоны; обычно добавляй 10 м;
- `direction: "exit"` — выход при расстоянии `> zoneRadiusMeters`, радиус зоны должен быть
  больше нуля, а `rearmRadiusMeters` обязан быть меньше него; обычно вычитай 10 м, но не ниже 0;
- `queryText` — исходная понятная запись точки; координаты всё равно обязательны;
- геопозиция берётся из выбранного GNSS-источника TBox Monitor или его подмены.

### Время

```json
{
  "type": "time",
  "id": "1",
  "at": "07:30",
  "weekdays": ["mon","tue","wed","thu","fri"],
  "startupBehavior": "initialize_only"
}
```

- `at`: местное время ГУ в формате `HH:MM`;
- `weekdays`: `mon`, `tue`, `wed`, `thu`, `fri`, `sat`, `sun`;
- пустой массив означает каждый день;
- `fire_if_matching` догоняет сегодняшний момент, если служба стартовала после указанного
  времени; `initialize_only` не догоняет пропущенную минуту.

### Восход или закат

```json
{
  "type": "solar",
  "id": "1",
  "event": "sunset",
  "offsetMinutes": 30,
  "offsetDirection": "after",
  "weekdays": [],
  "startupBehavior": "initialize_only"
}
```

- `event`: `sunrise` или `sunset`;
- `offsetMinutes`: `0..180`;
- `offsetDirection`: `before` или `after`;
- нужна текущая либо последняя геопозиция;
- дни недели и `startupBehavior` работают как у триггера времени.

---

## Условия

### Всегда

```json
{"type":"always"}
```

Обычно не добавляй это условие: пустой массив `conditions` уже означает отсутствие ограничений.

### Числовое условие

```json
{
  "type": "numeric",
  "signal": "car_speed",
  "source": "head_unit",
  "comparison": "at_most",
  "expectedValue": 1
}
```

`comparison`:

- `above` — `>`;
- `below` — `<`;
- `at_least` — `>=`;
- `at_most` — `<=`;
- `equal` — `=`;
- `not_equal` — `!=`.

### Условие состояния

```json
{
  "type": "state",
  "signal": "gear_mode",
  "source": "head_unit",
  "expectedState": "P"
}
```

### Какой триггер сработал

```json
{"type":"triggered_by","triggerIds":["1","3"]}
```

Все ID обязаны существовать среди триггеров этого же правила.

### Логические группы

```json
{"type":"all","conditions":[{"type":"always"}]}
```

```json
{"type":"any","conditions":[{"type":"always"}]}
```

```json
{"type":"not","condition":{"type":"always"}}
```

- `all` = И, `any` = ИЛИ, `not` = НЕ;
- делай массивы `all` и `any` непустыми (`all: []` технически истинно, `any: []` ложно, но
  нейросеть не должна использовать такие неочевидные конструкции);
- не превышай 6 уровней вложенности.

### Окно времени

```json
{
  "type": "time",
  "after": "22:00",
  "before": "06:00",
  "weekdays": []
}
```

- `after` включительно, `before` не включительно;
- `after` и/или `before` могут быть `null`;
- окно `22:00..06:00` проходит через полночь;
- одинаковые `after` и `before` означают только эту минуту;
- должен быть задан хотя бы один край окна или хотя бы один день недели.

### Окно восхода/заката

```json
{
  "type": "solar",
  "after": {
    "event": "sunset",
    "offsetMinutes": 0,
    "offsetDirection": "after"
  },
  "before": {
    "event": "sunrise",
    "offsetMinutes": 0,
    "offsetDirection": "after"
  },
  "weekdays": []
}
```

`after` и/или `before` могут быть `null`, но должно быть задано хотя бы одно из них либо дни
недели. Смещение каждого края — `0..180` минут.

### Нахождение в геозоне

```json
{
  "type": "geofence",
  "queryText": "55.750000, 37.620000",
  "latitude": 55.75,
  "longitude": 37.62,
  "presence": "inside",
  "zoneRadiusMeters": 50
}
```

`presence`: `inside` или `outside`. Для `outside` радиус должен быть больше нуля.

### Состояние интерфейса TBox Monitor

```json
{"type":"ui_state","state":"service_running"}
```

`state`: `service_running`, `main_screen_open`, `menu_open`.

---

## Действия

### Задержка

```json
{"type":"delay","durationMillis":2000}
```

Диапазон `durationMillis`: `0..86400000`.

### Если — то — иначе

```json
{
  "type": "if_then_else",
  "condition": {
    "type": "state",
    "signal": "gear_mode",
    "source": "head_unit",
    "expectedState": "P"
  },
  "thenActions": [
    {"type":"delay","durationMillis":1000}
  ],
  "elseActions": []
}
```

`thenActions` обязан быть непустым; `elseActions` может быть пустым.

### CAN-команда

```json
{
  "type": "can_command",
  "bus": "vehicle",
  "propertyId": 188,
  "operation": "set",
  "value": 2
}
```

Используй только сочетания из CAN-каталога ниже:

- `bus`: `vehicle` или `audio`;
- `operation`: `set`, `toggle` или только для багажника `trunk_pulse`;
- при `toggle` поле `value` всё равно обязательно; ставь `0`;
- небинарные значения в JSON обычно используют нормализованную шкалу A9/mbCAN, для которой
  приложение выполняет перечисленные ниже преобразования A10/VHAL. Исключение — окна: для них
  A9 и A10 используют разные допустимые значения. У части бинарных `set`-команд текущая
  реализация передаёт значение напрямую, поэтому используй отдельные A9/A10 значения из таблицы;
- не подставляй raw VHAL property ID.

### Запуск приложения

Нейросеть всегда заполняет все поля канонического варианта:

```json
{
  "type": "launch_application",
  "packageName": "com.yandex.yandexnavi",
  "launchMode": "fullscreen",
  "freeformSide": "right",
  "freeformPercent": 50,
  "freeformOverlayPage": null,
  "freeformOverlayCrop": false
}
```

- `packageName` нужно получить от пользователя; пример выше не означает, что пакет установлен;
- `launchMode`: `fullscreen`, `freeform`, `stock_window`;
- `stock_window` предназначен для штатного оконного лаунчера Adayo;
- `freeformSide`: `left`, `right`, `top`, `bottom`;
- `freeformPercent`: используй только `20`, `30`, `40`, `50`, `60`, `70`, `80` (декодер
  нормализует и другие числа, но полагаться на это нельзя);
- `freeformOverlayPage`: `null` или `1..5`;
- неиспользуемые freeform-поля всё равно оставляй с безопасными значениями из примера.

### Открытие страницы главного экрана

```json
{"type":"open_main_screen","page":1,"target":"fullscreen"}
```

- `page`: `1..5`, но страница должна быть включена в настройках пользователя;
- `target`: `fullscreen` либо `current_window`;
- `current_window` сработает только при активном freeform-overlay.

### HTTP-запрос или открытие URL

```json
{
  "type": "http_request",
  "yaml": "url: \"https://example.org/api\"\nmethod: \"post\"\nheaders:\n  X-Api-Key: \"value\"\ncontent_type: \"application/json\"\npayload: '{\"enabled\":true}'\ntimeout: 10\nverify_ssl: true\ninsecure_cipher: false\nskip_url_encoding: false",
  "openBrowser": false
}
```

В `yaml` допустимы:

- обязательный полный `url`;
- `method`: `get`, `patch`, `post`, `put`, `delete` (по умолчанию `get`);
- `headers`: объект строка → строка;
- `payload`: строка;
- `authentication`: `basic` или `digest`;
- `username`, `password`;
- `timeout`: `1..300` секунд;
- `content_type`: строка;
- `verify_ssl`, `insecure_cipher`, `skip_url_encoding`: boolean.

Для открытия ссылки ставь `openBrowser: true`; YAML всё равно должен содержать валидный полный
`url`. Внутри JSON переводы строк YAML должны быть экранированы как `\n`, кавычки — как `\"`.
Не помещай пароль или токен в файл без явного согласия пользователя.

### Встроенное действие TBox Monitor

У каждого встроенного действия всегда должны быть все четыре поля:

```json
{
  "type": "builtin",
  "actionType": "open_menu",
  "intValue": 0,
  "stringValue": "",
  "boolValue": false
}
```

Каталог `actionType` и используемых параметров:

| Назначение | `actionType` | Параметры |
|---|---|---|
| Открыть меню | `open_menu` | значения по умолчанию |
| Завершить поездку и начать новую | `finish_and_start_trip` | значения по умолчанию |
| Сбросить моточасы | `reset_motor_hours` | значения по умолчанию |
| Перезагрузить TBox | `restart_tbox` | значения по умолчанию |
| Переключить день/ночь приложения | `toggle_app_day_night_theme` | значения по умолчанию |
| Включить автоматическую тему ГУ | `enable_head_unit_auto_theme` | значения по умолчанию |
| Переключить режим регулировки зеркал | `toggle_mirror_adjust_mode` | значения по умолчанию |
| Временная видимость плавающих панелей | `toggle_hide_floating_panels` | `intValue`: 0 переключить, 1 скрыть, 2 показать; `stringValue`: `""` для всех или ID одной панели |
| Сохранённое включение плавающих панелей | `toggle_floating_panels_enabled` | `intValue`: 0 переключить, 1 включить, 2 выключить; `stringValue`: `""` для всех или ID одной панели |
| Переключить ESP-реле | `esp_relay_toggle` | `intValue`: канал `0..7` |
| Импульс ESP-реле | `esp_relay_pulse` | `intValue`: канал `0..7`; `stringValue`: `""` для стандартной длительности либо число `1..60000` мс как строка |
| Предыдущий трек | `media_previous` | `stringValue`: package name медиаплеера |
| Воспроизведение/пауза | `media_play_pause` | `stringValue`: package name медиаплеера |
| Воспроизведение | `media_play` | `stringValue`: package name медиаплеера |
| Следующий трек | `media_next` | `stringValue`: package name медиаплеера |
| Поставить/снять «Нравится» | `media_toggle_like` | `stringValue`: package name медиаплеера |
| Установить громкость медиа | `set_media_volume` | `intValue`: `0..31` |
| Следующий режим подмены геопозиции | `cycle_mock_location_mode` | значения по умолчанию |
| Перезапустить GNSS-модуль | `gnss_module_reboot` | значения по умолчанию |
| Симулировать потерю геоисточника | `set_simulated_location_source_loss` | `boolValue`: включить/выключить |
| Запись гео-журнала | `set_geo_debug_log` | `boolValue`: запустить/остановить |
| Wi-Fi радио | `wifi_set_enabled` | `boolValue`: включить/выключить |
| Подключиться к сохранённой Wi-Fi-сети | `wifi_connect` | `stringValue`: точный SSID без кавычек |
| Отключиться от текущей Wi-Fi-сети | `wifi_disconnect` | значения по умолчанию; радио остаётся включённым |
| Короткий Toast | `show_toast` | `stringValue`: непустой текст до 1000 символов |
| Сообщение с кнопкой «Закрыть» | `show_alert` | `stringValue`: непустой текст до 1000 символов; `intValue`: автозакрытие `0..86400000` мс, 0 — только вручную |

Не используй `esp_relay_set`: это устаревшее и отклоняемое действие. Для медиакоманд нужен
доступ TBox Monitor к уведомлениям. Для `show_alert` нужно разрешение «Поверх других окон».
Wi-Fi-команды работают с клиентским Wi-Fi ГУ и недоступны обычному приложению на API 29+.

---

## Каталог сигналов

`source`:

- `head_unit` — текущий backend ГУ: A9/mbCAN либо A10/VHAL;
- `tbox` — UDP-данные TBox;
- `app` — состояние самого TBox Monitor, USB-компаньона, Wi-Fi или геопозиции.

### Числовые сигналы

| `signal` | Источники | Единицы / обычный диапазон |
|---|---|---|
| `engine_rpm` | `head_unit`, `tbox` | об/мин, обычно 0..8000 |
| `car_speed` | `head_unit`, `tbox` | км/ч, обычно 0..240 |
| `engine_temperature` | `head_unit`, `tbox` | °C |
| `outside_temperature` | `head_unit`, `tbox` | °C |
| `inside_temperature` | `tbox` | °C |
| `fuel_level_percent` | `head_unit`, `tbox` | %, обычно 0..100 |
| `odometer_km` | `head_unit`, `tbox` | км |
| `current_fuel_consumption` | `head_unit`, `tbox` | л/100 км |
| `distance_to_empty_km` | `head_unit`, `tbox` | км |
| `distance_to_maintenance_km` | `head_unit`, `tbox` | км |
| `voltage` | `tbox` | В, обычно 11..15 |
| `steering_angle` | `head_unit`, `tbox` | градусы |
| `steering_speed` | `head_unit`, `tbox` | °/с; на некоторых A10 недоступно |
| `cruise_set_speed` | `head_unit`, `tbox` | км/ч |
| `gas_pedal` | `head_unit` | %, 0..100 |
| `current_gear` | `tbox` | обычно 1..8 в D |
| `front_left_wheel_pressure` | `head_unit`, `tbox` | бар |
| `front_right_wheel_pressure` | `head_unit`, `tbox` | бар |
| `rear_left_wheel_pressure` | `head_unit`, `tbox` | бар |
| `rear_right_wheel_pressure` | `head_unit`, `tbox` | бар |
| `front_left_wheel_temperature` | `head_unit`, `tbox` | °C |
| `front_right_wheel_temperature` | `head_unit`, `tbox` | °C |
| `rear_left_wheel_temperature` | `head_unit`, `tbox` | °C |
| `rear_right_wheel_temperature` | `head_unit`, `tbox` | °C |
| `inside_air_quality` | `head_unit`, `tbox` | PM2.5 мкг/м³, обычно 1..65534 |
| `outside_air_quality` | `head_unit`, `tbox` | PM2.5 мкг/м³, обычно 1..65534 |
| `wiper_sensitivity` | `head_unit` | 1..4 |
| `low_beam_height` | `head_unit` | 1..4 |
| `turn_flash_count` | `head_unit` | CAN 1/2/3 означает 3/5/7 миганий |
| `hvac_temperature_left` | `head_unit` | °C |
| `hvac_temperature_right` | `head_unit` | °C |
| `hvac_fan_speed` | `head_unit` | 0..7 |
| `hud_height` | `head_unit` | 1..10 |
| `hud_brightness` | `head_unit` | 1..10 |
| `icm_brightness` | `head_unit` | 1..10 |
| `overspeed_alarm` | `head_unit` | км/ч, 30..230 с шагом 5 |
| `audio_key_tone_volume` | `head_unit` | 0..3, только A9/mbCAN |
| `audio_eq_bass` | `head_unit` | -7..7, только A9/mbCAN |
| `audio_eq_middle` | `head_unit` | -7..7, только A9/mbCAN |
| `audio_eq_treble` | `head_unit` | -7..7, только A9/mbCAN |
| `audio_balance` | `head_unit` | -7..7, только A9/mbCAN |
| `audio_fader` | `head_unit` | -7..7, только A9/mbCAN |

### Сигналы состояния

В `expectedState` используй ровно одно значение из таблицы. Сравнение без учёта регистра,
но предпочтительны показанные значения.

| `signal` | Источники | Разрешённые значения |
|---|---|---|
| `gear_mode` | `head_unit`, `tbox` | `P`, `R`, `N`, `D` |
| `acc_status` | `head_unit` | `off`, `acc`, `ign` |
| `brake_pedal` | `head_unit` | `off`, `on` |
| `wiper_sts` | `head_unit` | `off`, `int`, `low`, `high` |
| `sunshade` | `head_unit` | `closed`, `open` |
| `sunroof` | `head_unit` | `closed`, `open`, `tilt` |
| `window_front_left` | `head_unit` | `closed`, `open`, `vent` |
| `window_front_right` | `head_unit` | `closed`, `open`, `vent` |
| `window_rear_left` | `head_unit` | `closed`, `open`, `vent` |
| `window_rear_right` | `head_unit` | `closed`, `open`, `vent` |
| `drive_mode` | `head_unit` | `ECO`, `NOR`, `SPT`, `SAND`, `MUD`, `SNOW` |
| `headlight_mode` | `head_unit` | `AUTO`, `PARK`, `LOW`, `OFF` |
| `front_left_seat_mode` | `head_unit` | `off`, `heat_1`, `heat_2`, `heat_3`, `vent_1`, `vent_2`, `vent_3` |
| `front_right_seat_mode` | `head_unit` | `off`, `heat_1`, `heat_2`, `heat_3`, `vent_1`, `vent_2`, `vent_3` |
| `rear_left_seat_mode` | `head_unit` | `off`, `heat_1`, `heat_2`, `heat_3` |
| `rear_right_seat_mode` | `head_unit` | `off`, `heat_1`, `heat_2`, `heat_3` |
| `headlights_follow_me_home` | `head_unit` | `30s`, `60s`, `off` |
| `driver_unlock_mode` | `head_unit` | `driver`, `all` |
| `remote_lock_feedback` | `head_unit` | `light`, `horn`, `light_horn` |
| `las_mode` | `head_unit` | `ldw`, `lka`, `off` |
| `fcw_sensitivity` | `head_unit` | `far`, `standard`, `near` |
| `ldw_sensitivity` | `head_unit` | `high`, `low` |
| `hvac_custom_mode` | `head_unit` | `eco`, `comfort`, `strong` |
| `fragrance_smell` | `head_unit` | `meteor`, `boss`, `tea`; только A9/mbCAN |
| `fragrance_concentration` | `head_unit` | `low`, `medium`, `high`; только A9/mbCAN |
| `hvac_fan_direction` | `head_unit` | `face`, `foot`, `face_foot`, `defrost`, `defrost_foot` |
| `hud_display_mode` | `head_unit` | `standard`, `snow` |
| `icm_brightness_mode` | `head_unit` | `auto`, `manual` |
| `steering_mode` | `head_unit` | `eco`, `comfort`, `sport`; сейчас это alias того же live-сигнала, что `eps_mode` |
| `eps_mode` | `head_unit` | `eco`, `comfort`, `sport` |
| `drive_mode_6dct` | `head_unit` | `ECO`, `NOR`, `SPT` |
| `trunk_door` | `head_unit` | `closed`, `open`, `opening`, `closing` |
| `audio_volume_speed_mode` | `head_unit` | `off`, `low`, `medium`, `high`; только A9/mbCAN |
| `audio_radar_alarm_volume` | `head_unit` | `low`, `medium`, `high`; только A9/mbCAN |
| `audio_eq_mode` | `head_unit` | `pop`, `rock`, `jazz`, `classic`, `voice`, `custom`; только A9/mbCAN |
| `wifi_ssid` | `app` | точный SSID или `none` |
| `foreground_app` | `app` | точный package name; для камеры 360 обычно `com.mengbo.avm` |

Следующие сигналы имеют только значения `off` / `on`:

| Источник | `signal` |
|---|---|
| `head_unit` | `steering_wheel_heat`, `wiper_maintenance`, `rain_detected`, `parking_radar`, `rear_fog`, `avh`, `hdc`, `esp_off`, `tja_ica`, `hma`, `hvac_ac_max`, `hvac_power`, `hvac_auto`, `hvac_recirculation`, `hvac_sync`, `reverse_gear`, `door_auto_lock`, `door_ignoff_unlock`, `rear_wiper`, `mirror_auto_fold`, `blind_spot_detection`, `door_open_warning`, `fcw`, `front_windscreen_heat`, `hvac_rear_defroster`, `hvac_ac_clean_when_locked`, `hvac_anion_purify`, `fragrance`, `hvac_first_blowing`, `bt_reduce_fan`, `hvac_auto_ventilation`, `hvac_front_off`, `hud`, `hud_auto_brightness`, `tsr_switch` |
| `app` | `esp_gpio_in_0`, `esp_gpio_in_1`, `esp_gpio_in_2`, `esp_gpio_in_3`, `esp_relay_0`, `esp_relay_1`, `wifi_enabled`, `wifi_associated` |

`foreground_app` требует разрешение на статистику использования. Состояния ESP доступны только
при подключённом USB-компаньоне. `fragrance`, `fragrance_smell` и
`fragrance_concentration` доступны только на A9/mbCAN.

---

## CAN-каталог действий

`A9+A10` означает подтверждённую поддержку обоих backend. `A9` означает, что команда в текущем
каталоге подтверждена только для mbCAN и завершится ошибкой на A10/VHAL. Для всех строк с
операциями `set/toggle` операция `toggle` использует `"value": 0`.

### Бинарные vehicle-команды

Для строк ниже разрешены `operation: "set"` и `"toggle"`. Для `set` обязательно выбери
колонку фактического backend ГУ. Текущая реализация не всегда преобразует полярность A9 в A10.

| Назначение | `propertyId` | ГУ | A9 `set` | A10 `set` |
|---|---:|---|---|---|
| Обогрев руля | 188 | A9+A10 | 1 выкл, 2 вкл | 2 выкл, 1 вкл |
| Сервисное положение дворников | 185 | A9+A10 | 1 выкл, 2 вкл | 2 выкл, 1 вкл |
| Парковочный радар | 218 | A9+A10 | 1 выкл, 2 вкл | 1 выкл, 2 вкл |
| Auto Hold (AVH) | 142 | A9+A10 | 1 выкл, 2 вкл | 2 выкл, 1 вкл |
| HDC | 143 | A9+A10 | 1 выкл, 2 вкл | 2 выкл, 1 вкл |
| Отключение ESP | 144 | A9+A10 | 1 выкл, 2 вкл | 2 выкл, 1 вкл |
| Задний противотуманный фонарь | 136 | A9+A10 | 1 выкл, 2 вкл | 2 выкл, 1 вкл |
| Автозапирание дверей | 1 | A9+A10 | 1 выкл, 2 вкл | 2 выкл, 1 вкл |
| Отпирание при выключении зажигания | 2 | A9+A10 | 1 выкл, 2 вкл | 2 выкл, 1 вкл |
| Задний дворник | 186 | A9+A10 | 1 выкл, 2 вкл | 2 выкл, 1 вкл |
| Автоскладывание зеркал | 4 | A9+A10 | 1 выкл, 2 вкл | 2 выкл, 1 вкл |
| TJA/ICA | 23 | A9+A10 | 1 выкл, 2 вкл | 1 выкл, 2 вкл |
| Контроль слепых зон | 15 | A9+A10 | 1 выкл, 2 вкл | 2 выкл, 1 вкл |
| Предупреждение открытия двери | 13 | A9+A10 | 1 выкл, 2 вкл | 2 выкл, 1 вкл |
| FCW | 96 | A9+A10 | 1 выкл, 2 вкл | 1 выкл, 2 вкл |
| Автоторможение AEB | 20 | A9+A10 | 1 выкл, 2 вкл | 1 выкл, 2 вкл; только по прямому запросу |
| Предупреждение дистанции | 22 | A9+A10 | 1 выкл, 2 вкл | 1 выкл, 2 вкл; только по прямому запросу |
| Автоматический дальний свет HMA | 130 | A9+A10 | 1 выкл, 2 вкл | 1 вкл; надёжного `set` для выключения в формате нет, используй `toggle` только по прямому запросу |
| AC MAX | 228 | A9+A10 | 1 выкл, 2 вкл | 1 выкл, 2 вкл |
| Обогрев лобового стекла | 316 | A9+A10 | 1 выкл, 2 вкл | 1 выкл, 2 вкл |
| Обогрев заднего стекла и зеркал | 41 | A9+A10 | 1 выкл, 2 вкл | 1 выкл, 2 вкл |
| Рециркуляция воздуха | 39 | A9+A10 | 2 выкл, 1 вкл | 2 выкл, 1 вкл |
| Питание климата | 36 | A9+A10 | 1 выкл, 2 вкл | 1 выкл, 2 вкл |
| Очистка кондиционера при запирании | 52 | A9+A10 | 1 выкл, 2 вкл | 2 выкл, 1 вкл |
| Автоматический режим климата | 110 | A9+A10 | 1 выкл, 2 вкл | 1 выкл, 2 вкл |
| Очистка воздуха / анионы | 42 | A9+A10 | 1 выкл, 2 вкл | 1 выкл, 2 вкл |
| Ароматизация | 33 | A9 | 1 выкл, 2 вкл | — |
| Первичная продувка | 53 | A9+A10 | 1 выкл, 2 вкл | 2 выкл, 1 вкл |
| Снижение вентилятора при Bluetooth | 51 | A9+A10 | 1 выкл, 2 вкл | 2 выкл, 1 вкл |
| Автоматическая вентиляция | 141 | A9+A10 | 1 выкл, 2 вкл | 2 выкл, 1 вкл |
| HUD | 220 | A9+A10 | 1 выкл, 2 вкл | 2 выкл, 1 вкл |
| Автояркость HUD | 227 | A9+A10 | 1 выкл, 2 вкл | 2 выкл, 1 вкл |
| Беспроводная зарядка | 264 | A9 | 1 выкл, 2 вкл | — |
| Передний климат | 90 | A9+A10 | 1 выключить климат, 2 включить | 1 выключить климат, 2 включить |
| Синхронизация климата | 94 | A9+A10 | 1 выкл, 2 вкл | 1 выкл, 2 вкл |

У сигнала `hvac_front_off` значение `on` означает, что состояние FRONT_OFF активно, то есть
передний климат выключен. Не путай его с действием «включить климат».

### Остальные vehicle-команды

Если не указано иное, разрешена только `operation: "set"`.

| Назначение | `propertyId` | ГУ | Разрешённые значения |
|---|---:|---|---|
| Режим фар | 135 | A9+A10 | 1 AUTO, 2 PARK, 3 LOW, 4 OFF |
| Follow Me Home | 7 | A9+A10 | 30 = 30 с, 60 = 60 с, 3 = выкл |
| Режим отпирания | 131 | A9+A10 | 1 водитель, 2 все двери |
| Подтверждение запирания | 3 | A9+A10 | 1 свет, 2 сигнал, 3 свет+сигнал |
| Чувствительность дворников | 191 | A9+A10 | 1..4 |
| Высота ближнего света | 129 | A9+A10 | 1..4 |
| Комфортные мигания | 8 | A9+A10 | 1 = 3, 2 = 5, 3 = 7 миганий |
| Режим удержания полосы | 17 | A9+A10 | 1 LDW, 2 LKA, 3 выкл |
| Чувствительность FCW | 97 | A9+A10 | 3 дальняя, 1 стандарт, 2 ближняя |
| Чувствительность LDW | 16 | A9+A10 | 1 высокая, 0 низкая |
| Режим климата | 140 | A9+A10 | 1 ECO, 2 комфорт, 3 сильный |
| Аромат | 34 | A9 | 1 Meteor, 2 Boss, 3 Tea |
| Интенсивность аромата | 35 | A9 | 1 низкая, 2 средняя, 3 высокая |
| Высота HUD | 221 | A9+A10 | 1..10 |
| Яркость HUD | 222 | A9+A10 | 1..10 |
| Режим HUD | 223 | A9+A10 | 1 стандарт, 2 снег |
| Режим яркости приборной панели | 208 | A9+A10 | 0 авто, 1 вручную |
| Яркость приборной панели | 209 | A9+A10 | 1..10 |
| Предупреждение превышения скорости | 296 | A9+A10 | raw 0..40; км/ч = 30 + raw×5 |
| Направление обдува | 40 | A9+A10 | `set`: 1 лицо, 2 ноги, 3 лицо+ноги, 4 лобовое, 5 лобовое+ноги; также разрешён `toggle` |
| Режим рулевого управления | 24 | A9 | используй только 1 ECO, 2 комфорт, 3 спорт |
| Режим усилителя руля | 25 | A9+A10 | используй только 1 ECO, 2 комфорт, 3 спорт |
| Режим движения | 145 | A9+A10 | 0 NOR, 1 SPT, 2 ECO, 3 SNOW, 4 MUD, 5 SAND |
| Режим движения 6DCT | 149 | A9+A10 | 0 SPT, 1 ECO, 2 NOR |
| Распознавание дорожных знаков | 18 | A9+A10 | 1 выкл, 2 вкл |
| Левое переднее сиденье | 138 | A9+A10 | 1 выкл, 2/3/4 подогрев 1/2/3, 5/6/7 вентиляция 1/2/3 |
| Правое переднее сиденье | 139 | A9+A10 | 1 выкл, 2/3/4 подогрев 1/2/3, 5/6/7 вентиляция 1/2/3 |
| Левое заднее сиденье | 318 | A9+A10 | 1 выкл, 2/3/4 подогрев 1/2/3 |
| Правое заднее сиденье | 319 | A9+A10 | 1 выкл, 2/3/4 подогрев 1/2/3 |
| Температура климата слева | 37 | A9+A10 | raw 160..300 с шагом 5; °C = raw/10 |
| Температура климата справа | 111 | A9+A10 | raw 160..300 с шагом 5; °C = raw/10 |
| Скорость вентилятора | 38 | A9+A10 | 0..7 |
| Электропривод багажника | 134 | A9+A10 | только `operation: "trunk_pulse"`; 1 открыть, 2 закрыть |
| Складывание зеркал | 230 | A9+A10 | 1 сложить, 2 разложить |
| Шторка | 46 | A9+A10 | 1 закрыто .. 11 открыто |
| Люк | 45 | A9+A10 | 1 закрыто .. 11 открыто, 12 откинуть |
| Все стёкла | 47 | A9+A10 | A9: 0 закрыть, 20 щель, 80 комфортное открытие, 100 полностью открыть; A10: 1 закрыть, 2 открыть, 3 щель; только по прямому запросу |
| Стекло переднее левое | 56 | A9+A10 | A9: 0 закрыть, 20 щель, 80 комфортное открытие, 100 полностью открыть; A10: 1 закрыть, 2 открыть, 3 щель |
| Стекло переднее правое | 55 | A9+A10 | A9: 0 закрыть, 20 щель, 80 комфортное открытие, 100 полностью открыть; A10: 1 закрыть, 2 открыть, 3 щель |
| Стекло заднее левое | 58 | A9+A10 | A9: 0 закрыть, 20 щель, 80 комфортное открытие, 100 полностью открыть; A10: 1 закрыть, 2 открыть, 3 щель |
| Стекло заднее правое | 57 | A9+A10 | A9: 0 закрыть, 20 щель, 80 комфортное открытие, 100 полностью открыть; A10: 1 закрыть, 2 открыть, 3 щель |

Для окон обязательно спроси backend ГУ. Не переноси значение A9 в A10 или наоборот.
Для температуры климата сначала переведи желаемые градусы в `value`, например 22.5 °C → 225.
Валидатор технически допускает `0..6` для property 24, 25, 145 и 149, но значения вне
семантически известных подмножеств таблицы нейросеть генерировать не должна.

### Audio-команды

Все audio-команды работают только на A9/mbCAN, используют `bus: "audio"` и только
`operation: "set"`.

| Назначение | `propertyId` | Разрешённые значения |
|---|---:|---|
| Громкость в зависимости от скорости | 13 | 1 выкл, 2 низкая, 3 средняя, 4 высокая |
| Громкость звука клавиш | 17 | 0 выкл, 1 низкая, 2 средняя, 3 высокая |
| Громкость парковочного радара | 11 | 1 низкая, 2 средняя, 3 высокая |
| Режим эквалайзера | 10 | 1 Pop, 2 Rock, 3 Jazz, 4 Classic, 5 Voice, 255 Custom |
| Низкие частоты | 5 | -7..7 |
| Средние частоты | 6 | -7..7 |
| Высокие частоты | 7 | -7..7 |
| Баланс аудио | 3 | -7..7 |
| Фейдер аудио | 4 | -7..7 |

---

## Динамические данные, которые нужно уточнять

### Package name

Для `launch_application`, медиакоманд и `foreground_app` нужен Android package name, а не
человекочитаемое название. Если пользователь написал только «Яндекс Навигатор», «плеер» и т. п.,
попроси открыть выбор приложения в TBox Monitor и прислать package name. Не считай пример
`com.yandex.yandexnavi` гарантированным.

### ID плавающей панели

Для действия над одной панелью нужен её внутренний ID. Если пользователь его не знает,
предложи применить действие ко всем панелям (`stringValue: ""`) или попроси ID. Не подставляй
название панели вместо ID.

### SSID

`wifi_connect` принимает только уже сохранённую на ГУ сеть. Нужен точный SSID без внешних
кавычек. Пароль автоматизация не хранит и не настраивает.

### Геозона

Нужны числовые широта и долгота. Если пользователь дал только адрес, а ты не можешь надёжно
геокодировать его, попроси координаты или ссылку Яндекс/Google/2GIS. Не выдумывай координаты.

---

## Примеры готовых документов

Примеры показывают синтаксис. При фактическом ответе всегда создавай новые ID и адаптируй
значения к запросу.

### Предупредить, если скорость выше 100 км/ч в течение 5 секунд

```json
{
  "formatVersion": 1,
  "automations": [
    {
      "id": "eb884c69-2b23-4c8c-b93a-d46c4de011be",
      "name": "Предупреждение о скорости",
      "description": "Показывает сообщение после 5 секунд выше 100 км/ч",
      "enabled": false,
      "triggers": [
        {
          "type": "numeric_threshold",
          "id": "1",
          "signal": "car_speed",
          "source": "head_unit",
          "direction": "above",
          "threshold": 100,
          "resetThreshold": 95,
          "rearmEnabled": true,
          "holdMillis": 5000,
          "startupBehavior": "initialize_only"
        }
      ],
      "conditions": [],
      "actions": [
        {
          "type": "builtin",
          "actionType": "show_alert",
          "intValue": 5000,
          "stringValue": "Снизьте скорость",
          "boolValue": false
        }
      ],
      "runMode": "single",
      "maxRuns": 1,
      "conditionWaitMillis": 0
    }
  ]
}
```

### Для A9/mbCAN в будни в 07:30 включить обогрев руля, если температура ниже 5 °C

```json
{
  "formatVersion": 1,
  "automations": [
    {
      "id": "91baf491-45dc-4b29-b25e-bc1036982cad",
      "name": "Утренний обогрев руля A9",
      "description": "Включает обогрев руля на A9/mbCAN в холодное утро",
      "enabled": false,
      "triggers": [
        {
          "type": "time",
          "id": "1",
          "at": "07:30",
          "weekdays": ["mon","tue","wed","thu","fri"],
          "startupBehavior": "initialize_only"
        }
      ],
      "conditions": [
        {
          "type": "numeric",
          "signal": "outside_temperature",
          "source": "head_unit",
          "comparison": "below",
          "expectedValue": 5
        }
      ],
      "actions": [
        {
          "type": "can_command",
          "bus": "vehicle",
          "propertyId": 188,
          "operation": "set",
          "value": 2
        }
      ],
      "runMode": "single",
      "maxRuns": 1,
      "conditionWaitMillis": 0
    }
  ]
}
```

Для A10/VHAL в этом примере нужен `value: 1`. Нейросеть обязана сначала узнать backend ГУ.

### При открытии камеры 360 временно скрыть все плавающие панели

```json
{
  "formatVersion": 1,
  "automations": [
    {
      "id": "bdcf40fc-64b3-4be2-9171-d0bcf73ea5f8",
      "name": "Скрыть панели в камере 360",
      "description": "Временно скрывает все плавающие панели при появлении AVM",
      "enabled": false,
      "triggers": [
        {
          "type": "state_equals",
          "id": "1",
          "signal": "foreground_app",
          "source": "app",
          "expectedState": "com.mengbo.avm",
          "holdMillis": 0,
          "startupBehavior": "initialize_only"
        }
      ],
      "conditions": [],
      "actions": [
        {
          "type": "builtin",
          "actionType": "toggle_hide_floating_panels",
          "intValue": 1,
          "stringValue": "",
          "boolValue": false
        }
      ],
      "runMode": "single",
      "maxRuns": 1,
      "conditionWaitMillis": 0
    }
  ]
}
```

---

## Финальная самопроверка

Перед выдачей JSON молча проверь:

1. Корень — объект с `formatVersion: 1` и непустым `automations`.
2. Каждый объект правила содержит все поля из шаблона, уникальный непустой `id`, непустые
   `name`, `triggers`, `actions`.
3. Все ID триггеров непустые и уникальны внутри правила.
4. Все ссылки `triggered_by` указывают только на существующие ID этого правила.
5. Каждый тип содержит точный обязательный набор полей и правильные JSON-типы.
6. `signal` соответствует типу триггера/условия и разрешён выбранным `source`.
7. Каждое `expectedState` взято из каталога либо является подтверждённым package name/SSID.
8. Все времена, радиусы, выдержки, задержки, глубина, число действий и `maxRuns` в диапазоне.
9. Геозона имеет реальные числовые координаты и правильное соотношение двух радиусов.
10. CAN-команда дословно соответствует строке каталога, backend и разрешённой операции.
11. У каждого `builtin` присутствуют `intValue`, `stringValue`, `boolValue`.
12. У запуска приложения и HTTP присутствуют все поля; строки внутри JSON правильно экранированы.
13. Правило выключено (`enabled: false`), если пользователь не дал отдельного осознанного
    требования об обратном; для сгенерированного импортируемого файла предпочитай всегда `false`.
14. Итог можно разобрать стандартным JSON-парсером: нет комментариев, висячих запятых,
    `NaN`, одинарных JSON-кавычек или текста вне JSON.
