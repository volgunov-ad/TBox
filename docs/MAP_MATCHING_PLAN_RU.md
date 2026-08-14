# План: привязка к дорогам (map-matching) для улучшения геопозиции

Опциональная коррекция DR-тени по дорожному графу. **По умолчанию выкл.** — без опции поведение как сейчас.  
Доступна в режимах улучшения при **«Вкл. всегда»** и в power **«Нет фикса»**.

Связанные подсистемы: `MockLocationJob` / CONSTANT, `MockPowerState`, вкладка «Геопозиция», виджеты.

---

## 1. Цель продукта

Пока машина едет «по дороге», гиро/руль и интеграция пути могут уводить курс и координаты с полотна. Опция:

1. Периодически сопоставляет позицию/курс с рёбрами дорожного графа.
2. Мягко подтягивает **курс** и **координаты к линии дороги**, **сохраняя пройденную длину** (не телепорт к ближайшей точке).
3. На перекрёстке умеет **перецепиться** на другое ребро (гистерезис, согласование курса и связности).

При выключении — ноль изменений в pipeline.

---

## 2. UX (вкладка «Геопозиция»)

Рядом с блоком улучшения / после power и источника курса:

| Элемент | Поведение |
|---------|-----------|
| **Переключатель** «Привязка к дорогам» | Default **выкл.** Enabled, если mock power ∈ {`WHEN_NO_FIX`, `ALWAYS_ON`} **и** (для Always-on) режим `enhancesMock` / для No-fix — всегда (там forced CONSTANT). При Off / Direct — выкл. и disabled. |
| **Кнопка** «Карты дорог…» | Открывает **отдельное окно** (hub dialog, как калибровки гиро/руля). Доступна всегда при источнике ≠ Android (или всегда — скачивание не зависит от mock). |

Описание переключателя (кратко): подтягивает тень DR к дорогам OSM; нужен скачанный регион; не на каждом тике.

### 2.1 Окно «Карты дорог»

Полноэкранный/крупный `AlertDialog` + scroll (паттерн `LocationCalibrationEntryUi` / gyro-steer hub):

1. **Страны / макрорегионы** (чек-листы или секции):
   - Россия (включая Крым, ДНР, ЛНР — явно в каталоге областей)
   - Беларусь
   - Казахстан
   - Армения
   - Азербайджан
   - Узбекистан
2. Внутри страны — **области/субъекты** (или крупные bbox-пакеты), не один файл на всю РФ.
3. На каждой области: размер (если известен), статус (нет / качается % / готово / ошибка), кнопки Скачать / Обновить / Удалить.
4. Сводка: занято на диске, «активный охват вокруг нас» (есть ли покрытие текущих lat/lon).
5. Юридическая строка: данные © OpenStreetMap contributors, ODbL.

Очередь загрузок: одна активная + очередь; пауза при нехватке места; отмена.

---

## 3. Данные дорог

### 3.1 Источник

- **OpenStreetMap**, дистрибуция через **Geofabrik** (и при необходимости свои срезы для спорных/новых границ).
- На устройство — **не сырой `.osm.pbf` целиком в рантайме**, а **наш компактный граф** (extract на PC/CI или on-device one-shot convert).

Рекомендуемый пайплайн (v1):

1. Сервер/скрипт в `tools/` (готовые пакеты — Яндекс.Диск `release/maps/`):
   Geofabrik PBF → фильтр `highway` нужных классов → граф рёбер (polyline + длина + class + connectivity).
2. Пакет: `*.tboxroads` (версия формата, bbox, список рёбер/узлов, gzip/zstd).
3. Приложение получает временный URL через публичный API Яндекс.Диска и качает
   **готовый пакет**, не Geofabrik/Overpass напрямую с HU.

Альтернатива later: on-device osmium-like — слишком тяжело для HU API 28.

### 3.2 Каталог областей

Машиночитаемый `road_map_catalog.json` (в APK + опционально remote refresh):

```json
{
  "version": 1,
  "regions": [
    {
      "id": "ru-crimea",
      "country": "RU",
      "title_ru": "Крым",
      "title_en": "Crimea",
      "bbox": [32.2, 44.2, 36.8, 46.3],
      "url": "https://…/ru-crimea-v1.tboxroads",
      "bytes": 12345678,
      "graphVersion": 1
    }
  ]
}
```

