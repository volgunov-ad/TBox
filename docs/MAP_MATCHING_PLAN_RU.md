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

1. Сервер/скрипт в `tools/` (или GitHub release assets):  
   Geofabrik PBF → фильтр `highway` нужных классов → граф рёбер (polyline + длина + class + connectivity).
2. Пакет: `*.tboxroads` (версия формата, bbox, список рёбер/узлов, gzip/zstd).
3. Приложение качает **готовый пакет** по HTTPS (CDN/GitHub Releases/свой хост), не Geofabrik напрямую с HU (нестабильно, тяжёлый PBF).

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

**Объём v1:** не все субъекты РФ сразу — начать с:

- пилотный набор (например ЦФО крупные + **Нижегородская область** + Крым + РБ целиком + KZ крупные),  
- остальное — добавлять в каталог без смены формата.

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
| Накоплено ≥ **10–15 м** пути с прошлого match | Обычный match |
| Прошло ≥ **2 с** и v ≥ порога | Обычный match |
| \|Δкурса\| за окно большой / намёк на поворот | Внеочередной match (смена ребра) |
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
  RoadMapMatcher.kt             # candidates + score + correction
  RoadMatchRuntime.kt           # throttle triggers, state (current edge)

ui/
  RoadMapsDownloadUi.kt         # hub dialog (countries → regions)
  DashboardMapKitWidget.kt      # этап F: Yandex MapKit + overlays (ветка mapkit)
  (toggle in UiPrimaryTabs near mock enhance controls)

  # Не использовать SystemLocationTracker / Android LocationManager для этого виджета —
  # позиции только из нашего pipeline (тень DR, GNSS TBox/USB, GeoDisplay).

tools/
  osm_to_tboxroads.py           # Geofabrik/OSM → .tboxroads + catalog entries
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
- [ ] USER_GUIDE черновик (короткая пометка).

**Результат:** пользователь может качать области; match ещё no-op.

### Этап B — Формат графа и tools/

- Спека `.tboxroads` v1.
- `tools/osm_to_tboxroads.py` + пример малого региона.
- Загрузка графа в `RoadGraph`, spatial query unit-tests.
- Индикатор «есть покрытие здесь» в окне карт.

### Этап C — Matcher offline + throttle

- `RoadMapMatcher` + throttle (метры/время/поворот).
- Включение в CONSTANT / WHEN_NO_FIX path.
- Geo-debug поля.
- Тесты: synthetic polyline, смена ребра на «перекрёстке», сохранение длины.

### Этап D — Каталог стран (полный охват из ТЗ)

- Пакеты: Россия (с Крымом, ДНР, ЛНР), РБ, KZ, AM, AZ, UZ — нарезка по областям.
- Хостинг пакетов + версии/обновления.
- Проверка размера/качества на HU.

### Этап E — Полировка

- Гистерезис перекрёстков, классы дорог (игнор footway), дворы.
- Политика обновления пакетов, ODbL attribution в About/окне карт.

### Этап F — Виджет карты на Yandex MapKit (после B+C + merge MapKit-плитки)

Плитка дашборда / плавающей панели на **Yandex MapKit** (ветка `cursor/yandex-mapkit-map-window-17f1` / PR mapKitWidget) — визуальная проверка match и ручная подстройка тени. **Не навигатор.**

**Базовая карта:** тайлы Яндекса (нужны `MAPKIT_API_KEY` и сеть). Offline Canvas не делаем, если MapKit уже в продукте.

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

**Ручной seed тени (обязательная часть этапа F):**

1. Кнопка на виджете («Задать тень» / аналог).
2. Тап по карте → черновая точка.
3. Подстройка направления (жест поворота / второе касание / UI курса).
4. Подтверждение → тень подтягивается к lat/lon/bearing (API в `MockLocationJob`, аналог hard-resync, но не к GNSS).
5. Доступно только в режимах с тенью (enhance / «Нет фикса»); в edit-mode плитки жесты seed выключены; желательно явное подтверждение от случайных тапов.

**Зависимости:** merge MapKit-виджета в `preRelease`; `RoadGraph` + runtime match (`edgeId`); lat/lon/bearing тени и GNSS из нашего pipeline; API seed тени.

**Критерии готовности этапа F:**

- [ ] Плитка на MapKit без Android LocationManager; показывает тень и GNSS из нашего pipeline.
- [ ] При активном match синее ребро совпадает с `edgeId` из runtime/geo-debug.
- [ ] Auto-камера: тень в центре/кадре, GNSS видна при разумном расстоянии.
- [ ] Ручной seed: тап + курс → тень переезжает; DR продолжается от новой точки.
- [ ] Нет ключа MapKit / нет сети — понятный fallback (как у текущей mapKit-плитки), без краша.
- [ ] На HU нет заметных фризов от числа polyline/placemark.

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
7. **PR6 (F):** overlays match (синее ребро, стрелки тень/GNSS, auto-камера) + ручной seed тени с карты.
