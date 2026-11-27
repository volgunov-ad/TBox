# Voice AI Agent — дизайн (облако + локально + aliases)

Статус: **дизайн + этапы A–E + freeform**. Маршрут NLU:
`cloud → локальный n-gram классификатор → aliases`. Нейросеть ONNX/GGUF на ГУ —
следующая итерация того же интерфейса `LocalIntentClassifier`.

Связанные документы:

| Документ | Роль |
|----------|------|
| [VOICE_APK_RU.md](VOICE_APK_RU.md) | Текущий Voice APK (STT/TTS/aliases/API) |
| [EXTERNAL_API_RU.md](EXTERNAL_API_RU.md) | Контракт `/v1` (каталог, invoke, run) |
| [AUTOMATIONS_AI_JSON_GUIDE_RU.md](AUTOMATIONS_AI_JSON_GUIDE_RU.md) | Формат действий/сигналов |

---

## 1. Зачем

Жёсткий список фраз (`voiceAliasesRu`) неудобен: фразы надо помнить, синонимы
и «включи/выключи/сделай потеплее» плохо покрываются без слотов. Нужен **ИИ-агент**,
который понимает свободную речь и вызывает только разрешённые tools поверх
External API Monitor.

Aliases **не удаляем**: это быстрый офлайн-fallback и страховка, пока модель
недоступна или не уверена.

---

## 2. Что такое «встроенный крошечный классификатор»

Это **не** чат-бот и **не** большая LLM на ГУ.

Это маленькая локальная модель (или даже правила + эмбеддинги), которая на вход
получает текст фразы (после Vosk) и на выход отдаёт **одну метку + слоты**, например:

```text
вход:  «сколько бензина осталось»
выход: intent=query_signal, signal_id=fuel_level_percent

вход:  «следующий трек»
выход: intent=invoke_action, action_type=media_next

вход:  «запусти климат»
выход: intent=run_automation, name≈«климат»
```

### Чем отличается от облачного агента

| | Крошечный классификатор | Облачный LLM-агент |
|--|-------------------------|---------------------|
| Где крутится | На ГУ, офлайн | У провайдера пользователя (HTTP) |
| Размер / RAM | Десятки МБ, квант | Не на устройстве |
| Умеет | Выбрать intent + id из каталога | Свободный диалог, уточнения, tool-calling |
| Не умеет (обычно) | Длинные ответы «своими словами», сложная логика | — |
| Когда нужен | Нет API / нет сети / облако упало | Есть ключ и живой endpoint |

### Как это выглядит в пайплайне

```text
Vosk (текст)
  → если cloud OK:  LLM tool-calling (каталог как tools)
  → иначе local:    крошечный классификатор (intent→id)
  → иначе:          AliasNluMatcher (как сейчас)
  → External API /v1
  → Piper TTS
```

**Этап E (сейчас):** встроенный офлайн `NgramLocalIntentClassifier` (char n-gram +
priors по RU-cue), без native-модели — безопасно по RAM/CPU для A9/A10.

**Позже:** тот же интерфейс `LocalIntentClassifier` может закрыть ONNX/GGUF
(отдельная загрузка модели, не в APK).

---

## 3. Зафиксированные решения (этот этап дизайна)

| Тема | Решение |
|------|---------|
| Облачный протокол | **OpenAI-compatible** `…/v1` (`chat/completions`, `models`) |
| Пресеты провайдеров | Несколько готовых + «Свой URL» (см. §4) |
| Выбор модели | Список из `GET {base}/models` (или аналог); пользователь выбирает |
| Ключ API | Только в Voice (DataStore prefs, как Monitor-токен; EncryptedSharedPreferences — позже), не в Monitor |
| Tools агента | Только обёртки над External API: signals / invoke / run (+ позже clarify) |
| Fallback | cloud → local classifier (позже) → **aliases** |
| Отправка речи в облако | Явный opt-in в настройках Voice |
| Длинные свободные ответы | Отдельный opt-in «Длинные свободные ответы»; без него Speak ≤240 знаков |
| Dangerous | Как сейчас: токен + тумблер Monitor; Voice не дублирует запреты |

Вне этого дизайна (позже): телефон как клиент; стриминг TTS по предложениям;
относительные «потеплее/тише» со слотами; can_command invoke из голоса.

---

## 4. Пресеты облачных API

Базовый контракт: OpenAI-compatible Chat Completions.