Страны в UI — группировка `country`. Россия: федеральные округа / субъекты + отдельные id для Крыма, ДНР, ЛНР (даже если upstream Geofabrik режет иначе — наш pack с нужным bbox).

**Каталог v1:** все 89 субъектов РФ (включая Крым, ДНР, ЛНР) и 6 областей
Беларуси + Минск. Каждый пакет — целая административная область/субъект,
не город и не bbox центра. Другие страны пока исключены.

### 3.3 Хранение на устройстве

- Каталог: `context.filesDir/road_maps/` или `getExternalFilesDir(null)/road_maps/`.
- Индекс: DataStore / JSON manifest установленных `id → path, graphVersion, installedAt`.
- Не класть в backup settings без явной опции (карты тяжёлые).

---

## 4. Алгоритм (рантайм)

### 4.1 Когда вызывать (не каждый DR-тик)

DR тик остаётся как сейчас. Map-match **триггеры**:

| Условие | Действие |
|---------|----------|
| Накоплено ≥ **12 м** пути с прошлого match (уверенная прямая) | Обычный match |
| Прошло ≥ **2 с** и v ≥ порога (уверенная прямая) | Обычный match |
| Recover / нет sticky / после LOW-reject: ≥ **6 м** или ≥ **0,5 с** | Учащённый match |
| Ожидание confirm смены ребра: ≥ **5 м** или ≥ **0,5 с** | Учащённый match |
| \|Δкурса\| ≥ **~18°** | Внеочередной match (смена ребра) |
| v &lt; ~COURSE_HOLD_MIN_KMH | **Не match** (стоянка) |
| Нет покрытия графа в радиусе | no-op |

Между match — только интеграция v/курса.

### 4.2 Match и коррекция

1. Кандидаты рёбер в радиусе R (например 25–40 м), с отсечением по курсу (±45–70°).
2. Score: расстояние до линии + согласованность курса + бонус связности с предыдущим ребром.
3. Смена ребра: только если новый score устойчиво лучше N раз подряд или на узле-перекрёстке.
4. Коррекция:
   - курс → мягкий blend к азимуту ребра (и учёт reverse);
   - позиция → проекция на polyline **с сохранением длины**: сдвиг поперечной ошибки постепенно (доля за шаг), продольная координата вдоль пути не отматывается.
5. Ограничить max поперечную коррекцию за шаг (метры) и max Δкурса (°), чтобы не дёргать навигацию.

Встраивание: после CONSTANT (и enhancement DR) шага тени, **до** `setMockLocation` — только если опция вкл. и power/mode позволяют.

### 4.3 Отладка

Расширить geo-debug / ConstantDrRuntimeDebug:

- `mapMatch.active`, `edgeId`, `crossTrackM`, `alongTrackM`, `switchedEdge`, `skippedReason`.

Опционально строка на вкладке Геопозиция в expert/constant блоке.

---

## 5. Архитектура кода (предложение)

```
location/roadmatch/
  RoadMatchSettings.kt          # toggle + paths
  RoadGraph.kt                  # load .tboxroads, spatial index
  RoadMapCatalog.kt             # countries/regions from JSON
  RoadMapDownloadManager.kt     # queue, progress, cancel, delete
  RoadMapOfflineImportManager.kt # этап G: SAF/USB validate + atomic install
  RoadMapMatcher.kt             # candidates + score + correction
  RoadMatchRuntime.kt           # throttle triggers, state (current edge)

ui/
  RoadMapsDownloadUi.kt         # hub dialog (countries → regions)
  RoadMapsOfflineImportUi.kt    # этап G: выбор каталога/регионов/прогресс
  DashboardMapKitWidget.kt      # этап F: Yandex MapKit + overlays (ветка mapkit)
  (toggle in UiPrimaryTabs near mock enhance controls)

  # Не использовать SystemLocationTracker / Android LocationManager для этого виджета —
  # позиции только из нашего pipeline (тень DR, GNSS TBox/USB, GeoDisplay).

tools/
  osm_to_tboxroads.py           # GeoJSON / synthetic → .tboxroads v1
  samples/ru_moscow_demo.geojson

docs/
  TBOXROADS_FORMAT_RU.md        # спека пакета v1
```

