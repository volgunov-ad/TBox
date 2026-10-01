# Voice APK — план реализации

Статус: **design / plan**. Отдельное Android-приложение — голосовой клиент к
уже реализованному External HTTP API TBox Monitor.

Связанные документы:

| Документ | Роль |
|----------|------|
| [EXTERNAL_API_RU.md](EXTERNAL_API_RU.md) | Контракт HTTP `/v1` |
| [EXTERNAL_API_USER_GUIDE_RU.md](EXTERNAL_API_USER_GUIDE_RU.md) | Как включить API / токен |
| [AUTOMATIONS_AI_JSON_GUIDE_RU.md](AUTOMATIONS_AI_JSON_GUIDE_RU.md) | JSON действий и сигналов |

**Зафиксировано заранее**

- **TTS:** не системный `TextToSpeech` ГУ (на Adayo/Jetour часто отсутствует, битый
  или устаревший). Встроенный офлайн TTS (Piper / Sherpa-ONNX).
- **NLU v1:** без LLM; матч по `voiceAliasesRu` + имена автоматизаций из `/v1/catalog`
  и `/v1/automations`.
- **Транспорт:** только наш External API (не legacy broadcast).

---

## 1. Цели MVP

1. На ГУ (или телефоне в той же LAN) сказать фразу → получить ответ голосом и/или
   краткий UI.
2. Три класса команд:
   - **вопрос** → `GET /v1/signals` → озвучить значение;
   - **действие** → `POST /v1/actions/invoke` по каталогу;
   - **сценарий** → resolve имя правила → `POST /v1/automations/{id}/run`.
3. Работа **без интернета** (STT/TTS/NLU on-device; HTTP только в LAN к Monitor).
4. Не ломать Monitor: Voice APK — отдельный клиент с Bearer-токеном.

Вне MVP (явно позже):

- диалоговый контекст («потеплее ещё» с памятью сессии) — опционально тонкий;
- wake-word always-on (можно PTT сначала);
- облако / LLM;
- редактирование правил с голоса;
- полноценный UI ассистента как у Дуси.

---

## 2. Архитектура

```text
[Микрофон]
    → STT (Vosk MVP / Sherpa позже)
    → NLU (normalize → match voiceAliasesRu / automation names → intent)
    → HTTP Client (Bearer → Monitor /v1)
    → Response formatter (число/state → RU фраза)
    → TTS (Piper/Sherpa, вшитая RU-модель)
    → [Динамик + короткий UI]
```

| Слой | MVP | Заметки |
|------|-----|---------|
| STT | **Vosk** `vosk-model-small-ru` (~45 MB) | Проще встроить; при слабом качестве — Sherpa GigaAM/zipformer |
| NLU | Alias matcher + слоты из `namedValues`/`stateOptions` | Каталог кэшировать; refresh по `catalogVersion` |
| HTTP | OkHttp / HttpURLConnection | `127.0.0.1` на ГУ или LAN IP |
| TTS | **Piper** (RU irina/ruslan) через Sherpa-ONNX или piper-native | Не system TTS |
| Auth | Pairing **или** ручной токен из Monitor | Как в user guide |
| UI | Кнопка «Слушать» + статус + последняя фраза/ответ | Минимум |

Отдельный модуль/репозиторий APK (не внутри `:app` Monitor), чтобы не раздувать
основной APK моделями STT/TTS (~50–150+ MB).

---

## 3. Потоки UX

### 3.1. Первый запуск

1. Разрешения: микрофон (обязательно), уведомления при foreground-сервисе.
2. Настройка сервера: host/port (по умолчанию `127.0.0.1:8765` на ГУ).
3. Токен:
   - **A)** «Сопряжение» → Monitor «Подключить приложение» → approve; или
   - **B)** вставить токен, созданный в Monitor «Создать токен».
4. `GET /v1/health` + `GET /v1/catalog` → кэш aliases.

### 3.2. Команда (PTT)

1. Пользователь жмёт «Слушать» (или руль → позже).
2. STT → текст.
3. NLU выбирает один intent (или «не понял»).
4. HTTP; при ошибке — озвучить кратко (`нет связи`, `отказано`).
5. TTS ответ; показать текст на экране.

Примеры:

| Фраза | Intent | API |
|-------|--------|-----|
| «сколько градусов на улице» | query `outside_temperature` | `GET …/signals?ids=outside_temperature&source=head_unit` |
| «какая скорость» | query `car_speed` | signals |
| «запусти климат» | run automation by name | automations list → run |
| «пауза» / «следующий трек» | invoke builtin media | `POST …/actions/invoke` |

### 3.3. Относительные фразы («потеплее»)

В v1 API нет `adjust_+1`. Клиент: GET текущего → вычислить абсолют → invoke.
**В MVP можно отложить** и поддержать только абсолютные/каталожные фразы.

---

## 4. NLU (детали)