| Пресет | Default `baseUrl` | Примечание |
|--------|-------------------|------------|
| **AITunnel** | `https://api.aitunnel.ru/v1` | Пример пользователя; список моделей с `/models` |
| **OpenAI** | `https://api.openai.com/v1` | |
| **OpenRouter** | `https://openrouter.ai/api/v1` | |
| **Свой URL** | пусто → пользователь вводит | Любой совместимый gateway |

Поля настройки (Voice → ИИ):

- пресет;
- `baseUrl` (редактируемый даже у пресета);
- `apiKey`;
- `model` (из загруженного списка или ручной ввод);
- тумблер «Разрешить отправку фраз в облако»;
- тумблер «Длинные свободные ответы (озвучка)» — рассказы/факты без tools, Piper chunking;
- таймаут / «сначала пробовать облако».

Проверка связи: `GET /models` или короткий `chat/completions` smoke.
Если ошибка сети, 401/403, таймаут → сразу local/aliases, без длинного ожидания.

---

## 5. Агент и tools

System prompt (кратко): ты голосовой ассистент автомобиля; вызывай только tools;
не выдумывай id; если не уверен — спроси / откажись.

Tools (имена условные):

| Tool | Соответствие API |
|------|------------------|
| `list_catalog` / вшитый snapshot | кэш `/v1/catalog` + `/v1/automations` |
| `query_signal` | `GET /v1/signals?ids=…&source=…` |
| `invoke_action` | `POST /v1/actions/invoke` (пока только то, что умеет Voice: builtin / open_main_screen) |
| `run_automation` | `POST /v1/automations/{id}/run` |

Валидация: id действия/сигнала/автоматизации сверяются с кэшем каталога **до** HTTP.
Ответ модели без tool call:
- уточняющий вопрос (`?` / «какой…») → `Clarify`, ровно **один** follow-up (`continueClarify`);
- иначе короткая TTS-фраза («не понял») без side effects.

---

## 6. Маршрутизация NLU (целевая)

```text
1. Пользователь включил облако + opt-in + ключ + /models или ping OK
      → cloud agent (1–N tool rounds, лимит)
2. Иначе если установлен локальный классификатор
      → local intent
3. Иначе AliasNluMatcher
4. При низкой уверенности cloud/local → aliases; при miss → «Не понял фразу»
```

Aliases остаются обязательным этапом MVP и страховкой после появления агента.

---

## 7. Этапы реализации (после дизайна)

| # | Этап | Критерий |
|---|------|----------|
| A | Дизайн (этот документ) + полировка aliases | **сделано** |
| B | Настройки Voice: пресеты, ключ, модель, opt-in, ping `/models` | **сделано** (`CloudLlmSettings`, UI, `OpenAiCompatibleClient.listModels`) |
| C | Cloud tool-calling поверх каталога (без UI диалога) | **сделано** (`VoiceCloudAgent` → тот же `handleIntent` / TTS) |
| D | Диалог уточнения (1 follow-up) | **сделано** (`CloudAgentOutcome.Clarify` + один `continueClarify`) |
| E | Локальный классификатор (офлайн) | **сделано** (`NgramLocalIntentClassifier`; ONNX — позже) |
| F | Слоты: потеплее / тише / can_command | Invoke с value; тесты |
| G | Длинные свободные ответы + chunked TTS | **сделано** (`allowFreeformSpeak`, `TtsTextChunker`) |

Порядок B→C обязателен; E не блокирует облако.

---

## 8. Риски

| Риск | Митигация |
|------|-----------|
| Утечка ключа / фраз | Opt-in «отправка фраз»; ключ только в Voice DataStore; не логировать key/phrase; EncryptedSharedPreferences — позже |
| Галлюцинации id | Whitelist по каталогу; отказ без invoke |
| Латентность ГУ↔API | Короткий prompt; таймаут; параллельно не блокировать UI |
| ГУ без интернета | SoftAP / телефон как мост — вне Voice; иначе aliases/local |
| Размер локальной модели | E1 = n-gram без веса; нейросеть — отдельная загрузка, не в APK |
| can_command из голоса | Пока не в tools Voice; матчер aliases не выбирает can_command (см. VOICE_APK §5) |

---

## 9. Критерий «дизайн принят»

1. Понятна разница: облачный агент / крошечный классификатор / aliases.
2. Зафиксированы пресеты (включая AITunnel) и OpenAI-compatible контракт.
3. Tools только через External API; политика dangerous без дубля в Voice.
4. Aliases остаются fallback; известные дыры матчера закрыты или задокументированы.