Settings keys:

- `mock_road_match_enabled` (bool, default false)
- installed maps manifest (string JSON)

`MockLocationJob`: читать flag; вызывать `RoadMatchRuntime.maybeCorrect(...)`.
API для этапа F: ручной seed тени (`lat/lon/bearing` с карты) — тот же внутренний путь, что hard-resync, но координаты с тапа, не с GNSS.

---

## 6. Этапы внедрения

### Этап A — Каркас UI и каталог (без коррекции)

- [x] Тумблер + кнопка + окно стран/областей.
- [x] Локальный catalog JSON (`assets/road_maps/catalog.json`, `asset://` демо-пакеты + «ещё не опубликовано»).
- [x] Скачивание/удаление/прогресс, место на диске (`RoadMapDownloadManager`).
- [x] Строки RU/EN.
- [x] Unit-тесты catalog parse / manifest / toggle availability.
- [x] USER_GUIDE черновик (короткая пометка).

**Результат:** пользователь может качать области; match ещё no-op.

### Этап B — Формат графа и tools/

- [x] Спека `.tboxroads` v1 (`docs/TBOXROADS_FORMAT_RU.md`).
- [x] `tools/osm_to_tboxroads.py` + пример (`tools/samples/ru_moscow_demo.geojson`).
- [x] Загрузка графа в `RoadGraph`, spatial query unit-tests.
- [x] Индикатор «есть покрытие здесь» по bbox/рёбрам загруженного пакета (`RoadGraphStore`).

**Результат:** установленный пакет парсится; покрытие = точка в bbox пакета с непустым графом. Match ещё no-op (этап C).

### Этап C — Matcher offline + throttle

- [x] `RoadMapMatcher` + throttle (метры/время/поворот).
- [x] Включение в CONSTANT / WHEN_NO_FIX path (`MockLocationJob` + `roadMatchEnabled`).
- [x] Geo-debug поля (`mapMatch.*`).
- [x] Тесты: synthetic polyline, смена ребра по курсу, сохранение длины / soft cross-track.

**Результат:** при вкл. тумблере и установленном пакете тень мягко подтягивается к рёбрам (не каждый тик).

### Этап D — Каталог стран (полный охват из ТЗ)

- [x] Каталог: 89 субъектов РФ + 7 регионов РБ; KZ/AM/AZ/UZ удалены.
- [x] Нарезка только по целой OSM admin boundary (`admin_level=4`), без городских срезов.
- [x] Remote catalog + пакеты: общая публичная папка Яндекс.Диска `/maps/`.
- [x] Tool: `--fetch-overpass-area`; батч/публикация `tools/build_road_map_packs.py`.
- [x] graphVersion 4: один ZIP bundle на субъект для пользователя; внутри индекс +
      тайлы 0.1° с overlap, локальная атомарная распаковка, в RAM только соседние тайлы.
- [ ] Собрать и синхронизировать все 96 пакетов на рабочей машине (крупные
      области могут требовать Geofabrik PBF + polygon вместо Overpass).
- [ ] Проверить размеры/качество нескольких полных областей на HU.

**Результат кода:** graphVersion 4, fallback-список работает offline; реально
опубликованные пакеты появляются через `/maps/catalog.json` без обновления APK.

### Этап E — Полировка + E+ (связность / гипотезы / confidence)

Исходный E:
- [x] Гистерезис перекрёстков (`switchConfirmCount=3`).
- [x] После смены ребра — короткий карантин (~5 с) на возврат к только что брошенному (`return_to_prior`), без изменения скоринга.
- [x] Классы дорог: штрафы residential/living_street/service; footway отфильтрован в tool.
- [x] Политика обновления пакетов (`graphVersion` + «Обновить» в UI).
- [x] ODbL attribution в окне «Карты дорог».

E+ (после симуляций НН/Москва):
- [x] Связность рёбер: shared `from`/`to` в tool + spatial clustering при загрузке старых пакетов.
- [x] Beam из top‑N гипотез; бонус за connected / штраф за disconnected jump.
- [x] Топологический look-ahead: CAN-путь + свежий fused-курс продвигают гипотезу
  на 1,5 с (10–20 м) через связанные рёбра, с усилением ожидаемого ребра в ranking.
