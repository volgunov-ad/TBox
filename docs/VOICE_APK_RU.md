# Voice APK — план реализации

Статус: **в разработке** (этапы 1–5: каркас, NLU, signals, Piper TTS, Vosk STT).

Отдельное Android-приложение **VAD Voice** (`vad.dashing.voice`) — голосовой клиент к
External HTTP API TBox Monitor. Целевая платформа MVP: **только ГУ**.

Связанные документы:

| Документ | Роль |
|----------|------|
| [EXTERNAL_API_RU.md](EXTERNAL_API_RU.md) | Контракт HTTP `/v1` |
| [EXTERNAL_API_USER_GUIDE_RU.md](EXTERNAL_API_USER_GUIDE_RU.md) | Как включить API / токен |
| [AUTOMATIONS_AI_JSON_GUIDE_RU.md](AUTOMATIONS_AI_JSON_GUIDE_RU.md) | JSON действий и сигналов |

---

## 0. Зафиксированные решения

| Тема | Решение |
|------|---------|
| Платформа MVP | **Только ГУ** (телефон — позже) |
| Имя / package | **VAD Voice** / `vad.dashing.voice` |
| TTS | **Не** system TTS. Встроенный офлайн Piper (Sherpa-ONNX) |
| STT MVP | **Vosk** `vosk-model-small-ru` (~45 MB); Sherpa — если качество мало |
| Модели | **Вшить в APK** (ГУ часто без интернета) |
| NLU | `voiceAliasesRu` + имена автоматизаций; без LLM |
| Транспорт | External API `http://127.0.0.1:<port>/v1` |
| Репозиторий | **Модуль `:voice` в этом же git** → отдельный APK (не раздувает Monitor) |
| Дистрибуция MVP | **Сайдлоад** вместе с Monitor (USB / то же место, куда ставят Monitor). Магазины — не в MVP |
| Политика действий | Voice **не режет** сильнее Monitor: что разрешает токен + тумблер dangerous в Настройки→API, то и с голоса. (Пояснение: отдельный «запрет багажника только в Voice» не делаем.) |

### Активация (все три канала в scope MVP)

| Канал | По умолчанию | Где настраивается |
|-------|--------------|-------------------|
| Кнопка «Слушать» в UI Voice | вкл | Voice |
| Wake-word | **выкл** | Voice (слово настраиваемое, можно выключить) |
| Кнопка руля | **выкл** | Выбор кнопки + выкл; фактически через Monitor (см. §3) |
| Intent из Monitor / автоматизаций | всегда доступен | Контракт Intent + builtin в Monitor |

---

## 1. Цели MVP

1. На ГУ сказать фразу → ответ голосом + краткий UI.
2. Три класса команд:
   - **вопрос** → `GET /v1/signals` → озвучить значение;
   - **действие** → `POST /v1/actions/invoke`;
   - **сценарий** → имя правила → `POST /v1/automations/{id}/run`.
3. Полностью офлайн STT/TTS/NLU; HTTP только localhost к Monitor.
4. Активация: UI / wake-word (opt-in) / руль (opt-in) / Intent из Monitor.

Вне MVP:

- телефон как клиент;
- LLM / облако;
- диалог «потеплее ещё» с длинной памятью (короткий относительный adjust — по возможности);
- RuStore / свой OTA для Voice;
- замена Vosk на Sherpa (пока не упрёмся в качество).

---

## 2. Архитектура

```text
Активация: UI | wake-word | hard-key(via Monitor) | Intent
    → Listening session
    → STT (Vosk)
    → NLU (aliases / automation names)
    → HTTP Bearer → Monitor /v1
    → Formatter (RU фраза)
    → TTS (Piper bundle)
    → Динамик + UI
```

| Слой | Выбор |
|------|--------|
| TTS | Piper RU **irina-medium-int8** (sherpa-onnx OfflineTts), модель в assets APK |
| STT | Vosk small-ru, модель в assets APK |
| Wake-word | Отдельный лёгкий движок (openWakeWord / Porcupine-совместимый / keyword-spotting); **выкл** по умолчанию; своё слово в настройках |
| HTTP | `127.0.0.1` + порт из настроек (default 8765) |
| Auth | Pairing или ручной токен из Monitor |