1. Нормализация: lowercase, ё→е, схлопнуть пробелы, убрать пунктуацию.
2. Кандидаты:
   - все `voiceAliasesRu` сигналов и actionTypes из кэша catalog;
   - имена автоматизаций (`GET /v1/automations`).
3. Score: точное вхождение / longest alias match / простой token overlap.
4. Конфликты: уточняющий TTS («запустить правило климат или включить кондиционер?»)
   — в MVP достаточно взять лучший score + порог; иначе «не понял».
5. Слоты: для state/can — если в фразе есть label из `namedValues`, подставить
   `value`/`valueKey`.

Приоритет intent (черновик):

1. run automation (если имя правила почти целиком в фразе);
2. invoke action (alias действия);
3. query signal (alias сигнала).

---

## 5. TTS (без system)

| Вариант | Плюсы | Минусы |
|---------|-------|--------|
| **Piper RU** (irina/ruslan medium) через Sherpa-ONNX Android | Качество, офлайн, предсказуемо на ГУ | +десятки MB в APK |
| Sherpa VITS/Kokoro | Гибкость | Тяжелее |
| Системный TTS | — | **Отклонён** (на ГУ часто нет/сломан) |

MVP: одна голосная модель (например irina-medium), bundlе или first-run download
на внутреннее хранилище приложения (на ГУ без сети — **лучше bundle**).

---

## 6. Структура проекта (предложение)

```text
voice/   или отдельный репозиторий tbox-voice
  app/                 # Voice APK (product flavors ru)
  core/
    stt/               # Vosk wrapper
    tts/               # Piper/Sherpa wrapper
    nlu/               # alias matcher + tests
    api/               # External API client (health/catalog/signals/invoke/run/pair)
  docs/
```

Unit-тесты NLU на JVM без устройства (фраза → intent).  
Instrumented — только на железе/эмуляторе (в cloud VM устройства нет).

---

## 7. Этапы реализации

| # | Этап | Критерий готовности |
|---|------|---------------------|
| 0 | Этот план + ответы на §8 | Согласован scope MVP |
| 1 | Каркас APK + настройки host/port/token + health | С ГУ/ПК видно `ok: true` |
| 2 | Catalog cache + NLU matcher + unit-тесты фраз | Эталонный набор фраз зелёный |
| 3 | Signals query → текстовый ответ на экране | «температура на улице» → число |
| 4 | Встроенный TTS | Тот же ответ озвучивается на ГУ |
| 5 | STT Vosk + PTT кнопка | Полный цикл микрофон → ответ |
| 6 | Invoke safe actions + RunNow по имени | Медиа/toast + запуск правила |
| 7 | Pairing UX (опц. если ручного токена мало) | Как Monitor pair flow |
| 8 | Полировка: ошибки сети, dangerous reject, кэш | Полевые сценарии на ГУ |

Параллельно можно выпустить **скрипты Дуси/Tasker** как необязательный клиент
(не блокирует Voice APK).

---

## 8. Открытые вопросы

Нужны ответы, чтобы зафиксировать MVP:

1. **Где крутится Voice APK в первую очередь?**  
   Только ГУ / только телефон / оба (один APK, разные default host)?

2. **Активация в MVP:** только кнопка на экране (PTT), или сразу нужен
   wake-word / кнопка на руле (hard key → intent)?

3. **Репозиторий:** новый модуль в этом же git (`:voice`) или отдельный репозиторий?

4. **STT на MVP:** подтверждаем **Vosk small-ru**, Sherpa — этап 2 по качеству?

5. **Модели в APK:** bundlе STT+TTS в APK (больше размер, работает офлайн из коробки)
   или download при первом запуске (нужен интернет хоть раз)?

6. **Опасные / confirm действия с голоса:** в MVP разрешить всё, что разрешает
   токен Monitor, или Voice APK дополнительно режет confirm (окна, багажник)?

7. **Имя приложения / package:** рабочее `TBox Voice` / `vad.dashing.tbox.voice`?

8. **Дистрибуция:** только сайдлоад рядом с Monitor, или ещё RuStore/свой updater?

---

## 9. Риски

| Риск | Митигация |
|------|-----------|
| Микрофон/аудиофокус на ГУ занят навигатором | Явный PTT; audit audio focus; не держать always-on сначала |
| Vosk плохо слышит в салоне | Короткие aliases; позже Sherpa; подсказка «повторите» |
| Размер APK | Отдельный APK; одна TTS-модель; small STT |
| `outside_temperature=-40` sentinel | В formatter: `available` + разумный range → «нет данных» |
| Каталог устарел | Сверять `catalogVersion` при старте сессии |

---

## 10. Критерий «MVP готов»

На ГУ с включённым Monitor API и токеном:

1. PTT → «сколько градусов на улице» → голос отвечает числом или «нет данных».
2. PTT → «запусти &lt;имя правила&gt;» → правило уходит в RunNow.
3. PTT → safe media/toast-команда из aliases → invoke success.
4. Без сети WAN; при выключенном Monitor — внятная голосовая ошибка.