- [x] Connected corridor при `no_candidate`: до 5 с / 60 м позиция продолжается
  по одометру от последней matched-точки только по связанному графу; произвольные
  дороги расширенным радиусом не захватываются.
- [ ] Along-track catch-up: на логе НН одометр-вариант снижал лаг (~64→28 м),
  но ломал moscow `153602` (corrections↓, maxGap↑). Look-ahead bias ещё хуже.
  В `softCorrect` оставлен опциональный along-target API; runtime пока не включает.
- [x] Confidence HIGH/MEDIUM/LOW: при LOW поза не правится (чистый DR), гипотезы сохраняются.
- [x] Geo-debug: `confidence`, `candidateCount`, `runnerUpScore`, `connected`, `highway`.
- [x] `oneway` в пакете + мягкий штраф встречного направления (города); reverse gear без штрафа.
- [x] Съезды: hard-reject `againstOneway` на `*_link`; штраф disconnected link; confidence без MEDIUM/HIGH для against-oneway / disconnected sole; runtime `rejectReason` + geo-debug bearing/turn.
- [x] После потери HOLD: сброс phantom `currentEdgeId` + один rematch без disconnected-штрафа (развязки).
- [x] Связность между тайлами: adjacency в любом loaded graph + junction по endpoints ≤12 м.
- [x] Не HOLD_EDGE против oneway при движении вперёд (только reverse/`allowAgainstOneway`).
- [x] Past-end release: если поза уже за travel-концом sticky-ребра (`xt` ≳ 8 м
      или растёт) — не `softCorrect`/`HOLD` к старому endpoint (это назад);
      сразу брать связанного наследника либо чистый DR. Не путать с fade
      поперечного snap на повороте.
- [x] Geo-debug: отдельно sticky (`edgeId`/`highway`) и кандидат (`candEdgeId`/`candHighway`/`candXtM`/`candConnected`).
- [x] Advanced: осторожный hard-resync на стоянке (оба ~0, хорошая accuracy, trust 12 с).
- [x] Кинематический инвариант: без фактически интегрированного пути курс не меняют gyro/steer/GNSS; при движении gyro/hybrid ограничены максимально возможным поворотом bicycle-модели.
- [x] Исключение: при hard-resync / далёкой тени к восстановленному GNSS курс можно подтянуть быстрее (snap + catch-up 5 с), на стоянке по-прежнему без поворота.
- [x] При softCorrect к matched-ребру курс ловится быстрее (до 14°/тик), на повороте / большом residual по-прежнему inhibit.
- [x] Field replay production-matcher по geo-debug + опубликованному bundle и regression baseline: [ROAD_MATCH_REPLAY_RU.md](ROAD_MATCH_REPLAY_RU.md).
- [x] Адаптивный throttle: прямая 12 м / 2 с / 18°; recover и switch-pending — 5–6 м / 0,5 с.
- [ ] Полевой replay на HU (журналы с GNSS + искусственное скрытие) — операционный шаг.
- [ ] Массовая пересборка v4 bundles с shared nodes **и oneway**.

**Результат:** matcher предпочитает связный правдоподобный маршрут; при неоднозначности не тянет «куда попало».

### Этап F — Виджет карты на Yandex MapKit (после B+C + merge MapKit-плитки)

Плитка дашборда / плавающей панели на **Yandex MapKit** — визуальная проверка match и ручная подстройка тени. **Не навигатор.**

Разбиение (чтобы принимать по частям):

| Подэтап | Содержание |
|---------|------------|
| **F1** | Данные и логика оверлея **без** MapKit: тень + GNSS, polyline matched-ребра, соседи, camera hint, `RoadMatchMapRenderer` |
| **F2a** | Виджет Compose Canvas без подложки: отрисовка F1, локальный auto-fit, без SDK/ключа/сети |
| **F2b** | MapKit basemap под теми же F1-оверлеями + auto-follow (без SystemLocationTracker) |
| **F3** | Ручной seed тени с карты (меняет DR) |

#### F1 — данные оверлея (map-agnostic)