---

## 3. Доработка TBox Monitor (обязательная)

Голосовой APK на ГУ **не получает** штатно CAN-кнопки руля — их уже читает Monitor.
Поэтому контракт такой:

### 3.1. Intent (публичный контракт)

Voice экспортирует, например:

```text
Action:  vad.dashing.voice.action.LISTEN
Package: vad.dashing.voice
Extra (opt): source = ui | wake | hard_key | automation
```

Поведение: поднять listening session (STT), как кнопка «Слушать».

### 3.2. Builtin / действие автоматизации в Monitor

Новое действие каталога, например:

- `builtin` / `start_vad_voice` **или**
- узкий `launch_application` на `vad.dashing.voice` с нужным action
  (предпочтительнее **явный builtin** — понятнее в UI автоматизаций).

Эффект: `startActivity` / `startForegroundService` с Intent из §3.1.

Тогда пользователь может:

- правило «hard key X → start_vad_voice»;
- виджет / другая автоматизация → голос.

### 3.3. Упрощённая привязка кнопки руля (UX)

В **Voice** (или в Monitor→API/Voice): «Кнопка руля» = выбор key code / жеста
(как в тесте кнопок Monitor) + вкл/выкл.

Реализация MVP (рекомендация):

1. Voice хранит pref `hardKeyEnabled` + `hardKeyCode`.
2. Monitor при событии hard key, если Voice установлен и есть согласованный
   binding (pref через shared? или Monitor setting «форвард в VAD Voice»):
   шлёт Intent LISTEN.

Практически проще для v1:

- в Monitor: настройка **«VAD Voice: кнопка руля»** (выкл / код кнопки),  
  либо документировать «сделайте автоматизацию hard_key → start_vad_voice»;
- для «из коробки» — настройка в Monitor рядом с API, пишущая тот же binding.

**Открытый микро-выбор при коде:** отдельный экран в Monitor vs только автоматизация.
Рекомендация: **builtin + короткая настройка в Monitor «Открыть VAD Voice по кнопке»**,
чтобы не заставлять всех писать правило вручную.

### 3.4. Документы Monitor, которые обновить вместе с кодом

- `AUTOMATIONS_*` / AI guide — новый builtin;
- `EXTERNAL_API` / user guide — ссылка на Voice;
- `MBCAN_VHAL` не трогать, если только Intent.

---

## 4. Потоки UX

### 4.1. Первый запуск (на ГУ)

1. Микрофон (+ уведомления, если foreground для wake-word).
2. Host/port: default `127.0.0.1:8765`.
3. Токен: pairing **или** вставка ручного токена из Monitor.
4. Загрузка catalog в кэш.
5. Wake-word и руль — **выкл**; пользователь включает по желанию.

### 4.2. Listening

1. Старт по любому каналу активации.
2. STT → текст на экране.
3. NLU → HTTP → TTS ответ.

### 4.3. Примеры фраз

| Фраза | API |
|-------|-----|
| «сколько градусов на улице» | `GET /v1/signals?ids=outside_temperature&source=head_unit` |
| «запусти климат» | automations → run по имени |
| «следующий трек» | `POST /v1/actions/invoke` media_next |

---

## 5. NLU

1. Normalize: lowercase, ё→е, пробелы, без пунктуации.
2. Кандидаты: `voiceAliasesRu` + имена automations.
3. Score: longest alias / token overlap + порог.
4. Приоритет: run automation → invoke → query signal.
5. Relative «потеплее» — после MVP core, если останется время (GET → ± → invoke).

---

## 6. Структура репозитория (решение: monorepo)

```text
TBox/
  app/                 # TBox Monitor APK (:app)
  voice/               # VAD Voice APK (:voice)
    src/main/...
    src/test/...       # NLU unit tests
  docs/VOICE_APK_RU.md
```