- [x] `RoadMatchOverlayState` / `OverlayPoseMarker` / `OverlayEdgePolyline` / `OverlayCameraHint`
- [x] `RoadMatchOverlayBuilder` — геометрия ребра из `RoadGraph`, лимит соседних рёбер
- [x] `RoadMatchOverlayRepository` — `StateFlow` для UI
- [x] `RoadMatchMapRenderer` — контракт без Yandex SDK
- [x] Публикация из `MockLocationJob` (CONSTANT + match on); clear при выкл./stop
- [x] Unit-тесты builder / store lookup / repository

#### F2a — локальный Canvas-виджет (без подложки)

- [x] Плитка «Карта привязки к дорогам» в picker главной / плавающей панели
- [x] Соседние рёбра; matched-ребро синим (без сетки и угловой статус-надписи)
- [x] Тень зелёным, GNSS оранжевым кольцом поверх тени (в т.ч. залипший фикс, если ≤1000 м от тени)
- [x] Auto-fit вокруг тени; больше соседних рёбер (~180 м / до 48); matched только если геометрия рядом с тенью
- [x] Понятные состояния `no data` / `no graph` / `no edge`
- [x] Без MapKit, Android LocationManager, ключа API и сети

**Базовая карта (F2b):** тайлы Яндекса (нужны `MAPKIT_API_KEY` и сеть). Canvas F2a остаётся рабочим fallback/debug-режимом.

**Позиции — только наш механизм:**

- Не подключать `SystemLocationTracker` / системный GPS / `ACCESS_FINE_LOCATION` ради этой плитки.
- Источники: расчётная тень (DR / после match) и GNSS из TBox/USB / `GeoDisplayRepository` / `MockLocationJob`.

**Слои (MapObject overlays):**

| Слой | Отображение |
|------|-------------|
| Фон | Yandex MapKit basemap |
| Текущее matched-ребро | Polyline **синим** (из offline `.tboxroads` / runtime `edgeId`) |
| Соседние рёбра (опционально) | Нейтральный polyline, только viewport / лимит числа; не весь пакет |
| Расчётная позиция (тень) | Placemark-стрелка с курсом, цвет A |
| Позиция GNSS | Placemark-стрелка с курсом, цвет B |

**Камера (авто-follow):**

- Центр — **расчётная** позиция (тень).
- Масштаб/кадр так, чтобы GNSS тоже была в кадре (bbox двух точек + padding; min/max zoom).
- Жесты MapKit (pan/zoom) допустимы; hold-to-follow / кнопка возврата в auto-follow — по аналогии с текущей MapKit-плиткой.

**Ручной seed тени (обязательная часть этапа F / F3):**

1. Кнопка на виджете («Задать тень» / аналог).
2. Тап по карте → черновая точка.
3. Подстройка направления (жест поворота / второе касание / UI курса).
4. Подтверждение → тень подтягивается к lat/lon/bearing (API в `MockLocationJob`, аналог hard-resync, но не к GNSS).
5. Доступно только в режимах с тенью (enhance / «Нет фикса»); в edit-mode плитки жесты seed выключены; желательно явное подтверждение от случайных тапов.

**Зависимости:** F1 data plane в `preRelease`; F2 = MapKit host поверх F1; `RoadGraph` + runtime match (`edgeId`); lat/lon/bearing тени и GNSS из нашего pipeline; API seed тени (F3).

**Критерии готовности этапа F:**

- [x] F1: overlay state публикуется без MapKit; matched polyline совпадает с `edgeId` при наличии графа в cache.
- [x] F2a: Canvas-плитка без Android LocationManager показывает тень/GNSS/дороги из F1.
- [x] F2a: при активном match синее ребро совпадает с `edgeId` из runtime/geo-debug.
- [x] F2a: auto-fit держит тень в центре, GNSS и рёбра — в разумном кадре.
- [ ] F2b: MapKit basemap под теми же F1-оверлеями.
- [ ] Ручной seed: тап + курс → тень переезжает; DR продолжается от новой точки (F3).
- [x] Нет ключа MapKit / нет сети — Canvas F2a работает независимо.
- [ ] На HU нет заметных фризов от числа polyline/marker (F2a/F2b).

### Этап G — Установка карт с флешки (offline import)

Цель: установить/обновить региональные карты на ГУ **без интернета**. Пользователь
готовит на USB-флешке одну папку: внутри лежит JSON-каталог и указанные в нём
файлы карт. Приложение не читает карты с флешки в рантайме — после выбора они
проверяются и атомарно копируются в штатное внутреннее хранилище `road_maps/`.

#### UX

1. В окне «Карты дорог» добавить кнопку **«Установить с флешки…»**.
2. Открыть системный SAF file picker (`ACTION_OPEN_DOCUMENT`) с MIME
   `application/json`; пользователь выбирает файл каталога на флешке.
3. Приложение читает каталог и показывает отдельный диалог:
   - имя/версия каталога и папка-источник;
   - регионы, размер, версия, статус (`не установлен` / `обновление` /
     `уже установлен` / `файл отсутствует` / `ошибка`);
   - чекбоксы регионов;
   - кнопки **«Выбрать все»**, **«Снять все»**, **«Установить выбранные»**.
4. Во время импорта: общий и текущий прогресс, отмена, проверка свободного места.
5. После успеха обычный список карт сразу показывает установленные версии;
   источник (`USB`) можно хранить в manifest только для диагностики.

#### Формат USB-каталога

Использовать ту же модель регионов, что remote catalog, но вместо обязательного
HTTP `url` разрешить относительное поле `file`. Все пути считаются относительно
**папки выбранного JSON**:

```json
{
  "version": 1,
  "title": "Карты дорог 2026-08",
  "regions": [
    {
      "id": "ru-moscow",
      "country": "RU",
      "title_ru": "Москва",
      "title_en": "Moscow",
      "bbox": [36.80, 55.10, 38.30, 56.10],
      "file": "ru-moscow-v4.tboxroads.zip",
      "bytes": 12345678,
      "sha256": "…",
      "graphVersion": 4
    }
  ]
}
```

- Для совместимости, если `file` отсутствует, можно использовать basename из
  `url`, но **не** обращаться в сеть.
- `file` — только имя или относительный путь внутри выбранной папки; запретить
  абсолютные пути, `..`, URI-схемы и выход из дерева.
- Для новых USB-каталогов `bytes` и `sha256` обязательны. Старый каталог без
  hash можно показать как `непроверенный` и импортировать только после явного
  подтверждения (либо жёстко запретить — решить перед реализацией).
- Поддержать текущие форматы: v4 bundle ZIP (предпочтительно) и совместимый
  одиночный `.tboxroads`, если он ещё разрешён установщиком на момент G.

#### Реализация

- SAF: сохранить временный доступ к выбранному каталогу/дереву через
  `takePersistableUriPermission`, но не зависеть от постоянного USB URI после
  завершения импорта.
- Найти sibling-файлы через `DocumentsContract` / `DocumentFile` в папке
  выбранного JSON. Если конкретный provider не разрешает обход siblings,
  fallback — попросить выбрать **папку** (`ACTION_OPEN_DOCUMENT_TREE`) и затем
  каталог внутри неё.
- Новый `RoadMapOfflineImportManager` переиспользует:
  - парсер `RoadMapCatalog`;
  - проверку ZIP/bundle, bbox, `regionId`, `graphVersion`;
  - manifest/atomic install из `RoadMapDownloadManager`.
- Импортировать последовательно (один файл за раз): USB URI → временный файл
  во внутреннем storage → сверка `bytes`/SHA-256 → проверка содержимого →
  atomic rename/install. При отмене/ошибке удалить temp, старую рабочую карту
  не трогать.
- Дубликаты `regionId`: оставить одну запись с максимальным `graphVersion`,
  конфликт одинаковой версии/разного hash показать как ошибку каталога.
- Не давать USB-каталогу менять remote URL, встроенный каталог или настройки
  приложения; он является только одноразовым источником списка/файлов.

#### Критерии готовности G

- [ ] Выбор JSON-каталога с USB через SAF без `MANAGE_EXTERNAL_STORAGE`.
- [ ] Список регионов с выбором по одному и «выбрать все».
- [ ] Корректный import v4 bundle из той же папки, проверка size/hash/header.
- [ ] Атомарное обновление: при ошибке/извлечении флешки старая карта остаётся.
- [ ] Проверка места, прогресс и отмена; temp-файлы очищаются.
- [ ] Ошибки понятны: плохой JSON, отсутствует файл, hash/version/id mismatch,
      USB извлечён, нет места.