Почему не отдельный git:

- один контракт Intent / builtin с Monitor в одном PR;
- общие CI/ветки `preRelease`;
- модели живут только в `:voice` → Monitor APK не растёт.

Сборка: `./gradlew :voice:assembleDebug` (модели Piper + Vosk скачиваются
Gradle-задачами `:voice:fetchTtsModel` / `:voice:fetchSttModel` при `assemble*`,
если ещё нет в assets; Python не нужен).

Вручную:

```
./gradlew :voice:fetchTtsModel :voice:fetchSttModel
./gradlew :voice:assembleDebug
```

Модели **не** коммитятся в git (Piper ~20 MB + Vosk ~45 MB zip).
Опционально: `python3 tools/fetch_voice_tts_model.py` (только TTS).

---

## 7. Этапы реализации

| # | Этап | Где | Критерий |
|---|------|-----|----------|
| 0 | План + решения | docs | **сделано** |
| 1 | Каркас `:voice` APK, настройки host/port/token, health | voice | **сделано** |
| 2 | Catalog cache + NLU + unit-тесты фраз | voice | **сделано** |
| 3 | Signals query → текст на экране | voice | **сделано** (кнопка «Выполнить фразу») |
| 4 | Piper TTS bundle | voice | **сделано** (Irina int8 + sherpa-onnx; озвучка ответа) |
| 5 | Vosk STT + кнопка «Слушать» | voice | **сделано** (PTT → NLU → TTS) |
| 6 | Invoke + RunNow | voice | медиа + запуск правила |
| 7 | Intent `LISTEN` + Monitor builtin `start_vad_voice` | voice+app | автоматизация открывает слушание |
| 8 | Настройка кнопки руля (Monitor→Intent) | app(+voice) | вкл/выбор/выкл, default выкл |
| 9 | Wake-word opt-in | voice | своё слово, default выкл; расход батареи/CPU ок на ГУ |
| 10 | Полевая полировка | оба | ошибки сети, sentinel −40, audio focus |

Этапы 7–9 не откладывать «на потом после MVP»: они в scope; порядок после
рабочего PTT-цикла (1–6).

---

## 8. Пояснения к бывшим вопросам 6 и 8

**«Резать confirm?»**  
В Monitor действия делятся на safe / confirm / dangerous. Dangerous и так закрыты
тумблером в Настройки→API. Вопрос был: должен ли Voice **дополнительно** запрещать
голосom открыть багажник/окна (confirm), даже если API это разрешает.  
**Ответ по решению выше:** нет, не дублируем политику — только то, что уже настроили в Monitor.

**«Дистрибуция?»**  
Как пользователь получает APK: копирование на ГУ (сайдлоад), как Monitor, или публикация
в магазине.  
**Ответ:** для MVP только сайдлоад; магазин не нужен.

---

## 9. Риски

| Риск | Митигация |
|------|-----------|
| Wake-word грузит ГУ / ловит ложные срабатывания | Default **выкл**; порог; пауза после срабатывания |
| Hard key только в Monitor | Intent-контракт + builtin; не читать CAN из Voice |
| Размер Voice APK (Vosk+Piper) | Отдельный `:voice`; одна TTS-модель |
| Audio focus vs навигатор | Короткие сессии listening; корректный focus |
| Vosk в шуме салона | Короткие aliases; «не понял»; позже Sherpa |
| Sentinel температуры −40 | Formatter: `available` + диапазон → «нет данных» |

---

## 10. Критерий «MVP готов»

На ГУ с Monitor API + токеном:

1. «Слушать» → вопрос по телеметрии → голос отвечает.
2. Запуск правила по имени → RunNow.
3. Safe invoke из aliases → успех.
4. Автоматизация Monitor / builtin → Voice начинает слушать.
5. Wake-word и кнопка руля можно включить; по умолчанию выкл и не мешают.
6. Без WAN; Monitor выключен → понятная голосовая ошибка.