- [ ] После импорта matcher и F2a видят карту без перезапуска приложения.
- [ ] Unit-тесты каталога/валидации/path traversal/rollback; ручной тест на HU
      с FAT32/exFAT флешкой и импортом одного/всех регионов.

---

## 7. Зависимости и риски

| Риск | Митигация |
|------|-----------|
| Размер карт на HU | Области, не вся РФ; сжатие; удаление |
| CPU на тике | Match редко; spatial index; бюджет времени |
| Ложный snap (двор, парковка) | Классы highway; гистерезис; max correction |
| Политика границ / Geofabrik cuts | Свои bbox-пакеты для Крыма, ДНР, ЛНР |
| Сеть на ГУ (пакеты карт) | Очередь, resume, явный UI ошибок |
| MapKit: ключ / сеть / SDK на HU | Fallback без ключа; не дублировать Android GPS; лимит overlay |
| Случайный ручной seed тени | Режим только с тенью; кнопка → confirm; disabled в edit-mode |
| USB provider не даёт открыть sibling-файлы выбранного JSON | Fallback на выбор папки через `ACTION_OPEN_DOCUMENT_TREE` |
| Флешка извлечена / файл повреждён во время импорта | Temp + SHA-256 + проверка bundle + atomic install; старую карту не удалять до commit |
| Злой/ошибочный каталог (`..`, абсолютные URI, подмена id/version) | Только относительные пути внутри SAF tree; сверять manifest с header пакета |
| ODbL | Attribution; не смешивать в закрытый датасет без share-alike |

Нет эмулятора/девайса в cloud VM — pack build и unit-тесты здесь; полевые проверки на ГУ.

---

## 8. Критерии готовности (MVP = A+B+C)

- [ ] Default off — бит-в-бит текущий DR.
- [ ] Тумблер доступен в Always-on (enhance) и «Нет фикса».
- [ ] Окно скачивания: страны из ТЗ, области, download/delete/progress.
- [ ] При вкл. + покрытие: поперечная ошибка уменьшается без скачка одометра/длины пути.
- [ ] На синтетическом «повороте» возможен switch ребра.
- [ ] Geo-debug показывает активность match.
- [ ] Документация USER_GUIDE + этот план актуален.

---

## 9. Вне скоупа MVP

- Стриминг **OSM-графа** без offline pack (match всегда с `.tboxroads`).
- Пешеходная/велосипедная сеть.
- Голосовые подсказки / полноценный навигационный UI (поворот за поворотом).
- Отдельный offline Canvas-виджет карты (если MapKit уже в продукте — не дублируем).
- Подхват позиции через Android LocationManager / `SystemLocationTracker` для map-виджета.
- Автовыбор «скачать область по GPS» без подтверждения (можно later как кнопка «Скачать текущую область»).

Этап F (MapKit + overlays + ручной seed) — **после MVP** (A+B+C), не блокирует критерии §8. Базовый MapKit-виджет может войти в продукт раньше (отдельный PR), overlays/seed — после matcher.

---

## 10. Предлагаемый порядок PR

1. **PR1 (A):** UI тумблер + окно карт + catalog stub + download manager к file storage.  
2. **PR2 (B):** format + tool + load graph + coverage check.  
3. **PR3 (C):** matcher + wire into MockLocationJob + tests + debug.  
4. **PR4 (D):** нарезка/публикация пакетов по странам из ТЗ.  
5. **PR5 (E):** полировка match / классы дорог / обновления пакетов.  
6. **PR0 / parallel:** MapKit-плитка без Android GPS — позиции из нашего pipeline (prerequisite для F).  
7. **PR6a (F1):** overlay data plane (тень/GNSS/ребро/соседи/`RoadMatchMapRenderer`) без MapKit.  
8. **PR6a продолжение (F2a):** Canvas-плитка без basemap (в той же ветке, что F1).  
9. **PR6b (F2b):** MapKit basemap + отрисовка F1 + auto-follow (без System GPS).  
10. **PR6c (F3):** ручной seed тени с карты.
11. **PR7 (G):** offline import с USB: SAF-каталог → выбор регионов → validate/hash → atomic install.
